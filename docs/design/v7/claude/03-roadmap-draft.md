# v7 roadmap draft v2 (Claude, step 3, revised after Codex's feasibility review)

Built from `claude/01-gaps.md`, `claude/02-themes.md`, `codex/01-codebase-audit.md`,
`codex/02-signals.md` and the step-2/3 entries in `LOG.md`. Format follows
`docs/ROADMAP.md`. Evidence labels: `defect` (seen in today's source),
`platform` (sourced external deadline), `carryover` (deferred by a 6.0
design/decision), `hypothesis` (no Parem user evidence yet). Costs are guesses
in focused days, excluding device passes. Nothing here assumes 6.0 has shipped:
**re-audit the 6.0 tag before activating any M6 row** and drop a row M4
already solved.

Sources: Android 17 Advanced Protection restricts AccessibilityService to
accessibility tools (https://blog.google/security/android-advanced-protection-updates/);
Play requires API 37 targeting for new apps/updates from **August 2027**
(https://android-developers.googleblog.com/2026/02/prepare-your-app-for-resizability-and.html);
the existing device-admin lock fallback is `ui/home/HomeGesturesController.kt:316-323`
with `res/xml/policies.xml`.

## Changes from v1 (Codex step-3 review)

- Every **design first** row is now proposal-only with a design-file path and a
  sign-off criterion; its implementation is a separate row with frozen files,
  added only after approval. v1 mixed the two.
- Placeholder ids (`M5-WP2…`, `M2-WP2x`) are prose; the generator only accepts
  real numeric ids (`scripts/build-handoff.mjs:38`).
- Byte-identical round-trip replaced by semantic equality; "no behaviour
  change" on widget restore replaced by an explicit choice (see M6-WP4).
- Screen-time first-value fix moved out of M6 into the next upstream review.
- A **sequencing** section per shared file replaces the implied "nine
  independent packages".

## Fixes that should not wait for v7 (add to the current milestones)

| id | Outcome | Owns | Done | Evidence |
|---|---|---|---|---|
| M2-WP7 | App limits apply to folder launches: the folder sheet callback (`ui/home/HomeSlotsController.kt:273`) routes through `checkBadHabitAndLaunch` (`:232`) instead of `launchApp`. Surgical; no policy extraction | `ui/home/HomeSlotsController.kt`; a route-level test if M3-WP1's harness exists, else a device list | Below/at/over limit, warning proceed/cancel, and focus-denied behave as a home-slot tap; device matrix listed and pending until Patric runs it | defect (verified by both planners) |

Prose, not rows: at the **next upstream review (M2-WP2)** add sub-rows for
(a) PR #761 (https://github.com/tanujnotes/Olauncher/pull/761) — unknown IME
subtype bypasses the composition guard at `ui/AppDrawerFragment.kt:149`;
specify whether filtering (`:195`) as well as auto-launch pauses while
composing, and name the emulator IME plus one real third-party IME case; and
(b) upstream `17d37fb` — first screen-time value after process recreation
(`MainViewModel.kt:307-317` gates on a persisted timestamp); test a fresh
ViewModel with a recent timestamp and empty live value. Both only if
reproduced. Also fix the recurring-section note: `66712f7` is dated
2026-09-17, not July.

## M5 — v6.1.0: respond to launch

Starts when 6.0 reaches Play production; time-boxed to ~6 weeks. No theme.

| id | Outcome | Owns | Done | Evidence |
|---|---|---|---|---|
| M5-WP1 | Launch intake snapshot: `docs/LAUNCH_INTAKE.md` for a frozen window (date range, build, sources, retrieval date). Each crash/ANR cluster, closed-test report and GitHub/Izzy issue gets severity, reproducibility, affected launch routes and a disposition (fix row / won't fix / needs repro). Sources Patric cannot export are recorded as unavailable, not empty. No tester personal data in the repo | `docs/LAUNCH_INTAKE.md` | Every item in the snapshot has a disposition; vitals recorded with denominator and window beside (not instead of) the device-pass results | process |
| M5-WP2 | Play declarations file: `docs/PLAY_DECLARATIONS.md` — dated accessibility, notification-listener, QUERY_ALL_PACKAGES (if kept) and Data-safety text tied to a build and a permission/service inventory | `docs/PLAY_DECLARATIONS.md` | Text prepared and dated; **owner** compares with the Console and ticks it; re-check is triggered by any SDK/permission/service change | process |

Accepted intake findings become `M5-WP3`, `M5-WP4`, … one per finding, scoped
to the fix, like M1. Ids are assigned at acceptance, never in advance.

**Owner tasks:** read vitals weekly after the snapshot; decide minSdk 29 from
install share (becomes an M6 row if yes); decide which unpicked M3 rows ride
into M6 — **M3-WP10 perf budget is a prerequisite for M6**, or name a manual
baseline fallback.

## M6 — v7.0.0: the dependable everyday launcher

Headline (both planners): Parem launches the right app on every in-scope
route and profile; home and widget state survives; a permission that
disappears is explained, not silently broken. Theme B (calm phone round two)
and theme E (omnibox actions) are parked until M5 intake shows demand.
Depends on: M5 closed; 6.0 tag re-audited.

### Proposals (design first — proposal-only rows, each ends at Patric's sign-off)

| id | Outcome | Owns | Done | Evidence |
|---|---|---|---|---|
| M6-WP1 | **Design first.** Profile identity: limits, focus allow-list and renames are keyed by package (`helper/AppLimitManager.kt:22`, `helper/FocusModeManager.kt:66`, `data/Prefs.kt:462`); the app list knows package + profile; a failed profile launch retries in the current profile (`MainViewModel.kt:216-224`). Decide shared vs per-profile policy, unavailable/locked-profile behaviour (keep DECISIONS.md: no limits on private rows), backup key format for profile targets, and same-label disambiguation (upstream #765) as a separate presentation outcome | `docs/design/M6-WP1.md` | Acceptance matrix (same package two profiles, same label two packages, profile locked/unavailable, old backup) has a specified outcome per cell; Patric signs off | defect-likely + Codex C1/S1 |
| M6-WP2 | **Design first.** Launch policy as one decision: evaluate whether M4-WP2/WP3 already centralise focus/limit/pause; if not, an Android-free `helper/LaunchPolicy` called from `MainViewModel.selectedApp` only. Route inventory with in/out of scope: slot, folder, drawer, omnibox, gesture letter, swipe app, double-tap app, clock/calendar taps, URL slots (M4-WP7), pin shortcuts, notification PendingIntents. Decide the final-dispatch recheck after the async resolve (`MainViewModel.kt:191-209`), especially after a pause. Depends on M6-WP1 | `docs/design/M6-WP2.md` | Every in-scope route × state cell specified; "drop, M4 already does this" is an acceptable outcome; Patric signs off | defect (M2-WP7 shows routes diverge) + Codex C1 |
| M6-WP3 | **Design first.** Backup contract: is a version 2 needed? Supported versions, unknown-key policy, validate the whole payload before `clear()` (`Prefs.kt:498-535` currently invalidates caches at `:503` and checks only the marker), failure semantics (validation failure vs process death mid-write), display vs silent drop of device-bound/unavailable targets. Depends on M3-WP3, M6-WP1 and the final M4 key list. No DataStore | `docs/design/M6-WP3.md` | Format validation and profile/shortcut migration are specified as separate implementation rows with exact `Prefs.kt` sections and UI files; Patric signs off | defect + Codex C4 |
| M6-WP4 | **Design first.** Lock without the accessibility service on Advanced Protection devices: make the existing `lockNow()` device-admin path primary there? Matrix: service disabled with/without Advanced Protection; admin absent/declined/enrolled/revoked (`MainActivity.kt:114` handles enrolment results); process recreation; unlock behaviour; define "explain once" (per attempt/session/transition — a persisted flag adds `Prefs` ownership). Screen-timeout tricks excluded. "Explain and remove" means the affected capability, not deleting lock for everyone. Builds on M2-WP5 consent, M4-WP8 detection | `docs/design/M6-WP4.md` | Matrix complete with named OS versions; Play device-admin policy checked by owner; Patric signs off | platform (sourced) + carryover |
| M6-WP5 | **Design first.** targetSdk 37: behaviour-change list for launchers, large-screen resizability/orientation rules, insets re-check on every sheet, frozen affected-file list. Scheduled against the Aug 2027 deadline, independent of other M6 slips | `docs/design/M6-WP5.md` | File list frozen; device checks enumerated; Patric signs off | platform (sourced) |
| M6-WP6 | **Design first, gated.** Pin-shortcut requests (`ACTION_CONFIRM_PIN_SHORTCUT`, `docs/design/M4-WP7.md:23` option B) — only if M5 intake shows users expect "Add to Home screen" to land in Parem; otherwise dropped. Covers cancel/no free slot, duplicate request, expired/disabled shortcut, default-launcher loss, locked profile, process recreation, export/import residue, a controlled pin-request test producer. Depends on M6-WP1 and M6-WP3 | `docs/design/M6-WP6.md` | Gate evidence cited; manifest/permission changes listed for owner approval; Patric signs off | carryover, gated on hypothesis |

### Implementation (rows with files that are already frozen today)

| id | Outcome | Owns | Done | Evidence |
|---|---|---|---|---|
| M6-WP7 | Widget reconciliation extracted: pure `helper/WidgetReconciler` computing keep/rebind/remove/order from (host IDs, `widgetIds`, `widgetProviders`), **used** by `HomeWidgetController.restoreWidgets()`. Today's restore writes valid IDs first (`:128-150`) then appends rebound ones (`:220-223`), so invalid-A-before-valid-B becomes B,A. **Choice for Patric:** characterise that as-is (no behaviour change) or fix order (behaviour change, changelog line). Allocation and callback lifetime (`:657`, `:669`, `:698`; `MainActivity.kt:57-61`, `:98-111`, `:153-159`) stay out; a separate row only if a repro appears. Serialise after any M3-WP8 carryover | `helper/WidgetReconciler.kt` + tests, `ui/HomeWidgetController.kt`, trap #2 lines in `ARCHITECTURE.md` | Fixtures: mixed valid/invalid, provider gone, denied bind, process death (modelled inputs); device check with one invalid widget (induced by uninstalling its provider), pending until run | trap #2 + Codex C3a |
| M6-WP8 | Search session correctness: stale = session id/query revision; invalidated on clear, back/home, view destruction, picker change; checked at callback publication, list-diff completion and the posted click (`ui/AppDrawerAdapter.kt:138-155`). If the M2-WP2 IME fix already resolves it, keep only the regression tests. Follows M3-WP2/WP6 and M4-WP6 drawer rows | `ui/AppDrawerFragment.kt`, `ui/AppDrawerAdapter.kt`, `helper/` only if a pure decision exists, `app/src/androidTest/DrawerSessionTest.kt` (M3-WP1 harness) | Stale result never launches in the listed cases; picker never auto-launches; instrumented test green in CI | defect-likely (upstream #766 unreproduced) + Codex C2 |

Implementation rows for M6-WP1…WP6 are added after sign-off, with frozen
contracts and exact files, as `M6-WP9`, … .

### Sequencing by shared file (one active owner per file, serial PRs)

| File | Order |
|---|---|
| `MainViewModel.kt` | upstream screen-time fix → M6-WP1 identity impl → M6-WP2 policy impl → M6-WP6 pin impl |
| `ui/home/HomeSlotsController.kt` | M2-WP7 → M6-WP2 impl → M6-WP6 impl |
| `data/Prefs.kt` | M3-WP3 → M6-WP1 keys → M6-WP3 validation → M6-WP6 keys; append-only per Hot files |
| drawer files + androidTest | M3-WP2, M3-WP6, M4-WP6 closed or retired → M2-WP2 IME sub-row → M6-WP8 |
| `ui/HomeWidgetController.kt` | M3-WP8 carryover (if picked) → M6-WP7 |
| `app/build.gradle`, manifest, `policies.xml` | one SDK owner (M6-WP5 impl); permission/admin changes only with owner approval; version bumps stay with release |
| `CHANGELOG.md`, `strings.xml`, `ARCHITECTURE.md`, handoff | Hot-files rules as in `ROADMAP.md:43-54`; proposal-only rows never touch them |

**Quality bars** carry over from M4 unchanged. Vitals supplement the device pass
and the perf budget; they do not replace them.

**Owner tasks (M6):** re-audit the 6.0 tag against WP1/WP2/WP8 and drop what M4
solved; Play device-admin policy check before M6-WP4 sign-off; upstream review
(fixes only, never a wholesale merge of the ViewPager restructure); run every
"pending until run" device check.

## Parked (named so they stay parked)

- **Theme B, calm phone round two**: notification digest/history (M4-WP1 keeps
  keys, not content — needs its own retention/display design), focus profiles
  bundling allow-lists (reverses M4-WP1's deliberate independence), monthly
  review, cooldown-to-disable. At most one row, after M5 intake.
- **Theme E, omnibox actions/ranking**: no demand evidence.
- Unified inbox, hard blocking, theming studio, accounts/sync, Compose,
  DataStore, more than eight slots, search auto-open setting (upstream #580).

## Decisions needed from Patric

1. Add **M2-WP7** (folder limit fix) to M2 now, and the two upstream sub-rows at
   the next review?
2. v7.0 headline: **C "dependable launcher"** as drafted, or B "calm phone round
   two"? Both planners recommend C.
3. M6-WP7 widget order: characterise as-is, or fix the B,A reorder?
4. Is device-admin `lockNow()` acceptable as the primary lock on Advanced
   Protection devices (own consent + Play policy cost)? If no, M6-WP4 becomes
   "explain the unavailable state".
5. Which unpicked M3 rows ride into M6? M3-WP10 perf budget is assumed.
6. Promote to `docs/ROADMAP.md` as M5/M6 after your edits (Claude ports it), or
   iterate here first?
