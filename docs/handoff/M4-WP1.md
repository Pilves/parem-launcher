---
wp: M4-WP1
pr: 0
commit: f01623c, 1565639
branch: feat/filtered-notifications, chore/battery-audit
date: 2026-10-03
---
"Hide from shade, keep for later" (off by default): `helper/notifications/QuietNotificationListener` (manifest `enabled="false"` until turned on) snoozes notifications from non-allowed apps for 8 h; the quiet list is the system's snoozed set filtered to keys Parem snoozed, opened by the swipe action `GestureAction.QUIET_LIST = 7`; the rules live in `QuietFilter` with JVM tests.
Contract: no enabled pref — state is derived (component enabled + notification access) through `QuietNotificationsManager.state()`; snoozed keys live in a second prefs file `com.parem.launcher.quiet_keys.xml`, excluded from backup and extraction and never exported; never touched: ongoing, calls, alarms, media, navigation, group summaries, packages without a launcher activity, the default dialer/SMS and the cell-broadcast packages; manifest `default_filter_types = conversations|alerting`; the cheap rules run before any binder lookup (1565639).
Not verified: Play Protect on a sideloaded build with this listener (owner Gate 0); whether `cancelNotification` works on a snoozed key; survival across reboot; whether the 8 h re-snooze re-alerts.
Easy to get wrong: toggling the component must pass `DONT_KILL_APP` or the launcher process dies; double-tap never offers QUIET_LIST (filtered in the picker) yet the gesture executor must still handle value 7 for imported settings.
