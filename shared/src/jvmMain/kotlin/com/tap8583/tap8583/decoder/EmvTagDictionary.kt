package com.tap8583.tap8583.decoder

/**
 * EMV tag dictionary.
 *
 * Maps BER-TLV tag hex strings to their names and decode functions.
 * Based on:
 *  - EMV 4.4 Book 3 — Application Specification
 *  - EMV 4.4 Book 4 — Cardholder, Attendant, and Acquirer Interface
 *  - ISO 7816-4 Annex D
 *
 * Unknown tags are NOT an error — they are returned with name=null and the
 * raw hex value, allowing the UI to display "[UNKNOWN TAG xxxx]" gracefully.
 */
object EmvTagDictionary {

    data class TagEntry(
        val tag: String,
        val name: String,
        val decode: ((String) -> String)?   // null = show raw hex only
    )

    private val registry: Map<String, TagEntry> = buildMap {
        fun tag(tag: String, name: String, decode: ((String) -> String)? = null) =
            put(tag.uppercase(), TagEntry(tag.uppercase(), name, decode))

        // ── Basic card / application info ─────────────────────────────────
        tag("56",   "Track 1 Equivalent Data")
        tag("57",   "Track 2 Equivalent Data",       ::decodeTrack2Equivalent)
        tag("5A",   "Application Primary Account Number (PAN)")
        tag("5F20", "Cardholder Name")
        tag("5F24", "Application Expiration Date",   ::decodeYYMMDD)
        tag("5F25", "Application Effective Date",    ::decodeYYMMDD)
        tag("5F28", "Issuer Country Code",           ::decodeCountryCode)
        tag("5F2A", "Transaction Currency Code",     ::decodeCurrencyCode)
        tag("5F2D", "Language Preference")
        tag("5F30", "Service Code")
        tag("5F34", "Application PAN Sequence Number", ::decodeDecimal)
        tag("5F50", "Issuer URL")
        tag("5F53", "International Bank Account Number (IBAN)")
        tag("5F54", "Bank Identifier Code (BIC)")
        tag("5F55", "Issuer Country Code (alpha2)")
        tag("5F56", "Issuer Country Code (alpha3)")

        // ── Application ───────────────────────────────────────────────────
        tag("61",   "Application Template")               // constructed
        tag("6F",   "File Control Information (FCI) Template") // constructed
        tag("70",   "Record Template")                    // constructed
        tag("71",   "Issuer Script Template 1")           // constructed
        tag("72",   "Issuer Script Template 2")           // constructed
        tag("73",   "Directory Discretionary Template")   // constructed
        tag("77",   "Response Message Template Format 2") // constructed
        tag("80",   "Response Message Template Format 1")
        tag("82",   "Application Interchange Profile",    ::decodeAIP)
        tag("83",   "Command Template")
        tag("84",   "Dedicated File (DF) Name",           ::decodeAID)
        tag("86",   "Issuer Script Command")
        tag("87",   "Application Priority Indicator",     ::decodeDecimal)
        tag("88",   "Short File Identifier (SFI)")
        tag("89",   "Authorisation Code")
        tag("8A",   "Authorisation Response Code",        ::decodeResponseCode)
        tag("8C",   "Card Risk Management Data Object List 1 (CDOL1)")
        tag("8D",   "Card Risk Management Data Object List 2 (CDOL2)")
        tag("8E",   "Cardholder Verification Method (CVM) List")
        tag("8F",   "Certification Authority Public Key Index",  ::decodeDecimal)
        tag("90",   "Issuer Public Key Certificate")
        tag("91",   "Issuer Authentication Data")
        tag("92",   "Issuer Public Key Remainder")
        tag("93",   "Signed Static Application Data")
        tag("94",   "Application File Locator (AFL)")
        tag("95",   "Terminal Verification Results",      ::decodeTVR)
        tag("97",   "Transaction Certificate Data Object List (TDOL)")
        tag("98",   "Transaction Certificate (TC) Hash Value")
        tag("99",   "Transaction Personal Identification Number (PIN) Data")
        tag("9A",   "Transaction Date",                   ::decodeYYMMDD)
        tag("9B",   "Transaction Status Information",     ::decodeTSI)
        tag("9C",   "Transaction Type",                   ::decodeTransactionType)
        tag("9D",   "Directory Definition File Name")
        tag("9F01", "Acquirer Identifier")
        tag("9F02", "Amount, Authorised (Numeric)",       ::decodeAmount6)
        tag("9F03", "Amount, Other (Numeric)",            ::decodeAmount6)
        tag("9F04", "Amount, Other (Binary)")
        tag("9F05", "Application Discretionary Data")
        tag("9F06", "Application Identifier (AID) — Terminal", ::decodeAID)
        tag("9F07", "Application Usage Control",          ::decodeAUC)
        tag("9F08", "Application Version Number — Card",  ::decodeHex)
        tag("9F09", "Application Version Number — Terminal", ::decodeHex)
        tag("9F0B", "Cardholder Name Extended")
        tag("9F0D", "Issuer Action Code — Default",       ::decodeIAC)
        tag("9F0E", "Issuer Action Code — Denial",        ::decodeIAC)
        tag("9F0F", "Issuer Action Code — Online",        ::decodeIAC)
        tag("9F10", "Issuer Application Data",            ::decodeHex)
        tag("9F11", "Issuer Code Table Index",            ::decodeDecimal)
        tag("9F12", "Application Preferred Name")
        tag("9F13", "Last Online ATC Register",           ::decodeHex)
        tag("9F14", "Lower Consecutive Offline Limit",    ::decodeDecimal)
        tag("9F15", "Merchant Category Code",             ::decodeMCC)
        tag("9F16", "Merchant Identifier")
        tag("9F17", "Personal Identification Number (PIN) Try Counter", ::decodeDecimal)
        tag("9F18", "Issuer Script Identifier",           ::decodeHex)
        tag("9F1A", "Terminal Country Code",              ::decodeCountryCode)
        tag("9F1B", "Terminal Floor Limit",               ::decodeAmount4)
        tag("9F1C", "Terminal Identification")
        tag("9F1D", "Terminal Risk Management Data")
        tag("9F1E", "Interface Device Serial Number")
        tag("9F1F", "Track 1 Discretionary Data")
        tag("9F20", "Track 2 Discretionary Data")
        tag("9F21", "Transaction Time",                   ::decodeHHMMSS)
        tag("9F22", "Certification Authority Public Key Index — Terminal", ::decodeDecimal)
        tag("9F23", "Upper Consecutive Offline Limit",    ::decodeDecimal)
        tag("9F24", "Payment Account Reference")
        tag("9F25", "Last 4 Digits of PAN")
        tag("9F26", "Application Cryptogram",             ::decodeHex)
        tag("9F27", "Cryptogram Information Data",        ::decodeCryptogramType)
        tag("9F28", "Issuer Public Key Exponent")
        tag("9F29", "Extended Selection")
        tag("9F2D", "ICC PIN Encipherment Public Key Certificate")
        tag("9F2E", "ICC PIN Encipherment Public Key Exponent")
        tag("9F2F", "ICC PIN Encipherment Public Key Remainder")
        tag("9F32", "Issuer Public Key Exponent")
        tag("9F33", "Terminal Capabilities",              ::decodeTerminalCapabilities)
        tag("9F34", "Cardholder Verification Method (CVM) Results", ::decodeCVMResults)
        tag("9F35", "Terminal Type",                      ::decodeTerminalType)
        tag("9F36", "Application Transaction Counter (ATC)", ::decodeDecimal)
        tag("9F37", "Unpredictable Number",               ::decodeHex)
        tag("9F38", "Processing Options Data Object List (PDOL)")
        tag("9F39", "Point of Service (POS) Entry Mode",  ::decodeHex)
        tag("9F3A", "Amount, Reference Currency")
        tag("9F3B", "Application Reference Currency")
        tag("9F3C", "Transaction Reference Currency Code", ::decodeCurrencyCode)
        tag("9F3D", "Transaction Reference Currency Exponent")
        tag("9F40", "Additional Terminal Capabilities",   ::decodeHex)
        tag("9F41", "Transaction Sequence Counter",       ::decodeDecimal)
        tag("9F42", "Application Currency Code",         ::decodeCurrencyCode)
        tag("9F43", "Application Reference Currency Exponent")
        tag("9F44", "Application Currency Exponent",     ::decodeDecimal)
        tag("9F45", "Data Authentication Code",          ::decodeHex)
        tag("9F46", "ICC Public Key Certificate")
        tag("9F47", "ICC Public Key Exponent")
        tag("9F48", "ICC Public Key Remainder")
        tag("9F49", "Dynamic Data Authentication Data Object List (DDOL)")
        tag("9F4A", "Static Data Authentication Tag List")
        tag("9F4B", "Signed Dynamic Application Data")
        tag("9F4C", "ICC Dynamic Number",                 ::decodeHex)
        tag("9F4D", "Log Entry")
        tag("9F4E", "Merchant Name and Location")
        tag("9F4F", "Log Format")
        tag("9F51", "Application Currency Code",         ::decodeCurrencyCode)
        tag("9F52", "Card Verification Results")
        tag("9F53", "Consecutive Transaction Limit (International — Country)")
        tag("9F54", "Cumulative Total Transaction Amount Limit")
        tag("9F55", "Geographic Indicator")
        tag("9F56", "Issuer Authentication Indicator")
        tag("9F57", "Issuer Country Code",               ::decodeCountryCode)
        tag("9F58", "Lower Consecutive Offline Limit — International")
        tag("9F59", "Upper Consecutive Offline Limit — International")
        tag("9F5A", "Application Program Identifier")
        tag("9F5B", "Issuer Script Results")
        tag("9F5C", "Cumulative Total Transaction Amount Upper Limit")
        tag("9F60", "CVC3 (Track1)")
        tag("9F61", "CVC3 (Track2)")
        tag("9F62", "PCVC3 (Track1)")
        tag("9F63", "PUNATC (Track1)")
        tag("9F64", "NATC (Track1)")
        tag("9F65", "PCVC3 (Track2)")
        tag("9F66", "Terminal Transaction Qualifiers (TTQ)", ::decodeTTQ)
        tag("9F67", "NATC (Track2)")
        tag("9F68", "Card Additional Processes")
        tag("9F69", "Card Authentication Related Data")
        tag("9F6B", "Track 2 Data")
        tag("9F6C", "Card Transaction Qualifiers (CTQ)", ::decodeCTQ)
        tag("9F6D", "Mag-stripe Application Version Number — Reader")
        tag("9F6E", "Form Factor Indicator",             ::decodeFormFactor)
        tag("9F7C", "Customer Exclusive Data")
        tag("A5",   "File Control Information (FCI) Proprietary Template") // constructed
        tag("BF0C", "File Control Information (FCI) Issuer Discretionary Data") // constructed
        tag("DF01", "Reference PIN")
        tag("DF02", "Encrypted PIN Block")
        tag("DF78", "CDOL 1 Related Data Length")
        tag("DF79", "Authorization Code")
        tag("DF7E", "IBAN")
    }

