# Release checklist

The device pass Patric runs before tagging `v*`. Agents prepare the release;
nobody tags until the applicable listed device checks pass on real devices and the candidate CI checks pass. Copy this file's
checklists into the release PR or issue and tick them there — don't edit this
file to record a pass.

A failure becomes a roadmap row (`M<n>-WP<k>`, one per finding) and blocks the
tag until it is fixed and the affected section is re-run.

Run `.github/workflows/smoke.yml` (Actions → Smoke tests → Run workflow) on
the candidate commit first. It covers what is no longer listed here: Parem
resolves as HOME, swipe up opens the drawer, typing an app name in the drawer
launches it, and export/import keeps one pref of each type (long, float,
int, string, boolean, string set) intact and leaves excluded keys local.

## Setup

- [ ] Release-candidate APK installed over the previous release (not a fresh
      install), so prefs migration is exercised
- [ ] Parem is the default launcher; accessibility service enabled
- [ ] At least one folder, two widgets and one app limit already configured
- [ ] Phone in portrait; tablet (or a large-screen device) at hand for the
      landscape sections

## Core — every release

### 1. Home gestures

- [ ] Swipe down runs its configured action
- [ ] Swipe left / right launch their apps; disabled ones do nothing
- [ ] Long press on empty home: haptic tick, menu with Add widget and Settings
- [ ] Draw each mapped gesture letter — the right app launches; a fast letter
      does not also fire a swipe
- [ ] Flashlight action toggles the torch, and stays in sync after toggling it
      from quick settings

### 2. Double-tap lock (trap #1)

- [ ] Double tap locks the screen (Android 9+, accessibility service on)
- [ ] Service off, device admin on: double tap locks, next unlock asks for PIN (fingerprint refused)
- [ ] Gestures "turn off" row deactivates the admin; uninstall then needs no extra step
- [ ] With the admin active, uninstall is blocked until it is turned off, and the system screen says why
- [ ] Service off, admin off: first explanation offers the admin option, later taps toast and point to Settings › Gestures
- [ ] Other double-tap actions (app, none) do what they say

### 3. Slots, menus, folders

- [ ] Tap each filled slot — the app launches
- [ ] Long press a slot — change app, create folder, set/remove time limit
      and delete all work
- [ ] Open a folder, launch from it, edit it, delete it
- [ ] A slot with an exhausted app limit shows the limit warning, not the app

### 4. Clock, date, screen time

- [ ] Tap clock → clock app; long press → pick a different clock app
- [ ] Tap date → calendar app; long press → pick a different calendar app
- [ ] Tap the screen-time row → screen-time view; numbers look plausible
- [ ] Weather shows, dims when stale (3–24 h), hides past 24 h

### 5. Widgets — portrait and landscape

- [ ] Add a widget (bind + configure screens complete and return home)
- [ ] Resize it; reorder two widgets; remove one
- [ ] Rotate: widgets survive, landscape widgets start just below the clock
- [ ] Reboot or force-stop: widgets come back (no blank boxes)
- [ ] Home apps that don't fit beside widgets are never silently hidden

### 6. Capacity-capped apps picker

- [ ] Settings → number of home apps: counts that don't fit beside current
      widgets are dimmed and rejected with a hint
- [ ] Remove a widget — a previously rejected count becomes selectable

### 7. Settings walk

- [ ] Open every settings card and exercise each row's intended action; nothing
      crashes, and home-affecting changes apply without a restart
- [ ] Theme switch / scheduled theme applies without a restart loop
- [ ] Back from settings returns home; Home button from any sub-screen resets
      to home

### 8. Export / import

- [ ] Export settings to a file
- [ ] Change slots, gestures and an app limit; import the file
- [ ] Everything restores, including app limits — enforced immediately,
      without reopening settings

### 9. Contact search

- [ ] Toggle on → permission prompt; grant → a typed contact name shows a
      call row below the apps
- [ ] Toggle on, deny → toggle stays off, no contact rows, no re-prompt
- [ ] Revoke Contacts in system settings → toggle shows off on next visit
- [ ] Toggle off → no contact rows; no other screen ever asks for Contacts

