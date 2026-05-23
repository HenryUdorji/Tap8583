package com.tap8583.tap8583.decoder

import com.tap8583.tap8583.ingestion.RawField
import com.tap8583.tap8583.ingestion.RawLogEvent

/**
 * Orchestrates the full semantic decoding pipeline for a single [RawLogEvent].
 *
 * Produces a [DecodedPacket] by:
 *  1. Decoding the MTI (DE0) via [MtiDecoder]
 *  2. For each field, looking up [FieldSchema] and applying [FieldDecoders]
 *  3. For DE55 (ICC/EMV), invoking the [TlvParser] and [EmvTagDictionary]
 *  4. Masking sensitive fields by default (DE2, DE35, DE45, DE52)
 *  5. Collecting any parse errors without crashing
 *
 * The decoder is stateless and thread-safe.
 */
object SemanticDecoder {

    /**
     * Decodes a raw jPOS log event into a semantically rich [DecodedPacket].
     *
     * All decoders are lenient — failures are collected in [DecodedPacket.parseErrors]
     * and never cause an exception to propagate to the caller.
     *
     * @param event The raw event from [JposXmlParser]
     * @return Fully decoded packet
     */
    fun decode(event: RawLogEvent): DecodedPacket {
        val errors = mutableListOf<FieldParseError>()

        // 1. Decode MTI
        val mtiRaw = event.fields[0]?.value ?: "0000"
        val mti = try {
            MtiDecoder.decode(mtiRaw)
        } catch (e: Exception) {
            errors += FieldParseError(0, "MTI decode error: ${e.message}")
            MTI(mtiRaw, "Unknown", "Unknown", "Unknown", "Unknown", "Unknown MTI: $mtiRaw")
        }

        // Build context map (field number → raw value) for cross-field decoding
        val contextMap: Map<Int, String> = event.fields.mapValues { it.value.value }

        // 2. Decode each field
        val decodedFields = event.fields
            .entries
            .sortedBy { it.key }
            .mapNotNull { (fieldNumber, rawField) ->
                if (fieldNumber == 0) return@mapNotNull null  // MTI handled above
                decodeField(fieldNumber, rawField, contextMap, errors)
            }

        return DecodedPacket(
            rawEvent    = event,
            mti         = mti,
            fields      = decodedFields,
            parseErrors = errors
        )
    }

    // -----------------------------------------------------------------------
    // Field decoding
    // -----------------------------------------------------------------------

    private fun decodeField(
        fieldNumber: Int,
        rawField: RawField,
        context: Map<Int, String>,
        errors: MutableList<FieldParseError>
    ): DecodedField? {
        val definition = FieldSchema.get(fieldNumber)
        val rawValue   = rawField.value
        val isBinary   = rawField.isBinary

        var decodedValue: String? = null
        var subFields:    List<SubField>?   = null
        var tlvEntries:   List<TLVEntry>?   = null

        try {
            when (fieldNumber) {
                55 -> {
                    // EMV/ICC — TLV parse
                    val tlv = TlvParser.parse(rawValue)
                    tlvEntries = tlv
                    decodedValue = buildEmvSummary(tlv)
                }
                else -> {
                    val result = FieldDecoders.decode(fieldNumber, rawValue, context)
                    decodedValue = result?.decodedValue
                    subFields    = result?.subFields?.takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            errors += FieldParseError(fieldNumber, "Decode error: ${e.message}")
            decodedValue = "[Decode error — raw value shown]"
        }

        return DecodedField(
            number              = fieldNumber,
            definition          = definition,
            rawValue            = rawValue,
            isBinary            = isBinary,
            decodedValue        = decodedValue,
            subFields           = subFields,
            tlvEntries          = tlvEntries,
            isSensitive         = definition.sensitive,
            isCurrentlyMasked   = definition.sensitive   // masked by default
        )
    }

    // -----------------------------------------------------------------------
    // EMV summary line (shown in collapsed DE55 row)
    // -----------------------------------------------------------------------

    /**
     * Produces a compact summary for the DE55 collapsed row, e.g.:
     * "ARQC  ·  Verve  ·  ATC: 1113  ·  Chip"
     */
    private fun buildEmvSummary(tlv: List<TLVEntry>): String {
        val flat = flattenTlv(tlv)

        val cryptogramType = flat["9F27"]?.let { decodeCryptogramShort(it) }
        val aid            = flat["84"] ?: flat["9F06"]
        val scheme         = aid?.let { aidToSchemeShort(it) }
        val atc            = flat["9F36"]?.toLongOrNull(16)?.toString()
        val totalBytes     = tlv.sumOf { it.length + it.tag.length / 2 + 2 }

        return buildList {
            cryptogramType?.let { add(it) }
            scheme?.let { add(it) }
            atc?.let { add("ATC: $it") }
            add("Chip")
        }.joinToString("  ·  ")
    }

    private fun flattenTlv(entries: List<TLVEntry>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (entry in entries) {
            result[entry.tag] = entry.valueHex
            entry.children?.let { result.putAll(flattenTlv(it)) }
        }
        return result
    }

    private fun decodeCryptogramShort(hex: String): String? = when (hex.uppercase()) {
        "80" -> "ARQC"
        "40" -> "TC"
        "00" -> "AAC"
        else  -> null
    }

    private fun aidToSchemeShort(hex: String): String? {
        val upper = hex.uppercase()
        return when {
            upper.startsWith("A0000003710001") -> "Verve"
            upper.startsWith("A0000000031010") -> "Visa"
            upper.startsWith("A0000000041010") -> "Mastercard"
            upper.startsWith("A0000000043060") -> "Maestro"
            upper.startsWith("A0000000651010") -> "JCB"
            upper.startsWith("A000000025")     -> "Amex"
            else                               -> null
        }
    }
}
