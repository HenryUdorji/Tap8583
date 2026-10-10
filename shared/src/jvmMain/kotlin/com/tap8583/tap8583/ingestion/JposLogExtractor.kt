package com.tap8583.tap8583.ingestion

/**
 * Turns pasted or uploaded log text into <visualizer-event> blocks that
 * [JposXmlParser.parseEntry] understands, so a log can be decoded without a
 * terminal.
 *
 * Accepts, in order of preference:
 *  - jPOS logger output: `<log realm=… at=… lifespan=…><send><isomsg …>…</isomsg></send></log>`.
 *    Blocks with no <isomsg> but an `<io-timeout/>`, `<peer-disconnect/>` or
 *    `<exception>` become channel errors.
 *  - Tap8583 listener output: `<visualizer-event …>…</visualizer-event>`.
 *  - Bare `<isomsg direction="…">…</isomsg>` dumps.
 *
 * Android logcat prefixes ("05-21 14:20:58.266 I/System.out( 1234): ") are
 * stripped first, so a log copied straight from Logcat works.
 */
object JposLogExtractor {

    const val DEFAULT_MAX_EVENTS = 2_000

    private val LOGCAT_PREFIX = Regex(
        """^\s*(?:\d{4}-)?\d{2}-\d{2}[ T]\d{2}:\d{2}:\d{2}[.,]\d{3}\s+(?:\d+\s+\d+\s+)?[VDIWEF][/ ][^:]*:\s?"""
    )
    private val LOG_BLOCK = Regex("""<log\b([^>]*)>(.*?)</log>""", RegexOption.DOT_MATCHES_ALL)
    private val VISUALIZER_BLOCK = Regex("""<visualizer-event\b.*?</visualizer-event>""", RegexOption.DOT_MATCHES_ALL)
    private val ATTRIBUTE = Regex("""([\w-]+)\s*=\s*"([^"]*)"""")
    private val FIRST_ELEMENT = Regex("""<\s*([A-Za-z][\w-]*)""")
    private val ISOMSG_TOKEN = Regex("""<isomsg\b[^>]*?(/?)>|</isomsg\s*>""")
    private val TAGS = Regex("""<[^>]+>""")

    /** Returns one <visualizer-event> XML string per message or channel error found. */
    fun extract(text: String, serial: String, maxEvents: Int = DEFAULT_MAX_EVENTS): List<String> {
        val cleaned = text.lineSequence()
            .joinToString("\n") { it.replaceFirst(LOGCAT_PREFIX, "") }

        val fromLogBlocks = LOG_BLOCK.findAll(cleaned)
            .mapNotNull { fromLogBlock(it.groupValues[1], it.groupValues[2], serial) }
            .take(maxEvents)
            .toList()
        if (fromLogBlocks.isNotEmpty()) return fromLogBlocks

        val visualizerEvents = VISUALIZER_BLOCK.findAll(cleaned).map { it.value }.take(maxEvents).toList()
        if (visualizerEvents.isNotEmpty()) return visualizerEvents

        return isoMsgBlocks(cleaned)
            .take(maxEvents)
            .map { isomsg ->
                val tag = if (directionOf(isomsg) == "incoming") "receive" else "send"
                wrap(attrs = emptyMap(), tag = tag, serial = serial, body = isomsg)
            }
            .toList()
    }

    private fun fromLogBlock(openTagAttrs: String, body: String, serial: String): String? {
        val attrs = attributes(openTagAttrs)
        val tag = FIRST_ELEMENT.find(body)?.groupValues?.get(1) ?: "log"
        val isomsg = isoMsgBlocks(body).firstOrNull()
        if (isomsg != null) return wrap(attrs, tag, serial, isomsg)

        val kind = when {
            body.contains("<io-timeout") -> "io-timeout"
            body.contains("<peer-disconnect") -> "peer-disconnect"
            tag == "connect" && body.contains("<exception") -> "connect-failed"
            body.contains("<exception") -> "exception"
            else -> return null
        }
        // The text is still XML-escaped; unescape it so it is escaped exactly once below.
        val detail = unescapeText(body.replace(TAGS, " ")).replace(Regex("\\s+"), " ").trim()
            .take(JposXmlParser.MAX_CHANNEL_ERROR_CHARS)
        return wrap(attrs, tag, serial, """<channel-error kind="$kind">${escapeText(detail)}</channel-error>""")
    }

    /** Outermost <isomsg>…</isomsg> blocks, keeping nested ones (e.g. DE127) inside their parent. */
    private fun isoMsgBlocks(text: String): Sequence<String> = sequence {
        var depth = 0
        var start = -1
        for (match in ISOMSG_TOKEN.findAll(text)) {
            val isClose = match.value.startsWith("</")
            val selfClosing = !isClose && match.groupValues[1] == "/"
            when {
                selfClosing -> Unit
                !isClose -> {
                    if (depth == 0) start = match.range.first
                    depth++
                }
                depth > 0 -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        yield(text.substring(start, match.range.last + 1))
                        start = -1
                    }
                }
            }
        }
    }

    private fun directionOf(isomsg: String): String? =
        Regex("""<isomsg\b[^>]*direction\s*=\s*"([^"]*)"""").find(isomsg)?.groupValues?.get(1)?.lowercase()

    private fun attributes(s: String): Map<String, String> =
        ATTRIBUTE.findAll(s).associate { it.groupValues[1] to it.groupValues[2] }

    /** Attribute values are copied from XML that is already escaped. */
    private fun wrap(attrs: Map<String, String>, tag: String, serial: String, body: String): String = buildString {
        append("<visualizer-event")
        append(" realm=\"").append(attrs["realm"] ?: "pasted-log").append('"')
        attrs["at"]?.let { append(" at=\"").append(it).append('"') }
        attrs["lifespan"]?.let { append(" lifespan=\"").append(it).append('"') }
        append(" tag=\"").append(tag).append('"')
        append(" serial=\"").append(escapeText(serial)).append('"')
        append(">\n").append(body).append("\n</visualizer-event>")
    }

    private fun unescapeText(s: String): String = s
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

    private fun escapeText(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