    /** Returns a [TagEntry] for the tag, or null if not in the dictionary. */
    fun lookup(tag: String): TagEntry? = registry[tag.uppercase()]

    // -----------------------------------------------------------------------
    // Decode functions
    // -----------------------------------------------------------------------

    private fun decodeHex(hex: String): String = hex.uppercase()
    private fun decodeDecimal(hex: String): String =
        hex.toLongOrNull(16)?.toString() ?: hex.uppercase()

    private fun decodeAID(hex: String): String {
        val upper = hex.uppercase()
        val scheme = when {
            upper.startsWith("A0000000031010") -> "Visa Credit/Debit"
            upper.startsWith("A0000000032010") -> "Visa Electron"
            upper.startsWith("A0000000033010") -> "Visa Interlink"
            upper.startsWith("A0000000038010") -> "Visa Plus"
            upper.startsWith("A0000000041010") -> "Mastercard"
            upper.startsWith("A0000000042010") -> "Mastercard Specific"
            upper.startsWith("A0000000043060") -> "Maestro"
            upper.startsWith("A0000000044010") -> "Mastercard Cirrus"
            upper.startsWith("A0000000651010") -> "JCB"
            upper.startsWith("A000000025")     -> "American Express"
            upper.startsWith("A0000003710001") -> "Verve"
            upper.startsWith("A0000003710002") -> "Verve Prepaid"
            upper.startsWith("A000000004")     -> "Mastercard"
            upper.startsWith("A000000003")     -> "Visa"
            else -> null
        }
        return if (scheme != null) "$scheme  ($upper)" else upper
    }

