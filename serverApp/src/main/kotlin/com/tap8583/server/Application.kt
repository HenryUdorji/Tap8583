package com.tap8583.server

import com.tap8583.server.models.*
import com.tap8583.server.storage.ChannelEventLog
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.util.concurrent.CopyOnWriteArraySet

/** Largest ingest payload accepted from a terminal (one or a few jPOS events). */
private const val MAX_INGEST_BYTES = 512 * 1024

/** Largest log a viewer can paste or upload for decoding. */
private const val MAX_DECODE_BYTES = 2 * 1024 * 1024

/** Most events decoded from one pasted log. */
private const val MAX_DECODE_EVENTS = 2_000

/** Serial given to transactions decoded from a pasted log. */
private const val PASTED_LOG_SERIAL = "pasted-log"

/** How long a WebSocket client has to send its AUTH frame after connecting. */
private const val WS_AUTH_TIMEOUT_MS = 10_000L

/** WebSocket close code sent when the AUTH frame is missing or the key is wrong. */
private const val WS_CLOSE_UNAUTHORIZED: Short = 4401

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host, module = Application::module).start(wait = true)
}

/**
 * Server configuration, read from the environment.
 *
 * Two keys:
 *  - viewer key: full access (dashboard read, clear, and ingest for "Simulate")
 *  - ingest key: write-only, embedded in POS app builds — can POST events, cannot read them
 *
 * `TAP8583_API_KEY` is still honoured as the fallback for both.
 */
private class ServerConfig(
    val viewerKey: String,
    val ingestKey: String,
    val maskCardData: Boolean,
    val allowedOrigins: List<String>
) {
    companion object {
        fun fromEnv(log: org.slf4j.Logger): ServerConfig {
            fun env(name: String) = System.getenv(name)?.trim()?.takeIf { it.isNotEmpty() }

            val legacyKey = env("TAP8583_API_KEY")
            val viewerKey = env("TAP8583_VIEWER_KEY") ?: legacyKey ?: generateKey().also {
                log.warn("TAP8583_VIEWER_KEY is not set. Generated a temporary key for this run: {}", it)
            }
            val ingestKey = env("TAP8583_INGEST_KEY") ?: legacyKey ?: viewerKey

            val allowedOrigins = (env("TAP8583_ALLOWED_ORIGINS") ?: "http://localhost:3000")
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }

            return ServerConfig(
                viewerKey = viewerKey,
                ingestKey = ingestKey,
                maskCardData = env("TAP8583_MASK_CARD_DATA")?.lowercase() != "false",
                allowedOrigins = allowedOrigins
            )
        }

        private fun generateKey(): String {
            val bytes = ByteArray(24).also { SecureRandom().nextBytes(it) }
            return "tap_" + bytes.joinToString("") { "%02x".format(it) }
        }
    }
}

/** Constant-time key comparison. */
private fun keyMatches(provided: String?, expected: String): Boolean {
    if (provided.isNullOrEmpty()) return false
    return MessageDigest.isEqual(provided.toByteArray(), expected.toByteArray())
}

private fun ApplicationCall.providedKey(): String? =
    request.headers["x-api-key"]
        ?: request.headers[HttpHeaders.Authorization]?.removePrefix("Bearer ")?.trim()

/** Responds 401 and returns false unless the request carries one of [allowed]. */
private suspend fun ApplicationCall.requireKey(vararg allowed: String): Boolean {
    val key = providedKey()
    if (allowed.any { keyMatches(key, it) }) return true
    respond(HttpStatusCode.Unauthorized, mapOf("error" to "Invalid or missing API key"))
    return false
}

/** Runs the semantic decoder over both sides of a matched transaction. */
private fun decode(tx: Transaction): DecodedTransaction = DecodedTransaction(
    id = tx.id,
    stan = tx.stan,
    request = tx.request?.let { SemanticDecoder.decode(it) },
    response = tx.response?.let { SemanticDecoder.decode(it) },
    status = tx.status,
    rttMs = tx.rttMs,
    serial = tx.serial
)

