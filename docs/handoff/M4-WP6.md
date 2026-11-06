---
wp: M4-WP6
pr: 0
commit: 27dc4ee
branch: feat/private-space
date: 2026-10-03
---
Private Space (Android 15+): with `ACCESS_HIDDEN_PROFILES` declared and Parem as default home, private apps get their own drawer section under a lock/unlock header; locked apps are dropped from the list and search, and private apps cannot be put on home slots, swipes, gestures or folders.
Contract: `AppListSource.getAppsList(includePrivate = false)` is fail-closed — only `MainViewModel` opts in, and `AppDrawerAdapter.setAppList` gates private rows out whenever `flag != FLAG_LAUNCH_APP`; `privateLocked` is applied per call, never cached; `ACTION_PROFILE_AVAILABLE`/`UNAVAILABLE` invalidate the app snapshot; private launches are not counted and home launches never resolve to the private profile (`getUserHandleFromString` skips it); "time limit" and rename are hidden on private rows.
Not verified: anything on an Android 15 device or emulator with a private space; whether `getActivityList` on a locked profile returns empty or throws; the API 36 `PRIVATE_SPACE_ENTRYPOINT_HIDDEN` setting is not honoured.
Easy to get wrong: lock state is not a package change (trap #5), so any new cache built on the app list must also key on it or an unlock serves the empty locked-time list; a slot holding a private `userString` falls back to the main-profile copy on purpose — don't "fix" it.
