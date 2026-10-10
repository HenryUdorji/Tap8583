package com.tap8583.tap8583.ingestion

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.StringReader
import java.time.Instant
import java.util.UUID
import javax.xml.parsers.SAXParserFactory

/**
 * Parses a single jPOS log event XML fragment into a [RawLogEvent].
 *
 * The expected input is a complete <visualizer-event> block as produced by
 * VisualizerLogListener, for example:
 *
 * ```xml
 * <visualizer-event realm="channel/196.6.103.18:4018"
 *                   at="2026-05-21T14:20:58.266Z"
 *                   lifespan="19ms"
 *                   tag="send">
 *   <isomsg direction="outgoing">
 *     <field id="0"  value="0200"/>
 *     <field id="2"  value="5061240207416432753"/>
 *     <field id="52" value="26B7D3B168E69591" type="binary"/>
 *   </isomsg>
 * </visualizer-event>
 * ```
 *
 * The parser is SAX-based (via javax.xml), stateless, and can be called
 * from multiple threads simultaneously (a new handler is created per parse call).
 */
object JposXmlParser {

    private val saxParserFactory: SAXParserFactory = SAXParserFactory.newInstance().also {
        it.isNamespaceAware = false
        it.isValidating = false
        // Input arrives over the network: refuse DOCTYPEs and external entities
        // (XXE file disclosure / SSRF, "billion laughs" entity expansion).
        it.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        it.setFeature("http://xml.org/sax/features/external-general-entities", false)
        it.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        it.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        it.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true)
    }

    /**
     * Parses the given XML string.
     *
     * @param xml   Complete <visualizer-event>…</visualizer-event> XML string
     * @param rawXml Passed through verbatim to [RawLogEvent.rawXml] for the Raw tab
     * @return      A [RawLogEvent] on success
     * @throws      [ParseException] if the XML is malformed or lacks required attributes
     */
    fun parse(xml: String, rawXml: String = xml): RawLogEvent {
        val handler = IsoMsgHandler()
        try {
            val parser = saxParserFactory.newSAXParser()
            parser.parse(InputSource(StringReader(xml)), handler)
        } catch (e: org.xml.sax.SAXException) {
            throw ParseException("Malformed jPOS XML: ${e.message}", e)
        } catch (e: java.io.IOException) {
            throw ParseException("IO error parsing jPOS XML: ${e.message}", e)
        }
        return handler.buildEvent(rawXml)
    }

    // -----------------------------------------------------------------------
    // SAX Handler
    // -----------------------------------------------------------------------

    private class IsoMsgHandler : DefaultHandler() {

        // --- event-level attributes ---
        private var realm: String = ""
        private var capturedAt: Instant = Instant.EPOCH
        private var lifespanMs: Long = 0L
        private var tag: String = ""
        private var serial: String? = null

        // --- isomsg-level attributes ---
        private var direction: Direction = Direction.OUTGOING

        // --- collected fields ---
        private val fields = mutableMapOf<Int, RawField>()

        // ---- element tracking ----
        private var insideVisualizerEvent = false
        private var insideIsoMsg = false

        override fun startElement(
            uri: String?,
            localName: String?,
            qName: String?,
            attributes: Attributes
        ) {
            when (qName) {
                "visualizer-event" -> {
                    insideVisualizerEvent = true
                    realm  = attributes.getValue("realm") ?: ""
                    tag    = attributes.getValue("tag") ?: ""
                    serial = attributes.getValue("serial")

                    // Parse timestamp — jPOS may omit trailing 'Z' or use space
                    val atStr = attributes.getValue("at") ?: ""
                    capturedAt = parseInstant(atStr)

                    // Parse lifespan e.g. "19ms"
                    val lifespanStr = attributes.getValue("lifespan") ?: "0ms"
                    lifespanMs = lifespanStr.removeSuffix("ms").toLongOrNull() ?: 0L
                }

                "isomsg" -> {
                    if (!insideVisualizerEvent) return
                    insideIsoMsg = true
                    val dirStr = attributes.getValue("direction") ?: "outgoing"
                    direction = when (dirStr.lowercase()) {
                        "incoming", "receive", "in" -> Direction.INCOMING
                        else -> Direction.OUTGOING
                    }
                }

                "field" -> {
                    if (!insideIsoMsg) return
                    val idStr    = attributes.getValue("id") ?: return
                    val value    = attributes.getValue("value") ?: return
                    val isBinary = attributes.getValue("type")?.equals("binary", ignoreCase = true) == true

                    val id = idStr.toIntOrNull() ?: return
                    fields[id] = RawField(id = id, value = value, isBinary = isBinary)
                }
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName) {
                "isomsg"             -> insideIsoMsg = false
                "visualizer-event"   -> insideVisualizerEvent = false
            }
        }

        fun buildEvent(rawXml: String): RawLogEvent {
            if (fields.isEmpty()) {
                throw ParseException("No <field> elements found in parsed XML")
            }
            return RawLogEvent(
                id          = UUID.randomUUID().toString(),
                realm       = realm,
                capturedAt  = capturedAt,
                lifespanMs  = lifespanMs,
                direction   = direction,
                rawXml      = rawXml,
                fields      = fields.toMap(),
                serial      = serial
            )
        }
    }

    // -----------------------------------------------------------------------
    // Utility helpers
    // -----------------------------------------------------------------------

    /**
     * Lenient Instant parser that handles:
     *  - "2026-05-21T14:20:58.266Z"          (full ISO-8601)
     *  - "2026-05-21T14:20:58.266"            (no Z suffix)
     *  - "2026-05-21T14:20:58"                (no millis)
     *  - empty string → Instant.EPOCH
     */
    internal fun parseInstant(s: String): Instant {
        if (s.isBlank()) return Instant.EPOCH
        return try {
            val normalised = if (s.endsWith("Z") || s.contains("+")) s else "${s}Z"
            Instant.parse(normalised)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    }

    // -----------------------------------------------------------------------
    // Exceptions
    // -----------------------------------------------------------------------

    class ParseException(message: String, cause: Throwable? = null) :
        Exception(message, cause)
}
