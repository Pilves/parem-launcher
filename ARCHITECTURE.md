# Architecture

Parem Launcher is a single-module Android app: Kotlin, classic Views with ViewBinding (no Compose), one main activity + Navigation-component fragments, no database and no DI framework. Main code lives under `app/src/main/java/com/parem/launcher/`; JVM tests live in `app/src/test/` and instrumented tests in `app/src/androidTest/`.

## Code map

```
MainActivity.kt            Single activity. Hosts the nav graph, the AppWidgetHost,
                           ActivityResult launchers (widget bind/configure, device
                           admin, launcher selector), theme bootstrapping.
MainViewModel.kt           Shared across all fragments (activity-scoped). App list
                           loading, app launching, screen-time queries, weather,
                           WorkManager scheduling, and selectedApp(flag) dispatch.

ui/
  HomeFragment.kt          Home screen shell: lifecycle, observers, click/long-press
                           dispatch, navigation, status bar. Constructs and wires
                           the home/ controllers and HomeWidgetController.
  HomeWidgetController.kt  The entire multi-widget system (pick/bind/configure/
                           restore/resize/reorder/remove). Created in
                           HomeFragment.onViewCreated, released in onDestroyView.
  home/                    One class per home-screen concern, mirroring
                           HomeWidgetController's shape (fragment/binding/prefs/
                           viewModel in the constructor, isActive()-guarded async):
                           HomeSlotsController (the 8 app slots, dynamic fitting +
                           homeAppsCapacity publishing, slot menus, folders,
                           launch/limit-check flows), HomeGesturesController
                           (swipe/touch listeners, gesture-letter overlay, gesture
                           actions, torch, lock), HomeClockController (date/time/
                           weather rendering, clock/calendar taps, screen-time row).
  AppDrawerFragment.kt     App list + omnibox search (apps, calculator, unit and currency
                           conversion, dial, web, quick actions, contacts and settings). The active mode
                           is a single OmniboxMode (sealed, declared in
                           helper/OmniboxResolver.kt).
  AppDrawerAdapter.kt      Drawer rows, long-press menu, rename, auto-launch logic.
  SettingsFragment.kt      Binding lifecycle, constructs/wires the settings/ cards,
                           and the cross-section bits (resetOpenPickers,
                           populateWellbeingSection, the import ActivityResultLauncher).
  settings/                One class per settings card, mirroring HomeWidgetController's
                           shape (fragment/binding/prefs[/viewModel] in the constructor,
                           a bind() that populates + wires clicks + observers):
                           AppInfoSettingsCard, HomeScreenSettingsCard,
                           AppearanceSettingsCard, GesturesSettingsCard,
                           WellbeingSettingsCard. Plus two extracted dialogs:
                           WeatherSettingsDialog, GestureLetterConfigDialog.
  BottomSheetMenu.kt       THE way to build bottom sheets (handle + title + rows).
  BadHabitDialogs.kt       Launch gate for limited apps (mindful pause, "limit
                           reached" warning) + time-limit picker. Home app launches
                           use HomeSlotsController.launchApp; drawer app launches
                           check the gate when the app has a limit or pause. Web
                           opens use gateWebOpen to select and gate the handler.
                           Omnibox quick actions and dial intents bypass this gate.
  FocusModeDialog.kt, ScreenTimeGraphDialog.kt, ScreenTimeLimitDialog.kt,
  GestureLetterOverlayView.kt, ScreenTimeGraphView.kt, Onboarding*.kt

helper/
  One object per feature, each owning its own SharedPreferences keys:
  FocusModeManager, AppLimitManager (+ mindful-pause opt-in; logic in
  MindfulPause), FolderManager (class),
  GestureLetterManager, WeatherManager (Open-Meteo),
  DoubleTapActionManager, ThemeScheduleManager (+ Worker), IconPackManager,
  AppIconCache (bounded ConstantState cache for default app icons),
  WallpaperWorker, ExpressionEvaluator (omnibox calculator),
  UnitConverter (omnibox unit conversion),
  ContactMatcher (Android-free contact match/rank) + ContactSearchManager
  (the sole ContactsContract query, opt-in behind READ_CONTACTS),
  PackageChangeTracker (LauncherApps callback → cache-invalidation stamp),
  AppListRebuilder (Android-free filter/sort over the cached raw app query),
  AppListSource.kt (the raw LauncherApps query + stamp-keyed snapshot cache,
  installed-package/user-handle lookups), WallpaperUtils.kt (wallpaper/bitmap
  ops + daily-wallpaper HTTP fetch), SystemActions.kt (system intents:
  notification drawer, dialer/camera/alarm/calendar, app info, uninstall,
  accessibility check),
  Utils.kt (small leftovers: toasts, theme/color helpers, isTablet, clipboard,
  openUrl, default-launcher checks) / Extensions.kt (free functions),
  usageStats/ (UsageEvents parsing).
  MyAccessibilityService.kt  Locks the screen (see "Traps" below).
  FakeHomeActivity.kt        Default-launcher switching trick.

data/
  Prefs.kt                 Typed facade over the main SharedPreferences file,
                           plus JSON export/import (settings backup).
  Constants.kt             Flags, URLs, enum-ish objects.
  AppModel.kt, FolderData.kt

listener/
  OnSwipeTouchListener (gesture detection), DeviceAdmin.
```

