package com.parem.launcher.helper

/**
 * The omnibox can be in exactly one mode at a time. [None] is ordinary app
 * search; the others each drive the tip line and the submit action.
 */
sealed interface OmniboxMode {
    object None : OmniboxMode
    data class Calc(val result: String) : OmniboxMode
    data class Conversion(val result: String) : OmniboxMode
    data class Currency(val result: String, val date: String) : OmniboxMode
    // A currency query typed before any rates are cached.
    object CurrencyNoRates : OmniboxMode
    data class Dial(val number: String) : OmniboxMode
    object WebSearch : OmniboxMode
    data class Contact(val name: String, val number: String) : OmniboxMode
}

/**
 * Decides which omnibox mode a drawer query puts the search field in. Pure, no
 * Android deps. Precedence: calc → conversion → currency → dial → web →
 * contact → none.
 * The first mode that both recognises *and* resolves the query wins, so a
 * query that looks like an expression but fails to evaluate falls through.
 */
object OmniboxResolver {

    // Digits with optional leading + and spaces, at least 4 digits total.
    // Hyphenated numbers lose to the calculator (they parse as subtraction).
    private val DIAL_REGEX = Regex("^\\+?[0-9][0-9 ]{2,}[0-9]$")

    fun resolve(
        query: String,
        contacts: List<ContactMatcher.Contact>,
        rates: CurrencyConverter.Rates? = null,
    ): OmniboxMode {
        val trimmed = query.trim()

        if (ExpressionEvaluator.looksLikeExpression(trimmed)) {
            ExpressionEvaluator.evaluate(trimmed)?.let { value ->
                return OmniboxMode.Calc(ExpressionEvaluator.format(value))
            }
        }
        // Letters-only unit tokens keep this disjoint from both the calculator
        // (digits/operators only) and the dial matcher below (digits/spaces
        // only), so there's no ordering conflict between the three.
        if (UnitConverter.looksLikeConversion(trimmed)) {
            UnitConverter.convert(trimmed)?.let { result ->
                return OmniboxMode.Conversion(result.format())
            }
        }
        // After units so a unit alias always wins; ECB codes are letters too,
        // so this is just as disjoint from calc and dial.
        if (CurrencyConverter.looksLikeCurrency(trimmed)) {
            if (rates == null) return OmniboxMode.CurrencyNoRates
            CurrencyConverter.convert(trimmed, rates)?.let { result ->
                return OmniboxMode.Currency(result.format(), result.date)
            }
        }
        if (DIAL_REGEX.matches(trimmed) && trimmed.count { it.isDigit() } >= 4) {
            return OmniboxMode.Dial(trimmed)
        }
        // Checked on the raw query: the leading space is the trigger.
        if (query.startsWith(" ") && trimmed.isNotEmpty()) {
            return OmniboxMode.WebSearch
        }
        // Contacts rank below every other mode and below the app list (which the
        // filter still populates): the matching contact only fills the tip line.
        if (contacts.isNotEmpty() && ContactMatcher.looksLikeContactQuery(trimmed)) {
            ContactMatcher.match(trimmed, contacts).firstOrNull()?.let { top ->
                return OmniboxMode.Contact(top.name, top.number)
            }
        }
        return OmniboxMode.None
    }
}
