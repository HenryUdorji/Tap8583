package com.tap8583.visualizer

import org.jpos.core.Configurable
import org.jpos.core.Configuration
import org.jpos.iso.ISOMsg
import org.jpos.util.LogEvent
import org.jpos.util.LogListener
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.io.PrintWriter
import java.net.Socket
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Logger

/**
 * VisualizerLogListener
 * ─────────────────────
 * A jPOS [LogListener] that forwards transaction events to the Tap8583
 * desktop visualizer over a plain TCP socket on your local network.
 *
 * ## Quick start
 *
 * ```kotlin
 * // 1. Start the desktop app and note the IP shown in its status bar
 * //    (e.g. "Connect to: 192.168.1.42:9999")
 *
 * // 2. Add the listener at POS startup — typically Application.onCreate()
 * val visualizer = VisualizerLogListener(
 *     host = "192.168.1.42",   // desktop machine's LAN/WiFi IP
 *     port = 9999
 * )
 * Logger.getLogger("Q2").addListener(visualizer)
 * ```
 *
 * ## Logging
 * The lib logs through `java.util.logging` under the tag `"VisualizerLogListener"`.
 * On Android this routes automatically to Logcat. Filter by the tag to see all activity.
 *
 * @param host  Desktop machine's LAN/WiFi IP address (shown in the visualizer's status bar)
 * @param port  TCP port the desktop app listens on (default 9999)
 * @param maxReconnectAttempts  Retries before giving up (default 10)
 * @param reconnectDelayMs  Base delay between retries — doubles each attempt, capped at 30 s (default 2000)
 */
