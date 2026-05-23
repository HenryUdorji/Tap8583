package com.tap8583.tap8583.decoder

/**
 * Sub-field and semantic decoders for individual ISO 8583 data elements.
 *
 * Each decoder accepts the raw string value (as logged by jPOS) and returns
 * a [DecodeResult] with a human-readable primary [decodedValue] and optional
 * [subFields] for composite fields.
 *
 * The context map provides sibling field values needed for cross-field
 * decoding (e.g. DE4 needs DE49 for the currency symbol).
 */
object FieldDecoders {

    data class DecodeResult(
        val decodedValue: String,
        val subFields: List<SubField> = emptyList()
    )

    // -----------------------------------------------------------------------
    // Dispatcher
    // -----------------------------------------------------------------------

    /**
     * Dispatch to the appropriate decoder for [fieldNumber].
     *
     * @param fieldNumber ISO 8583 field number
     * @param rawValue    The raw string value from jPOS
     * @param context     Other fields in the same message (for cross-field decoding)
     * @return [DecodeResult] or null if no decoder exists for this field
     */
    fun decode(
        fieldNumber: Int,
        rawValue: String,
        context: Map<Int, String> = emptyMap()
    ): DecodeResult? = when (fieldNumber) {
        2    -> decodePan(rawValue)
        3    -> decodeProcessingCode(rawValue)
        4    -> decodeAmount(rawValue, context[49])
        5    -> decodeAmount(rawValue, context[50])
        6    -> decodeAmount(rawValue, context[51])
        7    -> decodeTransmissionDateTime(rawValue)
        12   -> decodeLocalTime(rawValue)
        13   -> decodeLocalDate(rawValue)
        14   -> decodeExpiryDate(rawValue)
        18   -> decodeMcc(rawValue)
        22   -> decodePosEntryMode(rawValue)
        25   -> decodePosConditionCode(rawValue)
        32   -> decodeAcquiringInstitution(rawValue)
        35   -> decodeTrack2(rawValue)
        39   -> decodeResponseCode(rawValue)
        49   -> decodeCurrency(rawValue)
        50   -> decodeCurrency(rawValue)
        51   -> decodeCurrency(rawValue)
        52   -> DecodeResult("████████████████  [Encrypted PIN block — 8 bytes, 3DES/ANSI X9.8]")
        54   -> decodeField54(rawValue)
        59   -> decodeNibssReference(rawValue)
        62   -> decodeField62(rawValue)
        70   -> decodeNetworkManagementCode(rawValue)
        123  -> decodeNibssTerminalCapabilities(rawValue)
        128  -> DecodeResult("${rawValue}  [NIBSS 3DES MAC — message integrity check — 32 bytes]")
        64   -> DecodeResult("${rawValue}  [3DES MAC — 8 bytes]")
        else -> null
    }

    // -----------------------------------------------------------------------
    // DE2 — Primary Account Number
    // -----------------------------------------------------------------------

    fun decodePan(pan: String): DecodeResult {
        val scheme = detectCardScheme(pan)
        val luhn   = if (luhnCheck(pan)) "✓" else "✗ FAIL"
        val masked = maskPan(pan)
        return DecodeResult(
            decodedValue = "$scheme  ·  ${pan.length} digits  ·  Luhn: $luhn",
            subFields = listOf(
                SubField("Masked PAN", pan, masked),
                SubField("Card Scheme", pan, scheme),
                SubField("Length", pan, "${pan.length} digits"),
                SubField("Luhn Check", pan, luhn)
            )
        )
    }

    private fun maskPan(pan: String): String {
        if (pan.length < 8) return "•".repeat(pan.length)
        val last4 = pan.takeLast(4)
        val masked = "••••  ••••  ••••  "
        return masked + last4
    }

    private fun detectCardScheme(pan: String): String {
        val bin6 = pan.take(6)
        val bin8 = pan.take(8)
        return when {
            pan.startsWith("506") || pan.startsWith("6500") -> "Verve"
            pan.startsWith("4")   -> "Visa"
            pan.startsWith("51") || pan.startsWith("52") || pan.startsWith("53") ||
            pan.startsWith("54") || pan.startsWith("55") -> "Mastercard"
            pan.startsWith("2221") || pan.startsWith("25") || pan.startsWith("26") ||
            pan.startsWith("27")  -> "Mastercard"
            pan.startsWith("34") || pan.startsWith("37") -> "American Express"
            pan.startsWith("35")  -> "JCB"
            pan.startsWith("6011") || pan.startsWith("65") -> "Discover"
            pan.startsWith("62")  -> "UnionPay"
            else                  -> "Unknown Scheme"
        }
    }

