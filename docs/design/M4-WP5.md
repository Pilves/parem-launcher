# M4-WP5 — Grayscale on demand (design proposal)

Status: proposal, revision 2, awaiting Patric's decision (see "Decision for Patric"). Base: `origin/chore/agent-harness`. No code changed, no gradle run.

## Problem

Android has a system grayscale mode: Secure settings `accessibility_display_daltonizer_enabled` = 1 and `accessibility_display_daltonizer` = 0 (monochromacy). Both are `@hide` string constants ([Settings.java](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/provider/Settings.java)). Writing them needs `WRITE_SECURE_SETTINGS`, which is `signature|privileged|development|role|installer` and "not for use by third-party applications" ([core manifest](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/res/AndroidManifest.xml)). Because of the `development` flag, `adb shell pm grant` can grant it once. After that, Parem can flip grayscale without help.

Patric's flow: "user puts in usb, clicks grayscale on app and then our app does its thing". The click is meant to happen inside Parem, with the phone plugged in. Every option below also needs Developer options with debugging switched on. Nothing removes that step.

## Options

| | Flow | Cost / deps | Failure modes |
|---|---|---|---|
| **(a) WebUSB page** | Phone plugged into a computer, open the page in Chrome or Edge, click "Grant", accept the RSA prompt on the phone | One static HTTPS page using Tango (`ya-webadb`, MIT), bundled once. No app dependency | Chromium only ([WebUSB](https://developer.chrome.com/docs/capabilities/usb)). Fails with "Unable to claim interface" if a native `adb` server holds the device ([ya-webadb #520](https://github.com/yume-chan/ya-webadb/discussions/520)). Needs a computer |
| **(b) In-app wireless self-pairing** | Turn on Wireless debugging, type the pairing code into Parem, Parem runs the grant on itself. No cable | An adb client plus TLS/SPAKE2 pairing inside the APK: Kadb (Apache-2.0), libadb-android (custom licence, NOASSERTION on GitHub), LADB (GPLv3). Heavy for one command | Android 11+ only ([adb docs](https://developer.android.com/tools/adb#wireless-android11-command-line)). Needs Wi-Fi. Entering the code in split screen is clumsy |
| **(c) Shizuku** | One tap if Shizuku is already running | `Shizuku-API` (MIT), a provider entry in the manifest, and a permission callback | Few users have it. Must be restarted after every reboot ([Shizuku guide](https://shizuku.rikka.app/guide/setup/)) |
| **(d) Copy-paste adb** | `adb shell pm grant com.parem.launcher android.permission.WRITE_SECURE_SETTINGS` | Zero | Needs platform-tools and a terminal |

## Recommendation

**Patric's exact flow is not possible over USB.** Over a USB cable the phone is the adb *device*, never the adb *host*. An app cannot open an adb session to its own phone through the cable, so a tap inside Parem cannot grant `WRITE_SECURE_SETTINGS` while the phone is plugged in. Whatever is on the other end of the cable has to send the grant.

The only route where the grant click happens inside Parem is **(b), wireless self-pairing**. It uses no cable at all. It costs an adb client and a pairing implementation in the APK, and it works on Android 11+ only.

So "plug in, tap Grayscale in Parem, done" splits into two real choices:

- **(a) as primary.** The user taps "Grayscale" in Parem. The sheet shows a short link. The user opens it on the computer the phone is plugged into, clicks "Grant" once, and accepts the RSA prompt on the phone. Parem notices the grant by itself and turns grayscale on (see "Grant sheet"). From then on, every grayscale change is an in-app toggle. Nothing is added to the APK. The cost: the one grant click is on the computer page, not in Parem.
- **(b) as primary.** No computer and no cable. The user turns on Wireless debugging, types a 6-digit pairing code into Parem, and Parem grants itself. The cost: an adb/TLS/SPAKE2 dependency (Kadb is the only cleanly licensed candidate) for a single command, Android 11+ only, needs Wi-Fi, and the code entry happens in split screen or from a notification.

**(d), the copy-paste adb command, ships with either choice.** It costs one string and a copy button, and it covers Firefox and Safari users and people who already run adb. (c) Shizuku stays out (YAGNI).

My lean is (a): it keeps the APK unchanged and needs only a little more than Patric's description (one click on a computer instead of in the app). That lean is not the decision. Patric picks.

### Decision for Patric

> **(a)** one click on a computer page, then in-app toggles forever, or **(b)** the grant done inside Parem over Wi-Fi with no cable, at the cost of an adb client in the APK and Android 11+ only?

If (b) is chosen, the "Web page" scope below is dropped and replaced by a Kadb pairing screen. The policy, controller, triggers, prefs and tests stay the same.

### Never stuck grey

Parem saves the user's previous daltonizer state before its first write, writes only while `GRAYSCALE_APPLIED` is set, and restores the saved state exactly. It never turns off colour correction that the user set up themselves. One function, `reconcile`, compares the desired state with the actual state. It runs on every home resume and at launcher start, and Parem is the home app, so a crash recovers on the next home press. A focus session with an end time also queues a one-off WorkManager job at that time, using the existing dependency, so grayscale clears even when the user is in another app.

If the user changes Color correction in system Settings while Parem has grey applied, Parem treats that as the user taking over and stops writing (see "User override" below). The escape hatch the grant sheet promises therefore never fights back.

### Triggers

- **manual**
- **during focus**
- **after a limit**: from "Open anyway" until Parem has been left and returned to. The clearing rule is in "Limit override lifecycle" below.

## Grant sheet (the in-app half of the flow)

Tapping "Grayscale" (settings row) without the grant opens a `BottomSheetMenu` with:

1. A one-paragraph explanation: turn on USB debugging, plug into a computer, open the link.
2. **The page URL, shown large in monospace and easy to type:** `pilves.github.io/parem-launcher/grant`. The release URL has no query string. The debug build appends `?pkg=com.parem.launcher.debug`.
3. **"Share link"**: `Intent.ACTION_SEND` with the URL, so the user can send it to the computer by Quick Share, email or a messenger.
4. **"Copy link"** and **"Copy adb command"** (`copyToClipboard`). The command uses `context.packageName`.
5. **"Check again"**, a fallback only.

**Automatic detection.** While the grant sheet is shown, the fragment polls `GrayscaleController.isGranted` every 2 s on the main looper. It also checks in `onResume`. Polling is needed because Parem can stay in the foreground the whole time the user works on the computer. The RSA dialog pauses and resumes Parem *before* the grant arrives, so `onResume` alone would miss it. The poll stops on sheet dismiss and in `onPause`, and each tick guards on `isAdded` / `_binding != null`.

**When the grant is detected**, the sheet closes and Parem does what the user asked for when they tapped "Grayscale": it sets `GRAYSCALE_MANUAL = true`, calls `reconcile`, and opens the toggles sheet (Now / During focus / After a limit) so the user sees the switch is on. "Check again" does the same check once, for anyone the poll misses.

With the grant already present, "Grayscale" opens the toggles sheet directly.

## Exact scope

- **Manifest:** `<uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" tools:ignore="ProtectedPermissions" />`
- **New `helper/GrayscalePolicy.kt`** (Android-free):
  - `desired(manual, onFocus, focusActive, onLimit, limitOverride): Boolean`
  - `plan(desired, applied, suppressed, current: State, saved: State?): Action`
  - Action is one of: `ApplyAndSave`, `Restore`, `UserOverride`, `ClearSuppression`, `None`. Rules are in "plan() rules" below.
- **New `helper/GrayscaleController.kt`:**
  - `isGranted(ctx)` (`checkSelfPermission`)
  - `reconcile(ctx)`: reads prefs and the two Secure settings, runs `plan`, performs the action. Catches `SecurityException`, so a revoked grant clears `GRAYSCALE_APPLIED` and never crashes.
  - `onLimitOverride(ctx)`: sets `GRAYSCALE_LIMIT_OVERRIDE = true` and `GRAYSCALE_LEFT_SINCE_OVERRIDE = false`, then `reconcile`.
  - `scheduleFocusEnd(ctx, endMs)` and `cancelFocusEnd(ctx)`: see "Focus-end worker".
- **`ui/settings/WellbeingSettingsCard.kt`** and a `fragment_settings.xml` row "Grayscale", opening the grant sheet or the toggles sheet as described above.
- **`MainActivity`:**
  - `onCreate`: call `reconcile`.
  - `onStop`: if `GRAYSCALE_LIMIT_OVERRIDE` is set, set `GRAYSCALE_LEFT_SINCE_OVERRIDE = true`.
  - `onStart`: if both are set, clear both, then `reconcile`.
- **`ui/HomeFragment.onResume`:** call `reconcile` after `checkAndExpire`. It does **not** touch the limit override.
- **`ui/FocusModeDialog.kt`:** after `enable`, call `reconcile` and `scheduleFocusEnd` (when there is an end time). On the disable path (`FocusModeDialog.kt:95`), call `cancelFocusEnd` and `reconcile`.
- **Limit "Open anyway" callbacks** in `ui/home/HomeSlotsController.kt:261` and `ui/AppDrawerFragment.kt:530`: call `onLimitOverride` before launching.
- **Prefs:**
  - exported: `GRAYSCALE_ON_FOCUS` and `GRAYSCALE_ON_LIMIT` (Boolean)
  - added to `exportExcludeKeys`: `GRAYSCALE_MANUAL`, `GRAYSCALE_APPLIED`, `GRAYSCALE_SUPPRESSED`, `GRAYSCALE_LIMIT_OVERRIDE`, `GRAYSCALE_LEFT_SINCE_OVERRIDE`, `GRAYSCALE_PREV_ENABLED`, `GRAYSCALE_PREV_MODE`. These describe this device at this moment.
- **Strings** (`translatable="false"`): `grayscale`, `grayscale_now`, `grayscale_during_focus`, `grayscale_after_limit`, `grayscale_grant_title`, `grayscale_grant_body` (includes the escape hatch: Settings → Accessibility → Color correction), `grayscale_grant_url` (the URL is in strings so it can change without code), `grayscale_share_link`, `grayscale_copy_link`, `grayscale_copy_command`, `grayscale_check_again`, `grayscale_not_granted`, `grayscale_ready`.
- **Web page** (option (a) only): `web/grant/index.html`, the vendored Tango bundle, and a deploy workflow. Details under "Web page".
- **CHANGELOG:** one `[Unreleased]` line.

## plan() rules

`GREY` means enabled = 1 and mode = 0. Rules are evaluated in order:

1. `applied && current != GREY` → **`UserOverride`**. The user changed Color correction outside Parem. Clear `GRAYSCALE_APPLIED` and `GRAYSCALE_MANUAL`, drop the saved state (the user's current setting is now their state; restoring the old one would overwrite what they just chose), and set `GRAYSCALE_SUPPRESSED = true`. Write nothing.
2. `!desired && suppressed` → **`ClearSuppression`**. The trigger that wanted grey has ended, so the next trigger may apply grey again. Write nothing.
3. `desired && suppressed` → **`None`**. Focus or the limit still wants grey, but the user turned it off by hand during this session. Do not fight it.
4. `desired && !applied && current != GREY` → **`ApplyAndSave`**. Save `current`, write grey, set `APPLIED`.
5. `desired && !applied && current == GREY` → **`None`**. The user already has monochromacy and Parem does not take ownership of it.
6. `!desired && applied` → **`Restore`**. Write `saved` exactly, clear `APPLIED` and the saved state.
7. Otherwise → **`None`**.

Turning "Now" on or off in the toggles sheet is an explicit choice inside Parem, so it clears `GRAYSCALE_SUPPRESSED` before calling `reconcile`.

## Limit override lifecycle

The override must survive Parem's own navigation and end only once the user has actually been in another app and come back.

The trap: `AppDrawerFragment.kt:530-533` calls `viewModel.selectedApp(...)` and then immediately `findNavController().popBackStack(R.id.mainFragment, false)`. That pop resumes `HomeFragment` while `MainActivity` is still in the foreground. If `HomeFragment.onResume` cleared the override, grey would be cleared before or during the app launch, and it would never stick or would flicker.

Rule:
- `onLimitOverride` sets `LIMIT_OVERRIDE = true` and `LEFT_SINCE_OVERRIDE = false`.
- `MainActivity.onStop` sets `LEFT_SINCE_OVERRIDE = true` while the override is set. The flag is in prefs, not a field, so it survives the process being killed while the user is in the limited app.
- `MainActivity.onStart` clears both and reconciles when both are set. That happens on the next return to Parem.
- `HomeFragment.onResume` only reconciles. It never clears the override.

Known side effects, both accepted: a configuration change or theme recreate (`onStop` then `onStart`) ends an active override, and so does a settings row that starts another activity. Neither happens while the user is inside the limited app. If the launch fails (activity not found), the override stays until the next time Parem is left and returned to.

## Focus-end worker

- `scheduleFocusEnd` uses `WorkManager.enqueueUniqueWork("grayscale_focus_end", ExistingWorkPolicy.REPLACE, OneTimeWorkRequest with setInitialDelay(endMs - now))`. Re-enabling focus replaces the pending job instead of adding a second one. This matches the existing `enqueueUniquePeriodicWork` use in `MainViewModel` and `ThemeScheduleManager`.
- `cancelFocusEnd` calls `cancelUniqueWork("grayscale_focus_end")`. It runs on the `FocusModeDialog.kt:95` disable path, so ending focus early leaves no job behind.
- The worker only calls `reconcile`. It does not need `FocusModeManager.checkAndExpire`, because `FocusModeManager.isActive` already returns false once the end time has passed (`FocusModeManager.kt:26-31`), so `desired` is already false.
- A stale or duplicate job is harmless, because `reconcile` is idempotent: it acts only on the difference between desired and actual state. Unique work keeps that a safety net, not something the design relies on.

## Web page (option (a))

- **Files:** `web/grant/index.html` and `web/grant/grant.js` (no inline script, so the CSP below can stay strict; no external fonts or CSS), `web/grant/vendor/tango.bundle.js`, `web/grant/vendor/LICENSE-tango.txt` (the MIT text as shipped), and `web/grant/vendor/VERSIONS.txt` (each `@yume-chan/*` package and version, the esbuild version, and the exact bundle command). The licence and version record lives next to the bundle, so updating the bundle and updating the record happen in the same commit.
- **Package allowlist.** The page hard-codes `com.parem.launcher` and `com.parem.launcher.debug` (`applicationId` plus `applicationIdSuffix ".debug"`, `app/build.gradle:17,50`). It never puts a URL value into the shell command:
  - no `?pkg=` → grant `com.parem.launcher`
  - `?pkg=com.parem.launcher.debug` → grant the debug build
  - any other value → show an error and grant nothing
  - before granting, run `pm path <pkg>`. If the package is not installed, say "Parem is not installed on this phone" and stop.
  A public page that ran `pm grant <any package> WRITE_SECURE_SETTINGS` from a URL parameter would be a social-engineering tool. This page cannot be used that way.
- **Privacy.** The page gets ADB access to the user's phone, so it is fully client-side:
  - no analytics, no third-party requests, no fonts or scripts from CDNs; the Tango bundle is vendored
  - it does not send the device serial, the package list or any command output anywhere
  - the only persistent state is the ADB RSA key that `adb-credential-web` keeps in the browser's IndexedDB, which is what lets the phone remember the computer
  - a `Content-Security-Policy` meta tag with `default-src 'self'; connect-src 'none'` enforces this (WebUSB is not governed by `connect-src`, so the page still works) in the browser
  - the page says this in one visible sentence above the Grant button: "This page runs only in your browser. It sends nothing anywhere and runs one command: granting Parem permission to change colour settings."
- **Hosting.** GitHub Pages for `Pilves/parem-launcher`, deployed by a small `.github/workflows/pages.yml` (`actions/upload-pages-artifact` with `path: web`, then `actions/deploy-pages`). This publishes only `web/`, not the markdown under `docs/`. **Owner action:** Patric enables Pages in the repo settings (Settings → Pages → Source: GitHub Actions). An agent cannot do this. A custom short domain is also Patric's call; the URL lives in `grayscale_grant_url` so it can change later.

## Implementation order

1. **Spike (first, before any app code; option (a) only).** Bundle `@yume-chan/adb`, `adb-daemon-webusb`, `adb-credential-web` and `stream-extra` with esbuild into one ESM file. Load it from a static `index.html` over `localhost` (WebUSB needs a secure context, and localhost counts) and run `pm grant com.parem.launcher.debug android.permission.WRITE_SECURE_SETTINGS` against one real phone. Pass = the permission shows as granted in `dumpsys package`.
   - Tango is several ESM packages that import each other by bare specifier, so "one static file without a bundler" is not the plan. The fallback build step *is* the plan: a one-time esbuild bundle, checked in with its versions recorded. There is no `npm` step in the Android build or CI.
   - If the spike fails, the recommendation changes: (a) is dropped, **(d) ships on its own** as the grant path for 6.0, and the choice between "(d) only" and "(b)" goes back to Patric.
2. `GrayscalePolicy` and `GrayscalePolicyTest`.
3. `GrayscaleController`, prefs, the manifest, the focus-end worker.
4. Wiring (MainActivity, HomeFragment, FocusModeDialog, the two limit callbacks).
5. The grant sheet and toggles sheet.
6. The web page and Pages workflow.

## Done criteria

- The grant through the page and through the adb command both make the toggle work.
- With the grant sheet open, granting on the computer turns grayscale on and opens the toggles sheet without the user tapping "Check again".
- The page refuses any `?pkg=` value other than the two allowlisted ones, and makes no network request after load (checked in the DevTools Network tab).
- Each trigger turns grey on and back off.
- **After a limit, home route** (`HomeSlotsController.kt:261`): grey is on in the launched app and stays on until the user returns to Parem, then clears.
- **After a limit, drawer route** (`AppDrawerFragment.kt:530`): same. Grey must not flicker or clear during the drawer's pop back to home.
- Changing Color correction in system Settings while grey is applied: Parem does not re-apply grey on the next home resume, during the same focus session, or for the same limit override.
- Pre-existing deuteranomaly correction survives a full on/off cycle.
- A timed focus that ends while the user is in another app clears within minutes. Re-enabling focus leaves one pending job, and ending it early leaves none (`adb shell dumpsys jobscheduler | grep parem`).
- `am crash` while grey, then the next home press, leaves the phone in the expected state.
- A revoked grant shows the grant sheet and does not crash.
- `testDebugUnitTest`, `compileDebugKotlin` and `assembleDebug` exit 0.

## Risks & traps

- **Uninstall cannot be intercepted.** Uninstalling Parem while grey leaves the phone grey. Mitigations: the grant-sheet text and the system Color correction toggle and Quick Settings tile. Switching to another launcher also skips the home-resume reconcile, though the focus-end worker still runs.
- **Play:** `WRITE_SECURE_SETTINGS` is not on Play's restricted-permission list as far as I found. The [permissions policy](https://support.google.com/googleplay/android-developer/answer/16558241) only demands that use is tied to a core feature. Disclose it in the listing. This does not use the accessibility service, so it stays within M4's "no new use of the accessibility service". Option (b) would add an adb client to the APK, which deserves a separate look at Play's policy on device and network abuse.
- **ARCHITECTURE conventions:** the export/exclude registration applies. The fragment `isAdded` guard applies to the sheet callbacks and the grant poll. Traps #1–#5 do not apply: the lock view, widgets, slot flags, usageStats and the package stamp are all untouched.
- **Stale doc:** CLAUDE.md still names the "4-hour self-recreate" trap, but `MainActivity` says it was removed in PAREM-108. Flagging this, not fixing it here.
- **Hot files:** `HomeSlotsController` and `AppDrawerFragment` are also owned by M4-WP2 (the mindful pause on the limit path), so expect a merge. `MainActivity.onStart/onStop` gains a few lines next to the widget host calls. M4-WP3's scheduled windows would need their own boundary trigger on top of `reconcile`.

## Test plan

**JVM** (`GrayscalePolicyTest`):
- the `desired` truth table
- `plan`:
  - never writes when it was not applied
  - restores the saved state, not "off"
  - keeps the user's existing monochromacy (rule 5)
  - does nothing when the state is already correct
  - `applied` with current = off → `UserOverride`
  - `applied` with current = deuteranomaly → `UserOverride` (the saved state is dropped, not restored)
  - `desired && suppressed` → `None` (focus still active after a user override)
  - `!desired && suppressed` → `ClearSuppression`, and the next `desired` then gives `ApplyAndSave`

**Device:**
- the spike (step 1) before anything else
- the page in Chrome on Linux and Windows, including the case where a native adb server is running
- the adb fallback
- Android 7 (minSdk) and 14+
- one Samsung or Xiaomi phone (OEM grant restrictions)
- every done criterion, including both limit routes
- a reboot while grey

## Not verified

- that the grant persists across reboot and OS updates (expected for development permissions)
- that `checkSelfPermission` in an already-running process sees a `pm grant` without a restart (expected; the poll would show it, and if not, the sheet asks the user to reopen Parem)
- Windows WinUSB driver needs for arbitrary phones
- Xiaomi's "USB debugging (Security settings)" requirement for `pm grant`
- an official Play statement on `WRITE_SECURE_SETTINGS`
- the libadb-android licence text (matters only if (b) is chosen)
- the exact WorkManager delay under Doze

Tango bundling has moved out of this list. It is now the spike in "Implementation order", step 1.

## Review notes

- **QR code: not adopted.** The QR would be on the phone's screen, and the device that needs the URL is the computer. Desktops rarely scan QR codes, so a QR helps only a user with a second phone. A short URL in large type, plus "Share link" (Quick Share, email or a messenger to the computer), covers the real path with no dependency. A QR generator would need zxing or a hand-made drawable for each URL. If Patric wants one anyway, a static vector drawable of the fixed release URL costs no dependency.
- **"Short URL".** `pilves.github.io/parem-launcher/grant` is about as short as GitHub Pages gets. Anything shorter needs a custom domain, which is Patric's call (see "Hosting").
- **Two issues, one answer.** The two items about "Tango loads as one static file" asked for the same change. Both are covered by step 1 of "Implementation order".
- **Turning grey on when the grant lands.** This goes slightly beyond "detect the grant". The user tapped "Grayscale", so the design treats that tap as the intent and acts on it when the grant arrives. That is the "then our app does its thing" part of Patric's sentence. If he would rather land on the toggles sheet with everything off, it is a one-line change.
