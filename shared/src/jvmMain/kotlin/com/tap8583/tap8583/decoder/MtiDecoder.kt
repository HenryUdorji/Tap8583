package com.tap8583.tap8583.decoder

/**
 * Decodes the 4-digit ISO 8583 Message Type Indicator (MTI) into its
 * semantic components and human-readable label.
 *
 * MTI structure (one digit each):
 *  - Digit 1: ISO 8583 version
 *  - Digit 2: Message class
 *  - Digit 3: Message function
 *  - Digit 4: Message origin
 *
 * Reference: ISO 8583-1:1993 §4.1
 */
object MtiDecoder {

    /**
     * Decodes a 4-character MTI string (e.g. "0200") into an [MTI] instance.
     *
     * @param raw Raw 4-digit MTI string from DE0
     * @return Decoded [MTI] with all components and a summary label
     * @throws [IllegalArgumentException] if [raw] is not exactly 4 digits
     */
    fun decode(raw: String): MTI {
        require(raw.length == 4 && raw.all { it.isDigit() }) {
            "MTI must be exactly 4 decimal digits, got: '$raw'"
        }

        val version  = decodeVersion(raw[0])
        val msgClass = decodeClass(raw[1])
        val function = decodeFunction(raw[2])
        val origin   = decodeOrigin(raw[3])
        val label    = knownLabel(raw) ?: "${msgClass} ${function}"

        return MTI(
            raw             = raw,
            version         = version,
            messageClass    = msgClass,
            messageFunction = function,
            messageOrigin   = origin,
            label           = label
        )
    }

    // -----------------------------------------------------------------------
    // Digit decoders
    // -----------------------------------------------------------------------

    private fun decodeVersion(digit: Char): String = when (digit) {
        '0' -> "ISO 8583-1:1987"
        '1' -> "ISO 8583-1:1993"
        '2' -> "ISO 8583-1:2003"
        '8' -> "National use"
        '9' -> "Private use"
        else -> "Reserved ($digit)"
    }

    private fun decodeClass(digit: Char): String = when (digit) {
        '1' -> "Authorization"
        '2' -> "Financial"
        '3' -> "File Action"
        '4' -> "Reversal / Chargeback"
        '5' -> "Reconciliation"
        '6' -> "Administrative"
        '7' -> "Fee Collection"
        '8' -> "Network Management"
        '9' -> "Reserved"
        else -> "Unknown ($digit)"
    }

    private fun decodeFunction(digit: Char): String = when (digit) {
        '0' -> "Request"
        '1' -> "Request Response"
        '2' -> "Advice"
        '3' -> "Advice Response"
        '4' -> "Notification"
        '5' -> "Notification Acknowledgement"
        '6' -> "Instruction"
        '7' -> "Instruction Acknowledgement"
        '8' -> "Reserved"
        '9' -> "Reserved"
        else -> "Unknown ($digit)"
    }

    private fun decodeOrigin(digit: Char): String = when (digit) {
        '0' -> "Acquirer"
        '1' -> "Acquirer Repeat"
        '2' -> "Issuer"
        '3' -> "Issuer Repeat"
        '4' -> "Other"
        '5' -> "Other Repeat"
        else -> "Unknown ($digit)"
    }

    // -----------------------------------------------------------------------
    // Known full-message labels
    // -----------------------------------------------------------------------

    private val KNOWN_LABELS = mapOf(
        // Authorization
        "0100" to "Authorization Request",
        "0110" to "Authorization Request Response",
        "0120" to "Authorization Advice",
        "0121" to "Authorization Advice Repeat",
        "0130" to "Authorization Advice Response",
        "0400" to "Reversal Request",          // some schemes use 01xx for auth reversals
        // Financial
        "0200" to "Financial Transaction Request",
        "0201" to "Financial Transaction Repeat",
        "0210" to "Financial Transaction Response",
        "0220" to "Financial Transaction Advice",
        "0221" to "Financial Transaction Advice Repeat",
        "0230" to "Financial Transaction Advice Response",
        // Reversal
        "0400" to "Reversal Request",
        "0401" to "Reversal Request Repeat",
        "0410" to "Reversal Request Response",
        "0420" to "Reversal Advice",
        "0421" to "Reversal Advice Repeat",
        "0430" to "Reversal Advice Response",
        // Reconciliation
        "0500" to "Reconciliation Request",
        "0510" to "Reconciliation Response",
        // Administrative
        "0600" to "Administrative Request",
        "0610" to "Administrative Response",
        "0620" to "Administrative Advice",
        "0630" to "Administrative Advice Response",
        // Network Management
        "0800" to "Network Management Request",
        "0810" to "Network Management Response",
        "0820" to "Network Management Advice",
        "0830" to "Network Management Advice Response"
    )

    fun knownLabel(raw: String): String? = KNOWN_LABELS[raw]

    /** Returns true if this MTI represents a request (function digit = 0, 2, or 4) */
    fun isRequest(mti: MTI): Boolean = mti.raw.getOrNull(2) in listOf('0', '2', '4')

    /** Returns true if this MTI represents a response (function digit = 1 or 3) */
    fun isResponse(mti: MTI): Boolean = mti.raw.getOrNull(2) in listOf('1', '3')
}