    private fun luhnCheck(number: String): Boolean {
        val digits = number.filter { it.isDigit() }
        if (digits.isEmpty()) return false
        var sum = 0
        var alternate = false
        for (i in digits.indices.reversed()) {
            var n = digits[i].digitToInt()
            if (alternate) {
                n *= 2
                if (n > 9) n -= 9
            }
            sum += n
            alternate = !alternate
        }
        return sum % 10 == 0
    }

    // -----------------------------------------------------------------------
    // DE3 — Processing Code
    // -----------------------------------------------------------------------

    fun decodeProcessingCode(code: String): DecodeResult {
        if (code.length != 6) return DecodeResult("Invalid processing code length (expected 6 digits)")

        val txnType = code.substring(0, 2)
        val fromAct = code.substring(2, 4)
        val toAct   = code.substring(4, 6)

        val txnLabel  = txnTypeLabel(txnType)
        val fromLabel = accountTypeLabel(fromAct)
        val toLabel   = accountTypeLabel(toAct)

        return DecodeResult(
            decodedValue = "$txnLabel  ·  From: $fromLabel  ·  To: $toLabel",
            subFields = listOf(
                SubField("Digits 1–2 (Transaction Type)", txnType, txnLabel),
                SubField("Digits 3–4 (From Account)",    fromAct, fromLabel),
                SubField("Digits 5–6 (To Account)",      toAct,   toLabel)
            )
        )
    }

    private fun txnTypeLabel(code: String): String = when (code) {
        "00" -> "Purchase"
        "01" -> "Cash"
        "02" -> "Adjustment"
        "09" -> "Purchase with Cashback"
        "10" -> "Account Funding"
        "11" -> "Quasi-Cash"
        "17" -> "Payment"
        "20" -> "Refund / Return"
        "28" -> "Balance Enquiry"
        "30" -> "Available Funds Enquiry"
        "31" -> "Balance Enquiry"
        "38" -> "Balance Enquiry"
        "40" -> "Transfer"
        "50" -> "Payment"
        "90" -> "Mini-Statement"
        "91" -> "Bill Payment"
        "96" -> "PIN Change"
        "9A" -> "Terminal Master Key"
        "9B" -> "Terminal Session Key "
        "9C" -> "Terminal Parameter Download"
        "9D" -> "Call – home"
        "9G" -> "Terminal PIN Key"
        else -> "Unknown ($code)"
    }

    private fun accountTypeLabel(code: String): String = when (code) {
        "00" -> "Default / Unspecified"
        "10" -> "Savings"
        "20" -> "Cheque / Current"
        "30" -> "Credit"
        "40" -> "Universal"
        "60" -> "Investment"
        "90" -> "Not Applicable"
        else -> "Unknown ($code)"
    }

    // -----------------------------------------------------------------------
    // DE4 / DE5 / DE6 — Amount fields
    // -----------------------------------------------------------------------

    fun decodeAmount(minorUnits: String, currencyCode: String?): DecodeResult {
        val currency = currencyCode?.let { CurrencyTable.lookup(it) }
        val amount   = minorUnits.toLongOrNull() ?: return DecodeResult("Invalid amount: $minorUnits")
        val decimals = currency?.decimals ?: 2
        val symbol   = currency?.symbol ?: "?"
        val name     = currency?.name ?: "Unknown currency (ISO 4217: $currencyCode)"

        val formatted = formatMinorUnits(amount, decimals)
        val display   = "$symbol $formatted"

        return DecodeResult(
            decodedValue = display,
            subFields = listOf(
                SubField("Formatted Amount", minorUnits, display),
                SubField("Minor Units",      minorUnits, "$amount"),
                SubField("Currency",         currencyCode ?: "?", "$name ($symbol)"),
                SubField("Decimal Places",   decimals.toString(), decimals.toString())
            )
        )
    }

    private fun formatMinorUnits(amount: Long, decimals: Int): String {
        return if (decimals == 0) {
            "%,d".format(amount)
        } else {
            val divisor = Math.pow(10.0, decimals.toDouble()).toLong()
            val whole   = amount / divisor
            val frac    = amount % divisor
            "%,d.%0${decimals}d".format(whole, frac)
        }
    }

    // -----------------------------------------------------------------------
    // DE7 — Transmission Date/Time
    // -----------------------------------------------------------------------

