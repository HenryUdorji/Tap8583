package com.tap8583.tap8583.ingestion

/**
 * Masks cardholder data in a [RawLogEvent] before it is stored or broadcast.
 *
 * Applied on the server at ingestion so that clear PAN, track data, expiry and
 * PIN blocks never leave the server — neither in decoded fields nor in the
 * raw XML shown in the Raw tab.
 *
 * Masking keeps value lengths and the BIN (first 6) / last 4 digits so that
 * scheme detection, length checks and DE55 TLV structure keep working.
 *
 *  - DE2, DE34          → BIN + '*' + last 4
 *  - DE14               → all '*'
 *  - DE35               → masked PAN, '*' expiry, service code kept, '*' discretionary
 *  - DE36, DE45, DE52   → all '*'
 *  - DE55 (EMV)         → tags 5A, 57, 5F24, 56, 9F1F, 9F20, 9F6B masked in place (hex 'F')
 */
object CardDataMasker {

    const val MASK_CHAR = '*'

    private val FULLY_MASKED_FIELDS = setOf(14, 36, 45, 52)
    private val PAN_FIELDS = setOf(2, 34)

    /** EMV tags whose value is PAN-like (keep BIN + last 4 digits). */
    private val EMV_PAN_TAGS = setOf("5A")

    /** EMV tags with track-2-equivalent layout (PAN 'D' expiry service discretionary). */
    private val EMV_TRACK2_TAGS = setOf("57", "9F6B")

    /** EMV tags whose whole value is masked. */
    private val EMV_FULL_TAGS = setOf("5F24", "56", "9F1F", "9F20")

    fun mask(event: RawLogEvent): RawLogEvent {
        val replacements = mutableMapOf<String, String>()

        val maskedFields = event.fields.mapValues { (id, field) ->
            val masked = maskField(id, field.value)
            if (masked != field.value) {
                replacements[field.value] = masked
                field.copy(value = masked)
            } else {
                field
            }
        }

        if (replacements.isEmpty()) return event

        // Replace longest originals first: DE35 contains the DE2 PAN as a prefix.
        var xml = event.rawXml
        replacements.entries
            .sortedByDescending { it.key.length }
            .forEach { (original, masked) ->
                if (original.isNotEmpty()) xml = xml.replace(original, masked)
            }

        return event.copy(fields = maskedFields, rawXml = xml)
    }

    private val PAN_LIKE = Regex("""(?<![0-9])[0-9]{13,19}(?![0-9])""")

    /** Masks card-number-like digit runs (13–19 digits) in free text such as error messages. */
    fun maskText(text: String): String = text.replace(PAN_LIKE) { maskPan(it.value) }

    /** Masks free text in a channel error before it is stored or shown. */
    fun mask(event: ChannelErrorEvent): ChannelErrorEvent = event.copy(message = maskText(event.message))

    internal fun maskField(id: Int, value: String): String = when {
        value.isEmpty()              -> value
        id in PAN_FIELDS             -> maskPan(value)
        id in FULLY_MASKED_FIELDS    -> MASK_CHAR.toString().repeat(value.length)
        id == 35                     -> maskTrack2(value, MASK_CHAR)
        id == 55                     -> maskEmv(value)
        else                         -> value
    }

    /** Keeps the BIN (first 6) and last 4 characters; masks the rest. */
    fun maskPan(pan: String, maskChar: Char = MASK_CHAR): String {
        if (pan.length <= 10) return maskChar.toString().repeat(pan.length)
        return pan.take(6) + maskChar.toString().repeat(pan.length - 10) + pan.takeLast(4)
    }

    /**
     * Track 2: PAN, separator ('D' or '='), YYMM expiry, 3-digit service code,
     * discretionary data. Service code is kept because it is not secret and
     * drives the "chip card / PIN required" decode.
     */
    internal fun maskTrack2(raw: String, maskChar: Char): String {
        val sep = raw.indexOfFirst { it == 'D' || it == 'd' || it == '=' }
        if (sep < 0) return maskChar.toString().repeat(raw.length)
        val pan = raw.substring(0, sep)
        val rest = raw.substring(sep + 1)
        val expiry = rest.take(4)
        val service = rest.drop(4).take(3)
        val discretionary = rest.drop(7)
        return maskPan(pan, maskChar) + raw[sep] +
            maskChar.toString().repeat(expiry.length) +
            service +
            maskChar.toString().repeat(discretionary.length)
    }

    // -----------------------------------------------------------------------
    // DE55 — mask values in place, preserving the TLV byte structure
    // -----------------------------------------------------------------------

    internal fun maskEmv(hex: String): String {
        if (hex.length % 2 != 0 || hex.any { !it.isHexDigit() }) return hex
        val out = hex.uppercase().toCharArray()
        return try {
            maskTlvRange(out, 0, out.size)
            String(out)
        } catch (_: Exception) {
            // Malformed TLV: mask everything rather than risk leaking card data.
            "F".repeat(hex.length)
        }
    }

    /** Walks TLV records in [chars] between nibble offsets [start] and [end]. */
    private fun maskTlvRange(chars: CharArray, start: Int, end: Int) {
        var pos = start
        while (pos + 2 <= end) {
            // Tag
            val tagStart = pos
            val first = byteAt(chars, pos)
            pos += 2
            // Padding bytes between records
            if (first == 0x00 || first == 0xFF) continue
            if ((first and 0x1F) == 0x1F) {
                while (pos + 2 <= end) {
                    val b = byteAt(chars, pos)
                    pos += 2
                    if ((b and 0x80) == 0) break
                }
            }
            val tag = String(chars, tagStart, pos - tagStart)
            val constructed = (first and 0x20) != 0

            // Length
            if (pos + 2 > end) throw IllegalStateException("Truncated length")
            var length = byteAt(chars, pos)
            pos += 2
            if (length >= 0x80) {
                val numBytes = length and 0x7F
                if (numBytes == 0 || numBytes > 3) throw IllegalStateException("Bad length")
                length = 0
                repeat(numBytes) {
                    if (pos + 2 > end) throw IllegalStateException("Truncated length")
                    length = (length shl 8) or byteAt(chars, pos)
                    pos += 2
                }
            }

            val valueStart = pos
            val valueEnd = pos + length * 2
            if (valueEnd > end) throw IllegalStateException("Value overruns buffer")

            if (constructed) {
                maskTlvRange(chars, valueStart, valueEnd)
            } else {
                val value = String(chars, valueStart, valueEnd - valueStart)
                val masked = when (tag) {
                    in EMV_PAN_TAGS    -> maskPan(value, 'F')
                    in EMV_TRACK2_TAGS -> maskTrack2(value, 'F')
                    in EMV_FULL_TAGS   -> "F".repeat(value.length)
                    else               -> value
                }
                masked.toCharArray().copyInto(chars, valueStart)
            }
            pos = valueEnd
        }
    }

    private fun byteAt(chars: CharArray, pos: Int): Int =
        (Character.digit(chars[pos], 16) shl 4) or Character.digit(chars[pos + 1], 16)

    private fun Char.isHexDigit(): Boolean = Character.digit(this, 16) >= 0
}
