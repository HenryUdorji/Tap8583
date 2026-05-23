package com.tap8583.tap8583.decoder

/**
 * Recursive BER-TLV parser for ISO 8583 DE55 (ICC / EMV data).
 *
 * Implements the BER-TLV encoding rules as defined in:
 *  - EMV Book 3 — Application Specification
 *  - ISO 7816-4 — Interindustry Commands for Interchange
 *
 * Algorithm (per spec §6.5):
 *  1. Read tag bytes (1 byte normally; 2 bytes if first byte low nibble = 0x1F)
 *  2. Read length (1 byte if < 0x80; multi-byte otherwise)
 *  3. Read value bytes
 *  4. If tag's class bit indicates "constructed," recurse into value
 *  5. Look up tag in [EmvTagDictionary] for name and decode logic
 *
 * Error resilience:
 *  - Parsing proceeds as far as possible — partial parse on malformed TLV
 *  - Unknown tags are represented with name=null and raw hex value
 *  - Errors at any offset are caught; remaining bytes shown as raw hex
 */
object TlvParser {

    /**
     * Parses a hex-encoded BER-TLV byte string into a list of [TLVEntry] records.
     *
     * @param hex  Hex string to parse (e.g. "820258008407A0000003710001...")
     * @return     Parsed TLV tree (may be partial on malformed input)
     */
    fun parse(hex: String): List<TLVEntry> {
        val bytes = hexToBytes(hex) ?: return listOf(rawEntry("00", hex, "Invalid hex string"))
        return parseBytes(bytes, 0, bytes.size).first
    }

    // -----------------------------------------------------------------------
    // Internal parsing
    // -----------------------------------------------------------------------

    /**
     * Parses TLV records from [bytes] starting at [offset] up to [end].
     * Returns a pair of (parsedEntries, nextOffset).
     */
    private fun parseBytes(bytes: ByteArray, offset: Int, end: Int): Pair<List<TLVEntry>, Int> {
        val result = mutableListOf<TLVEntry>()
        var pos = offset

        while (pos < end) {
            // --- Read Tag ---
            val tagStart = pos
            if (pos >= bytes.size) break

            val firstTagByte = bytes[pos].toInt() and 0xFF
            val isConstructed = (firstTagByte and 0x20) != 0  // bit 6 of first tag byte

            val tagBytes: ByteArray
            pos++
            if ((firstTagByte and 0x1F) == 0x1F) {
                // Multi-byte tag: continue while bit 8 is set
                val tagBuf = mutableListOf(firstTagByte.toByte())
                while (pos < bytes.size) {
                    val b = bytes[pos].toInt() and 0xFF
                    tagBuf.add(bytes[pos])
                    pos++
                    if ((b and 0x80) == 0) break  // last byte of tag
                }
                tagBytes = tagBuf.toByteArray()
            } else {
                tagBytes = byteArrayOf(firstTagByte.toByte())
            }
            val tag = bytesToHex(tagBytes).uppercase()

            // --- Read Length ---
            if (pos >= bytes.size) break
            val lenByte = bytes[pos].toInt() and 0xFF
            pos++

            val length: Int
            if (lenByte < 0x80) {
                // Short form
                length = lenByte
            } else {
                // Long form: next (lenByte & 0x7F) bytes encode the length
                val numLenBytes = lenByte and 0x7F
                if (pos + numLenBytes > bytes.size) break
                var len = 0
                repeat(numLenBytes) {
                    len = (len shl 8) or (bytes[pos].toInt() and 0xFF)
                    pos++
                }
                length = len
            }

            // --- Read Value ---
            if (pos + length > bytes.size) {
                // Truncated — show what we have
                val available = bytes.copyOfRange(pos, bytes.size)
                result.add(rawEntry(tag, bytesToHex(available), "Truncated TLV (expected $length bytes, got ${available.size})"))
                break
            }
            val valueBytes = bytes.copyOfRange(pos, pos + length)
            val valueHex   = bytesToHex(valueBytes).uppercase()
            pos += length

            // --- Decode ---
            val children: List<TLVEntry>?
            if (isConstructed && valueBytes.isNotEmpty()) {
                children = try {
                    parseBytes(valueBytes, 0, valueBytes.size).first
                } catch (_: Exception) { null }
            } else {
                children = null
            }

            val dictEntry = EmvTagDictionary.lookup(tag)
            val decoded   = if (!isConstructed) {
                dictEntry?.decode?.invoke(valueHex)
            } else null

            result.add(TLVEntry(
                tag           = tag,
                name          = dictEntry?.name,
                length        = length,
                valueHex      = valueHex,
                decoded       = decoded,
                children      = children,
                isConstructed = isConstructed
            ))
        }

        return Pair(result, pos)
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun rawEntry(tag: String, hex: String, note: String) = TLVEntry(
        tag           = tag,
        name          = "[RAW]",
        length        = hex.length / 2,
        valueHex      = hex,
        decoded       = note,
        children      = null,
        isConstructed = false
    )

    internal fun hexToBytes(hex: String): ByteArray? {
        val clean = hex.replace(" ", "").uppercase()
        if (clean.length % 2 != 0) return null
        return try {
            ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        } catch (_: NumberFormatException) { null }
    }

    internal fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02X".format(it) }
}