## Conventions

- **Storage**: core launcher state is in SharedPreferences `"com.parem.launcher"` (`Prefs.PREFS_NAME`), through `data/Prefs.kt` or feature managers in `helper/`. Search history and quiet-notification keys use separate SharedPreferences files, excluded from settings export and backup. Opt-in crash reports are files in `noBackupFilesDir`. When adding a key that must survive settings export/import with a specific type, register it in `Prefs.LONG_PREF_KEYS`/`FLOAT_PREF_KEYS`, and add device-specific keys to `exportExcludeKeys`.
- **Bottom sheets**: build them with `ui/BottomSheetMenu`. Don't hand-assemble LinearLayouts.
  A sheet built outside `BottomSheetMenu` (a direct `BottomSheetDialog`) must call `disableAnimationsOnEink()` before it shows, or it slides on e-ink.
  A sheet opened from a fragment must be tied to it with `dismissOnDestroy(viewLifecycleOwner)`: dialogs belong to the activity's window, so Home would otherwise pop the fragment and leave the sheet up with callbacks into a dead fragment.
- **App launching and selection**: use `MainViewModel.selectedApp(appModel, flag)`; the `flag` constants in `Constants` say what the selection means (launch, set home slot N, set swipe app, …). The app drawer is reused for "pick an app" flows via these flags. Gate limited apps before dispatch; `selectedApp` alone does not supply the mindful-pause or limit gate. Web-search gestures use `gateWebOpen` followed by a search intent, and omnibox quick actions and dial intents have separate dispatch paths.
- **Fragments** hold `_binding` nullable + `binding` accessor; async callbacks must check `isAdded` / `_binding != null` (or `isActive()` in HomeWidgetController) before touching views.
- **Home app slots** are 1-8, stored per-slot via `prefs.getHomeApp*(slot)` / `setHomeApp*(slot, …)`.

## Traps a new maintainer should know

1. **The invisible `lock` view is load-bearing.** `HomeFragment`'s `R.id.lock -> {}` click handler looks dead but isn't: `HomeGesturesController.lockPhone()` calls `binding.lock.performClick()`, and clicking that FrameLayout emits an accessibility event which `MyAccessibilityService` matches *by contentDescription* to run `GLOBAL_ACTION_LOCK_SCREEN`. Renaming `lock_layout_description` or removing the view (or its no-op handler) breaks double-tap-to-lock on Android 9+.
2. **Widget IDs are stateful three ways**: the AppWidgetHost allocation, `prefs.widgetIds` (CSV), and `prefs.widgetProviders` (id:component map used to re-bind widgets that the OS invalidated). `HomeWidgetController.restoreWidgets()` reconciles them; keep all three in sync when touching widget code.
3. **`FLAG_SET_HOME_APP_1..8` are the literal ints 1..8**, and the app-drawer "rename" path writes `prefs.setHomeAppName(flag, …)` using the flag as the slot number.
4. **Screen-time numbers come from `helper/usageStats/`**, a hand-rolled UsageEvents aggregator (see `UnmatchedCloseEventGuardian`), not `queryUsageStats` — Android's summary API is wildly inaccurate.
5. **The app list is cached against a package-change stamp** (`helper/PackageChangeTracker`); any new cache of package-derived data must check that stamp too — bypassing the tracker will serve stale data after installs/uninstalls/updates.

## Build & release