fun Application.module() {
    val log = LoggerFactory.getLogger("Tap8583Server")
    val config = ServerConfig.fromEnv(log)
    log.info(
        "Card data masking: {}. Allowed browser origins: {}",
        if (config.maskCardData) "ON" else "OFF", config.allowedOrigins
    )

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
    val channelEvents = ChannelEventLog(maxEvents = 500)

    // WebSocket Subscriber Registry (authenticated clients only)
    data class ClientSession(
        val session: DefaultWebSocketSession,
        val filterSerial: String?
    )
    val wsClients = CopyOnWriteArraySet<ClientSession>()

    // Broadcast helper. Sends sequentially so clients see updates in order
    // (e.g. PENDING before APPROVED for the same transaction).
    suspend fun broadcast(msg: WebSocketMessage) {
        val jsonText = jsonConfig.encodeToString(msg)
        wsClients.forEach { client ->
            try {
                val clientSerial = client.filterSerial
                val txSerial = msg.transaction?.serial ?: msg.channelEvent?.serial
                val relevant = (msg.transaction == null && msg.channelEvent == null) ||
                    clientSerial.isNullOrBlank() ||
                    clientSerial.equals("ALL", ignoreCase = true) ||
                    clientSerial == txSerial
                if (relevant) client.session.send(Frame.Text(jsonText))
            } catch (e: Exception) {
                log.debug("Error sending frame to WebSocket client: ${e.message}")
            }
        }
    }

    // Decode, store and broadcast exactly the transaction that changed —
    // including matches against older requests and timeouts.
    matcher.events
        .onEach { event ->
            val tx = when (event) {
                is TransactionEvent.Updated -> event.transaction
                is TransactionEvent.Timeout -> event.transaction
            }
            val decodedTx = decode(tx)
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
        // Terminals are not browsers, so CORS only matters for the dashboard.
        // Behind the Caddy reverse proxy the dashboard is same-origin and needs none.
        config.allowedOrigins.forEach { origin ->
            val uri = java.net.URI(origin)
            val hostWithPort = if (uri.port > 0) "${uri.host}:${uri.port}" else uri.host
            allowHost(hostWithPort, schemes = listOf(uri.scheme))
        }
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
        maxFrameSize = 64 * 1024L
        masking = false
    }

    install(CallLogging)
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            log.error("Unhandled error processing request: ${cause.message}", cause)
            call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Internal Error"))
        }
    }

    // -----------------------------------------------------------------------
    // Routes
    // -----------------------------------------------------------------------
    routing {
        get("/health") {
            call.respond(mapOf("status" to "healthy", "service" to "Tap8583 Cloud Ingestion", "version" to "2.0.0"))
        }

        // --- HTTP Ingest from Android POS listenerLib (and the dashboard's Simulate button) ---
        post("/api/v1/events") {
            if (!call.requireKey(config.ingestKey, config.viewerKey)) return@post

            val declaredLength = call.request.contentLength()
            if (declaredLength != null && declaredLength > MAX_INGEST_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Payload too large"))
                return@post
            }

            val headerSerial = call.request.headers["x-device-serial"]
            val rawBody = call.receiveText()

            if (rawBody.length > MAX_INGEST_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Payload too large"))
                return@post
            }
            if (rawBody.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Empty XML body"))
                return@post
            }

            try {
                // Split multi-event payloads if delimiter is present
                val xmlChunks = rawBody.split("---END---").map { it.trim() }.filter { it.isNotBlank() }
                var ingested = 0

                for (chunk in xmlChunks) {
                    when (val entry = JposXmlParser.parseEntry(chunk)) {
                        is LogEntry.Message -> {
                            val event = entry.event
                            val withSerial = if (event.serial.isNullOrBlank() && !headerSerial.isNullOrBlank()) {
                                event.copy(serial = headerSerial.trim())
                            } else {
                                event
                            }
                            val safeEvent = if (config.maskCardData) CardDataMasker.mask(withSerial) else withSerial
                            matcher.ingest(safeEvent)
                        }
                        is LogEntry.ChannelError -> {
                            val event = entry.event
                            val withSerial = if (event.serial.isNullOrBlank() && !headerSerial.isNullOrBlank()) {
                                event.copy(serial = headerSerial.trim())
                            } else {
                                event
                            }
                            val safeEvent = if (config.maskCardData) CardDataMasker.mask(withSerial) else withSerial
                            channelEvents.add(safeEvent)
                            broadcast(WebSocketMessage(type = "CHANNEL_EVENT", channelEvent = safeEvent.toDto()))
                        }
                    }
                    ingested++
                }

                call.respond(HttpStatusCode.OK, IngestResponse(status = "ok", eventsIngested = ingested))
            } catch (e: Exception) {
                log.warn("XML Parse / Ingestion error: ${e.message}")
                call.respond(HttpStatusCode.UnprocessableEntity, mapOf("error" to (e.message ?: "Parse error")))
            }
        }

        // --- Decode a pasted or uploaded jPOS log (nothing is stored or broadcast) ---
        post("/api/v1/decode") {
            if (!call.requireKey(config.viewerKey)) return@post

            val declaredLength = call.request.contentLength()
            if (declaredLength != null && declaredLength > MAX_DECODE_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Log is larger than 2 MB"))
                return@post
            }
            val text = call.receiveText()
            if (text.length > MAX_DECODE_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "Log is larger than 2 MB"))
                return@post
            }

            val blocks = JposLogExtractor.extract(text, PASTED_LOG_SERIAL, MAX_DECODE_EVENTS + 1)
            val truncated = blocks.size > MAX_DECODE_EVENTS
            val errors = mutableListOf<String>()
            val decodedChannelEvents = mutableListOf<ChannelEventDto>()

            // A private matcher pairs requests and responses within this log only.
            val decodeScope = CoroutineScope(Dispatchers.Default)
            val logMatcher = TransactionMatcher(scope = decodeScope, maxRetained = MAX_DECODE_EVENTS)
            try {
                for (block in blocks.take(MAX_DECODE_EVENTS)) {
                    try {
                        when (val entry = JposXmlParser.parseEntry(block)) {
                            is LogEntry.Message -> logMatcher.ingest(
                                if (config.maskCardData) CardDataMasker.mask(entry.event) else entry.event
                            )
                            is LogEntry.ChannelError -> decodedChannelEvents += (
                                if (config.maskCardData) CardDataMasker.mask(entry.event) else entry.event
                            ).toDto()
                        }
                    } catch (e: JposXmlParser.ParseException) {
                        if (errors.size < 5) errors += (e.message ?: "Could not parse an event")
                    }
                }
                val transactions = logMatcher.transactions.value.map { decode(it).toDto() }
                call.respond(
                    DecodeLogResponse(
                        transactions = transactions,
                        channelEvents = decodedChannelEvents,
                        eventsFound = blocks.size.coerceAtMost(MAX_DECODE_EVENTS),
                        errors = errors,
                        truncated = truncated
                    )
                )
            } finally {
                logMatcher.stop()
                decodeScope.cancel()
            }
        }

        // --- Device List API ---
        get("/api/v1/devices") {
            if (!call.requireKey(config.viewerKey)) return@get
            call.respond(store.getDevices())
        }

        // --- Transaction History REST API ---
        get("/api/v1/transactions") {
            if (!call.requireKey(config.viewerKey)) return@get
            val serial = call.request.queryParameters["serial"]
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            call.respond(store.getTransactions(serial, limit).map { it.toDto() })
        }

        // --- Clear Transactions API ---
        post("/api/v1/clear") {
            if (!call.requireKey(config.viewerKey)) return@post
            val serial = call.request.queryParameters["serial"]
                ?.takeUnless { it.isBlank() || it.equals("ALL", ignoreCase = true) }
            matcher.clear(serial)
            store.clear(serial)
            channelEvents.clear(serial)
            broadcast(WebSocketMessage(type = "CLEAR", serial = serial))
            call.respond(mapOf("status" to "cleared"))
        }

        // --- Real-time WebSocket Stream for the web dashboard ---
        // The client authenticates with its first frame:
        //   {"type":"AUTH","apiKey":"…","serial":"ALL"}
        // so the key never appears in URLs or access logs.
        webSocket("/ws/stream") {
            val auth = withTimeoutOrNull(WS_AUTH_TIMEOUT_MS) {
                val frame = incoming.receive()
                (frame as? Frame.Text)?.readText()?.let {
                    runCatching { jsonConfig.decodeFromString<WsAuthMessage>(it) }.getOrNull()
                }
            }

            if (auth == null || auth.type != "AUTH" || !keyMatches(auth.apiKey, config.viewerKey)) {
                close(CloseReason(WS_CLOSE_UNAUTHORIZED, "Unauthorized"))
                return@webSocket
            }

            val clientSerial = auth.serial?.takeUnless { it.isBlank() || it.equals("ALL", ignoreCase = true) }
            val client = ClientSession(this, clientSerial)
            wsClients.add(client)
            log.info("✓ WebSocket client connected [filter: ${clientSerial ?: "ALL"}] (Total clients: ${wsClients.size})")

            try {
                // Send initial snapshot of last 50 transactions + device list
                val initMessage = WebSocketMessage(
                    type = "INIT_SNAPSHOT",
                    transactions = store.getTransactions(clientSerial, 50).map { it.toDto() },
                    channelEvents = channelEvents.recent(clientSerial, 100).map { it.toDto() },
                    devices = store.getDevices()
                )
                send(Frame.Text(jsonConfig.encodeToString(initMessage)))

                // Keep the connection open; client commands are not used yet.
                for (frame in incoming) { /* ignore */ }
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
