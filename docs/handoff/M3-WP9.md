---
wp: M3-WP9
pr: 0
commit: 4eb42cc
branch: feat/lock-home-layout
date: 2026-10-03
---
Home Screen settings has "Lock home layout" (`Prefs.homeLayoutLocked`, key `HOME_LAYOUT_LOCKED`, default false); while on, long-pressing a home slot or folder shows a toast (`home_layout_locked`) instead of the change/folder/limit/delete menu, and empty slots' hint and contentDescription say the layout is locked.
Contract: the guard sits at the top of `HomeSlotsController`'s long-press handler before any menu is built; exported like any Boolean pref.
Not verified: on a device that no other route (drawer "Change app", rename) can still edit a locked slot.
Easy to get wrong: the lock covers slot long-press only — widgets, the drawer and settings still change the home layout.
