# v7 codebase audit — Codex, step 1

Evidence snapshot: 2026-10-23, checkout `6404d9686ab165f38f431800bef680558be90abb`. Planning only; no builds, device runs, code edits or commits. Paths below are repository-relative; `J/` means `app/src/main/java/com/parem/launcher/`. Candidate IDs are discussion handles, not claimed roadmap packages.

## Baseline: do not mistake planned 6.0 for shipped code

`HANDOFF.md:10` describes the pre-6.0 release sequence. M3/M4 are planned outcomes, not evidence that the checkout contains their implementation (`docs/ROADMAP.md:107`, `docs/ROADMAP.md:134`). Consequently “6.0 leaves behind” below means **likely residual risk, conditional on the final M4 implementation**. Re-audit the actual 6.0 tag before promoting a row. M3 already owns resolver extraction, preference type registration, smoke tests and performance budgets; M4 owns launch pauses, Private Space, shortcuts and scheduled focus. Do not charge these twice to v7.

Keep Kotlin/Views, the single SharedPreferences file and BottomSheetMenu. There is no evidence here that a database, DI framework, Compose migration or provider/plugin system would solve the identified problems more cheaply (`ARCHITECTURE.md:85`, `docs/ROADMAP.md:108`, `docs/ROADMAP.md:128`).

## Findings and pressure points

### 1. Launch policy is not yet one decision across every route — high priority recheck

Evidence: `J/MainViewModel.kt:89` centrally checks focus before launch; `J/ui/home/HomeSlotsController.kt:232` separately checks usage limits for a home tap. Folder items call `launchApp` directly at `J/ui/home/HomeSlotsController.kt:270`. Thus the inspected folder route does not execute that controller's limit check. This is a source-level finding, not a device repro. `J/MainViewModel.kt:99` also mixes launch dispatch with home-slot assignment, whose flag integers are persisted slot numbers (`ARCHITECTURE.md:95`).

M4-WP2 explicitly requires every route to respect mindful pause (`docs/ROADMAP.md:137`). That is a 6.0 acceptance criterion, not a feature to defer. After M4, recheck that limit/focus/pause ordering and cancel/proceed outcomes share a decision and that an asynchronous approval cannot launch a different or now-locked target. Proposed seam: a small Android-free decision object receiving explicit state; keep `selectedApp` as the public entry point. Do not replace all flag dispatch just for style.

### 2. Profile identity is richer than the policy/storage keys — high priority recheck

The app query records component and profile (`J/helper/AppListSource.kt:81`), but limits are indexed by package (`J/helper/AppLimitManager.kt:22`), focus allows by package (`J/helper/FocusModeManager.kt:66`), and rename keys use package (`J/data/Prefs.kt:462`). A failed launch catches SecurityException and retries the same component in the current profile (`J/MainViewModel.kt:216`). **Inference:** with two installations this can open a different profile's app than the one selected; no device repro performed.

M4-WP6 owns locked-profile non-leakage (`docs/ROADMAP.md:141`), so recheck these paths before calling 6.0 complete. Any v7 per-profile policy needs an explicit product decision: shared limits versus separate limits, unavailable-profile behavior, and portable backup identity. Android documents profile separation and intent restrictions: https://developer.android.com/work/managed-profiles . Do not silently infer a different profile when a target is unavailable.

### 3. Widget recovery mixes resource allocation, persistence and UI lifetime

`J/ui/HomeWidgetController.kt:100` queries existing IDs, deletes invalid host allocations, queues rebinds and writes the valid list. At `:200`, recovered IDs are appended after height/provider migration. **Inference:** mixed valid/invalid widgets may change order during recovery. Bind/configure callbacks at `:669` and `:698` guard activity, but pending allocation and callbacks span the activity/controller boundary. The restoration coroutine resumes into view work at `:128` without a local `isActive()` check; whether lifecycle cancellation fully protects it requires lifecycle tests, not an asserted crash.