    private fun decodeTrack2Equivalent(hex: String): String {
        val ascii = hex.chunked(2).mapNotNull { it.toIntOrNull(16)?.toChar() }.joinToString("")
        return "Track 2: $ascii"
    }

    private fun decodeYYMMDD(hex: String): String {
        if (hex.length < 6) return hex
        // EMV dates are BCD-encoded: each hex pair represents decimal digits literally
        // e.g. "260521" → year=26, month=05, day=21 (NOT hex→int conversion)
        val yy = hex.substring(0, 2).toIntOrNull(10) ?: return hex
        val mm = hex.substring(2, 4).toIntOrNull(10) ?: return hex
        val dd = hex.substring(4, 6).toIntOrNull(10) ?: return hex
        if (mm < 1 || mm > 12 || dd < 1 || dd > 31) return hex
        val monthName = monthName("%02d".format(mm))
        return "$dd $monthName 20%02d".format(yy)
    }

    private fun decodeHHMMSS(hex: String): String {
        if (hex.length < 6) return hex
        val hh = hex.substring(0, 2).toIntOrNull(16) ?: return hex
        val mm = hex.substring(2, 4).toIntOrNull(16) ?: return hex
        val ss = hex.substring(4, 6).toIntOrNull(16) ?: return hex
        return "%02d:%02d:%02d".format(hh, mm, ss)
    }

