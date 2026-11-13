---
wp: M4-WP2
pr: 0
commit: e854dc5
branch: feat/mindful-pause
date: 2026-10-03
---
Mindful pause asks why before opening a marked app and enables Open after 5 s; backing out returns home. M4-WP47 also added pause without a time limit.
Contract: pref `MINDFUL_PAUSE_APPS` (CSV string, exported; M4-WP47 permits a pause-only app); `helper/MindfulPause.decide(pauseEnabled, overLimit)` returns `Gate.LAUNCH | LIMIT_WARNING | PAUSE`; `HomeSlotsController.launchApp` gates home app routes (slots, folders, gesture letters, swipe apps, clock/calendar) and `AppDrawerFragment` gates configured apps. Web and quick-action exceptions are described below.
Not verified: the pause on every route on a device; the countdown UI.
Easy to get wrong: quick actions (M4-WP15) bypass the gate; M4-WP36 routes website shortcuts through `gateWebOpen` for the receiving browser, with an accepted unpinned chooser gap when there is no default outside focus. A new app launch that calls `startActivity` directly skips limits and the pause.
