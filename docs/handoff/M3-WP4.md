---
wp: M3-WP4
pr: 0
commit: 81061b4
branch: feat/omnibox-currency
date: 2026-10-03
---
"10 eur in usd" in the drawer shows the converted amount with the date of the ECB rates it used; `helper/CurrencyConverter` (parse, convert, ECB XML) is Android-free and `helper/CurrencyRates` downloads the fixed `eurofxref-daily.xml` once per 24 h with a 10 min backoff, only on the first currency query of a drawer session.
Contract: prefs `CURRENCY_RATES`, `CURRENCY_LAST_SUCCESS_MS`, `CURRENCY_LAST_ATTEMPT_MS` are export-excluded and the Long ones registered; `OmniboxMode.Currency(result, date)` and `CurrencyNoRates` (tip says downloading or unavailable offline); unit aliases win over currency codes; no settings toggle — typing the query is the opt-in.
Not verified: the ECB XML attribute names came from memory, not a captured fixture; the Play Data safety impact of a fixed anonymous GET is Patric's call; nothing was run against the live URL from this lane.
Easy to get wrong: the privacy policy (M2-WP3, still open) must list `www.ecb.europa.eu` as a destination or the app and the policy disagree; a parse failure keeps the old cache rather than clearing it.