Three-way identity is an explicit trap (`ARCHITECTURE.md:94`). New widget stacks, pop-ups or profile-aware widgets will amplify it. First isolate a pure reconciliation plan (keep/rebind/remove/order), leaving Android allocation in the controller. Enumerate denied binding, provider disappearance, rotation, process death and abandoned configuration. Do not promise transferable widget IDs: export deliberately excludes them (`J/data/Prefs.kt:467`). M3-WP8 owns picker work and expressly avoids ID logic (`docs/ROADMAP.md:115`); serialize any recovery change with it.

### 4. Search resolver extraction does not solve search session lifetime

The IME guard treats missing keyboard subtype as non-CJK (`J/ui/AppDrawerFragment.kt:149`), and text changes still filter at `:195`. Adapter filtering writes mutable auto-launch flags (`J/ui/AppDrawerAdapter.kt:103`); list submission invokes auto-launch at `:138`, which checks a guard and then posts a click at `:155`. **Inference:** stale session/query results merit a regression test; the source alone does not prove an observable loop.

M3-WP2 extracts mode precedence, not this temporal behavior (`docs/ROADMAP.md:108`). Upstream PR #761 documents the unknown-subtype composition problem, and issue #766 reports an unreproduced relaunch loop: https://github.com/tanujnotes/Olauncher/pull/761 and https://github.com/tanujnotes/Olauncher/issues/766 . A small query/session token checked immediately before dispatch is a candidate, not a mandate to rewrite the adapter. Test committed input, composing input, back/home, cleared query and picker mode. Preserve intentional Latin-keyboard behavior.

### 5. Backup has a version marker, but no strict version/shape validation

`J/data/Prefs.kt:477` exports version 1. Import at `:498` checks only that the marker exists, then clears/restores excluded state and infers types at `:529`; arbitrary non-excluded keys are accepted. M3's type registration guard addresses Long/Float fidelity, not malformed files, unsupported future versions or referential validation (`docs/ROADMAP.md:109`).

Before adding more schedules, shortcut targets or profile policy, define supported versions and validate the entire payload before applying it. Preserve the current SharedPreferences file and device exclusions. Do not assume cross-device `UserHandle.toString()` identifies the same profile (`J/data/Prefs.kt:453`). Decide how unavailable targets are displayed, rather than dropping them silently. This is a migration/validation boundary, not a request for DataStore or cloud sync.

### 6. Screen-time correctness needs event fixtures before richer analytics

The event wrapper combines Android queries, foreground-process observations and aggregation (`J/helper/usageStats/EventLogWrapper.kt:31`); the guardian performs a cached 24-hour lookback (`J/helper/usageStats/UnmatchedCloseEventGuardian.kt:17`, `:30`, `:51`). M4's review card consumes this foundation (`docs/ROADMAP.md:139`). Matching the graph does not independently prove either number correct.

Keep UsageEvents, as required by trap #4 (`ARCHITECTURE.md:96`). Candidate extraction is only the event-folding portion with explicit timestamps and foreground inputs. Fixtures should cover unmatched opens/closes, reboot, overlapping activities, day boundaries and unavailable history. Compare old/new outputs before changing interpretation. Existing pure-logic tests show the preferred seam, e.g. `app/src/test/java/com/parem/launcher/helper/AppListRebuilderTest.kt:1`; no tests were executed here.

Upstream's new off-main-thread fix is mostly already represented by `J/MainViewModel.kt:310`; however `:308` gates on a persisted timestamp before obtaining a value. **Inference:** process recreation inside that minute can delay the initial display. Review that small cold-state gap independently of a broad statistics refactor.

### 7. Lock and touch contracts should stay explicit, not be generalized

Lock depends on `binding.lock.performClick()` (`J/ui/home/HomeGesturesController.kt:314`), the no-op handler (`J/ui/HomeFragment.kt:100`), and the service matching a FrameLayout's description from `event.source` (`J/helper/MyAccessibilityService.kt:21`). Removing content retrieval without changing/validating that source dependency is unsafe. M2-WP5 and M4-WP8 already own consent/revocation, so a v7 lock rewrite is not justified just by age.