    private fun decodeCountryCode(hex: String): String {
        val code = hex.toIntOrNull(16)?.toString()?.padStart(3, '0') ?: hex
        val name = ISO_COUNTRY_CODES[code]
        return if (name != null) "$name (ISO 3166: $code)" else "Country $code"
    }

    private fun decodeCurrencyCode(hex: String): String {
        val code = hex.toIntOrNull(16)?.toString()?.padStart(3, '0') ?: hex
        val info = CurrencyTable.lookup(code)
        return if (info != null) "${info.name} ${info.symbol} (ISO 4217: $code)" else "Currency $code"
    }

    private fun decodeAmount6(hex: String): String {
        val amount = hex.toLongOrNull(16) ?: return hex
        return "%,d minor units".format(amount)
    }

    private fun decodeAmount4(hex: String): String = decodeAmount6(hex)

    private fun decodeResponseCode(hex: String): String {
        val ascii = hex.chunked(2)
            .mapNotNull { it.toIntOrNull(16)?.toChar() }
            .joinToString("")
        return FieldDecoders.decodeResponseCode(ascii).decodedValue
    }

    private fun decodeMCC(hex: String): String {
        val code = hex.toIntOrNull(16)?.toString()?.padStart(4, '0') ?: hex
        return MccTable.lookup(code)
    }

    private fun decodeTransactionType(hex: String): String = when (hex.uppercase()) {
        "00" -> "Purchase / Goods and Services"
        "01" -> "Cash"
        "09" -> "Purchase with Cashback"
        "20" -> "Refund / Return"
        "21" -> "Inquiry"
        "22" -> "Transfer"
        "30" -> "Balance Inquiry"
        "40" -> "Cash Advance"
        "50" -> "Payment"
        "60" -> "Administrative"
        "70" -> "Incremental Authorization"
        else -> "Transaction Type $hex"
    }

    // ── AIP (Application Interchange Profile) ─────────────────────────────
    private fun decodeAIP(hex: String): String {
        val value = hex.toLongOrNull(16) ?: return hex
        val byte1 = (value shr 8) and 0xFF
        val flags = buildList {
            if ((byte1 and 0x40) != 0L) add("SDA supported")
            if ((byte1 and 0x20) != 0L) add("DDA supported")
            if ((byte1 and 0x10) != 0L) add("Cardholder verification supported")
            if ((byte1 and 0x08) != 0L) add("Terminal risk management required")
            if ((byte1 and 0x04) != 0L) add("Issuer authentication supported")
            if ((byte1 and 0x02) != 0L) add("On device CVM supported")
            if ((byte1 and 0x01) != 0L) add("CDA supported")
        }
        return if (flags.isEmpty()) "No capabilities flagged ($hex)" else flags.joinToString("  ·  ")
    }

