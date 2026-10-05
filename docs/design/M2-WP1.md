# M2-WP1 — targetSdk / compileSdk 36 (design proposal)

Status: proposal, awaiting Patric's sign-off. Branch base: `origin/chore/agent-harness`. No code changed.

## Problem

Play rejects updates below targetSdk 36 since 2026-08-31; the extension ends 2026-11-01
([Play target API policy](https://support.google.com/googleplay/android-developer/answer/11926878)).
The app is at `compileSdk 35` / `targetSdkVersion 35` (`app/build.gradle`). The question is which
[API-36 target-gated changes](https://developer.android.com/about/versions/16/behavior-changes-16) actually hit this app.

Inventory, checked against the code:

| API-36 change | Hits Parem? | Evidence |
|---|---|---|
| Edge-to-edge opt-out (`windowOptOutEdgeToEdgeEnforcement`) removed | **No** | The opt-out is not used anywhere, so the app has been under the API-35 edge-to-edge rules since v5.x ([A15 changes](https://developer.android.com/about/versions/15/behavior-changes-15)). Insets are fixed paddings (`fragment_home.xml` 112dp/48dp, drawer 24dp) plus `FLAG_LAYOUT_NO_LIMITS`, so nothing changes at 36 |
| Predictive back on by default: `onBackPressed()` not called, `KEYCODE_BACK` not dispatched | **Compatible** | No `onBackPressed`/`KEYCODE_BACK`/`onKeyDown` overrides. `MainActivity` uses one AndroidX `OnBackPressedCallback(true)`. AndroidX Activity ≥1.6 is required ([predictive back guide](https://developer.android.com/guide/navigation/custom-back/predictive-back-gesture)); navigation 2.9.0 brings a newer one. Every sheet is a modal `BottomSheetDialog` on Material 1.12, and modal sheets handle predictive back on their own from 1.10. The few other dialogs are framework `AlertDialog`/`TimePickerDialog`, which close on back themselves ([MDC predictive back](https://github.com/material-components/material-components-android/blob/master/docs/foundations/PredictiveBack.md)) |
| `screenOrientation` / `setRequestedOrientation` ignored at sw ≥ 600dp | **Mostly no** | `MainActivity.setupOrientation()` locks portrait only when `!isTablet()`, and `isTablet` means a diagonal of 7" or more. A device with sw ≥ 600dp and a diagonal under 7" (a small tablet, or some foldables' inner screens) used to be locked and will now rotate. `layout-land/` covers home, drawer and settings |
| `elegantTextHeight` ignored | **No** | The app never sets the attribute. Since targetSdk 35 the default is already `elegantTextHeight=true` for the affected scripts, and the app has been on 35 since v5.x. At 36 the attribute is only ignored, so Arabic (`values-ar`) renders the same at 35 and 36. Device item 9 stays, as a general regression check and not as a 36 change |
| `scheduleAtFixedRate`, health/Bluetooth/MediaStore, opt-in intent matching | No | Not used. `intentMatchingFlags` is opt-in, so we skip it (YAGNI) |
| Home intent, accessibility lock, usage stats, `AppWidgetHost` | **No target-gated change found** | Not listed in the 36 target-gated changes. Device-wide A16 changes ([all apps](https://developer.android.com/about/versions/16/behavior-changes-all)) apply whatever the target: JobScheduler quotas (WorkManager weather/wallpaper/theme workers), 3-button predictive back, and the `announceForAccessibility` deprecation (not used) |

## Options

**A. Bump only the SDK lines and keep AGP 8.9.1.** This is two lines. The
[A16 SDK setup page](https://developer.android.com/about/versions/16/setup-sdk) says AGP 8.9.0-rc01 or newer is enough,
but the [AGP 8.9 notes](https://developer.android.com/build/releases/past-releases/agp-8-9-0-release-notes) list API 35 as
the maximum, so the build will probably print an "unsupported compileSdk" warning. Cost: nearly nothing. Risk: aapt2 or lint
may have problems with android-36 resources, and we can't know until the build runs.
On the Pi the parser is AGP 8.9.1's own pinned aapt2: `/home/pi/android-sdk/aapt2-x86_64.bin` is byte-identical to the
`aapt2-8.9.1-12782657-linux.jar` binary in the gradle cache (`cmp`, checked) and reports `2.19-12782657`; it runs through
the qemu wrapper via `android.aapt2FromMavenOverride`. That aapt2 has to read the android-36 `android.jar` resource table.
**This is the most likely way option A fails:** an older aapt2 has already failed this way on the Pi (the Debian Android 14
aapt2 could not parse SDK 35's `android.jar`, see the Pi build notes). The symptom would be a `processDebugResources`
failure while loading `android.jar`, not a Kotlin error. If that happens, go to B: AGP 8.10's aapt2 is built for API 36,
and it must then be re-copied into the wrapper path.

**B. Option A plus AGP 8.9.1 → 8.10.x.** AGP 8.10's maximum is API 36, and it uses the same Gradle 8.11.1
([8.10 notes](https://developer.android.com/build/releases/past-releases/agp-8-10-0-release-notes)). Cost: one line in
`libs.versions.toml`, and the Pi's qemu-wrapped aapt2 has to be re-copied (ARCHITECTURE "Building on ARM64"). That touches
every build lane on the Pi.

**C. Option B plus proactive adaptations:** switch the back callback on only off-home, use real `WindowInsets` listeners,
add `PROPERTY_COMPAT_ALLOW_RESTRICTED_RESIZABILITY`. Cost: more code to review, and nothing it fixes is known to be broken.
The resizability opt-out also stops working at targetSdk 37. Rejected under YAGNI.

## Recommendation

Go with **A**, and fall back to **B** only if `assembleDebug`/lint fails or Patric wants a build with no warnings.
Leave the always-enabled back callback as it is. On home it correctly swallows back, because a launcher has nothing to go
back to. In the drawer and settings it pops the nav stack as it does now, just without a predictive animation, which is
the same as today. Fix rotation or clipping only if the device pass shows a problem.

## Exact scope

- **Before the build lane runs:** install the API 36 platform on the Pi. The SDK at `/home/pi/android-sdk` has only
  `platforms/android-35` and `build-tools/35.0.0` (checked with `ls`). Run
  `/home/pi/android-sdk/cmdline-tools/latest/bin/sdkmanager --sdk_root=/home/pi/android-sdk "platforms;android-36"`.
  The licenses are already accepted (`licenses/android-sdk-license` exists), so AGP would probably auto-download the
  platform anyway, but we don't rely on that: an explicit install fails fast and visibly, instead of partway through a
  5–10 minute build. No new build-tools are needed for A (AGP 8.9.1 defaults to 35.0.0, which is installed). This step
  is for the Pi only; whether CI's runner already has android-36 or downloads it is not checked here.
- `app/build.gradle`: `compileSdk 35` → `36`, `targetSdkVersion 35` → `36`. Nothing else, and versionCode stays untouched (that belongs to the release task).
- With option B only: `gradle/libs.versions.toml` `gradle = "8.9.1"` → `"8.10.1"` (check the latest 8.10.x patch).
- `CHANGELOG.md` `[Unreleased]`: "Target Android 16 (API 36)."
- No changes to `MainActivity`, layouts, manifest, prefs or strings. If the device pass finds a real bug, it gets its own `M2-WP1a…` row.

## Done criteria

- `ls /home/pi/android-sdk/platforms` lists `android-36` before the build lane starts.
- `./gradlew compileDebugKotlin testDebugUnitTest assembleDebug` all exit 0 (run by the build-lane owner, not me).
- The merged APK manifest shows `targetSdkVersion=36` (`aapt2 dump badging`).
- Every item in the device list is ticked by Patric. Until then they are marked "not verified" in the PR and the handoff fragment.

## Risks & traps

- **Trap #1 (invisible `lock` view):** the SDK bump doesn't touch it, but double-tap lock is the most fragile path, so it is the first device check.
- **Trap #2 (widget IDs):** no widget code changes. Check that widgets restore after a reboot and after updating over v5.7.0.
- **ARM64 aapt2 pin:** option B makes the qemu wrapper stale (ARCHITECTURE "After an AGP upgrade, re-copy").
- **Hot file:** `app/build.gradle` is owned by this WP for the SDK lines only. The release task owns versionCode.
- The orientation change and predictive back only apply on Android 16 devices. Older devices behave exactly as before.

## Test plan

JVM: nothing new, because there is no new pure logic. The existing suite must stay green.

Device (Android 16, targetSdk 36 build), for Patric:
1. Home edge-to-edge: the clock isn't under the status bar, the bottom slot/screen-time row isn't under the gesture pill or 3-button bar. Test light and dark themes, status bar shown and hidden.
2. Drawer and settings scroll to the last row above the nav bar, and the omnibox keyboard doesn't cover results.
3. Back with gesture and with 3-button navigation: home does nothing, drawer and settings return home, and **every dialog** closes on back. A peek animation is fine, a crash or a stuck dialog is not. The full list (from a grep of `ui/` and `MainActivity` for `BottomSheetMenu`, `BottomSheetDialog`, `AlertDialog`, `TimePickerDialog`):
   - `BottomSheetMenu` sheets: slot menu, widget options, theme picker, time-limit picker (`BadHabitDialogs.showTimeLimitPicker`), the bad-habit limit warning from the drawer (`AppDrawerFragment.showBadHabitWarningDialog`, ~line 527) and from a home slot (`HomeSlotsController.showBadHabitWarningDialog`), `ScreenTimeGraphDialog` (tap on the screen-time row, `HomeClockController`), `GestureLetterConfigDialog`, and the remaining pickers in `AppInfoSettingsCard`, `GesturesSettingsCard` and `HomeScreenSettingsCard`.
   - `BottomSheetDialog` sheets: home long-press menu (`HomeGesturesController`), widget picker (`HomeWidgetController`), `FocusModeDialog`, `WeatherSettingsDialog`, `ScreenTimeLimitDialog`, `CreateFolderDialog`.
   - Framework dialogs: the three `AlertDialog`s in `MainActivity` (hidden apps, keyboard, usage-access prompt) and the two chained `TimePickerDialog`s for the scheduled theme.
4. Double-tap to lock (trap #1).
5. Widgets: add, resize, reorder, reboot, then update the APK over v5.7.0 (trap #2).
6. Set as default launcher, Home button from another app, `FakeHomeActivity` reset flow.
7. Screen time and limits: numbers match Digital Wellbeing roughly, and the limit warning fires.
8. Tablet or foldable (sw ≥ 600dp): rotation follows `layout-land`, nothing is clipped.
9. Arabic locale: home slots and drawer rows aren't vertically clipped. General regression check only; nothing in API 36 changes Arabic rendering for this app (see the `elegantTextHeight` row).
10. Weather and daily wallpaper still refresh within a day (JobScheduler quotas).

## Not verified

- Whether AGP 8.9.1 builds `compileSdk 36` cleanly (no gradle run, by instruction), and in particular whether its aapt2 2.19-12782657 parses the android-36 `android.jar`.
- Whether `platforms;android-36` installs cleanly with the Pi's `cmdline-tools/latest` (not run; installing it is part of the build lane).
- Every device item above, including the claim that home, lock, usage-stats and widget behaviour don't change at 36. That is based on docs, not a device.
- Which `androidx.activity` version actually resolves (navigation 2.9.0 should pull ≥1.6). Confirm with `./gradlew :app:dependencies`.