    fun decodeTransmissionDateTime(raw: String): DecodeResult {
        if (raw.length != 10) return DecodeResult("Invalid date/time format (expected MMDDhhmmss, got: $raw)")
        val month  = raw.substring(0, 2)
        val day    = raw.substring(2, 4)
        val hour   = raw.substring(4, 6)
        val minute = raw.substring(6, 8)
        val second = raw.substring(8, 10)
        val monthName = monthName(month)
        return DecodeResult(
            decodedValue = "$monthName $day, ${hour}:${minute}:${second} UTC",
            subFields = listOf(
                SubField("Month",  month,  monthName),
                SubField("Day",    day,    day),
                SubField("Hour",   hour,   hour),
                SubField("Minute", minute, minute),
                SubField("Second", second, second)
            )
        )
    }

    // -----------------------------------------------------------------------
    // DE12 — Local Transaction Time
    // -----------------------------------------------------------------------

    fun decodeLocalTime(raw: String): DecodeResult {
        if (raw.length != 6) return DecodeResult("Invalid time (expected hhmmss)")
        return DecodeResult("${raw.substring(0,2)}:${raw.substring(2,4)}:${raw.substring(4,6)}")
    }

    // -----------------------------------------------------------------------
    // DE13 — Local Transaction Date
    // -----------------------------------------------------------------------

    fun decodeLocalDate(raw: String): DecodeResult {
        if (raw.length != 4) return DecodeResult("Invalid date (expected MMDD)")
        val month = raw.substring(0, 2)
        val day   = raw.substring(2, 4)
        return DecodeResult("${monthName(month)} $day")
    }

    // -----------------------------------------------------------------------
    // DE14 — Expiration Date
    // -----------------------------------------------------------------------

    fun decodeExpiryDate(raw: String): DecodeResult {
        if (raw.length != 4) return DecodeResult("Invalid expiry (expected YYMM)")
        val year  = "20${raw.substring(0, 2)}"
        val month = raw.substring(2, 4)
        return DecodeResult(
            decodedValue = "${monthName(month)} $year",
            subFields = listOf(
                SubField("Year",  raw.substring(0, 2), year),
                SubField("Month", month, monthName(month))
            )
        )
    }

    // -----------------------------------------------------------------------
    // DE18 — Merchant Category Code
    // -----------------------------------------------------------------------

    fun decodeMcc(raw: String): DecodeResult {
        val label = MccTable.lookup(raw)
        return DecodeResult(decodedValue = label)
    }

    // -----------------------------------------------------------------------
    // DE22 — POS Entry Mode
    // -----------------------------------------------------------------------

    fun decodePosEntryMode(raw: String): DecodeResult {
        if (raw.length < 2) return DecodeResult("Invalid POS entry mode")
        val entryCode  = raw.substring(0, 2)
        val pinCode    = if (raw.length >= 3) raw.substring(2, 3) else "?"
        val entryLabel = posEntryLabel(entryCode)
        val pinLabel   = pinCapabilityLabel(pinCode)
        val summary    = "$entryLabel · $pinLabel"
        return DecodeResult(
            decodedValue = summary,
            subFields = listOf(
                SubField("Entry Method (digits 1–2)", entryCode, entryLabel),
                SubField("PIN Capability (digit 3)",  pinCode,   pinLabel)
            )
        )
    }

    private fun posEntryLabel(code: String): String = when (code) {
        "00" -> "Unknown"
        "01" -> "Manual (key-entered)"
        "02" -> "Magnetic Stripe"
        "03" -> "Barcode"
        "04" -> "OCR"
        "05" -> "Integrated Circuit Card (Chip)"
        "06" -> "Track data from chip"
        "07" -> "Contactless ICC (NFC/tap)"
        "09" -> "E-commerce"
        "10" -> "Credential on File"
        "79" -> "Hybrid terminal — fallback to magstripe"
        "80" -> "Fallback from chip to magstripe"
        "81" -> "Electronic Commerce"
        "82" -> "Server — track 1"
        "90" -> "Magnetic Stripe (full track)"
        "91" -> "Contactless Magnetic Stripe"
        "95" -> "Integrated Circuit Card"
        else -> "Unknown ($code)"
    }

    private fun pinCapabilityLabel(code: String): String = when (code) {
        "0" -> "Unspecified"
        "1" -> "PIN Entered"
        "2" -> "PIN Pad not present"
        "3" -> "PIN Bypass"
        "4" -> "Customer selected No PIN"
        "5" -> "PIN Verified by terminal"
        "6" -> "PIN Required, not entered"
        "8" -> "No PIN pad available"
        "9" -> "Unknown"
        else -> "Unknown ($code)"
    }

    // -----------------------------------------------------------------------
    // DE25 — POS Condition Code
    // -----------------------------------------------------------------------

