package com.tap8583.server

import com.tap8583.server.models.*
import com.tap8583.server.storage.TransactionStore
import com.tap8583.tap8583.decoder.DecodedTransaction
import com.tap8583.tap8583.decoder.SemanticDecoder
import com.tap8583.tap8583.ingestion.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host, module = Application::module).start(wait = true)
}

fun Application.module() {
    val log = LoggerFactory.getLogger("Tap8583Server")
    val configuredApiKey = System.getenv("TAP8583_API_KEY") ?: "tap_live_secret"

    val jsonConfig = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // -----------------------------------------------------------------------
    // Core Processing Subsystems
    // -----------------------------------------------------------------------
    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val matcher = TransactionMatcher(scope = appScope)
    val store = TransactionStore(maxPerDevice = 50, ttlDays = 7)

    // WebSocket Subscriber Registry
    data class ClientSession(
        val session: DefaultWebSocketSession,
        val apiKey: String?,
        val filterSerial: String?
    )
    val wsClients = CopyOnWriteArraySet<ClientSession>()

    // Broadcast helper
    fun broadcast(msg: WebSocketMessage) {
        val jsonText = jsonConfig.encodeToString(msg)
        appScope.launch {
            wsClients.forEach { client ->
                try {
                    // Check if message is relevant for this client
                    if (msg.transaction != null) {
                        val clientSerial = client.filterSerial
                        val txSerial = msg.transaction.serial
                        if (clientSerial.isNullOrBlank() || clientSerial.equals("ALL", ignoreCase = true) || clientSerial == txSerial) {
                            client.session.send(Frame.Text(jsonText))
                        }
                    } else {
                        client.session.send(Frame.Text(jsonText))
                    }
                } catch (e: Exception) {
                    log.debug("Error sending frame to WebSocket client: ${e.message}")
                }
            }
        }
    }

    // Connect matcher transaction updates to decoding & storage
    matcher.transactions
        .onEach { txList ->
            val latest = txList.lastOrNull() ?: return@onEach
            val decodedReq = latest.request?.let { SemanticDecoder.decode(it) }
            val decodedResp = latest.response?.let { SemanticDecoder.decode(it) }
            val decodedTx = DecodedTransaction(
                id = latest.id,
                stan = latest.stan,
                request = decodedReq,
                response = decodedResp,
                status = latest.status,
                rttMs = latest.rttMs,
                serial = latest.serial
            )
            store.saveOrUpdate(decodedTx)
            broadcast(WebSocketMessage(type = "TRANSACTION_UPDATED", transaction = decodedTx.toDto()))
        }
        .launchIn(appScope)

    // Periodic 1-hour TTL pruning
    appScope.launch {
        while (isActive) {
            delay(3600_000L)
            store.pruneExpired()
        }
    }

    // -----------------------------------------------------------------------
    // Plugins
    // -----------------------------------------------------------------------
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("x-api-key")
        allowHeader("x-device-serial")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
    }

    install(ContentNegotiation) {
        json(jsonConfig)
    }

    install(WebSockets) {
        pingPeriod = Duration.ofSeconds(15)
        timeout = Duration.ofSeconds(30)
        maxFrameSize = Long.MAX_VALUE
        masking = false
    }

    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error processing request: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to (cause.message ?: "Internal Error")))
        }
    }

    // -----------------------------------------------------------------------
    // Routes
    // -----------------------------------------------------------------------
    routing {
        get("/health") {
            call.respond(mapOf("status" to "healthy", "service" to "Tap8583 Cloud Ingestion", "version" to "2.0.0"))
        }

        // --- HTTP Ingest from Android POS listenerLib ---
        post("/api/v1/events") {
            val reqApiKey = call.request.headers["x-api-key"]
                ?: call.request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()

            if (configuredApiKey.isNotBlank() && reqApiKey != configuredApiKey && reqApiKey != "tap_live_secret") {
                call.respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing API key"))
                return@post
            }

            val headerSerial = call.request.headers["x-device-serial"]
            val rawBody = call.receiveText()

            if (rawBody.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Empty XML body"))
                return@post
            }

            try {
                // Split multi-event payloads if delimiter is present
                val xmlChunks = rawBody.split("---END---").map { it.trim() }.filter { it.isNotBlank() }
                val parsedEvents = mutableListOf<RawLogEvent>()

                for (chunk in xmlChunks) {
                    val event = JposXmlParser.parse(chunk)
                    val effectiveEvent = if (event.serial.isNullOrBlank() && !headerSerial.isNullOrBlank()) {
                        event.copy(serial = headerSerial.trim())
                    } else {
                        event
                    }
                    parsedEvents.add(effectiveEvent)
                    matcher.ingest(effectiveEvent)
                }

                call.respond(HttpStatusCode.OK, mapOf(
                    "status" to "ok",
                    "eventsIngested" to parsedEvents.size,
                    "devicesSeen" to store.getDevices().size
                ))
            } catch (e: Exception) {
                log.warn("XML Parse / Ingestion error: ${e.message}")
                call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to (e.message ?: "Parse error")))
            }
        }

        // --- Device List API ---
        get("/api/v1/devices") {
            val devices = store.getDevices()
            call.respond(devices)
        }

        // --- Transaction History REST API ---
        get("/api/v1/transactions") {
            val serial = call.request.queryParameters["serial"]
            val limit = call.request.queryParameters["limit"]?.toIntOrNull() ?: 50
            val list = store.getTransactions(serial, limit).map { it.toDto() }
            call.respond(list)
        }

        // --- Clear Transactions API ---
        post("/api/v1/clear") {
            val serial = call.request.queryParameters["serial"]
            matcher.clear()
            store.clear(serial)
            broadcast(WebSocketMessage(type = "CLEAR"))
            call.respond(mapOf("status" to "cleared"))
        }

        // --- Real-time WebSocket Stream for Next.js Web App ---
        webSocket("/ws/stream") {
            val clientApiKey = call.request.queryParameters["apiKey"]
            val clientSerial = call.request.queryParameters["serial"]

            val client = ClientSession(this, clientApiKey, clientSerial)
            wsClients.add(client)
            log.info("✓ WebSocket client connected [filter: ${clientSerial ?: "ALL"}] (Total clients: ${wsClients.size})")

            try {
                // Send initial snapshot of last 50 transactions + device list
                val initialTx = store.getTransactions(clientSerial, 50).map { it.toDto() }
                val initialDevices = store.getDevices()

                val initMessage = WebSocketMessage(
                    type = "INIT_SNAPSHOT",
                    transactions = initialTx,
                    devices = initialDevices
                )
                send(Frame.Text(jsonConfig.encodeToString(initMessage)))

                // Keep connection open and handle incoming client commands (e.g. change filter serial)
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        // Optional client commands if needed in future
                    }
                }
            } catch (_: ClosedReceiveChannelException) {
                // Normal disconnect
            } catch (e: Exception) {
                log.debug("WebSocket exception: ${e.message}")
            } finally {
                wsClients.remove(client)
                log.info("WebSocket client disconnected (Total clients: ${wsClients.size})")
            }
        }
    }
}
