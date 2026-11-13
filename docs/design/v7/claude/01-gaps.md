# What 6.0 leaves open — inputs for v7 (Claude, step 1)

> Historical planning snapshot from October 2026; statements below describe
> that baseline or proposal, not the current queue. See [HANDOFF](../../../../HANDOFF.md)
> and [ROADMAP](../../../ROADMAP.md) for current state.

Sources: `docs/ROADMAP.md` (2026-10-03), `docs/design/DECISIONS.md`,
`docs/design/M*-WP*.md`, `ARCHITECTURE.md` Traps / Known issues,
`docs/archive/TODO-pre-roadmap.md`. Nothing below is new scope; it is what the
6.0 plan explicitly pushed past itself.

## 1. Explicitly excluded from 6.0 (ROADMAP M4 header)

| Item | Why excluded then | Still true for v7? |
|---|---|---|
| Hard blocking without an override | Calm-phone philosophy: nudge, don't jail | Probably still out; but a *stricter* mode (opt-in, friction to disable) is a recurring request in this space — needs a product decision |
| Unified inbox (notification centre) | M4-WP1 only silences into a quiet list | Natural v7 follow-on once M4-WP1 data exists: grouping, per-app history, "digest at 18:00" |
| Theming studio | Scope | Still low value for a minimalist launcher; keep out unless store reviews say otherwise |
| Accounts / sync | Privacy story | Keep out. Offline export/import exists; a *file-based* backup (SAF folder, auto-export) is the privacy-compatible version |
| Compose, DataStore | Risk + cost before launch | v7 is the first point where a migration *could* be argued. Must be a design-first row with a measured payoff (APK size, startup) or it stays out |
| Any new use of the accessibility service | Play policy | Still out. Android 17 Advanced Protection revokes non-tool services (M4-WP8) — v7 should assume the lock service becomes unreliable and look for `DevicePolicyManager.lockNow` or screen-off alternatives |

## 2. Deferred inside the designs

- **M4-WP7 option B** (`docs/design/M4-WP7.md:30`): accept `ACTION_CONFIRM_PIN_SHORTCUT` so Chrome's "Add to Home screen" lands in Parem. Trigger: user reports after 6.0.
- **M4-WP3** (`DECISIONS.md:19`): blocked app opens the Focus sheet directly — out for 6.0.
- **M4-WP7** folders variant: "Add website…" in folders was the part to cut if slots-only shipped.
- **M3-WP4 / M4-WP5**: the "click inside Parem" grayscale grant chose wireless self-pairing; the WebUSB page needs GitHub Pages (owner). If 6.0 ships only the adb fallback, the full grant UX is a v7 row.
- **M3 owner task**: "pick which of M3-WP5…WP11 make the release; unpicked rows move to a later milestone." Whatever is unpicked (likely candidates: WP7 e-ink, WP8 work-profile widgets, WP10 perf budget, WP11 crash reports) is the first v7 backlog.
- **Archive out-of-scope notes**: folder sorting/icons (PAREM-101, 114), weather providers / retry (PAREM-106), gesture recognizer changes (PAREM-107, still open), picker visual redesign.

## 3. Debt that 6.0 adds on top of the existing traps

- Trap #1 (invisible lock view) gets *more* load-bearing after M2-WP5 consent + M4-WP8 revoked-detection. Three code paths depend on an accessibility quirk.
- M4-WP1 introduces a second SharedPreferences file (never exported). Export/import then has two sources of truth for "state"; M3-WP3's registration guard covers only the main file.
- M4-WP3 scheduled focus adds alarms + boot receiver; M4-WP11 audits battery once. There is no recurring battery check after launch.
- `HomeWidgetController.kt` is 749 lines and owns trap #2 (three widget-ID stores); M3-WP8 adds to it. Biggest file in the repo, no tests possible (Android-bound).
- `AppDrawerFragment.kt` (567) + `AppDrawerAdapter.kt` (401): M3-WP2 extracts the resolver but the drawer still mixes omnibox, list, work/private profiles (M4-WP6) and keyboard focus (M3-WP6).
- Android-bound code has zero automated coverage until M3-WP1 smoke tests exist; even then only four flows.

## 4. Platform and store deadlines that land in the v7 window

- **targetSdk**: Play requires new/updated apps to target the previous year's API by ~August each year. 6.0 ships at 36; v7 will need **API 37** inside its lifetime. Budget one design-first row per SDK bump (edge-to-edge and predictive back were the 36 surprises).
- **Android 17 Advanced Protection** (M4-WP8 only *detects*); v7 should decide whether double-tap lock survives at all.
- **minSdk 24 → 29** is an owner decision pending Play install share; if taken, it deletes compatibility code (list for v7).
- **Play policies**: accessibility declaration (M2-WP5), notification listener (M4-WP1), QUERY_ALL_PACKAGES (M2-WP6) — each is re-reviewed on every update. v7 should keep a `docs/PLAY_DECLARATIONS.md` so re-submissions are copy-paste.
- **F-Droid main** listing (owner task, "later") is the other channel; it forbids non-free deps and network SDKs, which constrains crash reporting (M3-WP11) and any rates/weather provider.

## 5. Launch quality bars become v7 constraints

From ROADMAP M4: crash-free ≥ 99.8%, ANR < 0.2%, battery < 1%/day, APK < 10 MB,
no RAM growth over 24 h, cold start < 300 ms (M3-WP10). Every v7 row that adds a
service, alarm, or dependency must say which bar it touches. Post-launch, the
measurement source changes from "device pass" to Play vitals + the closed-test
cohort — v7 should have a standing "vitals review" owner task per milestone.

## 6. What we do not know yet (and should not plan around)

- Real user data: Parem has no Play reviews, no vitals, no install share yet. Any
  v7 feature list written before 6.0 has ~4 weeks of closed-test data is a
  hypothesis. **Recommendation:** v7 = one milestone of "respond to launch"
  (M5, small, fixes + top-3 asks) before a themed milestone (M6).
- Which Olauncher upstream features users actually miss (Codex step 1, `02-signals.md`).
