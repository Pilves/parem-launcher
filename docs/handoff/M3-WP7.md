---
wp: M3-WP7
pr: 0
commit: 2079349
branch: feat/eink-mode
date: 2026-10-03
---
`Context.skipAnimations()` (e-ink, or any system animation scale at 0) is the single gate for fragment transitions, the drawer layout animation, `animateAlpha` and every bottom sheet's window slide (`BottomSheetDialog.disableAnimationsOnEink()`); `helper/EinkDetector` recognises e-ink by brand (Onyx/Boox, Dasung, Bigme, Boyue, Meebook, Mudita, Hisense e-ink line), the Onyx SDK class probe or max refresh rate, cached per process.
Contract: the forced light theme on e-ink applies only while `Prefs.hasAppTheme` is false (a first-run default), so a chosen dark theme sticks; the drawer drops its overscroll glow on e-ink; ARCHITECTURE.md records that new sheets call `disableAnimationsOnEink()`.
Not verified: anything on an e-ink device (decision: ship with the e-ink paths unverified); whether `setWindowAnimations(0)` removes all BottomSheet motion; the brand list is upstream `a9da9d4`'s, not checked against device dumps.
Easy to get wrong: a new sheet that forgets `disableAnimationsOnEink()` slides again and only the convention line catches it; `ThemeScheduleWorker` can still switch an e-ink user to dark on schedule (existing behaviour, left alone).