    fun decodePosConditionCode(raw: String): DecodeResult {
        val label = when (raw) {
            "00" -> "Normal presentment"
            "01" -> "Customer not present"
            "02" -> "Unattended terminal"
            "03" -> "Merchant suspicious"
            "04" -> "MOTO / Electronic commerce"
            "05" -> "Cardholder present — card not present"
            "06" -> "Preauthorized"
            "07" -> "Recurring"
            "08" -> "Mail/Phone order"
            "51" -> "Account number on file"
            "59" -> "Electronic commerce"
            "71" -> "Installment"
            else -> "Unknown ($raw)"
        }
        return DecodeResult(label)
    }

    // -----------------------------------------------------------------------
    // DE32 — Acquiring Institution ID Code
    // -----------------------------------------------------------------------

    fun decodeAcquiringInstitution(raw: String): DecodeResult {
        val name = acquirerName(raw)
        return DecodeResult(
            decodedValue = name,
            subFields = listOf(SubField("IIN / BIN", raw, name))
        )
    }

    private fun acquirerName(iin: String): String = when {
        iin.startsWith("506124") || iin.startsWith("506") -> "Verve International / Interswitch"
        iin.startsWith("000001") -> "NIBSS Interbank Settlement"
        iin.startsWith("000002") -> "Access Bank"
        iin.startsWith("000003") -> "Citibank Nigeria"
        iin.startsWith("000004") -> "Diamond Bank"
        iin.startsWith("000005") -> "Ecobank Nigeria"
        iin.startsWith("000006") -> "First Bank of Nigeria"
        iin.startsWith("000007") -> "First City Monument Bank"
        iin.startsWith("000008") -> "Fidelity Bank"
        iin.startsWith("000009") -> "Guaranty Trust Bank"
        iin.startsWith("000010") -> "Heritage Bank"
        iin.startsWith("000011") -> "Keystone Bank"
        iin.startsWith("000012") -> "Stanbic IBTC Bank"
        iin.startsWith("000013") -> "Sterling Bank"
        iin.startsWith("000014") -> "Union Bank"
        iin.startsWith("000015") -> "United Bank for Africa"
        iin.startsWith("000016") -> "Unity Bank"
        iin.startsWith("000017") -> "Wema Bank"
        iin.startsWith("000018") -> "Zenith Bank"
        iin.startsWith("000019") -> "Jaiz Bank"
        iin.startsWith("000023") -> "Providus Bank"
        iin.startsWith("000024") -> "Rand Merchant Bank"
        iin.startsWith("000025") -> "Titan Trust Bank"
        iin.startsWith("000026") -> "Taj Bank"
        iin.startsWith("4")      -> "Visa International"
        iin.startsWith("5")      -> "Mastercard International"
        else                     -> "Institution IIN: $iin"
    }

    // -----------------------------------------------------------------------
    // DE35 — Track 2 Data
    // -----------------------------------------------------------------------

    fun decodeTrack2(raw: String): DecodeResult {
        // Format: PAN = D YYMM ServiceCode Discretionary
        val separatorIdx = raw.indexOfFirst { it == 'D' || it == '=' }
        if (separatorIdx < 0) return DecodeResult("Track 2 — separator not found")

        val pan           = raw.substring(0, separatorIdx)
        val rest          = raw.substring(separatorIdx + 1)
        val expiry        = rest.take(4)        // YYMM
        val serviceCode   = rest.drop(4).take(3)
        val discretionary = rest.drop(7)

        val maskedPan = maskPan(pan)
        val expiryDec = if (expiry.length == 4) {
            "${monthName(expiry.substring(2, 4))} 20${expiry.substring(0, 2)}"
        } else expiry

        val svcDecoded = serviceCodeMeaning(serviceCode)

        return DecodeResult(
            decodedValue = "PAN: $maskedPan  ·  Expiry: $expiryDec  ·  Service: $svcDecoded",
            subFields = listOf(
                SubField("PAN",           pan,           maskedPan),
                SubField("Expiry Date",   expiry,        expiryDec),
                SubField("Service Code",  serviceCode,   svcDecoded),
                SubField("Discretionary", discretionary, discretionary)
            )
        )
    }

    private fun serviceCodeMeaning(code: String): String = when (code) {
        "101" -> "International — PIN required"
        "120" -> "International — No PIN required"
        "121" -> "International — PIN preferred"
        "201" -> "International interchange — PIN required"
        "221" -> "International interchange — PIN preferred"
        "601" -> "Domestic — PIN required"
        "620" -> "Domestic — No PIN required"
        "621" -> "Domestic — PIN preferred"
        else  -> code
    }

    // -----------------------------------------------------------------------
    // DE39 — Response Code
    // -----------------------------------------------------------------------

    enum class ResponseSeverity { SUCCESS, WARNING, DECLINED, TIMEOUT, ERROR }

    data class ResponseCodeEntry(
        val code: String,
        val meaning: String,
        val severity: ResponseSeverity
    )