### 10. Tablet landscape

- [ ] Widgets sit in the right-hand column, apps on the left
- [ ] Gesture letters work in landscape
- [ ] Rotating back to portrait restores the stacked layout

### 11. Sheet scrolling

- [ ] Tall sheets (screen time, app limits, focus mode, folder creation) open
      fully expanded and scroll on a short/landscape screen
- [ ] Long-press menus in the drawer and on slots reach their last row

## Performance

The smoke workflow also runs `scripts/perf-budget.mjs` on the emulator and
fails when a gated metric is more than 20% (and more than its noise floor)
worse than the pinned reference (996ef0c), measured on the same emulator after
functional tests warm Android. Both APKs use the current scroll driver. The
reference and candidate numbers, and screenshots of each frame phase, are in
its `perf-result` artifact and job summary. Invalid measurements fail the gate.
`scripts/perf-baseline.json` remains the historical default for manual runs;
CI passes its same-runner reference with `--baseline`.

Emulator baseline (CI, debug APK, 2026-10-03, medians of four runs on the
same app code; the spread also covers a fifth run, which passed the gate,
and 10 samples per start metric). These are regression
references, not quality numbers: a software-rendered emulator is no phone.

| metric | baseline | spread | gated (floor) | budget, mid-range phone |
|---|---|---|---|---|
| cold start to first home frame (`am start -W`) | 724 ms | 691–770 | yes (10 ms) | < 300 ms |
| return home from Settings | 49.5 ms | 45.5–53.5 | yes (10 ms) | < 100 ms |
| drawer scroll, janky frames | 8.4% of 286 | 8.4–8.7% | yes (5 points) | < 1% |
| drawer scroll, worst frame | 150 ms | — | no | ≤ 32 ms |
| omnibox typing, janky frames | 55.8% of ~118 | 50–62.7% | yes (5 points) | < 1% |
| omnibox typing, worst frame | 150 ms | — | no | ≤ 32 ms |

The table above is historical: its runner state and scroll workload differ
from the current driver. Do not overwrite it to make a failing run pass. A
change to the pinned reference or regression thresholds needs explicit evidence
and review; paired CI measurements keep runner drift out of that decision.

Budgets are checked on a device, release build:

- [ ] Mid-range phone, release APK installed and set up as home, USB
      debugging on: `node scripts/perf-budget.mjs --package com.parem.launcher`
      — cold start < 300 ms, return home < 100 ms, < 1% janky frames, no
      frame > 32 ms. Ignore its REGRESSION lines (the baseline is the
      emulator); record the numbers in the release PR

Device numbers (owner's pass; the script's `--out` JSON has the same keys).
Record the release build, not debug: debug has no R8 and StrictMode logging.

| metric | tablet (release, `adb` over Tailscale) | phone (release) | budget |
|---|---|---|---|
| cold start | 181 ms (median of 10 `Displayed` samples) | 175 ms | < 300 ms |
| return home from Settings | 56 ms (one sample) | 53 ms | < 100 ms |
| drawer scroll, janky frames | _ % | _ % | < 1% |
| drawer scroll, worst frame | _ ms | _ ms | ≤ 32 ms |
| omnibox typing, janky frames | _ % | _ % | < 1% |
| omnibox typing, worst frame | _ ms | _ ms | ≤ 32 ms |
| release APK on disk | _ MB | — | see the perf audit |

The same numbers by hand, when the script cannot run (e.g. a build that is
not past onboarding). `P=com.parem.launcher`; the release APK is the one
under test, with Parem set as home and the screen unlocked:

```
adb connect <tablet>:5555                      # Tailscale IP from the test-tablet note
adb shell cmd package set-home-activity $P/com.parem.launcher.MainActivity
# cold start: repeat 10x, take the median TotalTime
adb shell am force-stop $P
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME -n $P/com.parem.launcher.MainActivity
# return home: open Settings, then press home; TotalTime of the second line
adb shell am start -W -a android.settings.SETTINGS
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.HOME
# frames: reset, do the gesture (swipe the drawer up/down ~10 times, or type
# a few letters in the omnibox), then read the summary
adb shell dumpsys gfxinfo $P reset
adb shell dumpsys gfxinfo $P | sed -n '/Janky frames/p;/90th/p;/99th/p'
adb shell dumpsys gfxinfo $P framestats | awk -F, 'NR>1 && $1==0 {print ($14-$2)/1e6}' | sort -n | tail -1   # worst frame ms
# debug build only: main-thread disk/network that StrictMode caught
adb logcat -d -s StrictMode
```

