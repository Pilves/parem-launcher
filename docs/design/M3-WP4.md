# M3-WP4 — Omnibox currency conversion (design proposal)

Status: proposal, awaiting Patric's sign-off. Branch base: `origin/chore/agent-harness`. No code changed, no gradle run.
Depends on M3-WP2 (`helper/OmniboxResolver.kt` does not exist yet; this doc assumes its planned shape).

## Problem

"10 eur in usd" already matches `UnitConverter.looksLikeConversion` (letters-only unit tokens), then `convert` returns
null because `eur`/`usd` are not units, so the query falls through to app search. The app has no exchange rates. Rates
change daily, so they need a network source with no API key, a cache, and honest behaviour when offline or stale.

## Options

**A. ECB daily XML, fetched directly.** `https://www.ecb.europa.eu/stats/eurofxref/eurofxref-daily.xml` is about 2 KB
and lists 29 currencies against EUR. It is updated around 16:00 CET on working days, but not on TARGET closing days
([ECB](https://www.ecb.europa.eu/stats/policy_and_exchange_rates/euro_reference_exchange_rates/html/index.en.html)).
It comes from the first-party source, needs no key and no intermediary, and one file holds every pair. The costs: about
30 currencies only, XML instead of JSON, and the ECB calls the rates "for information purposes only".

**B. Frankfurter** (`api.frankfurter.dev`). It needs no key and returns JSON. Its ECB provider covers 47 currencies and
it has more providers beyond that ([frankfurter.dev](https://frankfurter.dev/), [ECB provider](https://frankfurter.dev/providers/ecb/)).
The costs: a volunteer-run third party sits between us and the ECB, the API is already on its v2 (so URLs may change),
and the user's IP reaches one more party.

**C. Bundled static rates.** These work offline, but they are wrong within weeks and need a release to refresh. Rejected.

**Fetch trigger.** (1) A periodic WorkManager job: the network is used even by users who never convert. (2) Lazy: the first
currency-shaped query in a drawer session starts at most one fetch, and only if the cache is 24h old or more. Rejected:
fetching on every keystroke.

## Recommendation

Use **A with lazy trigger (2)**. Download the whole ECB file and compute cross rates on the device. The request is the
same fixed URL for every user, so it sends nothing about which currencies the user converts. ECB's 29 currencies plus EUR
cover the realistic Estonian/EU cases. If more currencies are wanted later, B can replace the fetcher without touching
the parser.

**Offline/stale behaviour.** Every result shows the date of its rates, like `≈ 10.85 USD · ECB 2026-10-02`. Because the
date is always shown, stale rates are always marked. Weekends and holidays make a 1–4 day gap normal, so a "fresh" label
would mislead. If there is no cache yet, the tip line says the rates are downloading (or unavailable offline) and copies nothing. A failed fetch
backs off for 10 minutes, the same as `WeatherManager`, and the drawer stays quiet otherwise.

**Opt-in model: typing the query is the opt-in (option a, no settings toggle).** Weather has a toggle
(`WeatherManager.isEnabled`) because it fetches on every home view whether or not the user asked for it. Currency only
fetches after the user types a currency-shaped query, which is an explicit request for a currency answer, and the request
carries no user data. A toggle would add a pref key, a settings row and a disabled state to the resolver to guard a
request the user just asked for. What the user does need is to see that the launcher is using the network, so the
no-rates tip says so: `Downloading ECB rates…` while the fetch runs, `ECB rates unavailable offline` after it fails.
If the device pass or a user asks for a kill switch, a toggle can be added later without changing the resolver contract.

**Privacy note** (for the changelog, the Play listing and the M2-WP3 privacy policy). Typing a currency conversion may
make one daily anonymous download of public ECB rates from `www.ecb.europa.eu`. Nothing the user types leaves the device.
The privacy policy lists every outbound destination (Open-Meteo today), so it must name this one too, or the policy and
the app's behaviour disagree, which is a Play policy risk.

## Exact scope

- **New `helper/CurrencyConverter.kt`** (Android-free object):
  - `KNOWN_CODES` is a static set of the ECB codes plus EUR. It lets the drawer recognise a query before any cache exists.
  - `looksLikeCurrency(input)` uses a regex shaped like `UnitConverter`: amount, 3-letter code, optional `to`/`in`, 3-letter code. Both codes must be in `KNOWN_CODES`.
  - `parseEcbXml(xml): Rates?` is a regex over `time='…'` and `currency='…' rate='…'`. It does not use XmlPullParser, which is stubbed on the JVM.
  - `convert(input, rates): Result?` computes `amount / rate[from] * rate[to]`, with `rate[EUR] = 1`.
  - `Rates(date: String, perEur: Map<String, Double>)` has `serialize()`/`deserialize()` to a compact `USD=1.08;JPY=…` string.
- **New `helper/CurrencyRates.kt`** (Android side, shaped like `WeatherManager`):
  - `cached(context): Rates?`
  - `suspend fetchIfDue(context): Rates?` runs on `Dispatchers.IO` with `HttpURLConnection` and 10s timeouts.
  - Prefs keys:
    - `CURRENCY_RATES` (String)
    - `CURRENCY_LAST_SUCCESS_MS` (Long)
    - `CURRENCY_LAST_ATTEMPT_MS` (Long)
- **`helper/OmniboxResolver.kt`**:
  - Takes `rates: CurrencyConverter.Rates?` as input. The resolver stays pure, and the fragment passes `CurrencyRates.cached()`.
  - The currency step goes right after unit conversion. It is disjoint from calc and dial because it needs letters.
  - The step returns a new `OmniboxMode.Currency(result, date)`, or `OmniboxMode.CurrencyNoRates`.
  - `UnitConverter` is untouched. Unit lookup runs first, and no unit alias is an ECB code (`cup` is a unit, and the ECB does not publish CUP).
- **`ui/AppDrawerFragment.kt`**:
  - Shows the tip text for the two new modes.
  - Copies `Currency.result` on tap and on submit, like `Conversion`. `CurrencyNoRates` copies nothing.
  - For `CurrencyNoRates` it picks the tip from its own fetch state (the resolver stays pure): `currency_downloading`
    while a fetch is in flight, `currency_unavailable` once it has failed or is in backoff.
  - On the first `Currency`/`CurrencyNoRates` mode in a session, it runs `viewLifecycleOwner.lifecycleScope.launch { CurrencyRates.fetchIfDue(ctx) }`. When that finishes, it re-resolves the current query, guarded on `_binding != null`.
- **`data/Prefs.kt`**: add the three keys to `exportExcludeKeys`, because they are device cache. Also add both Longs to `LONG_PREF_KEYS`, as the weather keys do.
- **`res/values/strings.xml`**, both `translatable="false"`:
  - `currency_hint` = `≈ %1$s · ECB %2$s`
  - `currency_downloading` = `Downloading ECB rates…`
  - `currency_unavailable` = `ECB rates unavailable offline`
- **Manifest**: no change. `INTERNET` is already declared.
- **`CHANGELOG.md`**: add one Unreleased line that includes the privacy sentence.
- **Privacy policy (M2-WP3) / Owner task**: the policy text is Patric's (M2-WP3 is blocked on it). Update the M2-WP3
  roadmap row and the Owner task list to say the policy must list `www.ecb.europa.eu` as a destination, triggered only by
  typing a currency query, carrying no user data. If M2-WP3 has already shipped when this lands, the PR for this WP is
  not mergeable until the hosted policy text names ECB.

## Done criteria

- `CurrencyConverterTest` is green, and the existing `UnitConverterTest` and `OmniboxResolverTest` stay green.
- With the device in airplane mode and rates cached, "10 eur in usd" shows the result with its date. With no cache, it shows `currency_downloading`, then `currency_unavailable` when the fetch fails.
- The privacy policy text (M2-WP3) / Owner task is updated to list `www.ecb.europa.eu` as a destination, triggered only by typing a currency query, carrying no user data.
- Typing a 10-character query produces no more than one request in a network log. A second drawer session within 24h makes no request.
- `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` exit 0.

## Risks & traps

- **Trap #5 (stamped caches)**: does not apply. The rates are not derived from packages, so the cache is time-keyed.
- **The M3-WP3 guard**: new `getLong` keys must be registered, or that test fails.
- **Fragment async rule**: the fetch completion re-touches views, so it must check `_binding`. Using `viewLifecycleOwner` scope cancels the fetch on close. That is acceptable, because the next session retries.
- **Precedence regressions**: "5 in to cm" and "100 f c" must still resolve as units. Cover them in the resolver tests.
- **Number format**: reuse `ExpressionEvaluator.format`. Showing 6 decimals for currency is noise, so currency should be formatted to 2 decimals (JPY/HUF/KRW/ISK/IDR to 0). This adds a small table; it is the one non-minimal bit.
- **ECB format change**: the regex parse fails closed (returns null, old cache kept).

## Test plan

**JVM** (`app/src/test/.../CurrencyConverterTest.kt`):
- Parses: `10 eur in usd`, `10eur usd`, `1,5 GBP to jpy`, mixed case.
- Doesn't parse: `10 abc in usd`, `10 km in usd`, `eur in usd`, `10 eur`, app names.
- Cross-rate math from a fixture XML, EUR as both source and target, `parseEcbXml` on a captured real file, serialize round-trip, and garbage XML returning null.

**Resolver tests**: currency comes after units, `CurrencyNoRates` appears when rates are null, and unit queries are unchanged.

**Device**: the done criteria above, plus a fresh install offline (tip goes `Downloading ECB rates…` → `ECB rates unavailable offline`), then online, and the tip updating to a result without retyping.

## Not verified

- I could not fetch the ECB file or the Frankfurter API from this sandbox (DNS blocked). The URL, the update time and the "29 currencies" count come from search snippets of the ECB page, so the XML attribute names come from memory. Capture a real file as a fixture before writing the parser.
- Play Data safety: "collect" means transmitting data off the device ([Play Console Help](https://support.google.com/googleplay/android-developer/answer/10787469)). A fixed GET that carries no user data probably needs no new declaration. That is unconfirmed and is Patric's call on the form.
- Frankfurter's terms and rate limits were not read.

## Review notes

- **Relayed user request is out of scope here.** "User puts in USB, clicks grayscale on app and then our app does its
  thing" is M4-WP5 (grayscale grant UX), not M3-WP4. This revision does not address it. For the orchestrator: the
  review said `docs/design/M4-WP5.md` does not exist yet, but it does exist on this branch. Its Recommendation already
  makes the WebUSB page (plug in, one click) primary with copy-paste adb as fallback, and it states that the phone cannot
  be its own USB adb host, so the click has to happen on the computer, not inside Parem. Any M4-WP5 revision should start
  from that doc and should first settle whether Patric accepts "the click is on a computer page" as the primary flow.
  If he wants the click inside Parem, the only cable-free route is option (b), wireless self-pairing.
- **Opt-in: picked (a), not (b).** Reasons are in Recommendation, under "Opt-in model". No toggle was added.