    val RESPONSE_CODES: List<ResponseCodeEntry> = listOf(
        ResponseCodeEntry("00", "Approved",                               ResponseSeverity.SUCCESS),
        ResponseCodeEntry("01", "Refer to Card Issuer",                   ResponseSeverity.WARNING),
        ResponseCodeEntry("02", "Refer to Card Issuer — Special",         ResponseSeverity.WARNING),
        ResponseCodeEntry("03", "Invalid Merchant",                       ResponseSeverity.DECLINED),
        ResponseCodeEntry("04", "Pick Up Card",                           ResponseSeverity.DECLINED),
        ResponseCodeEntry("05", "Do Not Honour",                          ResponseSeverity.DECLINED),
        ResponseCodeEntry("06", "Error",                                  ResponseSeverity.ERROR),
        ResponseCodeEntry("07", "Pick Up Card — Special",                 ResponseSeverity.DECLINED),
        ResponseCodeEntry("08", "Honour with Identification",             ResponseSeverity.WARNING),
        ResponseCodeEntry("09", "Request in Progress",                    ResponseSeverity.WARNING),
        ResponseCodeEntry("10", "Approved — Partial",                     ResponseSeverity.SUCCESS),
        ResponseCodeEntry("11", "Approved (VIP)",                         ResponseSeverity.SUCCESS),
        ResponseCodeEntry("12", "Invalid Transaction",                    ResponseSeverity.DECLINED),
        ResponseCodeEntry("13", "Invalid Amount",                         ResponseSeverity.DECLINED),
        ResponseCodeEntry("14", "Invalid Card Number",                    ResponseSeverity.DECLINED),
        ResponseCodeEntry("15", "No Such Issuer",                         ResponseSeverity.DECLINED),
        ResponseCodeEntry("17", "Customer Cancellation",                  ResponseSeverity.DECLINED),
        ResponseCodeEntry("19", "Re-enter Transaction",                   ResponseSeverity.WARNING),
        ResponseCodeEntry("20", "Invalid Response",                       ResponseSeverity.ERROR),
        ResponseCodeEntry("21", "No Action Taken",                        ResponseSeverity.WARNING),
        ResponseCodeEntry("22", "Suspected Malfunction",                  ResponseSeverity.ERROR),
        ResponseCodeEntry("25", "Unable to Locate Record",                ResponseSeverity.ERROR),
        ResponseCodeEntry("28", "File is Temporarily Unavailable",        ResponseSeverity.ERROR),
        ResponseCodeEntry("30", "Format Error",                           ResponseSeverity.ERROR),
        ResponseCodeEntry("31", "Bank Not Supported",                     ResponseSeverity.DECLINED),
        ResponseCodeEntry("33", "Expired Card — Pick Up",                 ResponseSeverity.DECLINED),
        ResponseCodeEntry("34", "Suspected Fraud — Pick Up",              ResponseSeverity.DECLINED),
        ResponseCodeEntry("36", "Restricted Card — Pick Up",              ResponseSeverity.DECLINED),
        ResponseCodeEntry("38", "PIN Tries Exceeded",                     ResponseSeverity.DECLINED),
        ResponseCodeEntry("39", "No Credit Account",                      ResponseSeverity.DECLINED),
        ResponseCodeEntry("40", "Function Not Supported",                 ResponseSeverity.DECLINED),
        ResponseCodeEntry("41", "Lost Card — Pick Up",                    ResponseSeverity.DECLINED),
        ResponseCodeEntry("42", "No Universal Account",                   ResponseSeverity.DECLINED),
        ResponseCodeEntry("43", "Stolen Card — Pick Up",                  ResponseSeverity.DECLINED),
        ResponseCodeEntry("44", "No Investment Account",                  ResponseSeverity.DECLINED),
        ResponseCodeEntry("51", "Insufficient Funds",                     ResponseSeverity.DECLINED),
        ResponseCodeEntry("52", "No Cheque Account",                      ResponseSeverity.DECLINED),
        ResponseCodeEntry("53", "No Savings Account",                     ResponseSeverity.DECLINED),
        ResponseCodeEntry("54", "Expired Card",                           ResponseSeverity.DECLINED),
        ResponseCodeEntry("55", "Incorrect PIN",                          ResponseSeverity.DECLINED),
        ResponseCodeEntry("56", "No Card Record",                         ResponseSeverity.DECLINED),
        ResponseCodeEntry("57", "Transaction Not Permitted to Cardholder",ResponseSeverity.DECLINED),
        ResponseCodeEntry("58", "Transaction Not Permitted to Terminal",  ResponseSeverity.DECLINED),
        ResponseCodeEntry("59", "Suspected Fraud",                        ResponseSeverity.DECLINED),
        ResponseCodeEntry("60", "Card Acceptor Contact Acquirer",         ResponseSeverity.WARNING),
        ResponseCodeEntry("61", "Exceeds Withdrawal Amount Limit",        ResponseSeverity.DECLINED),
        ResponseCodeEntry("62", "Restricted Card",                        ResponseSeverity.DECLINED),
        ResponseCodeEntry("63", "Security Violation",                     ResponseSeverity.DECLINED),
        ResponseCodeEntry("64", "Original Amount Incorrect",              ResponseSeverity.DECLINED),
        ResponseCodeEntry("65", "Exceeds Withdrawal Frequency Limit",     ResponseSeverity.DECLINED),
        ResponseCodeEntry("66", "Card Acceptor Call Acquirer Security",   ResponseSeverity.WARNING),
        ResponseCodeEntry("67", "Hard Capture — Pick Up Card",            ResponseSeverity.DECLINED),
        ResponseCodeEntry("68", "Response Received Too Late",             ResponseSeverity.TIMEOUT),
        ResponseCodeEntry("75", "Allowable PIN Tries Exceeded",           ResponseSeverity.DECLINED),
        ResponseCodeEntry("76", "Invalid/Non-existent Account",           ResponseSeverity.DECLINED),
        ResponseCodeEntry("77", "Invalid Data on Card",                   ResponseSeverity.DECLINED),
        ResponseCodeEntry("78", "Blocked — First Used",                   ResponseSeverity.DECLINED),
        ResponseCodeEntry("79", "Lifecycle Error",                        ResponseSeverity.ERROR),
        ResponseCodeEntry("80", "No Financial Impact",                    ResponseSeverity.WARNING),
        ResponseCodeEntry("81", "Cryptographic Error",                    ResponseSeverity.ERROR),
        ResponseCodeEntry("82", "CVV Validation Error",                   ResponseSeverity.DECLINED),
        ResponseCodeEntry("83", "Unable to Verify PIN",                   ResponseSeverity.ERROR),
        ResponseCodeEntry("84", "Invalid Auth Life Cycle",                ResponseSeverity.DECLINED),
        ResponseCodeEntry("85", "No Reason to Decline",                   ResponseSeverity.SUCCESS),
        ResponseCodeEntry("86", "Cannot Verify PIN",                      ResponseSeverity.DECLINED),
        ResponseCodeEntry("88", "Cryptographic Failure",                  ResponseSeverity.ERROR),
        ResponseCodeEntry("89", "Unacceptable PIN — Transaction Declined", ResponseSeverity.DECLINED),
        ResponseCodeEntry("91", "Issuer or Switch Inoperative",           ResponseSeverity.ERROR),
        ResponseCodeEntry("92", "Financial Institution Not Found",        ResponseSeverity.ERROR),
        ResponseCodeEntry("93", "Transaction Cannot Be Completed",        ResponseSeverity.DECLINED),
        ResponseCodeEntry("94", "Duplicate Transaction",                  ResponseSeverity.WARNING),
        ResponseCodeEntry("95", "Reconcile Error",                        ResponseSeverity.ERROR),
        ResponseCodeEntry("96", "System Malfunction",                     ResponseSeverity.ERROR),
        ResponseCodeEntry("97", "Security Breach",                        ResponseSeverity.ERROR),
        ResponseCodeEntry("N3", "Cash Service Not Available",             ResponseSeverity.DECLINED),
        ResponseCodeEntry("N4", "Cash Request Exceeds Issuer Limit",      ResponseSeverity.DECLINED),
        ResponseCodeEntry("N7", "Decline for CVV2 Failure",               ResponseSeverity.DECLINED),
        ResponseCodeEntry("ND", "Decline",                                ResponseSeverity.DECLINED)
    )

