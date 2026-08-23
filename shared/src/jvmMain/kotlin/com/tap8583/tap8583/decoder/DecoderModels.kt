package com.tap8583.tap8583.decoder

import com.tap8583.tap8583.ingestion.RawLogEvent
import com.tap8583.tap8583.ingestion.TransactionStatus

// ---------------------------------------------------------------------------
// Field category — drives color coding in the UI
// ---------------------------------------------------------------------------

/**
 * Semantic groupings for ISO 8583 fields.
 * Maps directly to the color-coded category chips in the spec §6.3.
 */
enum class FieldCategory {
    CARD_DATA,        // red    — DE2, DE14, DE35, DE45
    AMOUNT,           // green  — DE4, DE5, DE6, DE28, DE29, DE30
    DATETIME,         // blue   — DE7, DE12, DE13, DE15, DE17
    IDENTIFICATION,   // purple — DE11, DE37, DE38, DE41, DE42
    RESPONSE,         // amber  — DE39, DE44
    POS_TERMINAL,     // teal   — DE22, DE25, DE26
    EMV,              // orange — DE23, DE55, DE56
    INSTITUTION,      // indigo — DE32, DE33, DE100
    SECURITY,         // crimson — DE52, DE53, DE64, DE128
    PRIVATE_NIBSS,    // grey   — DE59, DE60, DE123
    NETWORK,          // slate  — DE70, DE71, DE73
    GENERAL           // default for uncategorised fields
}

// ---------------------------------------------------------------------------
// Field definition — the schema entry for one ISO 8583 field
// ---------------------------------------------------------------------------

/**
 * Static metadata for a single ISO 8583 data element.
 *
 * @param number      ISO 8583 field number (1–128, 0 = MTI)
 * @param name        Full field name as defined in ISO 8583 or NIBSS spec
 * @param shortName   Abbreviated name for space-constrained UI contexts
 * @param dataType    ISO data type code: n, an, ans, b, z
 * @param isoType     Length type: FIXED, LLVAR, LLLVAR
 * @param maxLength   Maximum field length in characters (or bytes for binary)
 * @param description Human-readable description of the field's purpose
 * @param sensitive   When true, the field value must be masked in UI by default
 * @param category    The [FieldCategory] controlling the UI color accent
 */
data class FieldDefinition(
    val number: Int,
    val name: String,
    val shortName: String,
    val dataType: String,
    val isoType: String,
    val maxLength: Int,
    val description: String,
    val sensitive: Boolean = false,
    val category: FieldCategory = FieldCategory.GENERAL
)

// ---------------------------------------------------------------------------
// MTI (Message Type Indicator)
// ---------------------------------------------------------------------------

/**
 * Decoded representation of the 4-digit Message Type Indicator (DE0).
 *
 * Each digit has a distinct semantic meaning per ISO 8583 §4.
 */
data class MTI(
    val raw: String,                // e.g. "0200"
    val version: String,            // e.g. "ISO 8583-1:1987"
    val messageClass: String,       // e.g. "Financial"
    val messageFunction: String,    // e.g. "Request"
    val messageOrigin: String,      // e.g. "Acquirer"
    val label: String               // e.g. "Financial Transaction Request"
)

// ---------------------------------------------------------------------------
// Sub-field and TLV models
// ---------------------------------------------------------------------------

/**
 * A named sub-component extracted from a composite ISO 8583 field.
 * Used for DE3, DE22, DE35 etc.
 */
data class SubField(
    val name: String,
    val rawValue: String,
    val decodedValue: String
)

/**
 * A single TLV record from BER-TLV encoded data (DE55 / EMV).
 *
 * @param tag          Hex string of the tag bytes e.g. "9F26"
 * @param name         Human-readable name from [EmvTagDictionary], or null if unknown
 * @param length       Length of the value in bytes
 * @param valueHex     Hex string of the value bytes
 * @param decoded      Human-readable interpretation, or null if raw only
 * @param children     Parsed children for constructed (composite) TLV tags
 * @param isConstructed True if the tag's class indicates a constructed structure
 */
data class TLVEntry(
    val tag: String,
    val name: String?,
    val length: Int,
    val valueHex: String,
    val decoded: String?,
    val children: List<TLVEntry>?,
    val isConstructed: Boolean
)

// ---------------------------------------------------------------------------
// Decoded field
// ---------------------------------------------------------------------------

/**
 * A fully decoded ISO 8583 field — combines raw data with semantic meaning.
 */
data class DecodedField(
    val number: Int,
    val definition: FieldDefinition,
    val rawValue: String,
    val isBinary: Boolean,
    val decodedValue: String?,          // Human-readable primary decode (null if no decoder)
    val subFields: List<SubField>?,     // For composite fields: DE3, DE22, DE35 etc.
    val tlvEntries: List<TLVEntry>?,    // For EMV: DE55
    val isSensitive: Boolean,
    val isCurrentlyMasked: Boolean
)

// ---------------------------------------------------------------------------
// Parse error
// ---------------------------------------------------------------------------

data class FieldParseError(
    val fieldNumber: Int,
    val message: String
)

// ---------------------------------------------------------------------------
// Decoded packet
// ---------------------------------------------------------------------------

/**
 * A fully decoded ISO 8583 message, wrapping the raw [RawLogEvent] with
 * semantic information from the decoder.
 */
data class DecodedPacket(
    val rawEvent: RawLogEvent,
    val mti: MTI,
    val fields: List<DecodedField>,
    val parseErrors: List<FieldParseError>
)

// ---------------------------------------------------------------------------
// Decoded Transaction (Phase 2 counterpart to ingestion.Transaction)
// ---------------------------------------------------------------------------

/**
 * A matched request/response pair with full semantic decoding applied.
 * [status] is propagated from the ingestion-layer [TransactionStatus].
 */
data class DecodedTransaction(
    val id: String,
    val stan: String,
    val request: DecodedPacket?,
    val response: DecodedPacket?,
    val status: TransactionStatus,
    val rttMs: Long?,
    val serial: String? = null
)
