# M4-WP3 — Scheduled focus (design proposal)

Status: proposal, revision 2 (manager review addressed), awaiting Patric's sign-off. Branch base: `origin/chore/agent-harness`. No code changed, no gradle run.

## Problem

Focus mode only runs when you start it by hand, either for a set time or until you turn it off. The user wants it to switch on by itself on weekdays at set times, such as work 09–17 on Mon–Fri or sleep 22–07 every day. It should use the same whitelist and the same blocking.

Today, blocking is checked when an app is opened, not pushed. `MainViewModel.selectedApp` calls `FocusModeManager.isAppAllowed` on every `FLAG_LAUNCH_APP`. `isActive` reads two prefs and compares them with `System.currentTimeMillis()`. `HomeFragment.onResume` only tidies up with `checkAndExpire`. Nothing has to happen at the moment a session ends, so a schedule works the same way.

## Options

**A. Compute it at check time (no alarms).** `isActive`/`isAppAllowed` also ask whether the current local time falls inside a configured window. Because the answer comes from the clock at the moment of the check, it is exact to the minute. Screen off, Doze and reboot do not affect it, since nothing can be launched while the screen is off. Cost: nothing *happens* at a window boundary, so there is no notification and no side effect.

**B. Inexact alarms (`setWindow`/`setAndAllowWhileIdle`) that flip a stored flag.** On Android 12+, windows shorter than 10 minutes are "typically clipped to 600000" ms. Standard alarms are deferred to the next Doze maintenance window, and the while-idle variants fire at most once per 9 minutes per app. Alarms are also cancelled on reboot, so this needs `RECEIVE_BOOT_COMPLETED` and a receiver ([alarms](https://developer.android.com/develop/background-work/services/alarms/schedule), [Doze](https://developer.android.com/training/monitoring-device-state/doze-standby)). This misses the roadmap's ±1 min target, and the stored flag can drift away from the clock.

**C. Exact alarms.** `SCHEDULE_EXACT_ALARM` is not pre-granted on fresh installs that target 33+ ([Android 14 change](https://developer.android.com/about/versions/14/changes/schedule-exact-alarms)), so it would need a grant flow in Settings. `USE_EXACT_ALARM` is a restricted Play permission for alarm-clock and calendar apps only ([Play policy](https://support.google.com/googleplay/android-developer/answer/16558241)), so a launcher would be rejected. This adds Play risk and a permission flow, and buys nothing over A.

## Recommendation

**A.** It has no alarms, no new permissions, no receivers and no manifest change. Window state is never stored; it comes from the clock every time. If a later package needs a side effect at a boundary (M4-WP5 grayscale "during focus", M4-WP1 silencing), that package adds its own trigger on top of the same maths. Building alarms now would be speculative.

Rules:
- **Window:** a set of weekdays plus a start and end in local minutes-of-day. If `end <= start`, the window crosses midnight and belongs to the day it *starts* on. For example, Mon 22:00–07:00 runs until Tue 07:00. `start == end` means a full 24 h window (Sat 00:00–00:00 = all of Saturday); it is accepted by `parse`, not rejected, because "all day Saturday" is a real use.
- **Overlap:** focus is active if *any* window contains now (a union). A timed session and the schedule are also combined as a union. When windows overlap or chain into each other, the displayed end is the end of the whole merged run.
- **Run end is bounded:** windows can chain forever (days=127 with 00:00–00:00, or 22–07 plus 07–22 every day). `activeUntil` only looks 7 days ahead: it expands windows into concrete intervals for day offsets −1 (yesterday, for cross-midnight windows) through 7, which is 8 iterations of today plus the next 7 days. It merges them and returns the end of the merged interval that contains now. If that interval reaches the end of the horizon, it returns the sentinel `NO_END`. The loop is over a fixed day range, so it always terminates.
- **Override** (the roadmap rules out hard blocking without an override): the *user-facing* "Disable" button during a scheduled window clears any timed session and sets `skipUntil` to the end of the current run. The next window applies as normal. If the run is `NO_END`, `skipUntil` is the end of the single window that contains now and ends latest, so an always-on schedule pauses for at most 24 h, not a week. Only the button sets `skipUntil`. Automatic expiry of a timed session (`checkAndExpire`) never does (see Exact scope).
- **Empty whitelist:** a schedule with no allowed apps blocks everything except the dialer for the whole window. This is allowed, because it is a legitimate "phone only" sleep setup, but it must not be a surprise and the override must be reachable from the point where an app is blocked (see Exact scope: warning line + toast).
- **DST:** windows are evaluated on wall-clock time, so the result follows the phone's displayed time. Spring-forward: a start time inside the skipped hour begins at the first minute that exists. Fall-back: a window ending inside the repeated hour is active again during the repeat, so it ends one real hour later. Both cases are documented and tested. The UI shows "until 07:00" (wall clock), not a countdown, so DST cannot make the display wrong.
- **Time APIs:** use `java.util.Calendar`, not `java.time`, because minSdk is 24 and core-library desugaring is not enabled (see Risks).

## Exact scope

- **New `helper/FocusSchedule.kt`** (Android-free object):
  - `data class Window(days: Int /*bit0=Mon..bit6=Sun*/, startMin: Int, endMin: Int)`
  - `parse(String): List<Window>` / `serialize(List<Window>): String`. Format: `"31,540,1020;127,1320,420"`. Malformed entries are dropped.
  - `isActive(windows, dayOfWeek, minuteOfDay): Boolean`. This also checks yesterday's windows that cross midnight.
  - `activeUntil(windows, dayOfWeek, minuteOfDay): Int?` returns the end of the merged run as minutes from today's 00:00 (so `1860` = tomorrow 07:00). It returns `null` when no window contains now, and `NO_END` (`-1`) when the run reaches the 7-day horizon (see Rules).
  - `currentWindowEnd(windows, dayOfWeek, minuteOfDay): Int?` is the latest end among the single windows that contain now, in the same units. It is used only for the `NO_END` override.
- **`helper/FocusModeManager.kt`:**
  - one private `activeNow(context, prefs)` used by both `isActive` and `isAppAllowed`. This removes their duplicated end-time logic, which currently differs (`<` vs `>`).
  - `getSchedule`/`setSchedule`, `isScheduleEnabled`/`setScheduleEnabled`.
  - `activeNow` = timed session active **or** (schedule enabled **and** a window contains now **and** now ≥ `skipUntil`).
  - `disable` is unchanged: it only clears `KEY_ENABLED`/`KEY_END_TIME`. It never touches `skipUntil`.
  - new `pauseSchedule(context)` sets `skipUntil` to the end of the current run (or `currentWindowEnd` when the run is `NO_END`). It does nothing when no window is active. Only the dialog's Disable button (`FocusModeDialog.kt:95`) calls it, together with `disable`.
  - `checkAndExpire` stays as it is. It calls `disable`, which no longer has an override side effect, so a timed session that ends inside a window leaves the schedule running. This split is load-bearing: if `disable` set `skipUntil`, `HomeFragment.onResume` (`ui/HomeFragment.kt:93`) would quietly pause the schedule every time a timed session expired mid-window.
  - `getActiveLabel(context): ActiveLabel?` with `Timed(remaining)`, `ScheduledUntil(epochMs)`, `ScheduledNoEnd` and `Unlimited`, chosen as described for the active view below.
- **`ui/FocusModeDialog.kt`:**
  - inactive view: a "Schedule" section with an on/off row, one row per window (tap to edit, long-press to remove), and "Add window". The editor has day toggles plus two `android.app.TimePickerDialog`s (platform, no new dependency).
  - a "Save" row so the schedule and whitelist can be stored without starting a timed session.
  - inactive view, empty whitelist: while the schedule toggle is on and no apps are allowed, show one muted line under the Schedule section: "No apps allowed: only calls work during scheduled focus."
  - active view: one status line, replacing the current `remaining ?: focus_until_disabled_status` fallback (`FocusModeDialog.kt:84-87`), which would otherwise show "Until manually disabled" for every schedule-only session:
    - schedule only (no timed session, or `KEY_ENABLED` false): "Scheduled until %s" only.
    - timed session + schedule: whichever ends later, matching the union rule. If the timed end is later it shows "Remaining: %s", otherwise "Scheduled until %s".
    - schedule run is `NO_END`: "Scheduled, no end in the next 7 days".
    - "Until manually disabled" appears only when an unlimited manual session (`KEY_ENABLED` true, `END_TIME_UNLIMITED`) is actually running. It wins over any schedule, because that session has no end.
    - `%s` is `HH:mm` (12/24 h per system setting) when the end is within 24 h, and the short weekday plus time otherwise (for example "Tue 07:00").
- **`MainViewModel.selectedApp` block toast** (`MainViewModel.kt:93`): when the block comes from the schedule rather than a timed session, the toast names the end and the override: "Focus scheduled until 07:00. Pause it in Settings › Digital Wellbeing › Focus mode." Timed and unlimited sessions keep the existing `app_blocked_focus` text.
- **Prefs** (keys owned by `FocusModeManager`):
  - `FOCUS_SCHEDULE` (String) and `FOCUS_SCHEDULE_ENABLED` (Boolean) are exported. Strings and booleans import correctly by JSON type, so they need no registration.
  - `FOCUS_SCHEDULE_SKIP_UNTIL` (Long, epoch ms) goes into `Prefs.exportExcludeKeys` next to `FOCUS_MODE_END_TIME`, because it is device-moment state.
- **Strings** (all `translatable="false"`): `focus_schedule`, `focus_schedule_add`, `focus_schedule_until`, `focus_schedule_no_end`, `focus_schedule_empty_whitelist`, `app_blocked_focus_scheduled`, `focus_save`, and the weekday short labels. If the platform's short weekday names are good enough, use them instead of adding labels.
- **Manifest:** no change. **CHANGELOG:** one `[Unreleased]` line.

## Done criteria

- The roadmap test: set a window starting 2 min ahead and turn the screen off past the start. After unlocking, a non-whitelisted app is blocked. After the end minute it launches, and the settings toggle reads Off.
- Disable during a window allows apps until the run ends. The next window blocks again.
- A timed session that expires inside a window does not pause the schedule (see device plan).
- Schedule and enabled flag survive export → import. `SKIP_UNTIL` is absent from the exported JSON, and a value set before import is still there, unchanged, after import (the excluded-keys preserve path, `Prefs.kt:509-520`).
- A schedule-only session never shows "Until manually disabled".
- `testDebugUnitTest`, `compileDebugKotlin` and `assembleDebug` exit 0.

## Risks & traps

- **Prefs export convention (ARCHITECTURE "Conventions"):** the one new `getLong` key must be in `exportExcludeKeys`. Nothing on this branch checks that automatically: the M3-WP3 registration guard is still a roadmap item (`ROADMAP.md:109`), and `app/src/test` has no Prefs test. M3-WP3 is a listed M4 prerequisite (`ROADMAP.md:129`). If it lands first, it covers this key. Either way, the export/import assertion in Done criteria and the device plan is the check for this package. `Prefs` import needs a real `SharedPreferences`, so it is a device check, not a JVM one (`app/src/test` is pure logic only).
- **Fragment async rule (CLAUDE.md):** `TimePickerDialog` callbacks must check `isShowing` before touching the sheet.
- **Traps #1–#5** (lock view, widget IDs, slot flags, usageStats, package stamp) do not apply. Because the state lives in prefs and is computed fresh each time, it survives the periodic self-recreate. A whitelisted package that gets uninstalled stays harmless, as today.
- **Aside, not fixed here:** four main-source files import `java.time` with minSdk 24 and no desugaring: `helper/ThemeScheduleManager.kt`, `helper/AppOpenCounter.kt`, `helper/SunriseSunsetCalculator.kt` and `ui/home/HomeClockController.kt` (`LocalDate`/`DateTimeFormatter`, lines 26–27). `HomeClockController` runs on every home render, so on Android 7.x (API 24/25) the likely failure is the home screen itself, not only a settings feature. This belongs to the minSdk 24→29 owner decision, and that decision should use this full list. It does not block WP3, which uses `Calendar`.
- `isAppAllowed` reads the clock on every launch. That is a single `Calendar.getInstance()`, which is negligible.

## Test plan

**JVM** (`FocusScheduleTest`):
- parse/serialize round trip, plus garbage input
- inside, outside and on the exact start and end minutes
- windows crossing midnight, including Sunday→Monday
- overlap union and a chained `activeUntil`
- `start == end` is a full 24 h window: active at start, active at start − 1 min the next day, inactive at the start minute the next day unless that day is also set
- always-on runs return `NO_END` and terminate: days=127 00:00–00:00, and 22:00–07:00 + 07:00–22:00 every day
- a run that ends exactly at the horizon edge vs one minute before it (the boundary of `NO_END`)
- `currentWindowEnd` for the always-on cases (the override bound)
- an empty day mask
- DST: a start inside the spring-forward gap and the repeated fall-back hour. These are tested via a `Calendar`-to-(day, minute) adapter under `TimeZone.getTimeZone("Europe/Tallinn")` on 2026-03-29 and 2026-10-25.

**Device:**
- the done criteria above
- timed session expires mid-window: with a window active, start a 2-minute timed session, wait past its end, return to home (`checkAndExpire` runs in `onResume`). A non-whitelisted app is still blocked and the sheet reads "Scheduled until …". This is the regression case for the `disable`/`pauseSchedule` split.
- schedule-only window: the active sheet shows only "Scheduled until …"; with an unlimited manual session on top, it shows only "Until manually disabled"
- empty whitelist + schedule on: warning line in the sheet; blocked launch shows the scheduled toast naming the end and the Settings path
- export/import: `FOCUS_SCHEDULE_SKIP_UNTIL` absent from the exported file; set it (Disable during a window), import a file, still paused until the same time
- reboot inside a window (still blocked, no setup needed)
- a manual timezone change
- the dialer is still allowed

## Not verified

- On-device behaviour: nothing has been built.
- Whether `java.time` on API 24/25 really crashes, or is guarded somewhere I didn't find.
- The exact Play policy wording for `USE_EXACT_ALARM`. It is taken from the Play Console Help page linked above via search summary; a full page fetch was not done.
- Whether WP5 grayscale needs a boundary trigger at all. Applying it on unlock or home resume may be enough.

## Review notes (revision 2)

All six issues were accepted. Where an issue offered a choice, this is what was picked and why:
- **Override split:** I picked a separate `pauseSchedule` that only the button calls, so `disable` and `checkAndExpire` stay byte-for-byte as they are now. This is a smaller diff than having `checkAndExpire` write the prefs itself, and nothing else can reach the override by accident.
- **`start == end`:** I made it mean 24 h instead of rejecting it in `parse`. Rejecting it would silently drop a stored "all day" window, which is worse than the edge case.
- **Empty whitelist:** I did both the sheet warning and the scheduled toast. The warning prevents the surprise. The toast is the one that puts the override where the user gets blocked, which is what the roadmap's override rule needs. Together they add one TextView and one string choice.
- **Horizon:** I bounded it by days (offsets −1…7) rather than by hop count. A hop cap would end a chain of many short windows after a few hours, while a day range terminates trivially whatever the window count is.
- **Pref guard test:** I could not add it as a JVM test, because `Prefs` import needs Android `SharedPreferences` and `app/src/test` is pure logic. It is a device assertion now, with M3-WP3 named as the eventual automatic guard.
