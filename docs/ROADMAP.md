# Parem Launcher roadmap

Owner: Patric. Updated: 2026-10-03 (M0 in progress; M1 = ship v5.7.0, M2 =
v5.8.0, M3 = v5.9.0). Current state: `HANDOFF.md`. Rules: `AGENTS.md`.
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

## M2 — v5.8.0: platform currency + housekeeping

Depends on: M1 shipped (v5.7.0 tagged).

| id | Outcome | Owns | Done |
|---|---|---|---|
| M2-WP1 | **Design first.** targetSdk/compileSdk 36 — mandatory for Play updates. Proposal covers: edge-to-edge (opt-out removed at 36) on home, drawer, settings and every BottomSheetMenu sheet; predictive back in drawer/settings/sheets; any behaviour change list for API 36 that touches launchers (home intent, accessibility lock, usage stats, widgets) | `app/build.gradle` SDK lines, `MainActivity`, insets handling in layouts/fragments it names | Builds at 36; proposal's on-device checks listed as "not verified" until Patric runs them |
| M2-WP2 | Upstream Olauncher review (since 2026-07-09, upstream HEAD `66712f7`): a shortlist with verdicts. Port *fixes* only: drawer not closing with animations off (`7e69731`, #713), pending text size applied on leaving settings (`952d9e9`), e-ink false detection on adaptive-refresh displays (`fc5b37f`; this fork has `isEinkDisplay()` in `helper/Extensions.kt`). Features go to M3. Skip: settings popup rework, dialog blur, swipe-down removal | Review notes in the PR; each accepted fix becomes `M2-WP2a`, `M2-WP2b`, … rows | Every upstream commit since the last review has a verdict; log line added to the recurring section below |
| M2-WP3 | About + privacy wiring: fill `Constants.URL_ABOUT_PAREM` / `URL_PAREM_PRIVACY`, unhide their settings rows, remove the known-issue line. Blocked on Patric's privacy text (must cover READ_CONTACTS — read on-device only, opt-in, never transmitted — usage stats, weather requests to Open-Meteo) | `data/Constants.kt`, `ui/settings/AppInfoSettingsCard.kt`, `ARCHITECTURE.md` known issues | Rows visible and open the right URLs; Play Data safety answers listed in the PR for Patric |
| M2-WP4 | Remove dead `setPlainWallpaperByTheme` (moved, not removed, in PAREM-122). Confirm dead yourself; remove only what the deletion orphans | `helper/WallpaperUtils.kt` | No references left; full build green |

**Owner tasks**
- PAREM-113: remove dead `removeActiveAdmin()` (Patric's own ticket, spec in the archive).
- PAREM-107: device repro of residual gesture-letter vs swipe conflicts; findings become an M2 row if anything misfires.
- Write the privacy policy / about content for M2-WP3; update Play Data safety for READ_CONTACTS.
- Device pass at targetSdk 36 (checklist + M2-WP1's list), bump version, tag `v5.8.0`.

## M3 — v5.9.0: features + cheaper releases

Depends on: M2 shipped.

| id | Outcome | Owns | Done |
|---|---|---|---|
| M3-WP1 | **Design first.** Release smoke tests: instrumented tests (launcher starts as home, drawer opens, omnibox search launches an app, settings export/import round-trips) on an emulator in GitHub Actions. Proposal names the runner/emulator setup and CI cost | `app/src/androidTest/`, `app/build.gradle` test deps, new workflow file | Tests pass in CI; `docs/RELEASE_CHECKLIST.md` drops the items they now cover |
| M3-WP2 | **Design first.** Omnibox currency conversion ("10 eur in usd"). Proposal: rate source (no API key), fetch/cache policy (daily, never on keystroke), offline/stale behaviour, privacy note. Parser stays Android-free in `helper/` next to `UnitConverter`, with JVM tests | `helper/` new object + tests, omnibox dispatch, a rates fetcher | Parses/doesn't-parse tests like PAREM-103; works offline from cache; stale rates are marked |
| M3-WP3 | Bold font option (upstream `4c210da`), adapted to this fork's settings cards | the typography settings card, theme/text helpers, `Prefs` | Toggle applies live and survives export/import |
| M3-WP4 | D-pad focus on home text and drawer (upstream `33ea31e`), for TV / keyboard use | home + drawer layouts/fragments focus handling | Every home slot and drawer row reachable and launchable with arrow keys + enter (emulator) |
| M3-WP5 | **Design first.** E-ink mode: skip animations on e-ink displays (upstream `a9da9d4`), building on the existing `isEinkDisplay()` and M2-WP2's detection fix | per proposal | per proposal |

**Owner tasks**
- Pick which of M3-WP3…WP5 make the release; unpicked rows move to a later milestone.
- Device pass, bump, tag `v5.9.0`.

## Recurring (no id)

- **Upstream Olauncher review**, about monthly: `git fetch upstream`, verdict
  per commit since the last review, one WP row per pick. No blind merges.
  Log: 2026-07-03 (3 fixes ported, last port `bec09c7`), 2026-07-06 (nothing),
  2026-07-09 (4 skipped, upstream ViewPager restructure incompatible).
  Next: M2-WP2.
