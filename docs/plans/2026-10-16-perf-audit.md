# Performance audit — M3-WP10 "lightweight and fast"

Date: 2026-10-16. Branch `lane/perf` from `next` @ 504f17b. No device or
emulator was available on the build host (Pi 5), so everything below is
static reading plus local builds; the device numbers are the owner's to
record in `docs/RELEASE_CHECKLIST.md` → Performance.

## 1. Weight

Measured locally with `./gradlew assembleRelease` (unsigned; the signing
config is only attached when `KEYSTORE_FILE` is set, so the unsigned release
build is the real R8 + shrinkResources output). The signed APK on a release
is the same bytes plus a ~1–2 KB signing block.

| | bytes | MiB |
|---|---|---|
| before (504f17b) | 2,976,915 | 2.84 |
| initial draft (before review) | 2,945,283 | 2.81 |
| final reviewed change | pending rebuild | pending |

What is inside (before): `classes.dex` 2.05 MB uncompressed, `resources.arsc`
0.60 MB (19 locales, already filtered by `resourceConfigurations`), ~0.2 MB
of compiled layouts/drawables, no `assets/` of our own, no native libs, no
fonts, no vendored web assets (`web/` is the Pages site, outside `app/`).

Findings:

- Dependencies (`:app:dependencies --configuration releaseRuntimeClasspath`):
  nine direct deps — core-ktx, appcompat, recyclerview, lifecycle-viewmodel-ktx,
  navigation-fragment-ktx, work-runtime-ktx, material, viewbinding, stdlib.
  All used. Material is the heaviest (Snackbar, BottomSheetDialog, switches)
  and R8 already strips the rest of it. No kotlin-reflect, no support libs,
  nothing to remove.
- R8: AGP 8.9 runs full mode by default (`android.enableR8.fullMode` is not
  set to false anywhere); `proguard-android-optimize.txt` + two worker keeps;
  obfuscation on (`-dontobfuscate` absent); `LineNumberTable` kept so crash
  reports (M3-WP11) stay readable. Nothing to change.
- shrinkResources: on. No large drawables survive; the two biggest resources
  are the settings layouts (88 KB + 60 KB source XML — the known portrait/
  landscape duplication from the ponytail audit, an owner decision).
- Packaged junk the shrinker does not touch: `kotlin/**.kotlin_builtins`
  (32 KB, only read by kotlin-reflect), `DebugProbesKt.bin` (coroutines
  debugger), `META-INF/*.version`, build stamps.
  **Changed:** `packaging.resources.excludes` in `app/build.gradle`.
- `android.enableJetifier=true` with no support-library dependency: build-time
  only, no APK effect. **Changed** to `false` (faster sync/builds).

Deferred (not safe or not worth it without a device):

- Locale trimming beyond `resourceConfigurations` (the arsc is 20% of the
  APK): the 19 locales are shipped on purpose.
- App Bundle / per-density splits: Play will do this itself for 6.0; the
  GitHub/F-Droid APK stays universal.
- The portrait/landscape settings layout duplication (~150 KB source, far
  less compiled): owner decision from M4-WP42.

## 2. Speed (cold start, return home, main-thread stalls)

Cold path read: `ParemApp.onCreate` → `MainActivity.attachBaseContext/onCreate`
→ nav inflation → `HomeFragment.onViewCreated` → `onResume`. Already off the
main thread before this WP (PAREM-115/117, M4-WP11): the raw LauncherApps
query, per-app usage, weather fetch, `isParemDefault`, widget info lookup,
clock-app defaulting. `fragment_home.xml` is 23 views, depth 5 — nothing to
flatten.

What was still synchronous on the main thread at start, and what changed:

| site | cost | change |
|---|---|---|
| WorkManager `androidx.startup` initializer in `Application.onCreate` | builds `WorkManagerImpl`, schedulers and the Room handle on every launch of the home process, although no code path touches WorkManager at start (themes/wallpaper/focus-end only enqueue from settings) | **on-demand init**: `ParemApp : Configuration.Provider`, `WorkManagerInitializer` removed from the merged manifest. Scheduled jobs still fire: `SystemJobService` initialises WM through `getInstance` |
| Home-slot icon loading | cold icon loads remain synchronous | **Deferred after review**: the draft IO path could repopulate cleared caches after a package change and apply an old icon pack after settings changed. Preserving trap 5 needs generation-aware fills and device regression coverage; no async icon/cache API change ships here. |
| StrictMode | none existed | debug-only `detectAll().penaltyLog()` thread + VM policies in `ParemApp` (logcat tag `StrictMode`, never a crash) |

Read and left alone, with why:

- `isPackageInstalledCached` falls back to one `getActivityList` IPC per
  slot while the raw snapshot is still cold (first ~100 ms of a cold start).
  Trusting the pref and re-populating when the list arrives would flash an
  uninstalled app's slot; the IPC is ~1 ms each. Candidate for a device
  measurement first.
- `GrayscaleController.reconcile` runs three times per cold start (activity
  onCreate, activity onResume via `setAppForeground`, fragment onResume).
  Each is prefs + `Settings.Secure` reads (client-cached after the first).
  Collapsing them touches the grey/colour state machine (M4-WP21) for a
  sub-millisecond gain.
- `CrashReporter.pending`: one `File.exists()` in onCreate. StrictMode will
  flag it; it is a stat on no-backup storage, not worth a thread hop.
- `checkTheme()` 200 ms delayed `recreate()` (up to twice) when resolved
  colours disagree: a real double cold start on misconfigured themes. Already
  capped; fixing the cause is M4 theme work, not perf.
- `populateDateTime` runs twice on first resume (populate + observer) and
  reads the sticky battery intent each time; cheap, left.
- Drawer rows (`AppDrawerAdapter` bind) decode icons synchronously on a
  cache miss — the likely source of the 8.4% drawer-scroll jank baseline.
  Making RecyclerView binds async (placeholder + cancel on rebind) is a
  bigger change than this WP; tracked below.

## 3. Budget wiring

Already in `next` from `ci/perf-baseline` (merged): `scripts/perf-budget.mjs`
drives `am start -W` (cold ×10, return-from-Settings ×10) and
`dumpsys gfxinfo` (drawer scroll, omnibox typing), compares with
`scripts/perf-baseline.json`, fails the `pixel_6`/API 34 smoke leg on a
> 20% regression above a noise floor, and prints the mid-range budgets
(cold < 300 ms, return < 100 ms, jank < 1%, frame ≤ 32 ms) as "over budget
(not gated)" because the runner is a software-rendered emulator.

This WP adds to `docs/RELEASE_CHECKLIST.md` → Performance: a device table
(tablet / phone / budget, with the APK size row) and the raw adb commands
that reproduce every metric by hand, plus the StrictMode logcat grep for a
debug build.

Not done: a device baseline file. Until the owner records tablet numbers
the emulator baseline is the only gate; the budgets stay report-only.

## 4. Follow-ups (not in this WP)

1. Async drawer-row icons (jank budget). Needs device frame numbers first.
2. Rerun the smoke `perf` leg ×3 after this merges and refresh
   `perf-baseline.json` if cold start moved (expected: a little faster from
   the WorkManager change; no icon-threading change ships).
3. Baseline Profile: `assembleRelease` already emits
   `baselineProfiles/` from the AGP default (`assets/dexopt/baseline.prof`,
   2 KB — the library-provided rules only). A Macrobenchmark-generated
   profile for home + drawer is the single biggest remaining cold-start win
   on a real phone, and needs a device or a CI emulator with
   `androidx.benchmark`; a new module, so a separate package.
