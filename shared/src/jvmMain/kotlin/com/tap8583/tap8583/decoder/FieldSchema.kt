package com.tap8583.tap8583.decoder

/**
 * Complete ISO 8583 field schema dictionary.
 *
 * Covers:
 *  - All 128 standard ISO 8583:1993 data elements
 *  - NIBSS/Nigerian payment network private fields: DE59, DE60, DE123, DE128
 *
 * Keyed by field number. Use [FieldSchema.get] for lookups; an "UNKNOWN" fallback
 * is returned for any field not in this table so the application never crashes on
 * unexpected field IDs.
 */
object FieldSchema {

    private val schema: Map<Int, FieldDefinition> = buildMap {
        // ── MTI (field 0) ──────────────────────────────────────────────────
        put(0,   def(0,   "Message Type Indicator",              "MTI",          "n",   "FIXED",  4,  "Identifies the ISO 8583 message type", category = FieldCategory.GENERAL))

        // ── Bitmap (field 1) — jPOS does not log the bitmap as a field ─────
        put(1,   def(1,   "Bitmap",                              "BITMAP",       "b",   "FIXED",  8,  "Primary bitmap — not logged by jPOS"))

        // ── Card / Account ─────────────────────────────────────────────────
        put(2,   def(2,   "Primary Account Number",              "PAN",          "n",   "LLVAR",  19, "Cardholder PAN. Masked in UI by default.", sensitive = true,  category = FieldCategory.CARD_DATA))
        put(3,   def(3,   "Processing Code",                     "PROC CODE",    "n",   "FIXED",  6,  "Identifies transaction type and from/to account types (3×2 digits).", category = FieldCategory.GENERAL))
        put(4,   def(4,   "Amount, Transaction",                 "AMOUNT",       "n",   "FIXED",  12, "Transaction amount in minor units of the currency specified in DE49.", category = FieldCategory.AMOUNT))
        put(5,   def(5,   "Amount, Settlement",                  "SETT AMT",     "n",   "FIXED",  12, "Settlement amount in minor units.", category = FieldCategory.AMOUNT))
        put(6,   def(6,   "Amount, Cardholder Billing",          "BILL AMT",     "n",   "FIXED",  12, "Cardholder billing amount.", category = FieldCategory.AMOUNT))
        put(7,   def(7,   "Transmission Date and Time",          "TX DATE/TIME", "n",   "FIXED",  10, "Date and time of transmission (MMDDhhmmss UTC).", category = FieldCategory.DATETIME))
        put(8,   def(8,   "Amount, Cardholder Billing Fee",      "BILL FEE",     "n",   "FIXED",  8,  "Billing fee amount.", category = FieldCategory.AMOUNT))
        put(9,   def(9,   "Conversion Rate, Settlement",         "SETT RATE",    "n",   "FIXED",  8,  "Settlement currency conversion rate.", category = FieldCategory.AMOUNT))
        put(10,  def(10,  "Conversion Rate, Cardholder Billing", "BILL RATE",    "n",   "FIXED",  8,  "Cardholder billing currency conversion rate.", category = FieldCategory.AMOUNT))
        put(11,  def(11,  "Systems Trace Audit Number",          "STAN",         "n",   "FIXED",  6,  "Unique transaction trace number assigned by the initiator.", category = FieldCategory.IDENTIFICATION))
        put(12,  def(12,  "Local Transaction Time",              "LOCAL TIME",   "n",   "FIXED",  6,  "Local time of the transaction (hhmmss).", category = FieldCategory.DATETIME))
        put(13,  def(13,  "Local Transaction Date",              "LOCAL DATE",   "n",   "FIXED",  4,  "Local date of the transaction (MMDD).", category = FieldCategory.DATETIME))
        put(14,  def(14,  "Expiration Date",                     "EXPIRY",       "n",   "FIXED",  4,  "Card expiry date (YYMM). Sensitive.", sensitive = true, category = FieldCategory.CARD_DATA))
        put(15,  def(15,  "Settlement Date",                     "SETT DATE",    "n",   "FIXED",  4,  "Date on which the settlement is performed (MMDD).", category = FieldCategory.DATETIME))
        put(16,  def(16,  "Currency Conversion Date",            "CONV DATE",    "n",   "FIXED",  4,  "Date the conversion rate applies (MMDD).", category = FieldCategory.DATETIME))
        put(17,  def(17,  "Capture Date",                        "CAP DATE",     "n",   "FIXED",  4,  "Date funds are to be captured (MMDD).", category = FieldCategory.DATETIME))
        put(18,  def(18,  "Merchant Type",                       "MCC",          "n",   "FIXED",  4,  "Merchant Category Code per ISO 18245.", category = FieldCategory.GENERAL))
        put(19,  def(19,  "Acquiring Institution Country Code",  "ACQ COUNTRY",  "n",   "FIXED",  3,  "ISO 3166 country code of acquiring institution.", category = FieldCategory.INSTITUTION))
        put(20,  def(20,  "PAN Extended Country Code",           "PAN COUNTRY",  "n",   "FIXED",  3,  "Country code from the card's extended PAN.", category = FieldCategory.CARD_DATA))
        put(21,  def(21,  "Forwarding Institution Country Code", "FWD COUNTRY",  "n",   "FIXED",  3,  "ISO 3166 country code of the forwarding institution.", category = FieldCategory.INSTITUTION))
        put(22,  def(22,  "Point of Service Entry Mode",         "POS ENTRY",    "n",   "FIXED",  3,  "How the PAN/track data was entered and PIN capability (3 digits).", category = FieldCategory.POS_TERMINAL))
        put(23,  def(23,  "Application PAN Sequence Number",     "PAN SEQ",      "n",   "FIXED",  3,  "Distinguishes cards with the same PAN.", category = FieldCategory.EMV))
        put(24,  def(24,  "Network International Identifier",    "NII",          "n",   "FIXED",  3,  "Identifies the network.", category = FieldCategory.NETWORK))
        put(25,  def(25,  "Point of Service Condition Code",     "POS COND",     "n",   "FIXED",  2,  "Condition at the POS terminal at transaction time.", category = FieldCategory.POS_TERMINAL))
        put(26,  def(26,  "Point of Service Capture Code",       "POS CAP",      "n",   "FIXED",  2,  "Indicates the terminal's capture capability.", category = FieldCategory.POS_TERMINAL))
        put(27,  def(27,  "Authorising Identification Response Length", "AUTH ID LEN", "n", "FIXED", 1, "Length of the authorising ID response.", category = FieldCategory.IDENTIFICATION))
        put(28,  def(28,  "Amount, Transaction Fee",             "TX FEE",       "an",  "FIXED",  8,  "Transaction fee amount.", category = FieldCategory.AMOUNT))
        put(29,  def(29,  "Amount, Settlement Fee",              "SETT FEE",     "an",  "FIXED",  8,  "Settlement fee amount.", category = FieldCategory.AMOUNT))
        put(30,  def(30,  "Amount, Transaction Processing Fee",  "PROC FEE",     "an",  "FIXED",  8,  "Processing fee amount.", category = FieldCategory.AMOUNT))
        put(31,  def(31,  "Amount, Settlement Processing Fee",   "SETT PROC FEE","an",  "FIXED",  8,  "Settlement processing fee amount.", category = FieldCategory.AMOUNT))
        put(32,  def(32,  "Acquiring Institution ID Code",       "ACQ ID",       "n",   "LLVAR",  11, "Identifies the acquiring institution (IIN/BIN).", category = FieldCategory.INSTITUTION))
        put(33,  def(33,  "Forwarding Institution ID Code",      "FWD ID",       "n",   "LLVAR",  11, "Identifies the forwarding institution.", category = FieldCategory.INSTITUTION))
        put(34,  def(34,  "Primary Account Number, Extended",    "PAN EXT",      "ans", "LLVAR",  28, "Extended PAN.", sensitive = true, category = FieldCategory.CARD_DATA))
        put(35,  def(35,  "Track 2 Data",                        "TRACK 2",      "z",   "LLVAR",  37, "Magnetic stripe track 2 data (PAN, expiry, service code, discretionary).", sensitive = true, category = FieldCategory.CARD_DATA))
        put(36,  def(36,  "Track 3 Data",                        "TRACK 3",      "z",   "LLLVAR", 104,"Magnetic stripe track 3 data.", sensitive = true, category = FieldCategory.CARD_DATA))
        put(37,  def(37,  "Retrieval Reference Number",          "RRN",          "an",  "FIXED",  12, "Retrieval reference number assigned by the acquirer.", category = FieldCategory.IDENTIFICATION))
        put(38,  def(38,  "Authorization Identification Response","AUTH CODE",   "an",  "FIXED",  6,  "Authorization approval code from the issuer.", category = FieldCategory.IDENTIFICATION))
        put(39,  def(39,  "Response Code",                       "RESP CODE",    "an",  "FIXED",  2,  "ISO 8583 response code indicating the result of the transaction.", category = FieldCategory.RESPONSE))
        put(40,  def(40,  "Service Restriction Code",            "SVC RESTRICT", "n",   "FIXED",  3,  "Identifies the cards service restriction.", category = FieldCategory.GENERAL))
        put(41,  def(41,  "Card Acceptor Terminal ID",           "TID",          "ans", "FIXED",  8,  "Unique identifier for the POS terminal.", category = FieldCategory.IDENTIFICATION))
        put(42,  def(42,  "Card Acceptor ID Code",               "MID",          "ans", "FIXED",  15, "Merchant ID assigned by the acquirer.", category = FieldCategory.IDENTIFICATION))
        put(43,  def(43,  "Card Acceptor Name/Location",         "MERCHANT NAME","ans", "FIXED",  40, "Merchant name and location.", category = FieldCategory.GENERAL))
        put(44,  def(44,  "Additional Response Data",            "ADD RESP",     "an",  "LLVAR",  25, "Additional data from the issuer in response messages.", category = FieldCategory.RESPONSE))
        put(45,  def(45,  "Track 1 Data",                        "TRACK 1",      "ans", "LLVAR",  76, "Magnetic stripe track 1 data.", sensitive = true, category = FieldCategory.CARD_DATA))
        put(46,  def(46,  "Additional Data — ISO",               "ISO DATA",     "ans", "LLLVAR", 999,"Additional data — ISO use.", category = FieldCategory.GENERAL))
        put(47,  def(47,  "Additional Data — National",          "NAT DATA",     "ans", "LLLVAR", 999,"Additional data — national use.", category = FieldCategory.GENERAL))
        put(48,  def(48,  "Additional Data — Private",           "PRIV DATA",    "ans", "LLLVAR", 999,"Additional data — private use.", category = FieldCategory.GENERAL))
        put(49,  def(49,  "Currency Code, Transaction",          "TX CURRENCY",  "n",   "FIXED",  3,  "ISO 4217 numeric currency code of the transaction.", category = FieldCategory.AMOUNT))
        put(50,  def(50,  "Currency Code, Settlement",           "SETT CURRENCY","n",   "FIXED",  3,  "ISO 4217 numeric currency code for settlement.", category = FieldCategory.AMOUNT))
        put(51,  def(51,  "Currency Code, Cardholder Billing",   "BILL CURRENCY","n",   "FIXED",  3,  "ISO 4217 numeric currency code for billing.", category = FieldCategory.AMOUNT))
        put(52,  def(52,  "Personal Identification Number Data", "PIN BLOCK",    "b",   "FIXED",  8,  "Encrypted PIN block (3DES / AES). Always masked.", sensitive = true, category = FieldCategory.SECURITY))
        put(53,  def(53,  "Security Related Control Information","SEC CTRL",     "n",   "FIXED",  16, "Key management and security control information.", category = FieldCategory.SECURITY))
        put(54,  def(54,  "Additional Amounts",                  "ADD AMOUNTS",  "an",  "LLLVAR", 120,"Additional balance/amount information.", category = FieldCategory.AMOUNT))
        put(55,  def(55,  "ICC Data (EMV)",                      "EMV / ICC",    "b",   "LLLVAR", 255,"BER-TLV encoded EMV chip transaction data.", category = FieldCategory.EMV))
        put(56,  def(56,  "Reserved ISO",                        "RSVD 56",      "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.EMV))
        put(57,  def(57,  "Reserved National",                   "RSVD 57",      "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(58,  def(58,  "Reserved National",                   "RSVD 58",      "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(59,  def(59,  "Reserved Private (NIBSS Reference)",  "NIBSS REF",    "ans", "LLLVAR", 999,"NIBSS-private composite field: Terminal ID · PTSP ref · RRN/trace.", category = FieldCategory.PRIVATE_NIBSS))
        put(60,  def(60,  "Reserved Private (NIBSS)",            "NIBSS PRIV 60","ans", "LLLVAR", 999,"NIBSS-private field.", category = FieldCategory.PRIVATE_NIBSS))
        put(61,  def(61,  "Reserved Private",                    "RSVD 61",      "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(62,  def(62,  "Reserved Private",                    "RSVD 62",      "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(63,  def(63,  "Reserved Private",                    "RSVD 63",      "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(64,  def(64,  "Message Authentication Code (MAC)",   "MAC",          "b",   "FIXED",  8,  "3DES message authentication code over the primary bitmap fields.", category = FieldCategory.SECURITY))
        put(65,  def(65,  "Bitmap, Extended",                    "BITMAP EXT",   "b",   "FIXED",  8,  "Secondary/tertiary bitmap extension.", category = FieldCategory.GENERAL))
        put(66,  def(66,  "Settlement Code",                     "SETT CODE",    "n",   "FIXED",  1,  "Settlement code.", category = FieldCategory.GENERAL))
        put(67,  def(67,  "Extended Payment Code",               "EXT PAY",      "n",   "FIXED",  2,  "Extended payment code.", category = FieldCategory.GENERAL))
        put(68,  def(68,  "Receiving Institution Country Code",  "RCV COUNTRY",  "n",   "FIXED",  3,  "Country code of the receiving institution.", category = FieldCategory.INSTITUTION))
        put(69,  def(69,  "Settlement Institution Country Code", "SETT COUNTRY", "n",   "FIXED",  3,  "Country code of the settlement institution.", category = FieldCategory.INSTITUTION))
        put(70,  def(70,  "Network Management Information Code", "NET MGMT",     "n",   "FIXED",  3,  "Identifies the type of network management action (e.g. sign-on/off, echo).", category = FieldCategory.NETWORK))
        put(71,  def(71,  "Message Number",                      "MSG NUM",      "n",   "FIXED",  4,  "Sequence number of the message.", category = FieldCategory.NETWORK))
        put(72,  def(72,  "Data Record",                         "DATA REC",     "ans", "LLLVAR", 999,"Free-format data record.", category = FieldCategory.GENERAL))
        put(73,  def(73,  "Date, Action",                        "ACTION DATE",  "n",   "FIXED",  6,  "Date an action is to take effect (YYMMDD).", category = FieldCategory.NETWORK))
        put(74,  def(74,  "Credits, Number",                     "CREDITS NUM",  "n",   "FIXED",  10, "Number of credit transactions.", category = FieldCategory.GENERAL))
        put(75,  def(75,  "Credits Reversal Number",             "CR REV NUM",   "n",   "FIXED",  10, "Number of credit reversals.", category = FieldCategory.GENERAL))
        put(76,  def(76,  "Debits, Number",                      "DEBITS NUM",   "n",   "FIXED",  10, "Number of debit transactions.", category = FieldCategory.GENERAL))
        put(77,  def(77,  "Debits Reversal Number",              "DR REV NUM",   "n",   "FIXED",  10, "Number of debit reversals.", category = FieldCategory.GENERAL))
        put(78,  def(78,  "Transfer Number",                     "XFER NUM",     "n",   "FIXED",  10, "Number of transfer transactions.", category = FieldCategory.GENERAL))
        put(79,  def(79,  "Transfer Reversal Number",            "XFER REV NUM", "n",   "FIXED",  10, "Number of transfer reversals.", category = FieldCategory.GENERAL))
        put(80,  def(80,  "Inquiries Number",                    "INQ NUM",      "n",   "FIXED",  10, "Number of inquiry transactions.", category = FieldCategory.GENERAL))
        put(81,  def(81,  "Authorizations Number",               "AUTH NUM",     "n",   "FIXED",  10, "Number of authorization transactions.", category = FieldCategory.GENERAL))
        put(82,  def(82,  "Credits, Processing Fee Amount",      "CR PROC FEE",  "n",   "FIXED",  12, "Total credits processing fee.", category = FieldCategory.AMOUNT))
        put(83,  def(83,  "Credits Transaction Fee Amount",      "CR TX FEE",    "n",   "FIXED",  12, "Total credits transaction fee.", category = FieldCategory.AMOUNT))
        put(84,  def(84,  "Debits, Processing Fee Amount",       "DR PROC FEE",  "n",   "FIXED",  12, "Total debits processing fee.", category = FieldCategory.AMOUNT))
        put(85,  def(85,  "Debits Transaction Fee Amount",       "DR TX FEE",    "n",   "FIXED",  12, "Total debits transaction fee.", category = FieldCategory.AMOUNT))
        put(86,  def(86,  "Credits, Amount",                     "CR AMT",       "n",   "FIXED",  16, "Total credits amount.", category = FieldCategory.AMOUNT))
        put(87,  def(87,  "Credits Reversal Amount",             "CR REV AMT",   "n",   "FIXED",  16, "Total credits reversal amount.", category = FieldCategory.AMOUNT))
        put(88,  def(88,  "Debits, Amount",                      "DR AMT",       "n",   "FIXED",  16, "Total debits amount.", category = FieldCategory.AMOUNT))
        put(89,  def(89,  "Debits Reversal Amount",              "DR REV AMT",   "n",   "FIXED",  16, "Total debits reversal amount.", category = FieldCategory.AMOUNT))
        put(90,  def(90,  "Original Data Elements",              "ORIG DATA",    "n",   "FIXED",  42, "Original transaction's MTI, STAN, date/time, acquiring and forwarding institution codes.", category = FieldCategory.IDENTIFICATION))
        put(91,  def(91,  "File Update Code",                    "FILE UPD",     "an",  "FIXED",  1,  "File update code.", category = FieldCategory.GENERAL))
        put(92,  def(92,  "File Security Code",                  "FILE SEC",     "an",  "FIXED",  2,  "File security code.", category = FieldCategory.SECURITY))
        put(93,  def(93,  "Response Indicator",                  "RESP IND",     "an",  "FIXED",  5,  "Response indicator.", category = FieldCategory.RESPONSE))
        put(94,  def(94,  "Service Indicator",                   "SVC IND",      "an",  "FIXED",  7,  "Service indicator.", category = FieldCategory.GENERAL))
        put(95,  def(95,  "Replacement Amounts",                 "REPL AMTS",    "an",  "FIXED",  42, "Replacement amounts for partial reversals.", category = FieldCategory.AMOUNT))
        put(96,  def(96,  "Message Security Code",               "MSG SEC CODE", "b",   "FIXED",  8,  "Message security / MAC field.", category = FieldCategory.SECURITY))
        put(97,  def(97,  "Amount, Net Settlement",              "NET SETT AMT", "an",  "FIXED",  17, "Net settlement amount (signed).", category = FieldCategory.AMOUNT))
        put(98,  def(98,  "Payee",                               "PAYEE",        "ans", "FIXED",  25, "Payee name.", category = FieldCategory.GENERAL))
        put(99,  def(99,  "Settlement Institution ID Code",      "SETT INST ID", "n",   "LLVAR",  11, "Settlement institution identifier.", category = FieldCategory.INSTITUTION))
        put(100, def(100, "Receiving Institution ID Code",       "RCV INST ID",  "n",   "LLVAR",  11, "Receiving institution identifier.", category = FieldCategory.INSTITUTION))
        put(101, def(101, "File Name",                           "FILE NAME",    "ans", "LLVAR",  17, "Name of file being transferred.", category = FieldCategory.GENERAL))
        put(102, def(102, "Account Identification 1",            "ACCT 1",       "ans", "LLVAR",  28, "From-account identifier.", category = FieldCategory.GENERAL))
        put(103, def(103, "Account Identification 2",            "ACCT 2",       "ans", "LLVAR",  28, "To-account identifier.", category = FieldCategory.GENERAL))
        put(104, def(104, "Transaction Description",             "TX DESC",      "ans", "LLLVAR", 100,"Transaction description.", category = FieldCategory.GENERAL))
        put(105, def(105, "Reserved ISO",                        "RSVD 105",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(106, def(106, "Reserved ISO",                        "RSVD 106",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(107, def(107, "Reserved ISO",                        "RSVD 107",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(108, def(108, "Reserved ISO",                        "RSVD 108",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(109, def(109, "Reserved ISO",                        "RSVD 109",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(110, def(110, "Reserved ISO",                        "RSVD 110",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(111, def(111, "Reserved ISO",                        "RSVD 111",     "ans", "LLLVAR", 999,"Reserved for ISO use.", category = FieldCategory.GENERAL))
        put(112, def(112, "Reserved National",                   "RSVD 112",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(113, def(113, "Reserved National",                   "RSVD 113",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(114, def(114, "Reserved National",                   "RSVD 114",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(115, def(115, "Reserved National",                   "RSVD 115",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(116, def(116, "Reserved National",                   "RSVD 116",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(117, def(117, "Reserved National",                   "RSVD 117",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(118, def(118, "Reserved National",                   "RSVD 118",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(119, def(119, "Reserved National",                   "RSVD 119",     "ans", "LLLVAR", 999,"Reserved for national use.", category = FieldCategory.GENERAL))
        put(120, def(120, "Reserved Private",                    "RSVD 120",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(121, def(121, "Reserved Private",                    "RSVD 121",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(122, def(122, "Reserved Private",                    "RSVD 122",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(123, def(123, "NIBSS Terminal Capabilities",         "NIBSS CAPS",   "ans", "LLLVAR", 999,"NIBSS PTSP terminal capability flags string.", category = FieldCategory.PRIVATE_NIBSS))
        put(124, def(124, "Reserved Private",                    "RSVD 124",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(125, def(125, "Reserved Private",                    "RSVD 125",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(126, def(126, "Reserved Private",                    "RSVD 126",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(127, def(127, "Reserved Private",                    "RSVD 127",     "ans", "LLLVAR", 999,"Reserved for private use.", category = FieldCategory.GENERAL))
        put(128, def(128, "Message Authentication Code (NIBSS MAC)", "MAC 128",  "b",   "FIXED",  32, "NIBSS 3DES MAC over the full ISO 8583 message (tertiary bitmap MAC). 32-byte (256-bit) value logged as hex.", category = FieldCategory.SECURITY))
    }

    // -----------------------------------------------------------------------
    // Lookup API
    // -----------------------------------------------------------------------

    /** Returns the [FieldDefinition] for the given field number, or a generic fallback. */
    fun get(fieldNumber: Int): FieldDefinition =
        schema[fieldNumber] ?: unknown(fieldNumber)

    /** Returns all defined fields in order. */
    fun all(): List<FieldDefinition> =
        schema.values.sortedBy { it.number }

    // -----------------------------------------------------------------------
    // Factory helpers
    // -----------------------------------------------------------------------

    private fun def(
        number: Int,
        name: String,
        shortName: String,
        dataType: String,
        isoType: String,
        maxLength: Int,
        description: String,
        sensitive: Boolean = false,
        category: FieldCategory = FieldCategory.GENERAL
    ) = FieldDefinition(number, name, shortName, dataType, isoType, maxLength, description, sensitive, category)

    private fun unknown(number: Int) = FieldDefinition(
        number      = number,
        name        = "Unknown Field $number",
        shortName   = "DE $number",
        dataType    = "ans",
        isoType     = "LLLVAR",
        maxLength   = 999,
        description = "Undefined field — not in current schema.",
        sensitive   = false,
        category    = FieldCategory.GENERAL
    )
}
