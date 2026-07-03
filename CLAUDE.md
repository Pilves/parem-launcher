# Parem Launcher — working notes

Read ARCHITECTURE.md first: code map, conventions, and the "Traps" section
(the invisible lock view, the 4-hour self-recreate, widget ID bookkeeping).

## Rules

- Build-verify every change: `./gradlew compileDebugKotlin` for a quick check,
  `./gradlew assembleDebug` before calling anything done. There are no tests.
  A pipeline like `./gradlew … | tail` reports tail's exit code — check gradle's.
- Bottom sheets go through `ui/BottomSheetMenu`; app-launch/selection flows go
  through `MainViewModel.selectedApp(model, flag)`.
- All state is SharedPreferences file `"com.parem.launcher"`. New exported keys
  need type registration in `Prefs` (see LONG_PREF_KEYS / exportExcludeKeys).
- Async/posted callbacks in fragments must guard on `isAdded` / `_binding != null`.
- Strings: new user-facing text goes in `res/values/strings.xml`; mark it
  `translatable="false"` unless you also add translations.
- Release = bump versionCode/versionName in app/build.gradle, tag `v*`, push;
  CI signs and publishes. Never commit keystores.