## Battery soak (24 h)

What the code holds (M4-WP11 audit, 2026-10-03, re-read 2026-10-16 in
`docs/plans/2026-10-16-battery-audit.md`), so anything else in the numbers
is a bug:

- No `WAKE_LOCK` use of Parem's own, no `AlarmManager`, no foreground service.
  The only wake locks are WorkManager's while a job runs: scheduled theme
  (every 3 h, battery not low), daily wallpaper (every 8 h, network + battery
  not low) and the one-shot grayscale focus-end job.
- Weather and scheduled focus are evaluated only when home resumes (weather
  at most once an hour, 10 min backoff after a failure). Nothing polls them.
- Quiet notifications: the listener is disabled until the user turns it on;
  per notification it does cheap checks first and binder lookups only for a
  candidate it might hide.
- Per-app grayscale (M4-WP21): a 1 s main-thread tick while Parem is stopped
  and an app is marked. With the screen off it skips the query, slows to one
  tick per 5 s and holds no wake lock; its handler clock stops while the
  device sleeps.
- Home's clock receiver (`TIME_TICK`, date/timezone, and `BATTERY_CHANGED`
  only when the battery % is drawn) exists only while home is resumed.

Setup: release APK, Parem default home, and every background feature on —
weather, scheduled or sunrise theme, daily wallpaper, a focus schedule with
"grayscale during focus", quiet notifications, one grayscale-marked app, one
app limit. Charge to full. Keep the phone unplugged for the whole run: USB
charging resets the stats, so use wireless adb (or
`adb shell dumpsys battery unplug`, and `dumpsys battery reset` afterwards).

- [ ] `adb shell dumpsys batterystats --reset`, then 24 h of ordinary use
      including: 30 min in the grayscale-marked app, a focus window that ends
      while another app is in front, and a night with the screen off
