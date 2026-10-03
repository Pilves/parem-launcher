# Parem Launcher roadmap

Owner: Patric. Updated: 2026-10-03 (M1 = ship v5.7.0, M2 = v5.8.0 Play
readiness, M3 = v5.9.0 groundwork, M4 = v6.0.0 "Calm phone" = first Google
Play release). Parem is not on Play yet; until 6.0, releases are GitHub-only.
The bar for 6.0 is market leader at launch, not "good enough". 6.0 theme from the
2026-10-03 advisor round (product, platform, architecture). Current state: `HANDOFF.md`. Rules: `AGENTS.md`.
Code map and traps: `ARCHITECTURE.md`. Who is on what: `scripts/claim-wp.sh list`.
Tickets before this roadmap (PAREM-101…122) are archived with their full specs
in `docs/archive/TODO-pre-roadmap.md`; commits and CHANGELOG still cite those ids.

This file is the work queue. Every row with an id is a **work package (WP)**:
one outcome, one contributor, one branch, one PR. One milestone is one release.
Packages inside a milestone are disjoint by file unless the row says otherwise.

**Owner tasks** are listed under each milestone without an id, on purpose:
`scripts/claim-wp.sh next` only walks id rows, so an agent can never claim
work that needs Patric's hands (a device, a store console, a decision).

## How to pick up work

0. **Claim first, before reading any code.** `scripts/claim-wp.sh next` takes
   the first package in this file that you actually win.
   `scripts/claim-wp.sh claim <id>` takes a specific one, `list` shows what is
   taken. The claim is a ref on `origin` called `claim/<id>`; the second
   contributor to push it is refused. Release it with
   `scripts/claim-wp.sh release <id>` when the PR lands or you drop the work.
1. Take the lowest unclaimed WP in the current milestone. Check its
   "Depends on" first.
2. Branch from `master`: `<type>/<slug>` (`fix/…`, `feat/…`, `refactor/…`,
   `chore/…`).
3. Touch only what the WP owns. If you must change a hot file, say so in the PR
   and keep the change minimal.
4. Packages marked **design first** stop after a written proposal (in the PR
   or issue) and wait for Patric's sign-off before code.
5. Done means `./gradlew compileDebugKotlin testDebugUnitTest assembleDebug`
   and `node --test scripts/build-handoff.test.mjs` exit 0, plus the WP's own done criteria. State
   what you did **not** verify — on-device behaviour especially.
6. Closing a WP: add `docs/handoff/<WP-id>.md`, run
   `node scripts/build-handoff.mjs`, commit its output (`docs/handoff/README.md`).

### Hot files

| File | Why it conflicts | Rule |
|---|---|---|
| `app/build.gradle` | SDK levels, versionCode/versionName | Version bumps only in the release task; SDK change owned by M2-WP1 |
| `CHANGELOG.md` | Every behaviour change adds a line | Append under `## [Unreleased]` only; rebase before the PR |
| `app/src/main/res/values/strings.xml` | Every UI WP adds strings | Append at the end, `translatable="false"` unless translated |
| `app/src/main/java/**/data/Prefs.kt` | New exported keys need type registration | Append to the key lists, never reorder |
| `AndroidManifest.xml` | Permissions, activities | Say why in the PR; permission changes need Patric's sign-off |
| `ARCHITECTURE.md` | Code map, traps, known issues | Edit only the lines your WP changed the truth of |
| `HANDOFF.md`, this file's Done markers | Generated | `node scripts/build-handoff.mjs`; never hand-edit between the markers |
| `docs/handoff/` | Nothing conflicts here | One file per WP |

---

## M0 — agent harness