The same controller forwards touch to the letter overlay and swipe detector (`J/ui/home/HomeGesturesController.kt:144`, `:198`). **Hypothesis:** residual gesture conflict needs the owner's repro, then a narrowly scoped arbitration proposal. No speculative gesture engine. Keep lifecycle guards and release cleanup (`J/ui/HomeFragment.kt:232`).

### 8. Cache invalidation is a contract for future search/profile work

`J/helper/AppListSource.kt:42` keys the raw snapshot by a package stamp and avoids caching incomplete queries. `J/helper/PackageChangeTracker.kt:37` observes package availability changes and `:60` clears icon caches. New shortcut/profile data must have an explicit invalidation path; do not assume package installation callbacks cover every visibility or shortcut change. This is primarily a review criterion for M4-WP6/WP7, not an independent generic caching project.

## Candidate work packages, conditional on the 6.0 re-audit

Costs are **guesses** in focused engineering days, excluding owner device passes, reviews and release waiting. Each implementation row also needs the repository's normal checks, changelog/handoff and append-only hot-file handling when eventually authorized. None is authorized now.

| id | Outcome | Owns | Done |
|---|---|---|---|
| C1 | **Design first.** Decide profile-scoped launch/policy identity and migration; 1–2 days | new proposal under `docs/design/`; no runtime files | Every launch surface, old backup, missing/locked profile and same-package pair has a specified outcome; names exact implementation ownership; Patric signs off |
| C2 | Search results cannot launch after their input session becomes obsolete; 2–3 days | `J/ui/AppDrawerFragment.kt`, `J/ui/AppDrawerAdapter.kt`, a small helper + JVM test, drawer instrumentation test | Composition/commit, clear, back/home and queued-result regressions pass; picker never auto-launches; actual IME pass recorded |
| C3 | Widget recovery preserves identity/order without orphan allocations; 3–5 days | `J/ui/HomeWidgetController.kt`, new reconciliation helper + tests; `MainActivity.kt` only its pending-widget/result lifecycle | Mixed valid/invalid restore preserves order; deny/cancel/rotation/process-death scenarios have no leaked allocation or dead-view update; host behavior device-verified |
| C4 | **Design first.** Specify backup validation and unsupported-version behavior; 1 day | new backup-contract proposal | Supported input versions/types, unknown-key policy, profile/shortcut remapping and failure atomicity decided; separate implementation row scoped |
| C5 | Usage-event folding has deterministic regression fixtures; 3–5 days | `J/helper/usageStats/EventLogWrapper.kt`, `UnmatchedCloseEventGuardian.kt`, new pure helper + tests | Existing behavior preserved on explicit event fixtures; expected totals and known uncertainty documented; no replacement with summary API |
| C6 | Initial screen-time value loads after process recreation; 0.5–1 day | `J/MainViewModel.kt` screen-time method + focused regression coverage | Fresh ViewModel with recent persisted timestamp obtains a value; concurrent refresh behavior verified; retain IO execution |

Priority recommendation: carry C2/C6 into the next upstream-fix review if reproduced; do not withhold correctness fixes until a marketing release. C1/C4 are cheap design decisions before expanding state. C3/C5 earn their cost only if M4 or chosen v7 features still expose these seams. If M4 already consolidates launch policy, do not create another launch refactor.

File conflicts: C2 overlaps M3-WP2 and M4-WP6 drawer work; C3 overlaps M3-WP8; C5 overlaps M4-WP4. C1's eventual implementation intersects M4-WP2/WP6/WP7 and `Prefs`; C4's implementation must follow M3-WP3 and finalized M4 key contracts. Assign sequential ownership, not simultaneous PRs on these files.

## What is not verified

No 6.0 implementation was available to audit as a completed release. No compilation, tests, benchmarks, Android emulator/device runs, accessibility-service checks, widget lifecycle repros or malformed-import experiments were performed. Source findings and hypotheses above are deliberately distinguished. This document proposes seams and acceptance evidence, not a claim that the suspected failures occurred on a device.