    private val RESPONSE_CODE_MAP: Map<String, ResponseCodeEntry> =
        RESPONSE_CODES.associateBy { it.code }

    fun decodeResponseCode(raw: String): DecodeResult {
        val entry = RESPONSE_CODE_MAP[raw.trim()]
            ?: ResponseCodeEntry(raw, "Unknown Response Code ($raw)", ResponseSeverity.WARNING)
        val prefix = when (entry.severity) {
            ResponseSeverity.SUCCESS  -> "✅"
            ResponseSeverity.WARNING  -> "⚠️"
            ResponseSeverity.DECLINED -> "❌"
            ResponseSeverity.TIMEOUT  -> "⏱"
            ResponseSeverity.ERROR    -> "🔴"
        }
        return DecodeResult(
            decodedValue = "$prefix ${entry.meaning}",
            subFields = listOf(
                SubField("Code",    raw,           entry.code),
                SubField("Meaning", entry.meaning,  entry.meaning),
                SubField("Status",  entry.severity.name, entry.severity.name)
            )
        )
    }

    fun responseSeverity(raw: String): ResponseSeverity =
        RESPONSE_CODE_MAP[raw.trim()]?.severity ?: ResponseSeverity.WARNING

    // -----------------------------------------------------------------------
    // DE49 / DE50 / DE51 — Currency Code
    // -----------------------------------------------------------------------