| id | Outcome | Owns | Done |
|---|---|---|---|
| M0-WP1 | **Done** (PR #5). Platvorm's agent harness ported: claim script, generated handoff, roadmap queue, PR/issue templates, CI check | `scripts/`, `docs/ROADMAP.md`, `docs/handoff/`, `HANDOFF.md`, `AGENTS.md`, `CLAUDE.md`, `.github/` (new files only), `ONBOARDING.md` workflow lines | `node --test scripts/build-handoff.test.mjs` passes; generator idempotent; `claim-wp.sh list` reaches origin |

## M1 — ship v5.7.0 (this week)

Release candidate: `f4b55cf` plus whatever M1 adds. Held since 2026-07-10 for a
device pass. Nothing new goes into v5.7.0 except fixes for what the pass finds.

| id | Outcome | Owns | Done |
|---|---|---|---|
| M1-WP1 | `docs/RELEASE_CHECKLIST.md`: a standing device-pass checklist (core every release + a "this release touched" section), seeded from the f4b55cf watchlist: every home gesture, double-tap lock (trap #1), slot taps/menus/folders, clock/date/screen-time taps, widget add/resize/reorder portrait+landscape, capacity-capped picker, settings walk, export/import incl. limits, contact-search toggle + denial, tablet landscape, sheet scrolling | `docs/RELEASE_CHECKLIST.md`, release section of `ARCHITECTURE.md` (link only) | Checklist fits on one screen per section; ARCHITECTURE release steps point at it |

Fixes found during the pass are added here as `M1-WP2`, `M1-WP3`, … — one
row per finding, scoped to the fix.

**Owner tasks**
- Device pass on the M1 head using `docs/RELEASE_CHECKLIST.md`.
- Tag `v5.7.0` and push once the pass is clean. CI signs and publishes.

## M2 — v5.8.0: Play readiness + housekeeping

New Play apps must target API 36, and the accessibility, contacts and
package-visibility declarations must hold up in review. None of it is
date-driven yet — Parem is not listed — but all of it gates the 6.0 launch.

| id | Outcome | Owns | Done |
|---|---|---|---|
| M2-WP1 | **Design first.** targetSdk/compileSdk 36 — mandatory for Play updates. Proposal covers: edge-to-edge (opt-out removed at 36) on home, drawer, settings and every BottomSheetMenu sheet; predictive back in drawer/settings/sheets; any behaviour change list for API 36 that touches launchers (home intent, accessibility lock, usage stats, widgets) | `app/build.gradle` SDK lines, `MainActivity`, insets handling in layouts/fragments it names | Builds at 36; proposal's on-device checks listed as "not verified" until Patric runs them |
| M2-WP2 | Upstream Olauncher review (since 2026-07-09, upstream HEAD `66712f7`): a shortlist with verdicts. Port *fixes* only: drawer not closing with animations off (`7e69731`, #713), pending text size applied on leaving settings (`952d9e9`), e-ink false detection on adaptive-refresh displays (`fc5b37f`; this fork has `isEinkDisplay()` in `helper/Extensions.kt`). Features go to M3. Skip: settings popup rework, dialog blur, swipe-down removal | Review notes in the PR; each accepted fix becomes `M2-WP2a`, `M2-WP2b`, … rows | Every upstream commit since the last review has a verdict; log line added to the recurring section below |
| M2-WP3 | About + privacy wiring: fill `Constants.URL_ABOUT_PAREM` / `URL_PAREM_PRIVACY`, unhide their settings rows, remove the known-issue line. Blocked on Patric's privacy text (must cover READ_CONTACTS — read on-device only, opt-in, never transmitted — usage stats, weather requests to Open-Meteo, and ECB rate downloads from `www.ecb.europa.eu` — only after a currency query is typed, a fixed URL carrying no user data, M3-WP4) | `data/Constants.kt`, `ui/settings/AppInfoSettingsCard.kt`, `ARCHITECTURE.md` known issues | Rows visible and open the right URLs; Play Data safety answers listed in the PR for Patric |
| M2-WP5 | **Design first.** Accessibility disclosure + consent: a BottomSheetMenu sheet shown before sending the user to enable the lock service — what it does, that it reads nothing else, explicit accept/decline (Play accessibility-API policy). Also drop `canRetrieveWindowContent="true"` from the service config if the lock still works without `event.source` (trap #1 — device check) | lock-enable flow in `ui/settings/GesturesSettingsCard.kt` and the home double-tap path, `res/xml` accessibility config, strings | Consent shown before every route into accessibility settings; decline leaves lock off; Play declaration text drafted in the PR for Patric |
| M2-WP6 | Spike: can `QUERY_ALL_PACKAGES` go? App list comes from `LauncherApps`; the icon-pack `queryIntentActivities` may only need a `<queries>` entry. Remove the permission, add `<queries>`, check hidden apps, usage-stat names, icon packs, omnibox | `AndroidManifest.xml`, a verdict in the PR | Verdict with evidence; if removable, the change ships; if not, the Play declaration text for keeping it |
| M2-WP4 | Remove dead `setPlainWallpaperByTheme` (moved, not removed, in PAREM-122). Confirm dead yourself; remove only what the deletion orphans | `helper/WallpaperUtils.kt` | No references left; full build green |

**Owner tasks**
- PAREM-113: remove dead `removeActiveAdmin()` (Patric's own ticket, spec in the archive).
- PAREM-107: device repro of residual gesture-letter vs swipe conflicts; findings become an M2 row if anything misfires.
- Write the privacy policy / about content for M2-WP3 (it must list `www.ecb.europa.eu` as a destination: triggered only by typing a currency query, no user data — M3-WP4); update Play Data safety for READ_CONTACTS.
- Play Console accessibility declaration + demo video, using M2-WP5's text.
- Device pass at targetSdk 36 (checklist + M2-WP1's list), bump version, tag `v5.8.0`.

## M3 — v5.9.0: groundwork + small features

Depends on: M2 shipped. WP2 and WP3 are prerequisites for M4.

| id | Outcome | Owns | Done |
|---|---|---|---|
| M3-WP1 | **Design first.** Release smoke tests: instrumented tests (launcher starts as home, drawer opens, omnibox search launches an app, settings export/import round-trips) on an emulator in GitHub Actions. Proposal names the runner/emulator setup and CI cost | `app/src/androidTest/`, `app/build.gradle` test deps, new workflow file | Tests pass in CI; `docs/RELEASE_CHECKLIST.md` drops the items they now cover |
| M3-WP2 | Omnibox resolver: move the mode precedence in `AppDrawerFragment.updateOmniboxState` (calc → conversion → dial → web → contact) into an Android-free `helper/OmniboxResolver` returning a mode, with JVM tests for the precedence. Zero behaviour change. No provider/plugin system | `helper/OmniboxResolver.kt` + test, `ui/AppDrawerFragment.kt` | Precedence tests green; drawer behaviour unchanged |
| M3-WP3 | Prefs registration guard: a JVM test that fails when a key read with `getLong`/`getFloat` anywhere in `app/src/main` is neither in `LONG_PREF_KEYS`/`FLOAT_PREF_KEYS` nor `exportExcludeKeys` (import guesses types from JSON) | `app/src/test/` new test, `data/Prefs.kt` only if the test finds a real gap | Test green on master; deliberately unregistered key makes it fail |
| M3-WP4 | **Design first.** Omnibox currency conversion ("10 eur in usd"). Proposal: rate source (no API key), fetch/cache policy (daily, never on keystroke), offline/stale behaviour, privacy note. Parser Android-free in `helper/` next to `UnitConverter`, with JVM tests. Depends on M3-WP2 | `helper/` new object + tests, `helper/OmniboxResolver.kt`, a rates fetcher | Parses/doesn't-parse tests like PAREM-103; works offline from cache; stale rates are marked |
| M3-WP5 | Bold font option (upstream `4c210da`), adapted to this fork's settings cards | the typography settings card, theme/text helpers, `Prefs` | Toggle applies live and survives export/import |
| M3-WP6 | Keyboard + D-pad: focusable home text and drawer rows (upstream `33ea31e`, Olauncher #736/#567), typing on a hardware keyboard at home opens drawer search with that letter (Olauncher PR #762), Menu key opens the drawer (#472) | home + drawer layouts/fragments focus and key handling | Every home slot and drawer row reachable and launchable with arrow keys + enter; typing at home lands in search (emulator) |
| M3-WP9 | Lock home layout: a toggle that disables long-press edit/rename/reorder on home slots so the layout can't be changed by accident (Olauncher #726) | `ui/home/HomeSlotsController.kt`, a settings row, `Prefs` | Locked = long-press shows nothing editable; unlock restores; survives export/import |
| M3-WP7 | **Design first.** E-ink mode: skip animations on e-ink displays (upstream `a9da9d4`), building on the existing `isEinkDisplay()` and M2-WP2's detection fix | per proposal | per proposal |
| M3-WP8 | Widget picker: list work-profile providers (`getInstalledProvidersForProfile`) and show generated previews on Android 15+. Trap #2 — no widget-ID logic changes | widget picker code in `ui/HomeWidgetController.kt` | Work-profile widgets addable; previews shown where the provider offers them |

| M3-WP10 | Performance baseline + budget: cold start to interactive home, return-to-home, drawer scroll and omnibox typing frame timing (Macrobenchmark or `am start -W` + gfxinfo), run in the M3-WP1 emulator CI. Budgets: cold start < 300 ms mid-range, return home < 100 ms, < 1% janky frames, no frame > 32 ms | new benchmark module or scripts, CI workflow from M3-WP1 | Numbers recorded in `docs/RELEASE_CHECKLIST.md`; CI fails on a > 20% regression |
| M3-WP11 | **Design first.** Opt-in crash reports (ACRA or equivalent, F-Droid-compatible, no network SDK): off by default; on crash, offer to send a report via email/share sheet. Play vitals cover the Play channel | `app/build.gradle` dep, `Application` class, a settings row, strings | Off = nothing collected; on = user sees and sends the report themselves |

**Known limitations**
- M3-WP8: widget restore is not profile-aware. `prefs.widgetProviders` stores only the component string, and `processNextWidgetRestore()` rebinds via `bindAppWidgetIdIfAllowed(newId, component)` / an `ACTION_APPWIDGET_BIND` intent without `EXTRA_APPWIDGET_PROVIDER_PROFILE`. If the OS invalidates a work-profile widget (work profile removed or re-provisioned), restore silently rebinds the personal-profile provider of the same component, or shows a bind dialog for it. Fixing it means storing the profile alongside the component — a Trap #2 widget-ID change, so it needs its own WP.
- M3-WP8: the picker fetches every provider's generated preview up front on IO before the sheet opens. Fine for typical provider counts; if it shows up in memory or open latency, fetch lazily per visible row or cap the count.

**Owner tasks**
- Pick which of M3-WP5…WP11 make the release; unpicked rows move to a later milestone.
- Device pass, bump, tag `v5.9.0`.

## M4 — v6.0.0: Calm phone

Parem controls attention, not just measures it: notifications that wait,
a pause before habit apps, focus on a schedule, grayscale on demand — plus
the platform's privacy surface (Private Space) and the top user request
(website shortcuts). Depends on: M3-WP1…WP3. Everything new is opt-in and
on-device. Not in 6.0: hard blocking without an override, a unified inbox,
theming studio, accounts/sync, Compose, DataStore, any new use of the
accessibility service.

| id | Outcome | Owns | Done |
|---|---|---|---|
| M4-WP1 | **Design first.** Filtered notifications: opt-in `NotificationListenerService`; the user picks apps allowed to alert, everything else is silenced into a quiet list behind a home gesture. Proposal: listener lifecycle, storage (in-memory vs persisted, never exported), how silencing works per Android version, focus-mode interaction, Play Data safety impact | new `helper/notifications/`, a quiet-list sheet via BottomSheetMenu, settings card, manifest service entry | Off by default; listener never bound while off; quiet list survives process death per the design; JVM tests for filtering rules |
| M4-WP2 | Mindful pause: per-app opt-in delay (default 5 s, with "why are you opening this?") before a habit app launches, built on app limits' bad-habit flag; cancel returns home | the limit-check launch path (`ui/home/HomeSlotsController`, drawer launch via `MainViewModel.selectedApp`), app-limit sheet, `Prefs` | Pause shown on every launch route (home slot, drawer, omnibox, gesture letter, swipe app); cancel and proceed both work |
| M4-WP3 | **Design first.** Scheduled focus: weekday/time windows (work, sleep) on top of timed focus; same whitelist and enforcement. Proposal: scheduling mechanism (AlarmManager exact vs inexact, Doze), overlap rules, export/import | `helper/FocusModeManager.kt`, focus sheet, `Prefs` | Window starts/ends on time ±1 min with the screen off; JVM tests for window maths incl. midnight and DST |
| M4-WP4 | Weekly review card in the screen-time sheet: this week vs last week, biggest riser/faller app | screen-time sheet, `helper/usageStats/` read-only | Numbers match the 7-day graph; JVM test for the comparison |
| M4-WP5 | **Design first.** Grayscale on demand via `Settings.Secure` daltonizer (needs `WRITE_SECURE_SETTINGS`, adb-only). Goal: one tap for the user. Patric's target UX: plug in a cable, click once, done. An app cannot drive adb on its own phone over USB (the computer is the adb host), so the proposal compares (a) a static WebUSB page (ya-webadb/Tango) the user opens on a computer with the phone plugged in — one click runs `pm grant`; (b) in-app wireless-debugging self-pairing like LADB/Shizuku — no cable, Android 11+, pairing code typed into Parem; (c) Shizuku one-tap grant if installed; (d) copy-paste `adb` command fallback — cost, deps, Play risk, failure modes — and picks a primary + fallback. Triggers: manual toggle, during focus, after a limit is hit. Must restore colour on disable/uninstall-adjacent paths | per proposal; grant flow sheet, `helper/` grayscale controller, settings card | Toggle works after the chosen grant flow; never leaves the phone stuck grey (crash, focus end, toggle off) |
| M4-WP6 | **Design first.** Private Space: declare `ACCESS_HIDDEN_PROFILES`, separate drawer section, lock/unlock via `requestQuietModeEnabled`, nothing visible or searchable (omnibox included) while locked, profile available/unavailable receivers. `AppListSource` must gate by profile type | `helper/AppListSource.kt`, drawer section, omnibox filtering, manifest | Meets Android's launcher requirements for Private Space; locked = zero leakage in drawer, search, home slots, folders |
| M4-WP7 | **Design first.** Website shortcuts on home: pin a URL (or a PWA shortcut via `LauncherApps` pinned shortcuts, see upstream `14b89e9`) to a home slot or folder | home slots, shortcut handling, `Prefs` | Pinned site opens in the default browser/PWA; survives export/import |
| M4-WP8 | Lock-service revoked: detect when the accessibility service is off (Android 17 Advanced Protection revokes non-tool services — confirm on developer.android.com first) and explain it once instead of failing silently on double-tap | home double-tap path, a one-time sheet | Double-tap with the service off shows the explanation; no repeat nagging |

| M4-WP9 | Translation coverage: make every Parem-specific string translatable where it is user-facing, add Estonian, bring de/es/pt-BR/fr/ru to 100% (machine draft + native review list for Patric). Not upstream's newly added locales — their strings don't match ours | `res/values*/strings.xml` | Lint `MissingTranslation` clean for the six target locales |
| M4-WP10 | Store assets from 6.0: scripted screenshots (fastlane screengrab on an emulator) of home, omnibox, Calm-phone features, screen time; refreshed en-US/de/et listing text | `fastlane/`, a screengrab test | One command regenerates all screenshots |
| M4-WP11 | Battery + wake-lock audit: weather fetch, scheduled-focus alarms, notification listener hold no wake locks; 24 h soak < 1%/day | code the audit names | `dumpsys batterystats` soak recorded; zero excessive wake locks |
| M4-WP12 | **Design first.** Onboarding for 6.0: set default home first, then optional Calm-phone setup (notification filter, mindful pause, grayscale grant, lock consent), every step skippable | `ui/onboarding/`, strings | ≥ 90% of testers finish "set as default home" in the closed test |

**Launch quality bars** (Play vitals bad-behaviour lines are 1.09% crash / 0.47% ANR):
crash-free ≥ 99.8%, ANR < 0.2%, battery < 1%/day, APK < 10 MB, no RAM growth over 24 h.
Pricing: free, no ads, no accounts, donations only — the privacy story is the differentiator.

**Owner tasks**
- **Start now (≈4–5 weeks of lead time):** create the Play developer account + identity verification; register the package for Android developer verification (covers GitHub/IzzyOnDroid sideloads too); recruit 15–20 testers and run a closed test ≥ 14 days with ≥ 12 opted in (any current build is fine); then apply for production access.
- IzzyOnDroid listing request (GitHub releases already ship signed APKs); F-Droid main later.
- minSdk 24 → 29 decision from Play Console install share (do it if Android 7–9 < ~3%); becomes an M4 row if yes.
- Delete the local upstream `v6.*` tags and set `remote.upstream.tagOpt --no-tags` before tagging 6.0; push release tags by name only.
- Play launch: developer account, closed testing (check the current tester/day
  requirement for new personal accounts — start it early, it is the longest
  lead time), store listing, Data safety incl. notification access; device
  pass; tag `v6.0.0`.

## Recurring (no id)

- **Upstream Olauncher review**, about monthly: `git fetch upstream`, verdict
  per commit since the last review, one WP row per pick. No blind merges.
  Log: 2026-07-03 (3 fixes ported, last port `bec09c7`), 2026-07-06 (nothing),
  2026-07-09 (4 skipped, upstream ViewPager restructure incompatible),
  2026-10-03 (M2-WP2, to upstream `66712f7`: 2 fixes ported — `7e69731`
  drawer stuck with animations off, `fc5b37f` LTPO e-ink false positive;
  `952d9e9` not needed, Parem applies text size on tap; planned: `33ea31e` →
  M3-WP6, `4c210da` → M3-WP5, `a9da9d4` → M3-WP7, `14b89e9` → M4-WP7,
  `2b117ed` → M2-WP1; skipped: `8bbea58` `e3fa863` `33e9b29` `51018c9`
  settings popup/dialog-blur rework, `bf7cda5` swipe-down removal, `9e26070`
  footer code Parem lacks, `c3ee696` README + `dependenciesInfo` (wanted
  before the IzzyOnDroid request, not a fix), version bumps, README,
  translations).
