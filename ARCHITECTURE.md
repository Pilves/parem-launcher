# Architecture

Parem Launcher is a single-module Android app: Kotlin, classic Views with ViewBinding (no Compose), one activity + Navigation-component fragments, no database, no DI framework, no test suite (yet). Everything lives under `app/src/main/java/com/parem/launcher/`.

## Code map

```
MainActivity.kt            Single activity. Hosts the nav graph, the AppWidgetHost,
                           ActivityResult launchers (widget bind/configure, device
                           admin, launcher selector), theme bootstrapping.
MainViewModel.kt           Shared across all fragments (activity-scoped). App list
                           loading, app launching, screen-time queries, weather,
                           WorkManager scheduling, and selectedApp(flag) dispatch.

ui/
  HomeFragment.kt          Home screen: pinned apps, clock/date, gestures, folders,
                           dynamic fitting of app rows.
  HomeWidgetController.kt  The entire multi-widget system (pick/bind/configure/
                           restore/resize/reorder/remove). Created in
                           HomeFragment.onViewCreated, released in onDestroyView.
  AppDrawerFragment.kt     App list + omnibox search (apps / calculator / unit
                           conversion / dial / web / contact). The active mode
                           is a single OmniboxMode (sealed, declared here).
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
  BadHabitDialogs.kt       Shared "limit reached" warning + time-limit picker.
  FocusModeDialog.kt, ScreenTimeGraphDialog.kt, ScreenTimeLimitDialog.kt,
  GestureLetterOverlayView.kt, ScreenTimeGraphView.kt, Onboarding*.kt

helper/
  One object per feature, each owning its own SharedPreferences keys:
  FocusModeManager, AppLimitManager, FolderManager (class),
  GestureLetterManager, SwipeUpAppManager, WeatherManager (Open-Meteo),
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
  Prefs.kt                 Typed facade over the single SharedPreferences file,
                           plus JSON export/import (settings backup).
  Constants.kt             Flags, URLs, enum-ish objects.
  AppModel.kt, FolderData.kt, DrawerCharacterModel.kt

listener/
  OnSwipeTouchListener, ViewSwipeTouchListener (gesture detection), DeviceAdmin.
```

## Conventions

- **Storage**: everything is SharedPreferences in the single file `"com.parem.launcher"` (`Prefs.PREFS_NAME`). Core launcher state goes through `data/Prefs.kt`; feature managers in `helper/` read/write their own keys directly. When adding a key that must survive settings export/import with a specific type, register it in `Prefs.LONG_PREF_KEYS`/`FLOAT_PREF_KEYS`, and add device-specific keys to `exportExcludeKeys`.
- **Bottom sheets**: build them with `ui/BottomSheetMenu`. Don't hand-assemble LinearLayouts.
- **App launching**: goes through `MainViewModel.selectedApp(appModel, flag)`; the `flag` constants in `Constants` say what the selection means (launch, set home slot N, set swipe app, …). The app drawer is reused for every "pick an app" flow via these flags.
- **Fragments** hold `_binding` nullable + `binding` accessor; async callbacks must check `isAdded` / `_binding != null` (or `isActive()` in HomeWidgetController) before touching views.
- **Home app slots** are 1-8, stored per-slot via `prefs.getHomeApp*(slot)` / `setHomeApp*(slot, …)`.

## Traps a new maintainer should know

1. **The invisible `lock` view is load-bearing.** `HomeFragment`'s `R.id.lock -> {}` click handler looks dead but isn't: clicking that FrameLayout emits an accessibility event which `MyAccessibilityService` matches *by contentDescription* to run `GLOBAL_ACTION_LOCK_SCREEN`. Renaming `lock_layout_description` or removing the view breaks double-tap-to-lock on Android 9+.
2. **Widget IDs are stateful three ways**: the AppWidgetHost allocation, `prefs.widgetIds` (CSV), and `prefs.widgetProviders` (id:component map used to re-bind widgets that the OS invalidated). `HomeWidgetController.restoreWidgets()` reconciles them; keep all three in sync when touching widget code.
3. **`FLAG_SET_HOME_APP_1..8` are the literal ints 1..8**, and the app-drawer "rename" path writes `prefs.setHomeAppName(flag, …)` using the flag as the slot number.
4. **Screen-time numbers come from `helper/usageStats/`**, a hand-rolled UsageEvents aggregator (see `UnmatchedCloseEventGuardian`), not `queryUsageStats` — Android's summary API is wildly inaccurate.
5. **The app list is cached against a package-change stamp** (`helper/PackageChangeTracker`); any new cache of package-derived data must check that stamp too — bypassing the tracker will serve stale data after installs/uninstalls/updates.

## Build & release

- Debug: `./gradlew assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (package id gets a `.debug` suffix so it installs alongside release). CI: `.github/workflows/debug-build.yml` runs tests + build on every master push (or manually via workflow_dispatch) and uploads the APK as an artifact. Note the CI debug keystore differs per run — updating a CI-built debug install usually needs an uninstall first.
- Every behavior-changing PR adds an entry under `## [Unreleased]` in CHANGELOG.md; at release time, rename that section to the version and prepend a fresh `## [Unreleased]`.
- Release: bump `versionCode`/`versionName` in `app/build.gradle`, add `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` and a CHANGELOG.md entry, then push a tag matching `v*` → `.github/workflows/release.yml` builds a signed APK (keystore comes from repo secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) and attaches it to a GitHub release.
- Dependency versions live in `gradle/libs.versions.toml`. AGP 8.9.1 / Gradle 8.11.1 / Kotlin 2.1.20; JDK 17+ (21 works).

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
- `Constants.URL_ABOUT_PAREM` / `URL_PAREM_PRIVACY` / `URL_DOUBLE_TAP` are empty; their settings rows are hidden until filled in.
- Gesture-letter drawing and swipe gestures both see the same touch stream; a fast letter draw can also register as a swipe.
- JVM unit tests exist only for pure logic (`app/src/test`: evaluator, search
  matcher, sunrise calculator — `./gradlew testDebugUnitTest`); everything
  touching Android still relies on `assembleDebug` plus manual runs.