    fun decodeCurrency(raw: String): DecodeResult {
        val currency = CurrencyTable.lookup(raw)
            ?: return DecodeResult("Unknown currency code ($raw)")
        return DecodeResult(
            decodedValue = "${currency.name} (${currency.symbol})",
            subFields = listOf(
                SubField("Code",           raw,              raw),
                SubField("Name",           raw,              currency.name),
                SubField("Symbol",         raw,              currency.symbol),
                SubField("Decimal Places", raw,              "${currency.decimals}")
            )
        )
    }

    // -----------------------------------------------------------------------
    // DE59 — NIBSS Private Reference
    // -----------------------------------------------------------------------

    fun decodeNibssReference(raw: String): DecodeResult {
        val parts = raw.split("-")
        return when {
            parts.size >= 3 -> {
                val terminalId = parts[0]
                val ptspRef    = parts[1]
                val rrnTrace   = parts[2]
                DecodeResult(
                    decodedValue = "Terminal: $terminalId  ·  PTSP: $ptspRef  ·  RRN: $rrnTrace",
                    subFields = listOf(
                        SubField("Terminal ID",    terminalId, terminalId),
                        SubField("PTSP Reference", ptspRef,    ptspRef),
                        SubField("RRN / Trace",    rrnTrace,   rrnTrace)
                    )
                )
            }
            parts.size == 2 -> DecodeResult(
                decodedValue = "Terminal: ${parts[0]}  ·  Reference: ${parts[1]}",
                subFields = listOf(
                    SubField("Terminal ID", parts[0], parts[0]),
                    SubField("Reference",   parts[1], parts[1])
                )
            )
            else -> DecodeResult("NIBSS Reference: $raw")
        }
    }

    // -----------------------------------------------------------------------
    // DE70 — Network Management Information Code
    // -----------------------------------------------------------------------

    fun decodeNetworkManagementCode(raw: String): DecodeResult {
        val label = when (raw) {
            "001" -> "Sign-On"
            "002" -> "Sign-Off"
            "003" -> "Echo Test"
            "101" -> "Cutover Complete"
            "201" -> "Interchange Cutover"
            "301" -> "Reconciliation"
            "401" -> "Switch cutover"
            "501" -> "Log-on"
            "502" -> "Log-off"
            "801" -> "Key Change"
            "802" -> "Key Change ACK"
            else  -> "Unknown ($raw)"
        }
        return DecodeResult(label)
    }

    // -----------------------------------------------------------------------
    // DE123 — NIBSS Terminal Capabilities
    // -----------------------------------------------------------------------

    fun decodeNibssTerminalCapabilities(raw: String): DecodeResult {
        // NIBSS PTSP implementation guide format (15-character string)
        // Positions as defined by NIBSS spec
        val subFields = mutableListOf<SubField>()
        if (raw.length >= 2) subFields += SubField("Terminal Type",       raw.substring(0, 2),  terminalTypeLabel(raw.substring(0, 2)))
        if (raw.length >= 4) subFields += SubField("POS Capability",      raw.substring(2, 4),  raw.substring(2, 4))
        if (raw.length >= 6) subFields += SubField("PIN Entry Capable",   raw.substring(4, 6),  raw.substring(4, 6))
        if (raw.length >= 8) subFields += SubField("Printer Capability",  raw.substring(6, 8),  raw.substring(6, 8))
        if (raw.length >= 9) subFields += SubField("Interface",           raw.substring(8, 9),  raw.substring(8, 9))
        val summary = if (subFields.isNotEmpty()) subFields.joinToString("  ·  ") { it.decodedValue } else raw
        return DecodeResult(
            decodedValue = "Terminal Capabilities — $raw",
            subFields = subFields
        )
    }

    private fun terminalTypeLabel(code: String): String = when (code) {
        "51" -> "POS Terminal"
        "61" -> "ATM"
        "71" -> "Telephone initiated"
        "21" -> "Off-premise unattended"
        "22" -> "Off-premise attended"
        else -> "Terminal type $code"
    }

    // -----------------------------------------------------------------------
    // Shared utility
    // -----------------------------------------------------------------------

