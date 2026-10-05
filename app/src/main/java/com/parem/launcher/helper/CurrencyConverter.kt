package com.parem.launcher.helper

import java.util.Locale

/**
 * Currency conversion behind the drawer omnibox ("10 eur in usd"), shaped like
 * UnitConverter: a cheap shape check (looksLikeCurrency) and a convert that
 * returns null on any failure. Rates are ECB euro reference rates; the
 * Android-side fetch and cache live in CurrencyRates.
 */
object CurrencyConverter {

    /** ECB reference rates: units of each currency per 1 EUR, as of [date] (yyyy-MM-dd). */
    data class Rates(val date: String, val perEur: Map<String, Double>) {
        fun serialize(): String =
            date + "|" + perEur.entries.joinToString(";") { "${it.key}=${it.value}" }

        companion object {
            fun deserialize(s: String?): Rates? {
                if (s.isNullOrEmpty()) return null
                val date = s.substringBefore('|', "")
                if (!DATE_REGEX.matches(date)) return null
                val perEur = s.substringAfter('|').split(';').mapNotNull { entry ->
                    val code = entry.substringBefore('=', "")
                    val rate = entry.substringAfter('=').toDoubleOrNull()
                    if (code.length == 3 && rate != null && rate > 0) code to rate else null
                }.toMap()
                return if (perEur.isEmpty()) null else Rates(date, perEur)
            }
        }
    }

    data class Result(val value: Double, val code: String, val date: String) {
        // Two decimals; currencies whose minor unit is unused in practice get none.
        fun format(): String {
            val decimals = if (code in NO_DECIMALS) 0 else 2
            return String.format(Locale.ROOT, "%.${decimals}f", value) + " " + code
        }
    }

    // The codes the ECB publishes, plus EUR. Static so a query is recognised
    // before any rates are cached (that is what triggers the first download).
    val KNOWN_CODES: Set<String> = setOf(
        "EUR", "USD", "JPY", "CZK", "DKK", "GBP", "HUF", "PLN", "RON", "SEK",
        "CHF", "ISK", "NOK", "TRY", "AUD", "BRL", "CAD", "CNY", "HKD", "IDR",
        "ILS", "INR", "KRW", "MXN", "MYR", "NZD", "PHP", "SGD", "THB", "ZAR",
    )

    private val NO_DECIMALS = setOf("JPY", "HUF", "KRW", "ISK", "IDR")

    // amount, source code, optional "to"/"in", target code — UnitConverter's shape
    // narrowed to three-letter codes.
    private val CURRENCY_REGEX = Regex(
        "^(\\d+(?:[.,]\\d+)?)\\s*([a-zA-Z]{3})\\s+(?:(?:to|in)\\s+)?([a-zA-Z]{3})$",
        RegexOption.IGNORE_CASE
    )

    private val DATE_REGEX = Regex("\\d{4}-\\d{2}-\\d{2}")
    private val TIME_ATTR = Regex("time=['\"](\\d{4}-\\d{2}-\\d{2})['\"]")
    private val RATE_ATTRS = Regex("currency=['\"]([A-Z]{3})['\"]\\s+rate=['\"]([0-9.]+)['\"]")

    fun looksLikeCurrency(input: String): Boolean {
        val match = CURRENCY_REGEX.matchEntire(input.trim()) ?: return false
        return match.groupValues[2].uppercase() in KNOWN_CODES &&
            match.groupValues[3].uppercase() in KNOWN_CODES
    }

    fun convert(input: String, rates: Rates): Result? {
        if (!looksLikeCurrency(input)) return null
        val match = CURRENCY_REGEX.matchEntire(input.trim()) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = match.groupValues[2].uppercase()
        val to = match.groupValues[3].uppercase()
        val fromRate = rateOf(from, rates) ?: return null
        val toRate = rateOf(to, rates) ?: return null
        return Result(amount / fromRate * toRate, to, rates.date)
    }

    private fun rateOf(code: String, rates: Rates): Double? =
        if (code == "EUR") 1.0 else rates.perEur[code]

    /**
     * Parses eurofxref-daily.xml with regexes (XmlPullParser is stubbed on the
     * JVM). Fails closed: null unless both a date and at least one rate parse.
     */
    fun parseEcbXml(xml: String): Rates? {
        val date = TIME_ATTR.find(xml)?.groupValues?.get(1) ?: return null
        val perEur = RATE_ATTRS.findAll(xml).mapNotNull { m ->
            val rate = m.groupValues[2].toDoubleOrNull()
            if (rate != null && rate > 0) m.groupValues[1] to rate else null
        }.toMap()
        return if (perEur.isEmpty()) null else Rates(date, perEur)
    }
}
