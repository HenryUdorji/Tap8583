package com.tap8583.server

import com.tap8583.server.accounts.*
import com.tap8583.server.db.Db
import com.tap8583.server.models.*
import com.tap8583.server.storage.InMemoryStorage
import com.tap8583.server.storage.PostgresStorage
import com.tap8583.server.storage.Storage
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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration

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

/** WebSocket close code sent when the AUTH frame is missing, the key is wrong or the person isn't a member. */
private const val WS_CLOSE_UNAUTHORIZED: Short = 4401

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val host = System.getenv("HOST") ?: "0.0.0.0"
    embeddedServer(CIO, port = port, host = host, module = Application::module).start(wait = true)
}

fun Application.module() {
    val log = LoggerFactory.getLogger("Tap8583Server")
    module(ServerConfig.fromEnv(log))
}

/** [mailerOverride] lets tests capture sign-in emails. */
fun Application.module(config: ServerConfig, mailerOverride: Mailer? = null) {
    val log = LoggerFactory.getLogger("Tap8583Server")

    val jsonConfig = Json {
        prettyPrint = false
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // -----------------------------------------------------------------------
    // Storage, accounts and sign-in
    // -----------------------------------------------------------------------
    val db = config.database?.let { Db.connect(it) }
    val storage: Storage = if (db != null) PostgresStorage(db, jsonConfig) else InMemoryStorage()
    val accounts = db?.let { Accounts(it) }
    val access = AccessControl(config, accounts)

    val mailer: Mailer = when {
        mailerOverride != null -> mailerOverride
        config.smtp != null -> SmtpMailer(config.smtp)
        config.logEmailLinks -> LogMailer(log)
        else -> NoMailer
    }

    if (accounts != null) {
        log.info("Accounts mode: Postgres connected. History kept {} days.", config.retentionDays)
        if (!mailer.canSend) log.warn("SMTP_HOST is not set, so nobody can sign in. Set SMTP_* (or TAP8583_LOG_EMAIL_LINKS=true locally).")
    } else {
        log.info("Shared-key mode (no DATABASE_URL): one viewer key, one ingest key, history in memory.")
    }
    log.info(
        "Card data masking: {}. Allowed browser origins: {}",
        if (config.maskCardData) "ON" else "OFF", config.allowedOrigins
    )

    val appScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val hub = LiveHub(appScope, storage, jsonConfig, log)

    // Hourly housekeeping: retention and expired sessions/links.
    appScope.launch {
        while (isActive) {
            try {
                storage.pruneOlderThan(config.retentionDays)
                accounts?.pruneExpired()
            } catch (e: Exception) {
                log.warn("Housekeeping failed: {}", e.message)
            }
            delay(3600_000L)
        }
    }

    environment.monitor.subscribe(ApplicationStopped) {
        appScope.cancel()
        (db?.dataSource as? AutoCloseable)?.close()
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
        allowCredentials = true
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader("x-api-key")
        allowHeader("x-device-serial")
        allowMethod(HttpMethod.Options)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
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
            call.respond(HttpStatusCode.InternalServerError, ErrorDto("Internal Error"))
        }
    }

    // -----------------------------------------------------------------------
    // Routes
    // -----------------------------------------------------------------------
    routing {
        get("/health") {
            call.respond(mapOf("status" to "healthy", "service" to "Tap8583 Cloud Ingestion", "version" to "2.0.0"))
        }

        accountRoutes(config, access, mailer, log)

        // --- HTTP Ingest from Android POS listenerLib (and the dashboard's Simulate button) ---
        post("/api/v1/events") {
            val workspaceId = access.requireIngest(call) ?: return@post

            val declaredLength = call.request.contentLength()
            if (declaredLength != null && declaredLength > MAX_INGEST_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorDto("Payload too large"))
                return@post
            }

            val headerSerial = call.request.headers["x-device-serial"]?.trim()?.takeIf { it.isNotEmpty() }
            val rawBody = call.receiveText()

            if (rawBody.length > MAX_INGEST_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorDto("Payload too large"))
                return@post
            }
            if (rawBody.isBlank()) {
                call.respond(HttpStatusCode.BadRequest, ErrorDto("Empty XML body"))
                return@post
            }

            try {
                // Split multi-event payloads if delimiter is present
                val xmlChunks = rawBody.split("---END---").map { it.trim() }.filter { it.isNotBlank() }
                var ingested = 0

                for (chunk in xmlChunks) {
                    when (val entry = JposXmlParser.parseEntry(chunk)) {
                        is LogEntry.Message -> {
                            val event = entry.event.let {
                                if (it.serial.isNullOrBlank() && headerSerial != null) it.copy(serial = headerSerial) else it
                            }
                            hub.ingest(workspaceId, if (config.maskCardData) CardDataMasker.mask(event) else event)
                        }
                        is LogEntry.ChannelError -> {
                            val event = entry.event.let {
                                if (it.serial.isNullOrBlank() && headerSerial != null) it.copy(serial = headerSerial) else it
                            }
                            hub.channelError(workspaceId, if (config.maskCardData) CardDataMasker.mask(event) else event)
                        }
                    }
                    ingested++
                }

                call.respond(HttpStatusCode.OK, IngestResponse(status = "ok", eventsIngested = ingested))
            } catch (e: JposXmlParser.ParseException) {
                log.warn("XML parse error: ${e.message}")
                call.respond(HttpStatusCode.UnprocessableEntity, ErrorDto(e.message ?: "Parse error"))
            }
        }

        // --- Decode a pasted or uploaded jPOS log (nothing is stored or broadcast) ---
        post("/api/v1/decode") {
            if (access.accountsMode) access.requireUser(call) ?: return@post
            else access.requireViewer(call) ?: return@post

            val declaredLength = call.request.contentLength()
            if (declaredLength != null && declaredLength > MAX_DECODE_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorDto("Log is larger than 2 MB"))
                return@post
            }
            val text = call.receiveText()
            if (text.length > MAX_DECODE_BYTES) {
                call.respond(HttpStatusCode.PayloadTooLarge, ErrorDto("Log is larger than 2 MB"))
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
            val workspaceId = access.requireViewer(call) ?: return@get
            call.respond(storage.devices(workspaceId))
        }

        // --- Transaction History REST API ---
        get("/api/v1/transactions") {
            val workspaceId = access.requireViewer(call) ?: return@get
            val serial = call.request.queryParameters["serial"]
                ?.takeUnless { it.isBlank() || it.equals("ALL", ignoreCase = true) }
            val limit = (call.request.queryParameters["limit"]?.toIntOrNull() ?: 50).coerceIn(1, 200)
            call.respond(storage.recentTransactions(workspaceId, serial, limit))
        }

        // --- Clear Transactions API ---
        post("/api/v1/clear") {
            val workspaceId = access.requireViewer(call) ?: return@post
            val serial = call.request.queryParameters["serial"]
                ?.takeUnless { it.isBlank() || it.equals("ALL", ignoreCase = true) }
            hub.clear(workspaceId, serial)
            call.respond(mapOf("status" to "cleared"))
        }

        // --- Real-time WebSocket Stream for the web dashboard ---
        // The client authenticates with its first frame, so no secret appears in URLs or logs:
        //   shared-key mode: {"type":"AUTH","apiKey":"…","serial":"ALL"}
        //   accounts mode:   {"type":"AUTH","workspace":"<id>","serial":"ALL"} plus the session cookie
        webSocket("/ws/stream") {
            val auth = withTimeoutOrNull(WS_AUTH_TIMEOUT_MS) {
                val frame = incoming.receive()
                (frame as? Frame.Text)?.readText()?.let {
                    runCatching { jsonConfig.decodeFromString<WsAuthMessage>(it) }.getOrNull()
                }
            }
            val workspaceId = auth?.takeIf { it.type == "AUTH" }
                ?.let { access.streamWorkspace(call, it.apiKey, it.workspace) }
            if (auth == null || workspaceId == null) {
                close(CloseReason(WS_CLOSE_UNAUTHORIZED, "Unauthorized"))
                return@webSocket
            }

            val clientSerial = auth.serial?.takeUnless { it.isBlank() || it.equals("ALL", ignoreCase = true) }
            val client = LiveHub.Client(this, workspaceId, clientSerial)
            hub.add(client)
            log.info("✓ WebSocket client connected [filter: ${clientSerial ?: "ALL"}] (Total clients: ${hub.clientCount})")

            try {
                // Send initial snapshot of last 50 transactions + device list
                val initMessage = WebSocketMessage(
                    type = "INIT_SNAPSHOT",
                    transactions = storage.recentTransactions(workspaceId, clientSerial, 50),
                    channelEvents = storage.recentChannelEvents(workspaceId, clientSerial, 100),
                    devices = storage.devices(workspaceId)
                )
                send(Frame.Text(jsonConfig.encodeToString(initMessage)))

                // Keep the connection open; client commands are not used yet.
                for (frame in incoming) { /* ignore */ }
            } catch (_: ClosedReceiveChannelException) {
                // Normal disconnect
            } catch (e: Exception) {
                log.debug("WebSocket exception: ${e.message}")
            } finally {
                hub.remove(client)
                log.info("WebSocket client disconnected (Total clients: ${hub.clientCount})")
            }
        }
    }
}