- [ ] `adb shell dumpsys batterystats --charged com.parem.launcher > soak.txt`
      (find Parem's uid with `adb shell dumpsys package com.parem.launcher | grep userId`)
- [ ] Estimated power use: Parem's uid line (mAh) ÷ the `Capacity` figure
      < 1% for the day
- [ ] Wake locks for Parem's uid: only `*job*/…SystemJobService` entries,
      under a minute in total; no `*alarm*` wakeups; no foreground-service time
- [ ] `adb shell dumpsys alarm | grep com.parem` prints nothing;
      `adb shell dumpsys power | grep -i parem` shows no held wake lock;
      `adb shell dumpsys jobscheduler | grep -A3 com.parem.launcher` lists only
      the theme, wallpaper and focus-end WorkManager jobs
- [ ] Grayscale tick cost: `--reset`, 30 min screen-on in the marked app, then
      30 min in the same app unmarked; Parem's CPU delta between the two runs
      is under 1% of device CPU
- [ ] Record the numbers in the release PR

## This release touched

Rewrite this section for each release from `[Unreleased]` in CHANGELOG.md and
the roadmap rows that landed: one line per change, saying what to try on the
device. Delete it again once the tag is pushed.

**Historical example: v6.0.0-beta.2 (versionCode 104).** These rows describe that candidate and its tablet-only owner instruction, not the current release. Replace them with this release's checks. Its recorded results are in `docs/releases/v6.0.0-beta.2-validation.md`.

- [ ] Upgrade the same package without clearing data; retain folders, home slots, widgets and app limits; export/import all preference types including wallpaper dimming
- [ ] On a spare phone with the published, signed v5.5.1 full APK, configure home slots, a folder, a bound widget and a non-default theme; install the signed 6.0.0 full release over it without clearing data. Verify home opens and all four survive. Record the phone and Android version. CI's same-debug-key upgrade does not verify the release signing key.
- [ ] Existing and newly added widgets render after resize, portrait/landscape rotation and process restart; API31+ options include nonempty size lists
- [ ] At 200% font, light/dark/system themes and keyboard open, Focus, App limits, folder, website and widget sheets reach their last action; no tiny tablet column
- [ ] Home gestures retain thresholds and double-tap lock; per-slot swipe-up opens all apps; TalkBack cannot focus the invisible lock view
- [ ] Hidden-only searches offer the hint; locked Private Space never exposes a hidden-app hint; unlock refreshes the list
- [ ] App-limit and Focus gates work for home, folders, gestures, notification opens and web routes; test default browser, blocked default, and no default
- [ ] Accepted gap: with no default browser and Focus off, system chooser selection is not subject to a browser-specific limit/pause; record this as an accepted limitation, not a passing gate test
- [ ] Midnight, timezone changes and Focus expiry refresh enforcement; deny/revoke usage, contacts, notification and accessibility permissions without a crash
- [ ] Schedule theme/wallpaper and Focus-end grayscale work, restart the process, and confirm on-demand WorkManager still runs each enabled job
- [ ] Screen-off grayscale polling resumes correctly on unlock; disabling the feature cancels its work; run the battery soak above
- [ ] Wallpaper dim toggles independently, survives import/restart, and remains suppressed in e-ink mode; new colours remain readable with TalkBack and large fonts
- [ ] Final candidate smoke CI passes; release APK builds with R8; tablet release performance and crash/ANR observations are recorded

## UX device checks from 6.0

These checks originated in the 6.0 UX packages. Apply the relevant checks to
the current release and add checks for changed behavior. Run with TalkBack on
unless a line says otherwise.

### UX-2 — TalkBack users can leave home (M4-WP32)

- [ ] **Merge gate (trap #1):** TalkBack on, first swipe right on home lands on
      the clock or a slot — never on "lock layout description…"; linear
      navigation never reaches it at all
- [ ] **Merge gate (trap #1):** TalkBack off, accessibility lock service on,
      double tap on empty home still locks the phone
- [ ] TalkBack on: the "All apps" row shows under the slots (portrait and
      landscape) and opens the drawer; turning TalkBack off from the volume-key
      shortcut while home is showing hides it again
- [ ] TalkBack actions menu on a slot lists All apps, Settings, Notifications,
      Add widget and the configured swipe apps; each one does what it says
- [ ] Screen time reads "Screen time today, 1 minute" (not "1 m"); the settings
      info icon reads "App info"; a work-profile app's dot reads "Work profile"
- [ ] With the "All apps" row showing, no slot is cut off at the bottom

### UX-3 — Touch is steady and consistent (M4-WP44)

- [ ] On the Tab S8 and a phone: a 1-second press with about 5dp of drift on a
      slot launches the app (no swipe, no menu until the long-press delay)
- [ ] Long-press on a slot, on empty home, on a drawer row, on the clock and on
      the date vibrates with system touch feedback on, and stays silent with it
      off
- [ ] Gesture letters: a recognised letter buzzes once and launches; a stroke
      that turns but matches nothing (or a letter with no app) buzzes REJECT and
      its trail fades out; with "Remove animations" on it clears instantly
- [ ] With the home layout locked, long-press on a slot buzzes REJECT and shows
      locked-layout feedback with an Unlock action
- [ ] Pressed labels dim on touch-down and are never left dimmed after a swipe
      or a letter stroke; swipes fire at the same finger travel on phone and
      tablet

### UX-5 — Accessibility semantics (M4-WP35)

- [ ] TalkBack walk of Settings reads each row once as "label, role, state"
      ("Bold text, switch, off"; "Theme mode, Dark"); no orphan "Off" nodes
- [ ] Heading navigation jumps between the settings sections and the
      BottomSheetMenu titles
- [ ] App limits: each row names its app, usage today and limit; picking a
      limit updates what the row says
- [ ] Mindful pause: "Open X" is spoken exactly once, when the countdown ends
- [ ] Focus allowed apps: ticking a 6th app is announced and stays unticked
- [ ] Drawer: the search field reads "Search apps"; a no-match query announces
      "No apps found"
- [ ] At 200% font, settings rows, sheet rows and the limit rows still fit
      their text with no clipping (rows grow past 48dp)

### UX-8 — Locale-correct copy (M4-WP48)

- [ ] Hebrew: "Left" in Settings puts the home apps on the left of the screen.
- [ ] The focus-blocked toast fits 2 lines at 200% font on the Tab S8.

### Polish (M4-WP30)

- [ ] The focus-schedule and theme-schedule time pickers follow the app's light or dark theme and accent.
- [ ] The app-count picker's numbers don't collide with its label, including at 200% font.
- [ ] Editing a folder keeps its name, apps and websites; a hidden app in it survives Save.

## Sign-off

- [ ] Every box above ticked on: device / Android version ___
- [ ] Tablet pass on: device / Android version ___
- [ ] CHANGELOG `[Unreleased]` renamed, fastlane changelog added, version
      bumped — then tag `v*` and push

## Phone pass — both flavours

These checks originated in the 6.0.0 final phone pass; adapt them to the current
release and record any unavailable checks. Record the commit, APK SHA-256, flavour, version, device model and Android version,
then record pass/fail, observed result and a screenshot or notes for every item.
Run the steps once with `play` and once with `full`, on a physical phone. Debug
APKs are for this early pass only; repeat on the actual signed release builds
before release. Flavours have the same application ID: export settings and use a
spare test device before uninstalling or replacing an incompatible signature.
Do not clear the daily phone's data or change its wallpaper for this test.

- [ ] Fresh install, make Parem the default home app, complete or skip onboarding,
      and return home. Swipe up repeatedly at different speeds, including after
      immediately returning from another app; verify the drawer opens and no
      long-press menu appears. Long-press home deliberately and verify its menu.
- [ ] Add apps and a folder; edit its name and contents, launch its members,
      remove a slot and use Undo. Repeat with a hidden app in the folder.
- [ ] Grant usage access, notification access, contact access and the optional
      lock accessibility service from their in-app routes. Read each disclosure;
      decline first, retry and agree, then revoke in Android Settings and verify
      the corresponding feature reports the missing access and can be restored.
- [ ] Deny contacts until Android stops offering the permission prompt; verify
      the app explains how to restore it in Android app settings, then grant it
      there and verify contact search works. Reset permission state on the test
      device afterwards.
- [ ] Test grayscale's computer grant using the app's instructions. Verify the
      Play build offers the computer route and no in-app wireless pairing; verify
      Full offers its pairing route and that cancelling it leaves colour intact.
      Test the grant, enabling grayscale and restoring colour on each flavour.
- [ ] Full: turn wireless debugging off and on, then pair in Parem with a fresh
      code; verify the grant succeeds even if a stale local mDNS entry remains.
- [ ] Open home, app, folder, limit, focus, widget and lock/consent sheets;
      reach the last action, dismiss by Back and outside tap, and verify their
      intended action. Repeat at Android font size 200% with keyboard open.
- [ ] Select a right-to-left system language, restart Parem and verify home
      alignment, drawer, folder and sheets are readable and Left/Right choices
      match their visible direction; repeat key sheets at 200% font.
- [ ] On a spare device and test account, configure distinctive home slots,
      folder, weather place and settings. Allow Android cloud backup to complete,
      restore to a clean installation on another test device, and verify Parem's
      preferences and weather place did not return from cloud backup. Record the
      Android version and backup/restore route; a fresh install alone does not
      prove exclusion. Never inspect another person's backup.
- [ ] Separately use Android 12+ device-to-device transfer between test devices
      with distinctive settings and a folder. Verify the preferences transfer,
      identify anything missing, and verify weather and optional access state;
      permissions must be re-granted when Android requires it. Cloud and device
      transfer are different paths and must not substitute for one another.
- [ ] Verify settings export/import on test data; restart and check the restored
      values. Check weather requests only after opt-in and turning it off clears
      saved temperature as described in the weather sheet.

Record failures and device-specific behaviour without marking this gate complete
until both release flavours have passed. Cloud backup and transfer need suitable
spare devices and accounts; if unavailable, leave those boxes open.
