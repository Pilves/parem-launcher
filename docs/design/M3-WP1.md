# M3-WP1 — Release smoke tests on a CI emulator (design proposal)

Status: proposal, awaiting Patric's sign-off. Branch base: `origin/chore/agent-harness`. No code changed, no gradle run.

## Problem

Nothing automated checks that the APK still works as a launcher. The JVM suite (`app/src/test`) covers only pure
`helper/` logic. Today four regressions would only show up in Patric's device pass: the HOME intent no longer
resolves to us, swipe-up stops opening the drawer, omnibox submit stops launching apps, and export/import loses
or changes the type of a pref (`Prefs.importFromJson` guesses types from JSON, so a `Long` can come back as an `Int`).
The project has no `app/src/androidTest/`, no `testInstrumentationRunner` and no androidTest dependencies.
`docs/RELEASE_CHECKLIST.md` is not on this branch yet (M1-WP1).

## Options

**A. `reactivecircus/android-emulator-runner@v2` (v2.38.0) on `ubuntu-latest` with KVM.** This is the standard choice.
Standard 2-vCPU Linux runners have had KVM since 2024-04
([GitHub changelog](https://github.blog/changelog/2024-04-02-github-actions-hardware-accelerated-android-virtualization-now-available/)).
You turn it on with the udev snippet from the [action README](https://github.com/ReactiveCircus/android-emulator-runner).
Estimated time is about 3–4 min for the build, 2–4 min for a cold emulator boot and under 1 min for the tests: **about 8–12 min per run**.
Cost is **$0**, because `Pilves/parem-launcher` is public and standard runners are free for public repos
([Actions billing](https://docs.github.com/en/billing/concepts/product-billing/github-actions)). If the repo were private,
a run would cost about $0.06 at $0.006/min, out of 2,000 free minutes.

**B. Option A plus an AVD snapshot cache** (`actions/cache`, as the README recommends). This saves maybe 1–3 min per run.
It costs a second workflow step, cache-key upkeep, and one more way to fail when a snapshot is stale. Not worth it at release cadence.

**C. Gradle Managed Devices** (`./gradlew pixelApi34DebugAndroidTest`). The devices are declared in gradle and can run locally. They still
need KVM in CI, the gradle config is heavier, and they don't help on the Pi, which is ARM64 and can't run an x86_64 emulator anyway.

**D. Firebase Test Lab.** This uses real devices, but it needs a Google Cloud service-account secret and carries a quota and billing risk. Too much for four tests.

## Recommendation

**Option A without caching**, in its own workflow. It runs on `pull_request` to `master` and on `workflow_dispatch`, so Patric can
run it on the RC commit before tagging. It does not run on tag push, because `release.yml` would already be publishing by then.
Emulator: **`api-level: 34`, `target: google_apis`, `arch: x86_64`**. This is a mature image that is common in CI, and it is
well above minSdk 24. API-36 behaviour belongs to M2-WP1's device pass, not here. Add a second api level only if a
real regression ever slips through. Tests use **UiAutomator** (cross-app: HOME press, foreground package) plus plain JUnit for
the prefs test. No Espresso: the four flows don't need in-process view assertions.

## Exact scope

- `app/build.gradle`:
  - `defaultConfig`: add `testInstrumentationRunner "androidx.test.runner.AndroidJUnitRunner"` and
    `testInstrumentationRunnerArguments clearPackageData: 'true'`.
  - `android`: add `testOptions { execution 'ANDROIDX_TEST_ORCHESTRATOR'; animationsDisabled true }`.
  - Dependencies: `androidTestImplementation` runner 1.7.0, ext-junit 1.3.0, uiautomator 2.4.0, plus `androidTestUtil` orchestrator 1.6.1
    ([androidx.test releases](https://developer.android.com/jetpack/androidx/releases/test),
    [uiautomator](https://developer.android.com/jetpack/androidx/releases/test-uiautomator),
    [orchestrator setup](https://developer.android.com/training/testing/instrumented-tests/androidx-test-libraries/runner)).
- `gradle/libs.versions.toml`: the four entries above (repo convention: versions live there).
- `app/src/androidTest/java/com/parem/launcher/SmokeTest.kt`, with one `@Before` and three tests:
  - `@Before`:
    - Seed prefs through `Prefs(targetContext)`: `firstOpen = false`, so `MainActivity` doesn't fire `resetLauncherLiveData` and open the role chooser.
      Also set `onboardingVersionSeen = Constants.ONBOARDING_VERSION`, so `HomeFragment` doesn't navigate to onboarding.
    - Run `cmd package set-home-activity <pkg>/com.parem.launcher.MainActivity` through `UiAutomation.executeShellCommand`.
    - Call `pressHome()`.
  - Selectors: resolve every resource-id from the app's own resource table rather than hard-coding a package.
    `val homeId = targetContext.resources.getResourceName(R.id.homeAppsLayout)` (likewise `R.id.search`), then `By.res(homeId)`.
    The debug variant has `applicationIdSuffix ".debug"` but `namespace 'com.parem.launcher'`, so whether accessibility
    reports `com.parem.launcher.debug:id/…` or `com.parem.launcher:id/…` is not something to guess; `getResourceName` returns
    whatever the installed APK actually uses. The foreground-package check still uses `targetContext.packageName` (`.debug`).
  - `launcherIsHome`: the foreground package equals `targetContext.packageName`, and `By.res(homeId)` is visible.
  - `swipeUpOpensDrawer`: swipe up mid-screen, then wait for `By.res(searchId)`.
  - `omniboxLaunchesApp`: open the drawer, then type into the **inner query field**, not `R.id.search`. `R.id.search` is an
    `androidx.appcompat.widget.SearchView` container (`layout/fragment_app_drawer.xml:16`), and `setText` on it does nothing.
    Target `By.focused(true)`: on open the drawer calls `binding.search.showKeyboard(prefs.autoShowKeyboard)`, which defaults to true and
    `requestFocus()`es into the inner field (`helper/Extensions.kt:34`). If nothing is focused, fall back to
    `By.res(<pkg>:id/search_src_text)`, where `<pkg>` is the package part of `searchId` (appcompat's id is merged into the app's
    R). Type `Settings`, press Enter, then wait for `com.android.settings` in the foreground.
    Auto-launch may fire before Enter does. Either path ends in Settings.
- `app/src/androidTest/java/com/parem/launcher/PrefsRoundTripTest.kt`:
  - Seed one value of each type. The seed values are pinned, because they decide whether the type guards are exercised at all:
    - `FIRST_OPEN_TIME = 1000L` (a Long that fits in an int). Android's `org.json` parses `1000` back as `Integer`, so without the
      `LONG_PREF_KEYS` guard `importFromJson` takes the `value is Int -> putInt` branch. A realistic `System.currentTimeMillis()`
      would parse as `Long` and land in `value is Long -> putLong` (`data/Prefs.kt:543`), so the guard would never be tested.
    - `TEXT_SIZE_SCALE = 2.0f` (an integral float). Export writes it as `2.0` double, `JSONObject.toString()` writes that as `2`, and
      it parses back as `Integer`. Without the `FLOAT_PREF_KEYS` guard it becomes `putInt`. A value like `1.5f` would parse as `Double`
      and land in `value is Double -> putFloat`, which hides the guard.
    - Plus a string, a boolean, a string set, and an excluded key (`FOCUS_MODE_END_TIME`).
  - Pass the export through `toString()` and `JSONObject(...)`, which mimics the real file path. This step is required: an in-memory
    `JSONObject` still holds `Double`/`Long`, so the guards would never be exercised. Overwrite the prefs, call `importFromJson`, then
    assert, **for the seeded keys only** (not `prefs.all`), that each value and its runtime type in `prefs.all[key]` match. Process-written
    keys such as app-start bookkeeping would otherwise make the test brittle. Also assert that the excluded key keeps its local value.
- `.github/workflows/smoke.yml`: checkout, setup-java 17 (as in `debug-build.yml`), the KVM udev step, then the emulator-runner
  with `disable-animations: true` and `script: ./gradlew connectedDebugAndroidTest`. Upload
  `app/build/reports/androidTests/` if the run fails.
- No new prefs, strings or manifest entries in `src/main`.

## Done criteria

- `smoke.yml` is green on a PR, and green twice in a row on `workflow_dispatch` against the same commit as a basic flake check.
- Each test fails when its target is broken on purpose, checked once locally and noted in the PR. Examples: remove the HOME category,
  or drop a key from `LONG_PREF_KEYS`. For the prefs test, both mutations must fail it: removing `FIRST_OPEN_TIME` from
  `LONG_PREF_KEYS` (comes back as `Integer`), and removing `TEXT_SIZE_SCALE` from `FLOAT_PREF_KEYS` (comes back as `Integer`).
- `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` all exit 0. `compileDebugAndroidTestKotlin` also exits 0.
- Once M1-WP1 lands, `docs/RELEASE_CHECKLIST.md` drops the items these tests cover.

## Risks & traps

- **Trap #1 (lock view):** untouched. No test double-taps, because the accessibility service isn't enabled on the emulator.
- **Trap #2 (widget IDs):** untouched. The round-trip test must not assert on `WIDGET_*` keys. They are in `exportExcludeKeys` on purpose.
- **Self-recreate:** the 4-hour self-recreate is gone (PAREM-108), but `MainActivity.checkTheme()` can still `recreate()` up to twice
  within about 200 ms of start. Tests must wait for views with `Until` and timeouts and never grab them right after HOME.
- **Gesture conflicts (PAREM-107):** gesture letters are off on fresh prefs, so the swipe is clean. Don't enable them in the seed.
- `clearPackageData` wipes prefs before each test, which is why `@Before` reseeds them. Running `connectedDebugAndroidTest` against
  Patric's own phone wipes the **debug** variant's prefs only. Release stays untouched because of the `.debug` suffix.
- **Animations off vs. upstream #713:** running with animations disabled overlaps the known upstream bug "drawer not closing with
  animations off" (`7e69731`, #713), which M2-WP2 ports. Only `omniboxLaunchesApp` leaves the drawer by launching an app; no test
  depends on the drawer closing. If M2-WP2 hasn't landed and a test flakes on drawer state (for example, the drawer is still open
  after HOME in the next test's `@Before`), suspect #713 before the test.
- Flakiness controls: animations off (emulator plus `animationsDisabled`), the orchestrator isolates each test, no `sleep`, a
  10 s `wait` on every UI condition, `-no-snapshot` for a clean boot, and no automatic retries (a retry would hide real flakes).

## Test plan

- **JVM:** none new. Type-guessing coverage is M3-WP3's job. This WP exercises the real `SharedPreferences`.
- **CI emulator:** the 4 instrumented tests above.
- **Device:** Patric runs nothing new. The checklist shrinks.

## Not verified

- The exact time per run on `ubuntu-latest`. The 8–12 min figure is an estimate. The first run gives the real number.
- Whether `pm clear` (orchestrator) resets the default-home role. `@Before` reapplies it either way.
- Whether `google_apis` x86_64 API-34 images still download cleanly through the action today.
- That the Enter-key path picks Settings first, given `SearchMatcher` ranking on an emulator's app set.
- The resource-id package seen by UiAutomator (`com.parem.launcher.debug` or `com.parem.launcher`). The selectors sidestep this
  through `getResourceName`, but the first run should log the resolved names to confirm it.
- That focus has landed on the inner field by the time the drawer is visible on the emulator, so `By.focused(true)` finds it
  (wait on it with `Until`; the `search_src_text` fallback covers it if not).
- That `set-home-activity` works without a prompt on API 34 (`cmd role add-role-holder android.app.role.HOME` is the fallback).
