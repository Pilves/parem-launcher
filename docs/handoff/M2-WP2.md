---
wp: M2-WP2
pr: 0
commit: 6e41cbe, ffe45dc, ba43ab9
branch: fix/upstream-ports-2026-10 (merged 9c8af29)
date: 2026-10-03
---
The upstream Olauncher review to `66712f7` is logged in the roadmap's recurring section with a verdict per commit, and two fixes are ported: `BaseFragment.onCreateAnimator` substitutes a zero-length Animator when any system animation scale is 0 (#713, drawer stuck open) and `isEinkDisplay()` compares the display's highest supported refresh rate, not the current one (#724, LTPO false positives).
Contract: `Context.isSystemAnimationsDisabled()` in `helper/Extensions.kt` is true when any of the three scales is 0; Home, Drawer, Settings and Onboarding fragments extend `BaseFragment`; the e-ink refresh threshold stays 10 Hz.
Not verified: on a device with animations off that the drawer now closes; `952d9e9` (pending text size) was judged not needed because Parem applies text size on tap.
Easy to get wrong: features were not ported here but mapped to rows (`33ea31e` → M3-WP6, `4c210da` → M3-WP5, `a9da9d4` → M3-WP7, `14b89e9` → M4-WP7); the next review starts from `66712f7`.
