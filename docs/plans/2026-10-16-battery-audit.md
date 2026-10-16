# Battery + wake-lock audit (M4-WP11)

Read-only audit of every background and periodic path at `next` 504f17b
(v6.0.0-beta.1), on top of the first pass in `chore/battery-audit` 1565639
(quiet-filter binder lookups made lazy; soak procedure added to the release
checklist). No device: costs below are read from code, the owner's 24 h soak
is the measurement.

## Summary

- No `WakeLock`, `AlarmManager`, `JobScheduler` or foreground service of
  Parem's own anywhere in `app/src/main` (grep, including the manifest).
- Three WorkManager jobs, all uniquely named: theme schedule (periodic 3 h,
  battery-not-low, `REPLACE` but only from the Settings toggle), daily
  wallpaper (periodic 8 h, network + battery-not-low, linear 1 h backoff,
  gives up after 3 retries, `KEEP`), grayscale focus-end (one-shot, `REPLACE`).
- Nothing in the process ticks while Parem is home and visible except the
  system's `ACTION_TIME_TICK` (once a minute, receiver registered only while
  the home fragment is resumed).
- The only thing that ticks while Parem is *not* visible is the per-app
  grayscale watch, and only while a marked app exists and the grant, usage
  access and default-home are all in place.

## Findings