    // ── TVR (Terminal Verification Results) ───────────────────────────────
    private fun decodeTVR(hex: String): String {
        if (hex.length < 10) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val b2 = hex.substring(2, 4).toIntOrNull(16) ?: 0
        val b3 = hex.substring(4, 6).toIntOrNull(16) ?: 0
        val b4 = hex.substring(6, 8).toIntOrNull(16) ?: 0
        val b5 = hex.substring(8, 10).toIntOrNull(16) ?: 0

        val failures = mutableListOf<String>()
        val passes   = mutableListOf<String>()

        // Byte 1
        if ((b1 and 0x80) != 0) failures += "Offline data authentication not performed"
        if ((b1 and 0x40) != 0) failures += "SDA failed"
        if ((b1 and 0x20) != 0) failures += "ICC data missing"
        if ((b1 and 0x10) != 0) failures += "Card on terminal exception file"
        if ((b1 and 0x08) != 0) failures += "DDA failed"
        if ((b1 and 0x04) != 0) failures += "CDA failed"

        // Byte 2
        if ((b2 and 0x80) != 0) failures += "ICC and terminal have different app versions"
        if ((b2 and 0x40) != 0) failures += "Expired application"
        if ((b2 and 0x20) != 0) failures += "Application not yet effective"
        if ((b2 and 0x10) != 0) failures += "Requested service not allowed for card product"
        if ((b2 and 0x08) != 0) failures += "New card"

        // Byte 3
        if ((b3 and 0x80) != 0) failures += "Cardholder verification not performed"
        if ((b3 and 0x40) != 0) failures += "Unrecognised CVM"
        if ((b3 and 0x20) != 0) failures += "PIN Try Limit exceeded"
        if ((b3 and 0x10) != 0) failures += "PIN entry required, PIN pad not present"
        if ((b3 and 0x08) != 0) failures += "PIN entry required, PIN pad present, but PIN not entered"
        if ((b3 and 0x04) != 0) failures += "Online PIN entered"
        if ((b3 and 0x08) == 0 && (b3 and 0x80) == 0) passes += "Cardholder verification performed"

        // Byte 4
        if ((b4 and 0x80) != 0) failures += "Transaction exceeds floor limit"
        if ((b4 and 0x40) != 0) failures += "Lower consecutive offline limit exceeded"
        if ((b4 and 0x20) != 0) failures += "Upper consecutive offline limit exceeded"
        if ((b4 and 0x10) != 0) failures += "Transaction selected randomly for online processing"
        if ((b4 and 0x08) != 0) failures += "Merchant forced transaction online"
        if ((b4 and 0x04) == 0) passes += "Terminal risk management performed"

        // Byte 5
        if ((b5 and 0x80) != 0) failures += "Default TDOL used"
        if ((b5 and 0x40) != 0) failures += "Issuer authentication failed"
        if ((b5 and 0x20) != 0) failures += "Script processing failed before final GENERATE AC"
        if ((b5 and 0x10) != 0) failures += "Script processing failed after final GENERATE AC"
        if ((b5 and 0x40) == 0) passes += "Issuer auth data verified"

        val sb = StringBuilder()
        failures.forEach { sb.append("⚠  $it\n") }
        passes.forEach   { sb.append("✓  $it\n") }
        return sb.toString().trimEnd()
    }

    // ── TSI (Transaction Status Information) ──────────────────────────────
    private fun decodeTSI(hex: String): String {
        if (hex.length < 4) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val flags = buildList {
            if ((b1 and 0x80) != 0) add("Offline data authentication performed")
            if ((b1 and 0x40) != 0) add("Cardholder verification performed")
            if ((b1 and 0x20) != 0) add("Card risk management performed")
            if ((b1 and 0x10) != 0) add("Issuer authentication performed")
            if ((b1 and 0x08) != 0) add("Terminal risk management performed")
            if ((b1 and 0x04) != 0) add("Script processing performed")
        }
        return flags.joinToString("  ·  ").ifBlank { "No actions performed" }
    }

    // ── CID (Cryptogram Information Data) ─────────────────────────────────
    private fun decodeCryptogramType(hex: String): String = when (hex.uppercase()) {
        "00" -> "AAC — Application Authentication Cryptogram (offline declined)"
        "40" -> "TC  — Transaction Certificate (offline approved)"
        "80" -> "ARQC — Authorisation Request Cryptogram (online auth requested)"
        "C0" -> "RFU"
        else -> "Unknown ($hex)"
    }

