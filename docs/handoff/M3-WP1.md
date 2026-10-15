---
wp: M3-WP1
pr: 0
commit: 7f74ebe
branch: ci/smoke-tests
date: 2026-10-03
---
`.github/workflows/smoke.yml` runs instrumented tests on a CI emulator (`pull_request` to master and `workflow_dispatch`): `SmokeTest` (launcher is home, swipe-up opens the drawer, omnibox launches an app, rotates on large screens only — the last added by M4-WP13) and `PrefsRoundTripTest` (export/import keeps values and types), using UiAutomator, the test orchestrator and `clearPackageData`.
Contract: tests reseed prefs in `@Before` because the orchestrator wipes them; selectors go through `getResourceName` so the debug suffix doesn't matter; the matrix is `pixel_6`/API 34 (perf leg), `pixel_fold`/34, `pixel_tablet`/34 and `pixel_tablet`/36; no retries on purpose.
Not verified: run time per leg and CI minutes; whether the current matrix has gone green on GitHub (designed and written, not watched, from this lane).
Easy to get wrong: no test may double-tap (the accessibility service is not enabled on the emulator) or assert on `WIDGET_*` keys (export-excluded, trap #2); never grab views right after HOME — `checkTheme()` may `recreate()` twice at start.
