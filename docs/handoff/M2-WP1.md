---
wp: M2-WP1
pr: 0
commit: ffab000
branch: feat/target-sdk-36 (merged 78a4329)
date: 2026-10-03
---
`app/build.gradle` compiles and targets API 36 (`compileSdk 36`, `targetSdkVersion 36`); nothing else changed for the bump.
Contract: `docs/design/M2-WP1.md` option A — the opt-outs stay, so edge-to-edge and predictive back are not done here; the roadmap moved them to M4-WP43 (blocks 6.0).
Not verified: every on-device item in the design's test plan (home, lock, usage stats, widgets at 36); the build was not run in the docs lane.
Easy to get wrong: `styles.xml` still sets `windowTranslucentStatus`/`Navigation`, which Android 16 ignores at targetSdk 36 — "targets 36" does not mean "handles 36" until M4-WP43 lands.
