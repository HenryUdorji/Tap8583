package dev.sarari.tap8583.listener

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jpos.core.Configurable
import org.jpos.core.Configuration
import org.jpos.iso.ISOMsg
import org.jpos.util.LogEvent
import org.jpos.util.LogListener
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * VisualizerLogListener (Cloud & HTTPS Edition)
 * ─────────────────────────────────────────────
 * A lightweight jPOS [LogListener] that captures and forwards ISO 8583 transaction
 * events to the Tap8583 cloud visualizer over HTTPS.
 *
 * Designed for seamless QA & Developer workflows on Android POS terminals with
 * ZERO manual local IP/port configuration.
 *
 * ## Quick start (one-line setup in Android `Application.onCreate()`):
 * ```kotlin
 * VisualizerLogListener.install(
 *     apiKey = "YOUR_INGEST_KEY",
 *     endpoint = "https://your-tap8583-host/api/v1/events"
 * )
 * ```
 */
class VisualizerLogListener @JvmOverloads constructor(
    private var apiKey: String = "",
    private var endpoint: String = "",
    private var deviceSerial: String? = null,
    private val queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
) : LogListener, Configurable {

    companion object {
        private const val DEFAULT_QUEUE_CAPACITY = 100
        private val XML_MEDIA_TYPE = "application/xml; charset=utf-8".toMediaType()

        private val log = Logger.getLogger("VisualizerLogListener")
        private val ACCEPTED_TAGS = setOf("send", "receive")
        private val ISO_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

        @Volatile
        private var globalInstance: VisualizerLogListener? = null

        /**
         * Convenience one-line installer for Android apps.
         * Auto-resolves hardware serial and hooks into jPOS Q2 Logger if available.
         */
        @JvmStatic
        @JvmOverloads
        fun install(
            apiKey: String,
            endpoint: String,
            deviceSerial: String? = null
        ): VisualizerLogListener {
            val listener = VisualizerLogListener(
                apiKey = apiKey,
                endpoint = endpoint,
                deviceSerial = deviceSerial
            )
            globalInstance = listener

            // Attempt auto-registration with jPOS Logger if present on classpath
            try {
                val q2LoggerClass = Class.forName("org.jpos.util.Logger")
                val getLoggerMethod = q2LoggerClass.getMethod("getLogger", String::class.java)
                val loggerInstance = getLoggerMethod.invoke(null, "Q2")
                val addListenerMethod = q2LoggerClass.getMethod("addListener", LogListener::class.java)
                addListenerMethod.invoke(loggerInstance, listener)
                log.info("✓ Auto-registered VisualizerLogListener with jPOS Q2 logger")
            } catch (t: Throwable) {
                log.info("jPOS Logger auto-registration skipped (register manually via Logger.addListener or XML): ${t.message}")
            }

            return listener
        }
    }

    private val resolvedSerial: String by lazy {
        DeviceUtils.resolveDeviceSerial(deviceSerial)
    }

    private val eventQueue = ArrayBlockingQueue<String>(queueCapacity)
    private val isRunning = AtomicBoolean(true)
    private val missingEndpointWarned = AtomicBoolean(false)

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    init {
        log.info("Initialising Tap8583 Cloud Listener [Serial: $resolvedSerial, Target: $endpoint]")
        startDispatcherThread()
    }

    // -----------------------------------------------------------------------
    // Configurable (jPOS Q2 XML integration)
    // -----------------------------------------------------------------------

    override fun setConfiguration(cfg: Configuration) {
        apiKey = cfg.get("apiKey", cfg.get("api-key", apiKey))
        endpoint = cfg.get("endpoint", endpoint)
        deviceSerial = cfg.get("deviceSerial", deviceSerial)
        val enabled = cfg.getBoolean("enabled", true)

        log.info("Configured VisualizerLogListener: endpoint=$endpoint, enabled=$enabled, serial=$resolvedSerial")
        if (!enabled) {
            close()
        }
    }

    // -----------------------------------------------------------------------
    // LogListener
    // -----------------------------------------------------------------------

    /**
     * Called by jPOS on every log event.
     * Enqueues immediately without blocking the transaction execution thread.
     */
    override fun log(ev: LogEvent): LogEvent {
        if (!isRunning.get()) return ev
        if (ev.tag !in ACCEPTED_TAGS) return ev

        val rawPayload = try {
            ev.payLoad
        } catch (e: Exception) {
            log.log(Level.WARNING, "Could not read event payload", e)
            null
        }

        val collectionPayload = when (rawPayload) {
            is Collection<*> -> rawPayload
            is Iterable<*> -> rawPayload.toList()
            else -> if (rawPayload != null) listOf(rawPayload) else null
        }

        val isoMsg = collectionPayload?.filterIsInstance<ISOMsg>()?.firstOrNull() ?: return ev

        try {
            val xmlPayload = buildXml(ev, isoMsg)
            val offered = eventQueue.offer(xmlPayload)
            if (!offered) {
                log.warning("Tap8583 event queue full — dropping event to prevent latency")
            }
        } catch (t: Throwable) {
            log.log(Level.WARNING, "Failed to enqueue ISO event: ${t.message}", t)
        }

        return ev
    }

    // -----------------------------------------------------------------------
    // Background HTTP Dispatcher
    // -----------------------------------------------------------------------

    private fun startDispatcherThread() {
        val thread = Thread {
            while (isRunning.get() || eventQueue.isNotEmpty()) {
                try {
                    val xml = eventQueue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                    sendEventOverHttp(xml)
                } catch (_: InterruptedException) {
                    break
                } catch (t: Throwable) {
                    log.log(Level.FINE, "Dispatcher error: ${t.message}", t)
                }
            }
        }
        thread.name = "tap8583-http-dispatcher"
        thread.isDaemon = true
        thread.start()
    }

    private fun sendEventOverHttp(xml: String) {
        if (endpoint.isBlank()) {
            if (missingEndpointWarned.compareAndSet(false, true)) {
                log.warning("Tap8583 endpoint is not configured — events are not being sent")
            }
            return
        }
        try {
            val body = xml.toRequestBody(XML_MEDIA_TYPE)
            val requestBuilder = Request.Builder()
                .url(endpoint)
                .post(body)
                .addHeader("x-device-serial", resolvedSerial)
                .addHeader("x-client-version", "2.0.0")

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }

            httpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    log.warning("Ingest server returned error code ${response.code}: ${response.message}")
                }
            }
        } catch (e: Exception) {
            log.log(Level.FINE, "Could not reach Tap8583 ingest endpoint: ${e.message}")
        }
    }

    // -----------------------------------------------------------------------
    // XML Serialisation
    // -----------------------------------------------------------------------

    private fun buildXml(ev: LogEvent, isoMsg: ISOMsg): String {
        val createdAt: Instant = try {
            val f = ev.javaClass.getDeclaredField("createdAt")
            f.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            f.get(ev) as Instant
        } catch (_: Throwable) {
            Instant.now()
        }

        val timestamp = ISO_FORMAT.format(createdAt)
        val lifespanMs = Duration.between(createdAt, Instant.now()).toMillis()

        val realm = escapeXml(try { ev.realm ?: "unknown" } catch (_: Throwable) { "unknown" })
        val tag = escapeXml(try { ev.tag ?: "send" } catch (_: Throwable) { "send" })
        val isoMsgXml = dumpIsoMsg(isoMsg)

        return buildString {
            append("<visualizer-event")
            append(" realm=\"$realm\"")
            append(" at=\"$timestamp\"")
            append(" lifespan=\"${lifespanMs}ms\"")
            append(" tag=\"$tag\"")
            append(" serial=\"$resolvedSerial\"")
            append(">\n")
            append(isoMsgXml)
            append("</visualizer-event>\n")
            append("---END---\n")
        }
    }

    private fun dumpIsoMsg(isoMsg: ISOMsg): String {
        return try {
            val baos = ByteArrayOutputStream(4096)
            val ps = PrintStream(baos, true, "UTF-8")
            isoMsg.dump(ps, "  ")
            ps.flush()
            baos.toString("UTF-8")
        } catch (t: Throwable) {
            fallbackDump(isoMsg)
        }
    }

    private fun fallbackDump(isoMsg: ISOMsg): String = buildString {
        val direction = when {
            isoMsg.isRequest -> "outgoing"
            isoMsg.isResponse -> "incoming"
            else -> "unknown"
        }
        append("  <isomsg direction=\"$direction\">\n")
        for (i in 0..128) {
            try {
                val value = isoMsg.getString(i) ?: continue
                append("    <field id=\"$i\" value=\"${escapeXml(value)}\"/>\n")
            } catch (_: Exception) { }
        }
        append("  </isomsg>\n")
    }

    fun close() {
        isRunning.set(false)
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("'", "&apos;")
}