    // ── Terminal Capabilities ──────────────────────────────────────────────
    private fun decodeTerminalCapabilities(hex: String): String {
        if (hex.length < 6) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val b2 = hex.substring(2, 4).toIntOrNull(16) ?: 0
        val b3 = hex.substring(4, 6).toIntOrNull(16) ?: 0

        val caps = buildList {
            // Byte 1 — card data input
            if ((b1 and 0x80) != 0) add("Manual key entry")
            if ((b1 and 0x40) != 0) add("Magnetic stripe")
            if ((b1 and 0x20) != 0) add("IC with contacts")
            // Byte 2 — CVM
            if ((b2 and 0x80) != 0) add("Plaintext PIN for offline")
            if ((b2 and 0x40) != 0) add("Enciphered PIN for online")
            if ((b2 and 0x20) != 0) add("Signature")
            if ((b2 and 0x10) != 0) add("Enciphered PIN for offline")
            if ((b2 and 0x08) != 0) add("No CVM required")
            // Byte 3 — security
            if ((b3 and 0x80) != 0) add("SDA")
            if ((b3 and 0x40) != 0) add("DDA")
            if ((b3 and 0x20) != 0) add("Card capture")
            if ((b3 and 0x08) != 0) add("CDA")
        }
        return caps.joinToString("  ·  ").ifBlank { hex }
    }

    // ── CVM Results ────────────────────────────────────────────────────────
    private fun decodeCVMResults(hex: String): String {
        if (hex.length < 6) return hex
        val method    = hex.substring(0, 2)
        val condition = hex.substring(2, 4)
        val result    = hex.substring(4, 6)

        val methodStr = when (method.uppercase()) {
            "00" -> "Fail CVM processing"
            "01" -> "Plaintext PIN verification by ICC"
            "02" -> "Enciphered PIN verification by ICC"
            "03" -> "Plaintext PIN verification by ICC + signature"
            "04" -> "Enciphered PIN verification by ICC + signature"
            "1E" -> "Signature (paper)"
            "1F" -> "No CVM required"
            "3F" -> "CVM not performed"
            else -> "Method $method"
        }
        val resultStr = when (result.uppercase()) {
            "00" -> "Unknown"
            "01" -> "Failed"
            "02" -> "Successful"
            else -> "Result $result"
        }
        return "$methodStr  ·  Condition: $condition  ·  Result: $resultStr"
    }

    // ── Terminal Type ──────────────────────────────────────────────────────
    private fun decodeTerminalType(hex: String): String {
        val b = hex.toIntOrNull(16) ?: return hex
        val attendant = when ((b shr 4) and 0xF) {
            2 -> "Attended"
            2 -> "Unattended"
            6 -> "Attended"
            else -> "Type ${(b shr 4) and 0xF}"
        }
        val location = when (b and 0xF) {
            1 -> "On-premise"
            2 -> "Off-premise (merchant)"
            3 -> "Off-premise (cardholder)"
            4 -> "On-premise (unattended)"
            else -> "Location ${b and 0xF}"
        }
        return "Terminal: $attendant  ·  $location  ($hex)"
    }

    // ── AUC (Application Usage Control) ───────────────────────────────────
    private fun decodeAUC(hex: String): String {
        if (hex.length < 4) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val b2 = hex.substring(2, 4).toIntOrNull(16) ?: 0
        val flags = buildList {
            if ((b1 and 0x80) != 0) add("Valid for domestic cash")
            if ((b1 and 0x40) != 0) add("Valid for international cash")
            if ((b1 and 0x20) != 0) add("Valid for domestic goods")
            if ((b1 and 0x10) != 0) add("Valid for international goods")
            if ((b1 and 0x08) != 0) add("Valid for domestic services")
            if ((b1 and 0x04) != 0) add("Valid for international services")
            if ((b1 and 0x02) != 0) add("Valid at ATMs")
            if ((b1 and 0x01) != 0) add("Valid at terminals other than ATMs")
            if ((b2 and 0x80) != 0) add("Domestic cashback allowed")
            if ((b2 and 0x40) != 0) add("International cashback allowed")
        }
        return flags.joinToString("  ·  ").ifBlank { "No usage restrictions ($hex)" }
    }

    // ── IAC (Issuer Action Code) ───────────────────────────────────────────
    private fun decodeIAC(hex: String): String = decodeTVR(hex)  // same bit structure as TVR

