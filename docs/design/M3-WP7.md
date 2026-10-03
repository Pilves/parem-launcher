# M3-WP7 — E-ink mode: skip animations on e-ink displays

Status: proposal (design first, awaiting Patric). Upstream: `a9da9d4`, building on `fc5b37f` and `7e69731`.

## Problem

On e-ink, every frame of a fade or slide forces a partial refresh and leaves ghosting, so motion that is free on LCD/OLED costs time and image quality there. Parem today:

- `isEinkDisplay()` (`helper/Extensions.kt:98`) checks the **current** refresh rate `<= MIN_ANIM_REFRESH_RATE` (10f). Boox devices report 60 Hz, so they are missed (upstream comment in `a9da9d4`). Adaptive-refresh LCD/OLED panels can drop the current rate low and get detected as e-ink (`fc5b37f`, upstream #724).
- Only one animation respects it: the drawer `layoutAnimation` (`AppDrawerFragment.kt:372`). Still animated on e-ink: nav-graph fragment transitions (fade/slide on every action in `nav_graph.xml`), `View.animateAlpha` (`helper/Utils.kt:113`, used by the gestures card), drawer overscroll glow, and BottomSheetMenu's slide-in.
- A false positive is expensive here: `MainActivity.onCreate:77` overwrites `prefs.appTheme` with light mode **on every launch** when `isEinkDisplay()` is true. WP7 changes this to a one-time default (see Open questions).

## Options

**A. Port `a9da9d4` verbatim.** Brand/model list + Onyx class probe + max-supported-mode check, cached per process; skip `animateAlpha`; drawer `OVER_SCROLL_NEVER`; 0-duration animator in `BaseFragment.onCreateAnimator`. Cost: Parem has no `BaseFragment` (it comes with `7e69731`, an M2-WP2 port candidate) and leaves sheets animated. Small, upstream-aligned diffs.

**B. A + one shared gate + BottomSheetMenu (recommended).** Same detection, but expose `Context.skipAnimations() = isEinkDisplay() || isSystemAnimationsDisabled()` and use it at the four places that animate. Also turn off the sheet's window animation for every bottom sheet: a `BottomSheetDialog.disableAnimationsOnEink()` extension next to `transparentSheetFrame()`, called from `BottomSheetMenu.show()` and the six places that build a `BottomSheetDialog` directly. Cost: about 4 lines more than A, plus one call at each of 7 sheet sites. Users whose e-ink device isn't detected get a manual escape hatch for free: set animator scale to 0 in system settings.

**C. B + a user toggle ("Reduce motion" pref + settings row).** Covers misdetection without developer options. Cost: a pref, export registration, a settings-card row, 2 strings, and a decision on how it interacts with system scale. YAGNI until someone reports a missed device.

**D. Also strip `animateLayoutChanges`.** About 25 XML sites in settings and drawer layouts. Their `LayoutTransition` already respects the system animator scale. A lot of churn for little gain on an e-ink phone where settings are rarely opened. Rejected.

## Recommendation

**B.** It is the minimum that covers every animation a Parem user sees on a normal path (open drawer, open settings, open a menu or dialog sheet). It also reuses the system setting as the override instead of adding a pref.

**One gate, one exception.** Every animation site checks `skipAnimations()`, including the sheet window animation. The sheet uses the shared gate too, not `isEinkDisplay()` alone. The system zeroes window animations only when *window* scale is 0. The escape hatch in Option B is *animator* scale 0, and `isSystemAnimationsDisabled()` (from `7e69731`) is true when any of the three scales is 0. Gating sheets on `isEinkDisplay()` would leave sheets sliding for exactly the users who took that escape hatch. The only e-ink-specific branch is the drawer's `OVER_SCROLL_NEVER`. The overscroll glow is not an animator and no system scale removes it. It only matters on e-ink, where it causes a partial refresh.

**Dependency:** WP7 must land after M2-WP2 has ported `fc5b37f` (detection fix) and `7e69731` (`BaseFragment` + `isSystemAnimationsDisabled`). If M2-WP2 rejects `7e69731`, WP7 adds the `onCreateAnimator` override itself (one small `BaseFragment`, 4 fragments re-parented). Do not duplicate whatever M2-WP2 ports.

## Exact scope

| File | Change |
|---|---|
| `helper/EinkDetector.kt` (new, Android-free `object`) | `isEinkBrand(brand, manufacturer, model): Boolean` (upstream's list: onyx, boox, dasung, bigme, boyue, meebook, mudita; Hisense only for models matching `\bA[579]\b\|TOUCH\|HI READER`) and `isEinkRefreshRate(maxSupportedHz: Float)` |
| `helper/Extensions.kt` | `isEinkDisplay()` = cached `(refresh-rate check via max of display.supportedModes) \|\| isOnyx \|\| EinkDetector.isEinkBrand(Build.*)`. Keep Parem's existing API-30 `context.display` branch. New `Context.skipAnimations()` |
| `helper/Utils.kt` `animateAlpha` | if `context.skipAnimations()` set `alpha` directly and return |
| `ui/AppDrawerFragment.kt` ~372 | `isEinkDisplay()`: `overScrollMode = OVER_SCROLL_NEVER` (the one e-ink-only branch, see Recommendation); layoutAnimation only when `!skipAnimations()` |
| `ui/BaseFragment.kt` (from M2-WP2a) | `onCreateAnimator` condition uses `skipAnimations()` |
| `ui/BottomSheetMenu.kt` | new `fun BottomSheetDialog.disableAnimationsOnEink()` next to `transparentSheetFrame()` (line 20): `if (context.skipAnimations()) window?.setWindowAnimations(0)`. The name follows the e-ink theme of the WP; the gate is `skipAnimations()`. Called in `show()` before `dialog.show()` |
| `ui/CreateFolderDialog.kt:40`, `ui/HomeWidgetController.kt:556`, `ui/home/HomeGesturesController.kt:335` | `dialog.disableAnimationsOnEink()` next to the existing `transparentSheetFrame()` call (lines 155, 640, 347) |
| `ui/FocusModeDialog.kt:40`, `ui/ScreenTimeLimitDialog.kt:28` (subclasses) | `disableAnimationsOnEink()` next to their `transparentSheetFrame()` call (lines 45, 157) |
| `ui/settings/WeatherSettingsDialog.kt:42` (subclass) | `disableAnimationsOnEink()` in its setup. It has no `transparentSheetFrame()` call to sit beside. Do not add one, because that is a visual change outside this WP |
| `data/Prefs.kt` | `val hasAppTheme: Boolean get() = prefs.contains(APP_THEME)`. Reads an existing key, so no export registration |
| `MainActivity.kt:77` | `if (isEinkDisplay() && !prefs.hasAppTheme) prefs.appTheme = MODE_NIGHT_NO`. See Open questions, which gives this as the default |
| `app/src/test/.../helper/EinkDetectorTest.kt` (new) | see test plan |
| `CHANGELOG.md` | `[Unreleased]` line |
| `ARCHITECTURE.md` Conventions | one line: sheets built outside `BottomSheetMenu` call `disableAnimationsOnEink()` |

No new prefs, strings, or manifest entries. Every `BottomSheetDialog` in `app/src/main` at the time of writing is in the table (`grep -rn "BottomSheetDialog("` gives 7 hits). A sheet added later has to call the extension itself. That is a convention, not something the code enforces, so add one line to ARCHITECTURE.md Conventions next to the existing "bottom sheets go through BottomSheetMenu" rule.

## Done criteria

- `compileDebugKotlin`, `testDebugUnitTest`, `assembleDebug` exit 0.
- `EinkDetectorTest` green.
- Device step 2 passes: with animator, transition **and** window scale at 0, the drawer, settings, all bottom sheets (BottomSheetMenu and the six direct ones) and the gestures-card dim are instant, and the drawer still closes after launching an app (the #713 regression).
- Device step 2b passes: with **only** animator scale at 0, sheets still open with no slide. At window scale 1 the system would animate them, so this is the one stand-in check that proves WP7's own `skipAnimations()` sheet branch ran.
- On a normal phone with animations on, behaviour is unchanged (step 1). A dark-theme user on a non-e-ink phone keeps dark (step 3).
- **What this does not prove.** The stand-in exercises `skipAnimations()` through `isSystemAnimationsDisabled()` only. It never runs `isEinkDisplay()` returning true. Brand/Onyx/refresh-rate detection, the drawer's `OVER_SCROLL_NEVER` and the first-run theme default on e-ink are not covered on a device. The detection *logic* is covered by `EinkDetectorTest`. The *wiring* (feeding `Build.*` in, the Onyx probe, the e-ink-only branches) is covered only by device step 4. If no e-ink device is available, the release row must list those as **unverified**, not as passed.

## Risks & traps

- **Broader detection widens the theme override.** If `MainActivity:77` stayed as it is, Boox/Hisense/Mudita users who chose dark mode would be forced to light on every launch. WP7 gates the override on the pref being unset (Open questions), so a false positive costs only a one-time default. Existing users whose devices the old 10 Hz check detected already have the pref written (light) and see no change.
- **Trap #1 (invisible `lock` view):** not touched. Fragment transition changes must not touch `HomeFragment`'s click handlers. Verify double-tap lock still works.
- **Trap #2/#5:** not applicable. No widget-ID or package-derived cache changes. The detection cache is per process; a display doesn't change type at runtime. External displays and foldables are ignored on purpose.
- **Drawer exit by overscroll** is Parem's own `scrollVerticallyBy` override, not the glow. `OVER_SCROLL_NEVER` should not affect it, but this needs a device check.
- `Class.forName("android.onyx.ViewUpdateHelper")` runs once per process and is wrapped in a catch for `Throwable`. Cheap, but it is a reflective probe.
- `BottomSheetDialog` may also animate via `BottomSheetBehavior` settling. `setWindowAnimations(0)` may not remove all motion. Not verified.
- `ThemeScheduleWorker` (`helper/ThemeScheduleWorker.kt:43`) can still switch an e-ink user to dark on schedule. That is existing behaviour and the user opted into it. Out of scope.
- New sheets that skip `disableAnimationsOnEink()` will slide again. The ARCHITECTURE.md convention line is the only guard.

## Test plan

**JVM (`EinkDetectorTest`):**
- Each e-ink-only brand matches in either the brand or the manufacturer field.
- Hisense A5/A7/A9/"A9 PRO"/"HI READER" match; Hisense "H60" and "INFINITY H50" don't.
- Samsung/Google don't match.
- `isEinkRefreshRate`: 10f true, 10.1f false, 60f false.

**Device (Patric):**
1. Pixel/OLED with animations on: unchanged.
2. Same phone with animator, transition and window scale at 0: drawer, settings, every sheet (one BottomSheetMenu sheet, e.g. app long-press, plus create folder, widget menu, gesture picker, focus mode, screen-time limit, weather settings) and the gestures-card dim are instant. The drawer closes after launching an app. Double-tap lock still works (Trap #1). Most of this motion the system removes by itself at these scales, so step 2 shows nothing regresses. It does not show that WP7's code ran.
   2b. Reset window and transition scale to 1 and keep animator scale at 0. Open two sheets: no slide. This step exercises WP7's sheet branch.
3. An adaptive-refresh phone left idle, theme set to dark: cold start keeps dark (not detected, or if misdetected, the pref is already set).
4. A real e-ink device (Boox / Hisense / Mudita): detected. With animations at system defaults, drawer/settings/sheets are instant, with no overscroll glow and no ghosting from fades. A fresh install starts light. After switching to dark and cold-starting, it stays dark. **This is the only step that covers the e-ink branches.** Without the device they ship unverified (see Done criteria).

## Not verified

- Whether `setWindowAnimations(0)` fully removes the BottomSheetDialog slide in Material Components.
- The upstream brand list and Onyx class name. Taken from `a9da9d4`, not checked against device dumps.
- Whether `supportedModes` max rate is actually ≤10 Hz on any shipping e-ink device. If none, the refresh-rate check is dead weight but harmless.
- All on-device behaviour above, and the e-ink-only branches in particular unless step 4 runs. Gradle was not run (other lane).

## Open questions

**Q1 — `MainActivity.kt:77` forced light theme.** Broader detection makes this override hit more devices, so it needs an explicit decision. Options:

| Option | Behaviour | Cost |
|---|---|---|
| a. Keep as is | Every cold start on a detected device writes light | Boox/Hisense/Mudita users who chose dark lose it on every launch. That is a user-visible regression and the opposite of calm, opt-in behaviour. A false positive becomes a persistent override |
| **b. Force only when unset (default)** | `if (isEinkDisplay() && !prefs.hasAppTheme)`: light becomes the first-run default and the user's later choice sticks | One `Prefs` getter and one condition. A false positive costs only a one-time default |
| c. Drop it | No theme change on e-ink | Fresh e-ink installs start in dark (the app default `MODE_NIGHT_YES`), which looks worst on e-ink |

**Default if Patric doesn't answer: (b)**, in scope as listed above. "Unset" means the `APP_THEME` key is absent. The only writers are the settings card, `ThemeScheduleWorker` and import, all of them user actions. A first run therefore gets light, and a user who has ever picked a theme keeps it.

## Review notes

- Two of the issues raised overlap (the missing theme decision and the gate inconsistency each came twice). Both are resolved once above.
- One correction to the "the stand-in never reaches the sheet branch" issue: with the sheet gated on `skipAnimations()`, animator scale 0 *does* reach `setWindowAnimations(0)`, because `isSystemAnimationsDisabled()` checks all three scales. It only becomes observable when window scale stays at 1, which is why step 2b exists. The issues are right that `OVER_SCROLL_NEVER` and detection are never reached without a real e-ink device.
- I did not make step 4 a release gate that drops the WP. The changes are inert on non-e-ink phones apart from the shared `skipAnimations()` path, and steps 1–3 cover that path. Shipping with the e-ink branches marked unverified, and saying so in the release row, is honest and keeps an upstream-aligned fix moving. If Patric prefers the stricter gate, that is a one-line change to the owner task.

Sources: Display API (`getRefreshRate` API 1, `getSupportedModes` API 23, "might include synthetic modes"): https://developer.android.com/reference/android/view/Display. Fragment `onCreateAnimation`/`onCreateAnimator` ("called when onCreateAnimation returns null"): https://developer.android.com/reference/androidx/fragment/app/Fragment. Animator scale settings: https://developer.android.com/reference/android/provider/Settings.Global#ANIMATOR_DURATION_SCALE. Upstream: `git show fc5b37f a9da9d4 7e69731` on `upstream/master`.