| # | Path | file:line | What it costs | Verdict |
|---|---|---|---|---|
| 1 | Per-app grayscale watch tick | `helper/GrayscaleController.kt` `startAppWatch` | 1 s main-thread `postDelayed` while Parem is stopped and an app is marked. Screen on: `isInteractive` + `queryEvents` (2 binder calls) per tick = 7200/h, in a process already awake. Screen off: `isInteractive` only, 3600 binder calls/h until the device dozes (the handler clock is uptime, so deep sleep stops it). No wake lock. | **Fixed**: tick is 5 s with the screen off (`GrayscalePolicy.watchTickMs`, JVM test). The first tick after screen-on still replays every event since the last one, so no switch is missed; worst case colour lags 5 s after an unlock that lands in a different app. |
| 2 | Home clock refresh receiver | `ui/home/HomeClockController.kt:69` | `ACTION_BATTERY_CHANGED` is a sticky broadcast that fires on every level/voltage/temperature step, seconds apart while charging. Each delivery re-ran `populateDateTime()`: a sticky-intent binder query plus a date relayout, even with the status bar on, when the battery % is not drawn. | **Fixed**: the action is added to the filter only when `!prefs.showStatusBar`. Cheap because the pref is written only from Settings, under which home is paused and re-registers on resume. |
| 3 | Quiet-notification filter per event | `helper/notifications/QuietNotificationListener.kt:86` | `getAllowed()` split the allowlist pref into a new `Set` on every posted notification, before the cheap rules ran; an ongoing notification reposting every second paid it every second. | **Fixed**: the allowlist is a lambda like the two binder lookups, checked after the cheap rules (`QuietFilter.shouldSilence`, test updated). |
| 4 | Weather fetch | `MainViewModel.getWeather`, `helper/WeatherManager.fetchWeather` | Only from `HomeFragment.onResume`. In-memory 30 min gate, then a prefs gate of 1 h (`FETCH_INTERVAL_MS`), 10 min backoff after a failure, 10 s / 15 s timeouts, one GET. Never runs with the screen off. | OK. The release checklist said "every 30 min"; corrected to 1 h. |
| 5 | Currency rates | `helper/CurrencyRates.kt:43` | One GET per 24 h, 10 min backoff, 10 s timeouts, only on the first currency query of a drawer session. | OK |
| 6 | Daily wallpaper | `MainViewModel.setWallpaperWorker`, `helper/WallpaperWorker.kt` | Periodic 8 h, `CONNECTED` + battery-not-low, linear backoff 1 h, `runAttemptCount > 3` gives up, `KEEP` so the config-change re-enqueue is a no-op. Two GETs per run (list + image) with 10/15 s timeouts. | OK |
| 7 | Theme schedule | `helper/ThemeScheduleManager.startWorker`, `ThemeScheduleWorker` | Periodic 3 h, battery-not-low, no network. `REPLACE` resets the period and runs once immediately, but it is called only when the user picks a schedule mode. Work per run: prefs reads + a sunrise calculation. | OK. Aside (not battery): nothing re-evaluates `shouldBeDark` on home resume, so a scheduled switch can be up to 3 h late. |
| 8 | Scheduled focus | `helper/FocusModeManager.checkAndExpire`, `helper/FocusSchedule` | Evaluated on home resume and on each launch check; no alarm, receiver or job (M4-WP3 by design). | OK |
| 9 | Grayscale focus-end job | `helper/GrayscaleController.scheduleFocusEnd` | One-shot `OneTimeWorkRequest`, `REPLACE` under one unique name, only when a focus window has an end and grayscale-on-focus is on. One `reconcile()` per run. | OK |
| 10 | Notification listener binding | `AndroidManifest.xml` `QuietNotificationListener` | `enabled="false"` until the user turns it on, `default_filter_types=conversations|alerting` so silent/ongoing posts never reach the process on API 31+. `onListenerConnected` reads the snoozed list once per bind. | OK |
| 11 | Accessibility service | `res/xml/accessibility_service_config.xml`, `helper/MyAccessibilityService.kt` | `typeViewClicked` only, `packageNames` = Parem only, no window-content retrieval, 10 ms timeout. Per event: two string compares. | OK |
| 12 | Package-change tracking | `helper/PackageChangeTracker.kt` | One `LauncherApps.Callback` for the ViewModel's lifetime; a change bumps a stamp and clears two icon caches, nothing rebuilds eagerly. Shortcut changes get their own stamp so chat apps' dynamic shortcuts do not drop icons. | OK |
| 13 | Profile receiver | `MainViewModel.registerProfileReceiver` | Runtime receiver (API 35+) for `PROFILE_AVAILABLE/UNAVAILABLE`, rare; rebuilds the app list when it fires. | OK |
| 14 | Screen-time query | `MainViewModel.getTodaysScreenTime`, `helper/UsageStatsHelper.kt` | Full event-log scan since local midnight (plus the guardian's 24 h look-back, cached per `queryStart`), on `Dispatchers.IO`, gated by a 1 min prefs stamp and a 60 s TTL shared with limit checks and the drawer. Only on home resume / drawer open / a limited-app tap. | OK for the soak. Deferred: incremental scan from the last event instead of midnight (saves CPU on heavy-use days, no battery change with the screen off). |
| 15 | Home resume work | `ui/HomeFragment.onResume` | `isParemDefault` (IO thread), `checkAndExpire` (prefs), `GrayscaleController.reconcile` (two `Settings.Secure` reads), `getWeather` (gated). All one-shot per resume. | OK |
| 16 | `checkTheme` | `MainActivity.kt:371` | One 200 ms coroutine delay per `onStart`, at most two `recreate()`s. | OK |
| 17 | Dialog/sheet timers | `ui/GrayscaleSheet.kt:125`, `ui/BadHabitDialogs.kt:180` | Poll only while the sheet/dialog is shown; removed on pause/dismiss. | OK |
| 18 | `ACTION_TIME_TICK` | `ui/home/HomeClockController.kt:71` | 60 deliveries/h while home is resumed (the system stops ticking with the screen off; the receiver is gone once home pauses). `populateDateTime` allocates a `DateTimeFormatter` per call. | OK. Deferred: cache the formatter (microseconds, not battery). |

Fixed: 1, 2, 3. Deferred: 14 (incremental usage scan), 18 (formatter cache),
7 aside (theme re-check on resume, a correctness item for the owner to
schedule). Everything else holds as the checklist describes.

## Soak

The commands and pass criteria live in `docs/RELEASE_CHECKLIST.md` under
"Battery soak (24 h)". In short:

```
adb shell dumpsys batterystats --reset
# 24 h unplugged: night with the screen off, 30 min in a grayscale-marked app,
# a focus window ending while another app is in front
adb shell dumpsys batterystats --charged com.parem.launcher > soak.txt
adb shell dumpsys package com.parem.launcher | grep userId   # uid for the lines below
adb shell dumpsys alarm | grep com.parem                     # expect nothing
adb shell dumpsys power | grep -i parem                      # expect no held wake lock
adb shell dumpsys jobscheduler | grep -A3 com.parem.launcher # the three WorkManager jobs only
```

Pass: Parem's uid mAh ÷ `Capacity` < 1 %; wake locks only
`*job*/…SystemJobService`, under a minute total; no `*alarm*` wakeups; no
foreground-service time.