    // ── TTQ (Terminal Transaction Qualifiers) ─────────────────────────────
    private fun decodeTTQ(hex: String): String {
        if (hex.length < 8) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val b2 = hex.substring(2, 4).toIntOrNull(16) ?: 0
        val flags = buildList {
            if ((b1 and 0x80) != 0) add("MSD supported")
            if ((b1 and 0x20) != 0) add("qVSDC/EMV mode supported")
            if ((b1 and 0x10) != 0) add("EMV contact chip supported")
            if ((b1 and 0x08) != 0) add("Offline-only reader")
            if ((b1 and 0x04) != 0) add("Online PIN supported")
            if ((b1 and 0x02) != 0) add("Signature supported")
            if ((b1 and 0x01) != 0) add("ODA for Online Authorisations supported")
            if ((b2 and 0x80) != 0) add("Online cryptogram required")
            if ((b2 and 0x40) != 0) add("CVM required")
            if ((b2 and 0x20) != 0) add("Contact chip offline PIN supported")
        }
        return flags.joinToString("  ·  ").ifBlank { hex }
    }

    // ── CTQ (Card Transaction Qualifiers) ─────────────────────────────────
    private fun decodeCTQ(hex: String): String {
        if (hex.length < 4) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val flags = buildList {
            if ((b1 and 0x80) != 0) add("Online PIN required")
            if ((b1 and 0x40) != 0) add("Signature required")
            if ((b1 and 0x20) != 0) add("Online if offline — offline not available")
            if ((b1 and 0x10) != 0) add("Switch interface if offline not available")
            if ((b1 and 0x08) != 0) add("Switch interface if CVM was online PIN")
            if ((b1 and 0x04) != 0) add("Consumer Device CVM performed")
        }
        return flags.joinToString("  ·  ").ifBlank { hex }
    }

    // ── Form Factor Indicator ─────────────────────────────────────────────
    private fun decodeFormFactor(hex: String): String {
        if (hex.length < 8) return hex
        val b1 = hex.substring(0, 2).toIntOrNull(16) ?: 0
        val form = when ((b1 shr 4) and 0xF) {
            0 -> "Unknown"
            1 -> "Card"
            2 -> "Mobile (NFC)"
            3 -> "Wearable"
            4 -> "Watch"
            5 -> "Tablet"
            6 -> "PC"
            else -> "Form ${(b1 shr 4) and 0xF}"
        }
        return form
    }

    // -----------------------------------------------------------------------
    // ISO 3166 country codes (selected)
    // -----------------------------------------------------------------------

    private val ISO_COUNTRY_CODES = mapOf(
        "566" to "Nigeria",
        "840" to "United States",
        "826" to "United Kingdom",
        "276" to "Germany",
        "250" to "France",
        "380" to "Italy",
        "724" to "Spain",
        "528" to "Netherlands",
        "756" to "Switzerland",
        "752" to "Sweden",
        "578" to "Norway",
        "208" to "Denmark",
        "246" to "Finland",
        "620" to "Portugal",
        "056" to "Belgium",
        "040" to "Austria",
        "300" to "Greece",
        "616" to "Poland",
        "203" to "Czech Republic",
        "348" to "Hungary",
        "642" to "Romania",
        "100" to "Bulgaria",
        "191" to "Croatia",
        "703" to "Slovakia",
        "705" to "Slovenia",
        "840" to "United States",
        "124" to "Canada",
        "484" to "Mexico",
        "076" to "Brazil",
        "032" to "Argentina",
        "152" to "Chile",
        "170" to "Colombia",
        "604" to "Peru",
        "858" to "Uruguay",
        "862" to "Venezuela",
        "036" to "Australia",
        "554" to "New Zealand",
        "356" to "India",
        "156" to "China",
        "392" to "Japan",
        "410" to "South Korea",
        "702" to "Singapore",
        "458" to "Malaysia",
        "764" to "Thailand",
        "360" to "Indonesia",
        "608" to "Philippines",
        "704" to "Vietnam",
        "704" to "Vietnam",
        "682" to "Saudi Arabia",
        "784" to "United Arab Emirates",
        "792" to "Turkey",
        "818" to "Egypt",
        "710" to "South Africa",
        "288" to "Ghana",
        "404" to "Kenya",
        "800" to "Uganda",
        "834" to "Tanzania",
        "524" to "Nepal",
        "050" to "Bangladesh"
    )

    private fun monthName(mm: String): String = when (mm) {
        "01" -> "January"; "02" -> "February"; "03" -> "March"; "04" -> "April"
        "05" -> "May"; "06" -> "June"; "07" -> "July"; "08" -> "August"
        "09" -> "September"; "10" -> "October"; "11" -> "November"; "12" -> "December"
        else -> mm
    }
}