    private fun monthName(mm: String): String = when (mm) {
        "01" -> "January"
        "02" -> "February"
        "03" -> "March"
        "04" -> "April"
        "05" -> "May"
        "06" -> "June"
        "07" -> "July"
        "08" -> "August"
        "09" -> "September"
        "10" -> "October"
        "11" -> "November"
        "12" -> "December"
        else -> mm
    }

    // -----------------------------------------------------------------------
    // DE54 — Additional Amounts
    // -----------------------------------------------------------------------

    fun decodeField54(raw: String): DecodeResult {
        if (raw.isEmpty()) {
            return DecodeResult("0.00")
        }
        if (raw.length < 20) {
            return try {
                val amount = raw.toDouble() / 100.0
                DecodeResult("%,.2f".format(amount))
            } catch (e: Throwable) {
                DecodeResult(raw)
            }
        }
        return try {
            val amountVal = raw.substring(9, 20).toDouble()
            val amount = amountVal / 100.0

            val accountType = raw.substring(0, 2)
            val amountType = raw.substring(2, 4)
            val currencyCode = raw.substring(4, 7)
            val sign = raw.substring(7, 8)

            val accLabel = when (accountType) {
                "10" -> "Savings"
                "20" -> "Cheque / Current"
                "30" -> "Credit"
                else -> "Account Type $accountType"
            }

            val amtTypeLabel = when (amountType) {
                "01" -> "Ledger Balance"
                "02" -> "Available Balance"
                else -> "Amount Type $amountType"
            }

            val currency = CurrencyTable.lookup(currencyCode)
            val symbol = currency?.symbol ?: "₦"
            val curName = currency?.name ?: "Naira"

            val formattedAmount = "%,.2f".format(amount)
            val displaySign = if (sign == "C") "Credit" else "Debit"
            val displayAmount = "$symbol$formattedAmount"

            val summary = "$amtTypeLabel: $displayAmount ($accLabel)"

            DecodeResult(
                decodedValue = summary,
                subFields = listOf(
                    SubField("Account Type", accountType, accLabel),
                    SubField("Amount Type",  amountType,  amtTypeLabel),
                    SubField("Currency",     currencyCode, "$curName ($symbol)"),
                    SubField("Sign",         sign,         displaySign),
                    SubField("Amount",       raw.substring(8), displayAmount)
                )
            )
        } catch (e: Throwable) {
            DecodeResult(raw)
        }
    }

    // -----------------------------------------------------------------------
    // DE62 — Custom TLV Field Decoder
    // -----------------------------------------------------------------------

    fun decodeField62(raw: String): DecodeResult {
        val tlvMap = mutableMapOf<String, String>()
        var remaining = raw
        try {
            while (remaining.length >= 5) {
                val tag = remaining.substring(0, 2)
                val lenStr = remaining.substring(2, 5)
                val len = lenStr.toIntOrNull() ?: break
                if (remaining.length < 5 + len) break
                val value = remaining.substring(5, 5 + len)
                tlvMap[tag] = value
                remaining = remaining.substring(5 + len)
            }
        } catch (e: Exception) {
            // lenient parsing — ignore parse errors
        }

        val merchantId = tlvMap["03"] ?: "Not Present"
        val merchantLocation = tlvMap["52"] ?: "Not Present"
        val currencyCode = tlvMap["05"] ?: "Not Present"
        val mcc = tlvMap["08"] ?: "Not Present"

        val summary = "MID: $merchantId  ·  Loc: $merchantLocation"

        val subFields = mutableListOf<SubField>()
        subFields.add(SubField("Merchant ID (Tag 03)", tlvMap["03"] ?: "", merchantId))
        subFields.add(SubField("Merchant Location (Tag 52)", tlvMap["52"] ?: "", merchantLocation))
        
        val currencyLabel = if (currencyCode != "Not Present") {
            val currency = CurrencyTable.lookup(currencyCode)
            if (currency != null) "${currency.name} (${currency.symbol})" else "Code: $currencyCode"
        } else {
            "Not Present"
        }
        subFields.add(SubField("Currency Code (Tag 05)", tlvMap["05"] ?: "", currencyLabel))

        val mccLabel = if (mcc != "Not Present") {
            MccTable.lookup(mcc)
        } else {
            "Not Present"
        }
        subFields.add(SubField("Merchant Category Code (Tag 08)", tlvMap["08"] ?: "", mccLabel))

        // Generic support for any additional custom tags in the TLV block
        for ((tag, value) in tlvMap) {
            if (tag !in setOf("03", "52", "05", "08")) {
                subFields.add(SubField("Tag $tag", value, value))
            }
        }

        return DecodeResult(
            decodedValue = summary,
            subFields = subFields
        )
    }
}
