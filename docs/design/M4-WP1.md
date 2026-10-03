# M4-WP1 — Filtered notifications (design proposal)

Status: proposal, revision 2, awaiting Patric's sign-off. Base: `origin/chore/agent-harness`. No code changed, no gradle
run.

**Gate 0 (before any implementation):** the Play Protect check in "Go/no-go gate" below. If it fails, WP1 does not ship
in 6.0 as designed and goes back to Patric.

## Problem

Users want the phone to stop pulling them in. Only the apps they choose should alert. Everything else should wait in a
quiet list that they open on purpose from home. Parem has no notification code today. The manifest declares no listener,
and the only service is the accessibility lock (trap #1).

## Options

The platform facts drive every option:

- A `NotificationListenerService` (NLS) only hears about a notification **after it is posted**. That is also when the
  system plays the sound and buzzes. The AOSP order is "alert, then notify listeners" (see Not verified).
- `cancelNotification(key)` exists from API 21.
- `snoozeNotification(key, ms)` and `getSnoozedNotifications()` exist from API 26. When a snooze expires, the system
  reposts the notification.
- From targetSdk 35, apps cannot change global DND. `requestInterruptionFilter` now turns an app-owned `AutomaticZenRule`
  on or off instead.

Sources: [NLS reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService),
[Android 15 behaviour changes](https://developer.android.com/about/versions/15/behavior-changes-15).

**A. NLS + cancel + our own copy.** The listener cancels the notification and keeps a copy of its title, text and
`contentIntent` for the quiet list. We would have to store notification content, so persistence and backup become a real
privacy problem. Cost: the most code, and the most risk.

**B. NLS + snooze (recommended).** The listener snoozes the notification, and the system keeps it. The quiet list is
`getSnoozedNotifications()` filtered to the keys we snoozed (see "Our keys"). We store no notification content, and the
list survives our process dying. Cost: API 26+ only, and the first ding may still play.

**C. B plus a DND / Modes rule** for real silence. The user picks the allowed apps again inside the system Modes UI.
Third-party apps get no public per-app allowlist on `ZenPolicy` (not verified). Cost: two allowlists that drift apart,
it only fully works on Android 15+, and it touches global phone state. Not now.

## Recommendation

**Option B.** Here is how it behaves for the user:

- The feature is called "Hide from shade, keep for later".
- The settings card says plainly that a short sound may still play. It offers a per-app "Make silent" row that opens
  `Settings.ACTION_APP_NOTIFICATION_SETTINGS`, which gives real silence with zero code.
- Below API 26 the card is hidden, and so is the gesture picker entry.

### Feature states

There is **no `QUIET_NOTIF_ENABLED` pref.** The state is derived, so a restored or transferred device cannot show the
feature as on while the component is off:

| State | Derived from | Settings card shows | QUIET_LIST gesture does |
|---|---|---|---|
| Off | component not `COMPONENT_ENABLED_STATE_ENABLED` | toggle off | opens the turn-on flow |
| Needs access | component enabled, `hasAccess == false` | "Needs notification access" + "Open settings" | opens the "Needs access" sheet (below) |
| On | component enabled, `hasAccess == true` | toggle on, "Allowed apps" row | opens the quiet list |

- `isEnabled` = `packageManager.getComponentEnabledSetting(component) == COMPONENT_ENABLED_STATE_ENABLED`.
- `hasAccess` = our package is in `NotificationManagerCompat.getEnabledListenerPackages`.
- "Connected" (`QuietNotificationListener.instance != null`) is a third, shorter-lived fact. If the state is On but
  `instance` is null (the system has not rebound us yet), the gesture shows the "Needs access" sheet with the text
  "Notification filter is starting. Try again in a moment." It never shows an empty list.

The "Needs access" sheet has one line and one button: "Parem no longer has notification access. Hidden notifications
will come back to the shade within 8 h." Button: "Open settings" (the access screen). Second option: "Turn off filter"
(disables the component).

### Turn-on flow

Order matters. The user chooses apps and reads the disclosure **before** the system access screen.

1. **Pick allowed apps.** The multi-select (like `FocusModeDialog`) opens pre-checked with the default SMS app
   (`Telephony.Sms.getDefaultSmsPackage`) and the default dialer (`TelecomManager.defaultDialerPackage`). Cancel here
   aborts the flow, and nothing changes.
2. **Disclosure sheet** (`BottomSheetMenu`, shown every time the feature is turned on). Content:
   - Title: "Hide notifications from other apps?"
   - "Notifications from apps you did not allow are moved out of the shade into a quiet list. Open it from home with
     the gesture you choose in Settings > Gestures."
   - "Calls, alarms, media, navigation and emergency alerts are never hidden."
   - "A short sound or vibration may still play when a notification arrives. Use 'Make silent' to stop it for an app."
   - "Parem reads notifications only on this phone. Nothing is stored or sent anywhere."
   - "To undo: turn this off here, or remove Parem under Settings > Notification access. Hidden notifications come
     back within 8 h."
   - Buttons: "Continue" (step 3), "Not now" (aborts; the allowlist from step 1 is kept, the component stays off).
3. **Enable the component**, then open `ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS` (API 30+, falls back to
   `ACTION_NOTIFICATION_LISTENER_SETTINGS`). On a sideloaded APK on Android 13+, the card text says restricted settings
   may need to be allowed first.
4. On return, the card re-derives the state. If access was not granted, the card shows "Needs access", not "On".

### Listener lifecycle

- **Disabled by default.** The service is declared `android:enabled="false"` in the manifest. A disabled component is
  never bound.
- **Toggling the component** always calls `setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)`,
  as `Extensions.resetDefaultLauncher` does. With flags = 0 the system kills the launcher process.
- The listener does nothing until `onListenerConnected`. There it prunes the stored key set (see "Our keys") to keys
  still present in `getSnoozedNotifications()`.
- `onListenerDisconnected` clears the static `instance`.
- **Turning it off from our toggle:** there is no public unsnooze. If we have snoozed keys, we ask "Dismiss waiting
  notifications?":
  - "Dismiss": `cancelNotification` on our keys only, then clear the key set.
  - "Keep": the user is told "Hidden notifications will come back to the shade within 8 h." We keep the key set
    (harmless, pruned on the next connect).
  - Then `requestUnbind()` and disable the component (`DONT_KILL_APP`).
- **Access revoked in system settings** (our toggle not used): we cannot act, since we no longer have access. The next
  time the user opens the settings card or uses the gesture, the state is "Needs access", and the sheet says the items
  come back within 8 h. Whether the system reposts snoozed items early on revoke is not verified.

### Our keys

`getSnoozedNotifications()` also returns notifications the user snoozed from the system shade. "Dismiss all" and the
turn-off step must not cancel those. So we **keep the set of keys we snoozed**:

- Keys only, no title or text. A key is `user|pkg|id|tag|uid`; an app-chosen tag can still carry an identifier, so the
  set is treated as private.
- Stored in a one-line file in `context.noBackupFilesDir` (never in cloud backup or device transfer, never exported).
  This deliberately breaks the "all state in `com.parem.launcher` prefs" rule; see Open questions.
- Added to in `onNotificationPosted` after a successful snooze. Pruned in `onListenerConnected` and on every
  `waiting()` call to the intersection with `getSnoozedNotifications()`.
- Quiet list = `getSnoozedNotifications().filter { it.key in ourKeys }`.

The alternative (filter snoozed items by package against the allowlist) needs no storage, but it would show and
dismiss a user's own shade snoozes from non-allowed apps. Rejected.

### Filter rules

These live in `QuietFilter`, pure helper code. A notification is snoozed only if **all** of these hold:

- its package **has a launcher activity** (so the user could have allowed it in the picker). Packages with no launcher
  entry are never touched. That covers `com.android.phone` (voicemail, SIM), Play services security alerts, and most
  carrier and system apps.
- its package is not in the allowlist;
- its package is not in the never-touch set: our own package, `android`, `com.android.systemui`,
  `com.android.phone`, `com.android.cellbroadcastreceiver`, `com.google.android.cellbroadcastreceiver`,
  `com.android.cellbroadcastreceiver.module`, the default dialer and the default SMS app. The cell-broadcast packages are
  listed explicitly because some OEMs give "Emergency alerts" a launcher icon.
- its category is not `CATEGORY_CALL`, `CATEGORY_ALARM`, `CATEGORY_NAVIGATION` or `CATEGORY_TRANSPORT`;
- it is not media: `extras` has no `EXTRA_MEDIA_SESSION` (a paused MediaStyle notification is not ongoing and is
  clearable, so this rule is needed);
- it is not ongoing / foreground-service and it is clearable;
- it is not a group summary (snoozing the children is enough; see the device check below).

The listener computes the Android-side inputs (launcher set via `LauncherApps.getActivityList(pkg, user)`, default
dialer and SMS, media session) and passes plain values in.

Manifest meta-data `default_filter_types = "conversations|alerting"` keeps silent notifications off our listener on
API 31+. On older versions the code drops them.

### Snooze length

The snooze lasts 8 h (constant). If it expires and the app is still not allowed, the notification is re-snoozed when it
is reposted.

### Focus mode

There is no new coupling in v1. The notification allowlist is its own list. Focus mode's 5-app launch whitelist answers
a different question. See Open questions.

### Quiet list

- It opens through a new `GestureAction.QUIET_LIST` that the user picks for swipe-left or swipe-right. Swipe-down is not
  used, because it is a two-value pref and changing it changes the meaning of existing user settings.
- **Double-tap does not offer it.** `showDoubleTapActionPicker` filters `QUIET_LIST` out of `gestureActionChoices()`.
  `DoubleTapActionManager` stays unchanged: its `execute` has no branch for 7, and no UI can set 7 for double-tap.
- The swipe picker entry is shown only when API >= 26 and the state is not Off. The settings card, once on, says
  "Open the quiet list with a swipe: Settings > Gestures".
- **Fallback** in `HomeGesturesController.executeGestureAction` for `QUIET_LIST`:
  - API < 26 (for example a value imported from another device): toast "Not available on this Android version".
  - State Off: open the turn-on flow.
  - Needs access, or `instance == null`: the "Needs access" sheet.
  - On: the quiet list. If it is empty, it says "Nothing waiting". This text only appears when the listener is
    connected.
- Each row shows the app label and title.
- Tapping a row sends `contentIntent` with `ActivityOptions.setPendingIntentBackgroundActivityStartMode(...)`
  ([BAL rules](https://developer.android.com/guide/components/activities/background-starts)):
  - API 26–33: no options.
  - API 34–35: `MODE_BACKGROUND_ACTIVITY_START_ALLOWED`.
  - API 36+: `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` (the sheet is visible when the user taps).
    `MODE_BACKGROUND_ACTIVITY_START_ALLOWED` is deprecated at 36. This constant needs compileSdk 36, so WP1 lands after
    M2-WP1.
  It then calls `cancelNotification` if the notification has `FLAG_AUTO_CANCEL`, and drops the key from our set.
- The last row is "Dismiss all": cancels our keys only.
- `BottomSheetMenu` is a builder around `BottomSheetDialog`, not a Fragment. The `isAdded` / `_binding != null` guard
  goes in the **calling fragment** (`HomeFragment` via `HomeGesturesController`): check it before `show()`, and again
  after `waiting()` if that call is moved off the main thread.

## Go/no-go gate (Play Protect)

For sideloaded installs, Play Protect may *block* an app that declares a notification listener alongside
**accessibility** ([Play Protect guidance](https://developers.google.com/android/play-protect/warning-dev-guidance)).
Parem's GitHub APK is sideloaded and has the lock accessibility service.

- **When:** before implementation starts, not at the release device pass. Build a throwaway signed APK from the
  base branch plus only the manifest `<service>` entry and an empty listener class. Install it by sideload on a device
  with Play Protect on (one Android 13+ and one Android 15+ device if possible), then enable the listener.
- **Go:** no warning, or a soft warning the user can dismiss once.
- **No-go:** an install block, a "harmful app" warning, or a prompt to uninstall. Then WP1 does not ship in 6.0 as
  designed and goes back to Patric. Options for him, not decided here: ship the listener only in the Play build (a
  build flavour), ship it without the GitHub APK, or drop WP1 from 6.0.
- The result (device, Android version, Play Protect text, screenshot) is recorded in this file before code starts.

## Exact scope

**New files**

- `helper/notifications/QuietFilter.kt`: an Android-free `object` with
  `shouldSilence(pkg, category, isOngoing, isClearable, isGroupSummary, isMedia, hasLauncherActivity, allowed: Set<String>, alwaysAllowed: Set<String>): Boolean`.
  `alwaysAllowed` is the fixed never-touch set plus our package, default dialer and default SMS app.
- `helper/notifications/QuietNotificationListener.kt`: the NLS. Its parts are `onListenerConnected` (prune keys),
  `onNotificationPosted` → `snoozeNotification(key, SNOOZE_MS)` + record key, `companion var instance`, `waiting()`,
  `open(key)` and `dismissAll()`.
- `helper/notifications/QuietNotificationsManager.kt`: modelled on `FocusModeManager`. It holds:
  - `state(ctx)`: Off / NeedsAccess / On, derived as in "Feature states"
  - `setEnabled(ctx, on)` (toggles the component with `DONT_KILL_APP`)
  - `hasAccess`
  - `getAllowed` and `setAllowed`
  - our-keys read/write/prune (the `noBackupFilesDir` file)
  - `openAccessSettings`
- `ui/QuietListSheet.kt`: the quiet list, the disclosure sheet and the "Needs access" sheet, all built with
  `BottomSheetMenu`.
- `app/src/test/.../QuietFilterTest.kt`.

**Edits**

- `AndroidManifest.xml`: a `<service>` with `BIND_NOTIFICATION_LISTENER_SERVICE`, `exported="false"`,
  `enabled="false"`, the SERVICE_INTERFACE filter and the filter-types meta-data. **Permission change, so Patric must
  sign off.**
- `data/Constants.kt`: `GestureAction.QUIET_LIST = 7`.
- `ui/home/HomeGesturesController.executeGestureAction`: one new branch with the fallback above.
- `ui/settings/GesturesSettingsCard.kt`: labels plus a picker entry:
  - `gestureActionLabel` (around line 211): a `QUIET_LIST` label, so it does not fall through to `else -> appName`.
  - `gestureActionChoices()` (around line 250): the entry, added only when API >= 26 and the state is not Off.
  - `showDoubleTapActionPicker` (around line 280): filter `QUIET_LIST` out.
  - `getDoubleTapLabel` (around line 260): no change needed, since double-tap can never be 7.
- `helper/DoubleTapActionManager.kt`: **no change**, by design (double-tap never offers `QUIET_LIST`).
- `ui/settings/WellbeingSettingsCard.kt` and `res/layout/fragment_settings.xml`: a toggle row (three states), an
  "Allowed apps" row, and a "Make silent" row. The allowed-apps picker reuses the `showFocusModeFromSettings` pattern
  (`getAppsList`, `isAdded` guard after the coroutine).
- **Prefs:**
  - `QUIET_NOTIF_ALLOWED` (CSV) is exported, like `FOCUS_MODE_WHITELIST`.
  - No enabled pref (derived state). No Long or Float keys.
  - **No notification content is ever written anywhere.** That covers export, cloud backup (`data_extraction_rules.xml`
    backs up all sharedprefs) and logs. The key set lives in `noBackupFilesDir`, outside both.
- `strings.xml`: about 20 strings appended (disclosure sheet included), all `translatable="false"`.
- `CHANGELOG.md`.

## Done criteria

- Gate 0 passed and recorded.
- Off by default. A fresh install shows no Parem entry in the notification-access list, and the listener never binds
  while the feature is off.
- Turning on goes: pick apps (default SMS and dialer pre-checked) → disclosure → system access screen.
- When on: a non-allowed app's notification leaves the shade within 1 s and appears in the quiet list. An allowed app's
  notification behaves as normal.
- Calls, alarms, media (playing and paused), navigation, emergency alerts and packages without a launcher activity are
  never touched.
- A notification the user snoozed from the system shade never appears in the quiet list and is never cancelled by us.
- After the launcher process is killed, the quiet list still shows the same items.
- The gesture never shows an empty list unless the listener is connected; Off and Needs-access states open their sheets.
- Turning the feature off unbinds the listener without killing the launcher. After "Dismiss" nothing stays hidden; after
  "Keep" the user was told items return within 8 h.
- `QuietFilterTest` is green, and `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all exit 0.

## Risks & traps

- **Play Protect combo.** The biggest risk. Handled by Gate 0 above, before any code.
- **Android 13+ "restricted settings".** Users who sideload must allow restricted settings before they can grant access.
  Onboarding copy needs to say so.
- **Play policy.** Notification-listener use for "aggregate notifications to help users focus" / launchers is listed as
  allowed. Hiding notifications without prior consent is disallowed; the disclosure sheet (turn-on step 2) is the
  mitigation. The current Play permissions policy has no declaration form for it
  ([policy](https://support.google.com/googleplay/android-developer/answer/16558241)).
- **Data safety.** Data that is processed only on the device and never sent off it does not need to be disclosed
  ([Data safety help](https://support.google.com/googleplay/android-developer/answer/10787469)). There is no new
  disclosure as long as nothing leaves the device. The privacy policy (M2-WP3) should still mention it.
- **ARCHITECTURE traps.**
  - #1: do not touch `MyAccessibilityService` or the `lock` view. The new service is a separate component.
  - #5: allowed package names go stale after an uninstall. That is harmless, but the picker must build from
    `AppListSource`, not a new cache.
- **Snooze-repost loop.** The 8 h re-snooze may ding again each time (device check).
- **Group summaries.** We skip summaries, but NMS snoozes a whole group when its summary is snoozed, and a summary left
  alone after its children go may linger in the shade (device check).

## Test plan

**JVM (`QuietFilterTest`)**

- allowed vs not allowed
- every never-touch rule, including the cell-broadcast and `com.android.phone` packages
- a package without a launcher activity is never silenced, even with an empty allowlist
- `CATEGORY_TRANSPORT` and `isMedia = true` (paused, clearable, not ongoing) are never silenced
- an empty allowlist hides all clearable notifications from launchable, non-exempt apps
- the default dialer and default SMS app are always allowed

**Device (API 26, 30, 34, 36)**

- Gate 0 (before implementation): sideloaded APK with Play Protect on
- the turn-on flow: picker pre-checks SMS and dialer, disclosure appears before the system access screen, "Not now"
  leaves the component off
- the grant flow, including restricted settings on a sideloaded APK
- toggling the component does not restart the launcher (home stays, no cold start)
- does the ding still play? does a heads-up flash?
- a paused music notification stays in the shade
- an app-posted group summary: after its children are snoozed, is the summary left alone in the shade, and does it
  hide or linger?
- a notification snoozed by the user from the shade is not in the quiet list and survives "Dismiss all"
- tapping a row opens the right screen with BAL on 34, 35 and 36
- the quiet list after the process is killed, and after a reboot
- turning off: "Dismiss" path, and "Keep" path (items return within 8 h)
- listener revoked in system settings while items are snoozed: what the card and gesture show, and when the items
  come back
- the gesture in each state: Off, Needs access, On-but-not-yet-connected, On
- restore from backup / device transfer with the feature on: the card shows Off (or Needs access), not On, and the
  allowlist is restored
- a work-profile notification (the docs say the system ignores listeners running in a work profile)

## Open questions (for Patric)

1. **Focus mode.** Should focus mode's 5-app whitelist also be notification-allowed while focus is active?
   Default answer: **no** for v1. The two lists stay separate; the notification allowlist applies unchanged during focus.
   Revisit if testers ask for it.
2. **Manifest permission.** Sign off on adding the notification-listener `<service>` (after Gate 0).
3. **Key storage outside prefs.** The snoozed-key set goes in `noBackupFilesDir`, not the `com.parem.launcher` prefs
   file, so it never reaches backup or export. Accept the exception to the prefs rule? Alternative: a second prefs file
   plus an `<exclude>` in `data_extraction_rules.xml`, which touches backup config.

## Not verified

- The "alert before listener" order comes from my memory of AOSP `NotificationManagerService`, not from the docs. The docs
  do not promise that cancel or snooze stops the sound.
- Whether `cancelNotification` works on a *snoozed* key, and whether there is any way to unsnooze early.
- Whether snoozed notifications survive a reboot.
- What happens to our snoozed items when the listener is revoked in system settings while items are snoozed (reposted
  at once, or only when the 8 h snooze ends).
- How NMS treats a group whose children we snoozed but whose summary we skipped.
- That `ZenPolicy` has no per-app allowlist for third-party apps.
- The official Android 13 restricted-settings page. My source for it is secondary press coverage.
- That `MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE` is enough for a tap from our own visible sheet on API 36.

## Review notes

- **Double-tap:** chose filtering over a new `DoubleTapActionManager` branch. A quiet list on double-tap adds a second
  entry point with no user ask behind it, and filtering is one line.
- **Picker entry hidden while Off:** done as asked, but the gesture still handles Off (turn-on flow), because an existing
  swipe set to `QUIET_LIST` survives the feature being turned off or an import.
- **Never-touch set and launcher rule:** both. The launcher rule alone misses OEMs that give emergency alerts a
  launcher icon.
- **Scope note (relayed user request):** the request "user puts in usb, clicks grayscale on app and then our app does
  its thing" is about the M4-WP5 grayscale grant flow, not this package. Nothing in WP1 changes for it.
  `docs/design/M4-WP5.md` is the design to check against that one-click USB flow.
