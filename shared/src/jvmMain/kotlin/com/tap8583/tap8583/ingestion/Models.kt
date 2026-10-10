package com.tap8583.tap8583.ingestion

import java.time.Instant

// ---------------------------------------------------------------------------
// Direction
// ---------------------------------------------------------------------------

enum class Direction {
    OUTGOING,   // send  — POS → NIBSS
    INCOMING    // receive — NIBSS → POS
}

// ---------------------------------------------------------------------------
// ServerStatus
// ---------------------------------------------------------------------------

enum class ServerStatus {
    IDLE,        // Not yet started
    LISTENING,   // Bound to port, waiting for POS connection
    CONNECTED,   // At least one POS connection active
    ERROR        // Failed to bind or fatal IO error
}

// ---------------------------------------------------------------------------
// Raw Models (pre-decode)
// ---------------------------------------------------------------------------

/**
 * A single raw field as logged by jPOS.
 *
 * @param id       The ISO 8583 field number (0 = MTI, 1 = bitmap which jPOS omits,
 *                 2–128 = data elements)
 * @param value    The string value as it appears in the jPOS XML dump.
 *                 Numeric fields are zero-padded strings. Binary fields are hex strings.
 * @param isBinary True when the XML had [type="binary"]. Examples: DE52, DE55, DE64, DE128.
 */
data class RawField(
    val id: Int,
    val value: String,
    val isBinary: Boolean = false
)

/**
 * A complete, raw jPOS log event representing one ISO 8583 message.
 *
 * Corresponds to one <visualizer-event> block received over TCP, terminated
 * by the ---END--- delimiter sent by VisualizerLogListener.
 *
 * @param id          Unique ID for this event (UUID generated at ingestion time)
 * @param realm       The jPOS channel realm string, e.g. "channel/196.6.103.18:4018"
 * @param capturedAt  When the jPOS LogEvent was fired (from the [at] attribute)
 * @param lifespanMs  jPOS internal processing/sending time in ms (from [lifespan] attribute)
 * @param direction   OUTGOING (send) or INCOMING (receive)
 * @param rawXml      The original XML string as received — used for the Raw tab
 * @param fields      Parsed field map: field ID → RawField. DE0 (MTI) is always present.
 */
data class RawLogEvent(
    val id: String,
    val realm: String,
    val capturedAt: Instant,
    val lifespanMs: Long,
    val direction: Direction,
    val rawXml: String,
    val fields: Map<Int, RawField>,
    val serial: String? = null
)

/**
 * A channel-level problem reported by jPOS instead of an ISO message:
 * a read timeout, the host closing the connection, a failed connect or an
 * exception while packing or unpacking.
 *
 * Sent by VisualizerLogListener as
 * `<visualizer-event …><channel-error kind="io-timeout">Read timed out</channel-error></visualizer-event>`.
 *
 * @param kind     "io-timeout", "peer-disconnect", "connect-failed" or "exception"
 * @param message  Human-readable detail (exception class and message), already shortened
 */
data class ChannelErrorEvent(
    val id: String,
    val realm: String,
    val capturedAt: Instant,
    val tag: String,
    val kind: String,
    val message: String,
    val serial: String? = null
)

/** One parsed <visualizer-event>: an ISO message or a channel error. */
sealed class LogEntry {
    data class Message(val event: RawLogEvent) : LogEntry()
    data class ChannelError(val event: ChannelErrorEvent) : LogEntry()
}
