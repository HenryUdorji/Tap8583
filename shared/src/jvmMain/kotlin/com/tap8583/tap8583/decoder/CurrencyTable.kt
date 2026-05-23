package com.tap8583.tap8583.decoder

/**
 * ISO 4217 currency look-up table.
 *
 * Contains the most common currencies used in payment processing, with
 * emphasis on Nigerian Naira (NGN/566) as the primary NIBSS currency.
 *
 * Reference: ISO 4217:2015
 */
object CurrencyTable {

    data class CurrencyInfo(
        val code: String,        // ISO 4217 numeric code (as string, e.g. "566")
        val alpha: String,       // ISO 4217 alphabetic code (e.g. "NGN")
        val name: String,        // Full currency name
        val symbol: String,      // Currency symbol
        val decimals: Int        // Number of decimal places (minor unit exponent)
    )

    private val currencies: Map<String, CurrencyInfo> = buildMap {
        fun add(code: String, alpha: String, name: String, symbol: String, decimals: Int = 2) {
            put(code, CurrencyInfo(code, alpha, name, symbol, decimals))
            put(alpha, CurrencyInfo(code, alpha, name, symbol, decimals)) // also keyed by alpha
        }

        // ── African currencies ─────────────────────────────────────────────
        add("566", "NGN", "Nigerian Naira",          "₦")
        add("008", "ALL", "Albanian Lek",             "L")
        add("012", "DZD", "Algerian Dinar",           "DA")
        add("024", "AOA", "Angolan Kwanza",            "Kz")
        add("204", "XOF", "West African CFA Franc",   "CFA", 0)
        add("950", "XAF", "Central African CFA Franc","FCFA", 0)
        add("188", "CRC", "Costa Rican Colón",        "₡")
        add("288", "GHS", "Ghanaian Cedi",            "GH₵")
        add("404", "KES", "Kenyan Shilling",          "KSh")
        add("504", "MAD", "Moroccan Dirham",          "MAD")
        add("516", "NAD", "Namibian Dollar",          "N$")
        add("654", "SHP", "Saint Helena Pound",       "£")
        add("694", "SLL", "Sierra Leonean Leone",     "Le")
        add("706", "SOS", "Somali Shilling",          "Sh")
        add("710", "ZAR", "South African Rand",       "R")
        add("728", "SSP", "South Sudanese Pound",     "£")
        add("736", "SDG", "Sudanese Pound",           "LS")
        add("800", "UGX", "Ugandan Shilling",         "USh", 0)
        add("834", "TZS", "Tanzanian Shilling",       "TSh", 0)
        add("894", "ZMW", "Zambian Kwacha",           "ZK")

        // ── Major global currencies ────────────────────────────────────────
        add("840", "USD", "United States Dollar",     "$")
        add("978", "EUR", "Euro",                     "€")
        add("826", "GBP", "Pound Sterling",           "£")
        add("392", "JPY", "Japanese Yen",             "¥",  0)
        add("756", "CHF", "Swiss Franc",              "Fr")
        add("036", "AUD", "Australian Dollar",        "A$")
        add("124", "CAD", "Canadian Dollar",          "C$")
        add("208", "DKK", "Danish Krone",             "kr")
        add("578", "NOK", "Norwegian Krone",          "kr")
        add("752", "SEK", "Swedish Krona",            "kr")
        add("554", "NZD", "New Zealand Dollar",       "NZ$")
        add("344", "HKD", "Hong Kong Dollar",         "HK$")
        add("356", "INR", "Indian Rupee",             "₹")
        add("156", "CNY", "Chinese Yuan",             "¥")
        add("702", "SGD", "Singapore Dollar",         "S$")
        add("458", "MYR", "Malaysian Ringgit",        "RM")
        add("586", "PKR", "Pakistani Rupee",          "₨")
        add("050", "BDT", "Bangladeshi Taka",         "৳")
        add("764", "THB", "Thai Baht",                "฿")
        add("360", "IDR", "Indonesian Rupiah",        "Rp", 0)
        add("643", "RUB", "Russian Ruble",            "₽")
        add("960", "XDR", "Special Drawing Rights",  "XDR")

        // ── Middle East ─────────────────────────────────────────────────────
        add("784", "AED", "UAE Dirham",               "د.إ")
        add("682", "SAR", "Saudi Riyal",              "﷼")
        add("414", "KWD", "Kuwaiti Dinar",            "KD",  3)
        add("048", "BHD", "Bahraini Dinar",           "BD",  3)
        add("512", "OMR", "Omani Rial",               "OMR", 3)
        add("634", "QAR", "Qatari Riyal",             "QR")
        add("400", "JOD", "Jordanian Dinar",          "JD",  3)
    }

    /** Returns [CurrencyInfo] for the given ISO 4217 numeric or alpha code, or null. */
    fun lookup(code: String): CurrencyInfo? = currencies[code.trim()]
}