class VisualizerLogListener @JvmOverloads constructor(
    private var host: String = "localhost",
    private var port: Int = 9999,
    private val maxReconnectAttempts: Int = 10,
    private val reconnectDelayMs: Long = 2_000L
) : LogListener, Configurable {

    companion object {
        private val log = Logger.getLogger("VisualizerLogListener")
        private val ACCEPTED_TAGS = setOf("send", "receive")
        private val ISO_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    }

    // -----------------------------------------------------------------------
    // Connection state
    // -----------------------------------------------------------------------

    @Volatile private var writer: PrintWriter? = null
    private val connected    = AtomicBoolean(false)
    private val reconnecting = AtomicBoolean(false)

    init {
        log.info("Initialising — will connect to $host:$port")
        connectAsync()
    }

    // -----------------------------------------------------------------------
    // Configurable
    // -----------------------------------------------------------------------

    override fun setConfiguration(cfg: Configuration) {
        val newHost = cfg.get("host", "localhost")
        val newPort = cfg.getInt("port", 9999)
        val enabled = cfg.getBoolean("enabled", true)

        log.info("Configuring VisualizerLogListener: host=$newHost, port=$newPort, enabled=$enabled")

        if (!enabled) {
            close()
            return
        }

        if (newHost != host || newPort != port) {
            log.info("Configuration changed (from $host:$port to $newHost:$newPort) — reconnecting…")
            host = newHost
            port = newPort
            reconnect()
        }
    }

    // -----------------------------------------------------------------------
    // LogListener
    // -----------------------------------------------------------------------

    /**
     * Called by jPOS on every log event.
     * Returns immediately regardless of connection state — never blocks the transaction thread.
     */
    override fun log(ev: LogEvent): LogEvent {
        log.info("log() called with event tag: ${ev.tag}")

        if (ev.tag !in ACCEPTED_TAGS) {
            log.info("Skipped event — tag '${ev.tag}' is not in accepted tags $ACCEPTED_TAGS")
            return ev
        }

        val rawPayload = try {
            ev.payLoad
        } catch (e: Exception) {
            log.log(Level.WARNING, "Could not read event payload", e)
            null
        }

        // Support standard Collection, Iterable, or even single object payload robustly
        val collectionPayload = when (rawPayload) {
            is Collection<*> -> rawPayload
            is Iterable<*> -> rawPayload.toList()
            else -> if (rawPayload != null) listOf(rawPayload) else null
        }

        val isoMsg = collectionPayload?.filterIsInstance<ISOMsg>()?.firstOrNull()

        if (isoMsg == null) {
            val payloadClasses = collectionPayload?.map { it?.javaClass?.name ?: "null" } ?: listOf("null")
            log.warning("Skipped ${ev.tag} event — no ISOMsg found in payload. Payload items: $payloadClasses")
            return ev
        }

        if (!connected.get()) {
            log.info("Dropped ${ev.tag} event — not connected to $host:$port")
            return ev
        }

        try {
            val mti = try { isoMsg.mti } catch (t: Throwable) { "unknown" }
            log.info("Forwarding ${ev.tag} event with MTI $mti to $host:$port")

            val xml = buildXml(ev, isoMsg)
            val w = writer ?: run {
                log.warning("Dropped ${ev.tag} event — writer is null")
                return ev
            }
            synchronized(w) {
                w.print(xml)
                w.flush()
                if (w.checkError()) {
                    log.warning("Write error detected — connection to $host:$port lost, reconnecting…")
                    handleDisconnect()
                } else {
                    log.info("✓ Successfully forwarded ${ev.tag} event to $host:$port")
                }
            }
        } catch (t: Throwable) {
            log.log(Level.WARNING, "Failed to forward ${ev.tag} event: ${t.message}", t)
            handleDisconnect()
        }
        return ev
    }

    // -----------------------------------------------------------------------
    // XML serialisation
    // -----------------------------------------------------------------------

    private fun buildXml(ev: LogEvent, isoMsg: ISOMsg): String {
        val createdAt: Instant = try {
            val f = ev.javaClass.getDeclaredField("createdAt")
            f.isAccessible = true
            // Android desugaring wraps java.time as j$.time; the value is
            // assignment-compatible with java.time.Instant at runtime.
            @Suppress("UNCHECKED_CAST")
            f.get(ev) as Instant
        } catch (t: Throwable) {
            log.log(Level.WARNING, "Could not read createdAt field", t)
            Instant.now()
        }

        val timestamp = ISO_FORMAT.format(createdAt)
        val lifespanMs = Duration.between(createdAt, Instant.now()).toMillis()

        val realm     = escapeXml(try { ev.realm ?: "unknown" } catch (t: Throwable) { "unknown" })
        val tag       = escapeXml(try { ev.tag   ?: "send"    } catch (t: Throwable) { "send"    })
        val isoMsgXml = dumpIsoMsg(isoMsg)

        return buildString {
            append("<visualizer-event")
            append(" realm=\"$realm\"")
            append(" at=\"$timestamp\"")
            append(" lifespan=\"${lifespanMs}ms\"")
            append(" tag=\"$tag\"")
            append(">\n")
            append(isoMsgXml)
            append("</visualizer-event>\n")
            append("---END---\n")
        }
    }

    /**
     * Dumps the ISOMsg to XML using jPOS's built-in `Loggeable.dump()`.
     * Falls back to a hand-rolled representation if the packager is not set.
     */
    private fun dumpIsoMsg(isoMsg: ISOMsg): String {
        return try {
            val baos = ByteArrayOutputStream(4096)
            val ps   = PrintStream(baos, true, "UTF-8")
            isoMsg.dump(ps, "  ")
            ps.flush()
            baos.toString("UTF-8")
        } catch (t: Throwable) {
            log.log(Level.FINE, "ISOMsg.dump() failed — using fallback serialiser", t)
            fallbackDump(isoMsg)
        }
    }

    private fun fallbackDump(isoMsg: ISOMsg): String = buildString {
        val direction = when {
            isoMsg.isRequest  -> "outgoing"
            isoMsg.isResponse -> "incoming"
            else              -> "unknown"
        }
        append("  <isomsg direction=\"$direction\">\n")
        for (i in 0..128) {
            try {
                val value = isoMsg.getString(i) ?: continue
                append("    <field id=\"$i\" value=\"${escapeXml(value)}\"/>\n")
            } catch (e: Exception) { /* field not set — skip */ }
        }
        append("  </isomsg>\n")
    }

    // -----------------------------------------------------------------------
    // Connection management
    // -----------------------------------------------------------------------

    private fun connectAsync() {
        if (reconnecting.getAndSet(true)) {
            log.fine("connectAsync() called but already reconnecting — skipping")
            return
        }

        val thread = Thread {
            log.info("Starting connection loop to $host:$port (max $maxReconnectAttempts attempts)")
            for (attempt in 1..maxReconnectAttempts) {
                log.info("Connecting to $host:$port — attempt $attempt/$maxReconnectAttempts")
                try {
                    val socket = Socket(host, port).apply {
                        keepAlive  = true
                        soTimeout  = 0        // no read timeout — we only write
                        tcpNoDelay = true
                    }
                    val pw = PrintWriter(socket.outputStream.bufferedWriter(Charsets.UTF_8))
                    writer    = pw
                    connected.set(true)
                    reconnecting.set(false)
                    log.info("✓ Connected to $host:$port")
                    return@Thread
                } catch (e: Exception) {
                    val delay = (reconnectDelayMs * attempt).coerceAtMost(30_000L)
                    log.warning(
                        "Connection attempt $attempt/$maxReconnectAttempts to $host:$port failed" +
                        " (${e.message}) — retrying in ${delay}ms"
                    )
                    Thread.sleep(delay)
                }
            }
            log.severe(
                "All $maxReconnectAttempts connection attempts to $host:$port failed — giving up." +
                " Call reconnect() to try again."
            )
            reconnecting.set(false)
        }
        thread.name     = "visualizer-connect"
        thread.isDaemon = true
        thread.start()
    }

    private fun handleDisconnect() {
        connected.set(false)
        writer = null
        log.info("Disconnected from $host:$port — triggering reconnect")
        connectAsync()
    }

    // -----------------------------------------------------------------------
    // Public control API
    // -----------------------------------------------------------------------

    /**
     * Manually triggers a reconnect attempt.
     * Useful if the desktop app was restarted.
     */
    fun reconnect() {
        log.info("Manual reconnect requested")
        handleDisconnect()
    }

    /**
     * Permanently closes this listener.
     * Call when the POS application is shutting down.
     */
    fun close() {
        log.info("Closing VisualizerLogListener — no further reconnects will be attempted")
        connected.set(false)
        reconnecting.set(true)   // prevents future connectAsync() from doing anything
        try { writer?.close() } catch (e: Exception) {
            log.log(Level.FINE, "Error closing writer", e)
        }
        writer = null
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private fun escapeXml(s: String): String = s
        .replace("&",  "&amp;")
        .replace("\"", "&quot;")
        .replace("<",  "&lt;")
        .replace(">",  "&gt;")
        .replace("'",  "&apos;")
}
