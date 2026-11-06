---
wp: M4-WP2
pr: 0
commit: e854dc5
branch: feat/mindful-pause
date: 2026-10-03
---
Mindful pause: an app with a time limit can be marked (home slot menu, App limits sheet) so every launch of it first asks why and enables Open after 5 s; backing out returns home.
Contract: pref `MINDFUL_PAUSE_APPS` (CSV string, exported, dropped with the app's limit); `helper/MindfulPause.decide(pauseEnabled, overLimit)` returns `Gate.LAUNCH | LIMIT_WARNING | PAUSE`; one gate for every route — `HomeSlotsController.launchApp` (slots, folders, gesture letters, swipe apps, clock/calendar) and `AppDrawerFragment` (drawer, omnibox); app limits now apply on those routes too.
Not verified: the pause on every route on a device; the countdown UI.
Easy to get wrong: quick actions (M4-WP15) and website shortcuts bypass the gate on purpose because they are not app launches; any new launch path that calls `startActivity` directly skips limits and the pause.