- Flavours: `play` is the Google Play bundle. `full` is the APK for GitHub and F-Droid. Both flavours use the same applicationId; signing may differ by distribution channel. `full` adds in-app adb pairing, in `app/src/full/` (`helper/adb/`, `GrayscalePairing`, its receiver, POST_NOTIFICATIONS and the pairing strings). `play` has none of that code, and `scripts/check-play-build.sh` fails CI if any comes back (`docs/design/DECISIONS.md`). Shared code reaches the difference only through `helper/ChannelFeatures`, which exists once per flavour. Strings in `app/src/full/res/` override the wording in `main`, which is Play's.
- Debug: `./gradlew assembleFullDebug` → `app/build/outputs/apk/full/debug/app-full-debug.apk`, likewise `play` (package id gets a `.debug` suffix so it installs alongside release). CI: `.github/workflows/debug-build.yml` runs tests + build of both flavours on every master push (or manually via workflow_dispatch) and uploads the APKs as an artifact. A manual run also builds the `full` release APK and `play` bundle, signed with that run's debug key, as a `play-check` artifact: the way to prove the bundle before a tag, because `release.yml` publishes. Note the CI debug keystore differs per run — updating a CI-built debug install usually needs an uninstall first.
- Every behavior-changing PR adds an entry under `## [Unreleased]` in CHANGELOG.md; at release time, rename that section to the version and prepend a fresh `## [Unreleased]`.
- Release: run the device pass in [`docs/RELEASE_CHECKLIST.md`](docs/RELEASE_CHECKLIST.md) first. Then bump `versionCode`/`versionName` in `app/build.gradle`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and a CHANGELOG.md entry, add `docs/releases/<tag>-notes.md`, then push a tag matching `v*` → `.github/workflows/release.yml` builds a signed APK (keystore comes from repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and the Play bundle. It uploads the AAB and R8 `mapping.txt` as the run's `play-<tag>` artifact for 90 days before attaching the APK to a GitHub release; the AAB and mappings are not public release assets. The AAB carries the same mapping as `BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map`, so a Play upload needs no separate file. The first upload's signing key matters: see "Play App Signing" below.
- Dependency versions live in `gradle/libs.versions.toml`. AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.1.20; JDK 17+ (21 works).

### Play App Signing

Decided 2026-11-06: **Google generates the app signing key, with a new, separate upload key** (`docs/play/signing.md`). The existing keystore stays the release key for GitHub and F-Droid, and is never given to Play.

| Option | Play and GitHub certificate | Cost |
|---|---|---|
| **Google-generated signing key, new upload key** (recommended) | Different | One more set of repo secrets |
| Google-generated signing key, existing keystore as upload key | Different | Saves those secrets, but one key holds two roles. Recoverable with an upload key reset |
| Copy of the existing key to Google (PEPK), new upload key | Same | Google permanently holds the key every sideloaded install trusts, and one leak hits all channels. Cannot be taken back |

- The existing key (certificate SHA-256 `e3f739b2acf8094cbd61fe6016c532d6b102af36f8fb3b083c89dd78cf0fbc0f`, RSA 2048, valid to 2053, read from the published APK) qualifies for either role, so this is a choice rather than a constraint.
- That key cannot be replaced: GitHub and F-Droid installs accept updates only from it, and the draft F-Droid recipe pins it in `AllowedAPKSigningKeys`. An upload key can be reset in Play Console. Keep the replaceable key apart from the irreplaceable one.
- Sharing the key buys one thing: an in-place move from GitHub to Play. All published APKs together have 37 downloads (2026-11-05), and `docs/fdroid/README.md` already plans Play as a separate install, so that does not pay for the permanent cost.
- "Updates must keep the same signing key" (`docs/releases/v6.0.0-beta.2-validation.md`) holds per channel, and the GitHub channel does not change. Moving between Play and a sideload channel means uninstalling, which erases app data, so users export their settings first.
- **The trap:** with a Google-generated key, whichever key signs the first uploaded bundle becomes the upload key. `release.yml` signs the bundle with `UPLOAD_KEYSTORE_*` for that reason, so check the bundle's certificate against `docs/play/signing.md` before the first upload.
- The 2026-11-06 decision assumed the signing key could still change in closed testing. Verify current Play Console policy before relying on that assumption.
- Android developer verification ties a package name to signing keys, so `com.parem.launcher` needs both fingerprints registered: the existing certificate, and Play's from the Play App Signing page. Google may ask for proof of the existing key: an APK signed with it, carrying the console's snippet in `assets/adi-registration.properties`.
- GitHub secrets are write-only and Play cannot recover this key, so keep an offline backup of the keystore.

### Building on ARM64 hosts (Raspberry Pi etc.)

Google ships no ARM64 Linux `aapt2`, so plain `assembleDebug` fails at `processDebugResources`. Working setup (as configured on the maintainer's Pi 5):

1. Install `qemu-user-static` and extract amd64 `libc6`/`libgcc-s1` debs into a sysroot (e.g. `/opt/amd64-sysroot`; plain multiarch install fails on Raspberry Pi OS because of its `+rpt` libc versions).
2. Copy the official x86_64 aapt2 out of gradle's cache (`~/.gradle/caches/**/aapt2-*-linux/aapt2`) and wrap it:
   `exec qemu-x86_64-static -L /opt/amd64-sysroot /path/to/aapt2-x86_64.bin "$@"`
3. Point gradle at the wrapper in `~/.gradle/gradle.properties`:
   `android.aapt2FromMavenOverride=/path/to/wrapper`
4. On an 8 GB Pi also cap `org.gradle.jvmargs=-Xmx2560m` there (the project default of 4.5 GB will swap).

After an AGP upgrade, re-copy the new aapt2 binary — the override pins a specific version.

## Known issues / debt

- Weather staleness (PAREM-106): cache age is tracked via a success-only
  `WEATHER_LAST_SUCCESS_MS` key (separate from `WEATHER_LAST_FETCHED`, which
  a failed fetch also rewrites for retry-backoff). `HomeFragment` dims the
  temperature at 3-24h old and hides it past 24h old; classification lives
  in `helper/WeatherStaleness`. Still no per-fetch failure toast/indicator
  beyond that (by design — the home screen stays quiet).
- Gesture-letter drawing and swipe gestures both see the same touch stream; a fast letter draw can also register as a swipe.
- JVM unit tests cover pure logic in `app/src/test`; run both flavour tasks
  `testFullDebugUnitTest` and `testPlayDebugUnitTest`. Instrumented tests in
  `app/src/androidTest` run on CI emulators, but do not replace real-device
  checks for gestures, accessibility, permissions and device-specific behaviour.
