# Ponytail audit — Parem Launcher `app/src/main` (ultra, read-only)

Scope read: all 118 Kotlin files (17.7k lines), manifest, every `res/` file. Traps respected: `R.id.lock` + `lock_layout_description`, widget ID triple store, `PackageChangeTracker` stamp are all untouched below. Tags: `delete:` / `stdlib:` / `native:` / `yagni:` / `shrink:`.

Format: `path:lines | cut | replacement | −lines | risk | proof`

## 1. Dead code and unused resources (risk none/low)

| # | location | what | replaces it | −lines | risk | proof |
|---|---|---|---|---|---|---|
| 1 | `res/values/strings.xml` (98 entries) + `values-*/strings.xml` | `delete:` 98 strings with zero `R.string.`/`@string/`/`"name"` refs in main+test: `about black white follow email donate cool hide copy roadmap public_roadmap affiliate accept permission enable not_working info delete learn_more adapter_show adapter_hide icon_pack no_icon_packs screen_time_limits set_time_limits time_limit_reached minutes_15 minutes_30 hour_1 hours_2 unlimited screen_time_7_days set_swipe_up_app swipe_up_app clear_swipe_up theme_schedule manual light_time dark_time focus_mode_off allowed_apps note write_something clear_note set_time_limit remove_time_limit draw_letter_to_launch assign_app letter_detected app_options grayscale_app_on grayscale_app_off app_text_color welcome_to_parem_settings digital_wellbeing_message tip_start_typing_for_rename accessibility_disclosure system_app_cannot_delete unable_to_launch_app search_for_Launcher_or_home_app remove_widget_confirm remove_folder` + all 41 `onboarding_gestures_* / onboarding_omnibox_* / onboarding_home_* / onboarding_features_* / onboarding_feature_* / onboarding_ready_* / onboarding_skip / onboarding_get_started` (old onboarding). 29 have copies in all 18 translation files. | nothing | ~620 XML | none | grep over `*.kt` + `*.xml` incl. tests; only `getIdentifier` use is IconPackManager on pack resources. `lock_layout_description` is used and NOT in this list. |
| 2 | `res/drawable/rounded_rectangle_dark.xml` | `delete:` | — | ~12 | none | zero refs |
| 3 | `res/values/colors.xml:3` | `delete:` `colorPrimary` color | — | 1 | none | zero refs |
| 4 | `res/layout-land/fragment_settings.xml:668-715, 1004-1012` | `delete:` `appThemeSelectLayout` and `toggleLock` rows | theme picker is BottomSheetMenu in AppearanceSettingsCard | ~55 | low | ids unreferenced; landscape may render a dead row |
| 5 | `res/layout-land/fragment_home.xml` `firstRunTips` | `delete:` | — | ~10 | low | id has no refs |
| 6 | `data/Constants.kt:76-81, 90-95, 107, 117, 127-128, 130, 132-137, 59-62` | `delete:` `FLAG_SET_HOME_APP_2..7`, `FLAG_SET_SWIPE_UP_APP_2..7`, `LONG_PRESS_DELAY_MS`, `URL_DOUBLE_TAP`, `DIGITAL_WELLBEING_SAMSUNG_*`, `THEME_SCHEDULE_WORKER_NAME`, `object FocusTimer`, `object CharacterIndicator` | ranges use `_1.._8` | ~30 | none | zero refs outside Constants |
| 7 | `data/DrawerCharacterModel.kt` | `delete:` file | — | 6 | none | only its own declaration |
| 8 | `data/Prefs.kt:33, 37, 114-116, 364-367, 609-616` | `delete:` `PREFS_FILENAME` alias, `firstHide`, `getWidgetProvider`; `shrink:` `importFromJson` Int branch dead `doubleVal` test | `putInt(key, value)` | ~14 | none | zero refs |
| 9 | `helper/Extensions.kt:204-208` | `delete:` `hasBeenDays`, `hasBeenHours` | — | 6 | none | zero refs |
| 10 | `helper/Utils.kt:47-57, 102-113` | `delete:` `getChangedAppTheme`, `View.animateAlpha` | — | 25 | none | zero refs |
| 11 | `helper/usageStats/EventLogWrapper.kt:256-267, 303-362` + `SimpleUsageStat.kt:19-45` | `delete:` `getForegroundStatsByPartialDay`, `getForegroundStatsByDay`, `getAllSimpleUsageStats`, `getIncrementalSimpleUsageStats`, `aggregateSimpleUsageStats`, `endConsumer` param, `SimpleUsageStat(UsageStats)` ctor, `asSimpleStats`, `getEpochDay`; the `.day` computation in `aggregateForegroundStats:288-290` is never read | callers use `getForegroundStatsByRelativeDay(0)` / `getForegroundStatsByTimestamps` + `aggregateForegroundStats` | ~85 | none | callers: UsageStatsHelper:35-37, ScreenTimeGraphDialog:109-119. Trap #4 untouched. |
| 12 | `helper/FolderManager.kt:101-115` | `delete:` `getAllFolders` | — | 14 | none | zero refs |
| 13 | `ui/AppDrawerAdapter.kt:251-257` | `delete:` `removeApp` | — | 7 | none | zero refs |
| 14 | `ui/GrayscaleSheet.kt:41-43` | `delete:` `startSetup` | — | 3 | none | zero refs |
| 15 | `helper/SwipeUpAppManager.kt:49-56` | `delete:` `clearSwipeUpApp` | — | 8 | none | zero refs |
| 16 | `helper/ThemeScheduleManager.kt:22-24` + `ThemeScheduleWorker.kt:16` | `delete:` `MODE_MANUAL/SCHEDULED/SUNRISE_SUNSET` | `Constants.ThemeScheduleMode.*` | 3 | none | only user is the worker |
| 17 | `helper/WeatherManager.kt:78` + 2 callers | `delete:` `getDisplayString` alias | `getCachedTemp` | 1 | none | alias |
| 18 | `MainViewModel.kt:66` + `MainActivity.kt:270-272, 357-362` | `delete:` `launcherResetFailed` LiveData, observer, `openLauncherChooser` | — | ~12 | none | nothing posts to it |
| 19 | `ui/AppDrawerFragment.kt:600-603` | `delete:` `checkMessageAndExit` wrapper | inline `if (isAdded) findNavController().popBackStack()` | 3 | none | one caller |
| 20 | `ui/home/HomeGesturesController.kt:292-294, 357-359` | `delete:` no-op `onClick()` override; `textOnClick`/`textOnLongClick` delegates | call `fragment.onClick(view)` | 7 | none | base is empty |
| 21 | `helper/usageStats/ComponentForegroundStat.kt:17-27` | `delete:` hand-written `toString` on data class | default | 10 | none | log-only |
| 22 | `data/AppModel.kt:20-24` | `delete:` `Comparable<AppModel>` | `AppListRebuilder` sorts by `key` | 5 | none | only hits are the override |
| 23 | `MainViewModel.kt:424-430` | `delete:` `startThemeScheduleWorker`/`cancelThemeScheduleWorker` | `ThemeScheduleManager.startWorker(ctx)` | 6 | none | delegation |
| 24 | `ui/home/HomeClockController.kt:84-95` | `yagni:` companion locale-cached formatter | `DateTimeFormatter.ofPattern("EEE, d MMM")` inline | 10 | low | — |
| 25 | `ui/AppDrawerAdapter.kt:267-270` | `delete:` default values on `ViewHolder.bind` params | — | 0 | none | single call site |

## 2. Reinvented stdlib / AndroidX

| # | location | what | replacement | −lines | risk | proof |
|---|---|---|---|---|---|---|
| 26 | `ui/HomeWidgetController.kt:422-428, 453-459, 502-508` | `native:` three hand-built `OPTION_APPWIDGET_*` Bundles | `hostView.updateAppWidgetSize(null, w, h, w, h)` | 18 | low | framework fills the bundle |
| 27 | `ui/HomeWidgetController.kt:397-401, 432-436` | `native:` manual parent-chain walk of `requestDisallowInterceptTouchEvent` | call once on `parent` | 8 | low | ViewGroup propagates |
| 28 | `ui/HomeWidgetController.kt:579-585` | `stdlib:` loop over children comparing `tag` | `findViewWithTag<View>(widgetId)?.let(container::removeView)` | 5 | none | — |
| 29 | `CreateFolderDialog.kt:118-124`, `FocusModeDialog.kt:191-197`, `QuietListSheet.kt:125-131`, `WeatherSettingsDialog.kt:114-117`, `HomeWidgetController.kt:760-767` | `native:` 5 anonymous `TextWatcher`s | `doAfterTextChanged { }` (core-ktx) | 20 | none | — |
| 30 | 27 sites in FocusModeManager, GestureLetterManager, AppLimitManager, WeatherManager, ThemeScheduleManager, ContactSearchManager, QuietNotificationsManager, CurrencyRates, AppOpenCounter, FolderManager, ThemeScheduleWorker | `native:` `.edit().putX().apply()` chains | `edit { }` (core-ktx; Prefs and GrayscaleController already use it) | ~30 | none | mechanical |
| 31 | `EventLogWrapper.kt:233-245, 257-265`, `ScreenTimeGraphDialog.kt:134-140` | `stdlib:` 4× Calendar set-to-midnight | existing `Long.convertEpochToMidnight()` (`Extensions.kt:195`) | ~20 | low | identical semantics |
| 32 | `helper/AppListSource.kt:208-213` + `helper/Extensions.kt:143-147` | `shrink:` `if (size > 0) return true; return false`; two `isPackageInstalled` | one `Context.isPackageInstalled(pkg, user)`; `.isNotEmpty()` | 7 | none | Extensions version has 1 caller |
| 33 | `helper/Utils.kt:30-33` vs `helper/Extensions.kt:72-75` | dup `isParemDefault(ctx)` ≡ `Context.isDefaultLauncher()` | keep `isParemDefault` (9 callers) | 4 | none | identical |
| 34 | `HomeGesturesController.kt:320`, `HomeFragment.kt:119-124` | `stdlib:` `try { tag.toString().toInt() } catch` | `toIntOrNull() ?: return` | 6 | none | — |
| 35 | `helper/ThemeScheduleManager.kt:80-85, 92-97, 104-109` | `shrink:` identical light/dark parse ×3 | `private fun scheduledTimes(ctx): Pair<LocalTime,LocalTime>` | 16 | none | — |
| 36 | `data/Prefs.kt:266-280, 341-355, 383-398, 332-339` | `shrink:` three CSV-to-map parsers with caches; `removeWidgetHeight` re-implements the setter | one `parseCsvMap(raw, sep)`; `removeWidgetHeight = setWidgetHeights(parse().minus(id))` | ~28 | low | WIDGET_* semantics unchanged (trap #2) |
| 37 | `FocusModeManager.kt:176-182, 190-195`, `QuietNotificationsManager.kt:80-81`, `AppLimitManager.kt:109-118` | `shrink:` CSV→Set ×4 | existing `MindfulPause.parse(csv)` | ~14 | none | identical semantics |
| 38 | `ui/BadHabitDialogs.kt:45-52`, `ui/ScreenTimeLimitDialog.kt:144-149` | `shrink:` hand-rolled `"${h}h ${m}m"` + untranslated literals | `Context.formattedTimeSpent(ms)` + `R.string.app_menu_no_limit` | ~12 | low | output shape must stay identical or skip |
| 39 | `helper/SystemActions.kt:52-77` | `shrink:` 4× `try { startActivity } catch { Log.e }` | `Context.startOrLog(intent, tag)` | 15 | none | — |
| 40 | `BadHabitDialogs.kt:102-110`, `QuietListSheet.kt:234-239`, `ScreenTimeGraphDialog.kt:162-166`, `ScreenTimeLimitDialog.kt:102-107`, `GestureLetterConfigDialog.kt:49-54`, `HomeWidgetController.kt:617-623` | dup: 6 copies of "label from PackageManager or fallback" | `Context.appLabelOrNull(pkg)` in Utils | ~22 | none | — |
| 41 | `helper/IconPackManager.kt:114-120, 156-160, 140-152` | `shrink:` three catch arms returning null; `cache[..]=; buildPackageIndex()` ×3 | one catch; one `store(pkg, map)` | 12 | none | — |

## 3. Duplicated logic across files

| # | location | what | replacement | −lines | risk | proof |
|---|---|---|---|---|---|---|
| 42 | `FocusModeDialog.kt:182-211`, `QuietListSheet.kt:113-140`, `CreateFolderDialog.kt:109-136` (+ widget picker `HomeWidgetController.kt:671-681, 758-767`) | roadmap item "one shared app-picker search component": search EditText + AppPickerAdapter + 300dp RecyclerView + ADJUST_RESIZE built 3× | `fun appPickerView(ctx, adapter, hint): View` in `ui/` | ~60 | low | copy-identical except padding |
| 43 | `ui/AppDrawerFragment.kt:233-260` vs `522-556` | "act on omnibox mode" switch twice; copy+toast 6× | `private fun actOn(mode: OmniboxMode): Boolean`; `copyResult(mode.result)` | ~35 | low | same actions |
| 44 | `ui/settings/GesturesSettingsCard.kt:191-203, 244-256, 258-269` | three label tables for GestureAction ints; `toggleSwipeLeft/Right` mirror | `actionLabelRes(action)`; `toggleSwipe(isLeft)` | ~30 | none | — |
| 45 | `ui/home/HomeGesturesController.kt:368-402` | `openSwipeLeftApp` / `openSwipeRightApp` mirror | `openSwipeApp(isLeft)` | 12 | none | — |
| 46 | `ui/settings/HomeScreenSettingsCard.kt:74-82, 110-118` | 9 listeners + 9 `when` branches for `maxApps0..8` | `maxAppsOptions.forEachIndexed { i, v -> ... updateHomeAppsNum(i) }` | 15 | none | list exists at 151 |
| 47 | HomeScreenSettingsCard (×7), AppearanceSettingsCard (×3), GesturesSettingsCard, AppInfoSettingsCard, WellbeingSettingsCard, SettingsFragment:199 | `getString(if (x) R.string.on else R.string.off)` 13× | `Context.onOff(on)` | ~10 | none | — |
| 48 | `ui/HomeFragment.kt:246-250` vs `AppearanceSettingsCard.kt:123-131` | identical show/hide status bar | `Activity.setStatusBarVisible(Boolean)` | 8 | none | — |
| 49 | `ui/HomeFragment.kt:222-241` | `bundleOf(...)` built twice in try/fallback | build once | 8 | low | fallback is a swallowed-nav smell (§5) |
| 50 | 14 files (GrayscaleController, AppLimitManager, CurrencyRates, ContactSearchManager, GestureLetterManager, AppOpenCounter, FocusModeManager, DoubleTapActionManager, SwipeUpAppManager, FolderManager, QuietNotificationsManager, WeatherManager, ThemeScheduleManager) | roadmap item "prefs filename/key conventions": 13 private `PREFS_NAME` copies + 13 `prefs(context)` helpers + 14 inline `getSharedPreferences` | `fun Context.paremPrefs() = getSharedPreferences(Prefs.PREFS_NAME, 0)` in Prefs.kt | ~45 | none | `Prefs.PREFS_NAME` already public |
| 51 | `ui/AppMenu.kt` (8×), `HomeGesturesController.kt` (5×), `AppDrawerFragment.kt` (6× `isAdded && _binding != null`), `OnboardingFragment` (4×) | roadmap item: repeated guard idiom | `val Fragment.isAlive get() = isAdded && view != null` | ~15 | none | pure extraction |
| 52 | `ScreenTimeLimitDialog.kt:184-202` vs `BadHabitDialogs.kt:195-211` | two time-limit pickers over `[15,30,60,120,none]` | `BadHabitDialogs.showTimeLimitPicker(ctx, pkg) { refresh }` | ~22 | low | converges on newer picker (gains check marks) |
| 53 | `helper/UsageStatsHelper.kt:35-38`, `ScreenTimeGraphDialog.kt:114-119` | both `groupBy{applicationId}.mapValues{sumOf}` an already-grouped result | `aggregateForegroundStats` returns `Map<String, Long>`; `SimpleUsageStat.kt` deletes | ~30 | low | with #11 |
| 54 | `LockConsentSheet.kt:25-28, 57-60`, `QuietListSheet.kt:152-155`, `WellbeingSettingsCard.kt:152-154` | `if (SDK_INT >= 33) body += lock_consent_restricted` ×4 | `Context.withRestrictedNote(body)` | 6 | none | — |
| 55 | `ui/HomeWidgetController.kt:85-97` | `getActiveContainer` / `getActiveScrollView` same branch | one pair getter | 4 | none | — |
| 56 | `helper/WeatherManager.kt:19-24` vs `WeatherSettingsDialog.kt:86-96` | `CityResult.displayName` re-derived | `subtitle` getter | 6 | none | — |
| 57 | `listener/OnSwipeTouchListener.kt` vs `listener/ViewSwipeTouchListener.kt` | 136 + 115 lines, ~85% identical | one class with `trackPressed` | ~90 | **medium** | home touch path; §6 |

## 4. Speculative abstractions / dead flexibility

| # | location | what | replacement | −lines | risk |
|---|---|---|---|---|---|
| 58 | `helper/SwipeUpAppManager.kt`, `Constants.kt:89-96`, `MainViewModel.kt:182-190`, `HomeGesturesController.kt:140-147, 184, 318-326, 348-355`, `HomeAccessibilityActions.kt` swipeUp branch, `SlotSnapshot.kt:12-15` + `HomeSlotsController.kt:338-339, 349`, 3 strings | roadmap item "finish or remove the half per-slot swipe-up feature": no UI path sets a swipe-up app | remove, or add the picker row | ~110 | **medium** (owner decision) |
| 59 | `MainViewModel.kt:218-232` | four one-line `postValue` wrappers; `updateSwipeApps: MutableLiveData<Any>` | post directly; `LiveData<Unit>` | 12 | none |
| 60 | `data/Prefs.kt:289-302` | `swipeLeftAction` `-1` sentinel + `getEffectiveSwipe*Action()` | getter default `OPEN_APP` | 6 | low |
| 61 | `ui/ScreenTimeGraphView.kt:64-114` | six Paints where three + alpha swap do | `paint.alpha = ...` | ~25 | low |
| 62 | `HomeWidgetController.kt:108-126` `PendingOp` | keep (trap #2) | — | 0 | — |
| 63 | `helper/SingleLiveEvent.kt` | keep | — | 0 | — |
| 64 | `AppDrawerFragment.kt:76, 201-217` CJK cache | keep | — | 0 | — |
| 65 | `Prefs.kt:242-244, 400-409` legacy `WIDGET_ID` migration | keep | — | 0 | medium |
| 66 | `CreateFolderDialog.kt:48-70, 199-208`, `WebsiteDialog.kt:31-51, 85-91`, `ScreenTimeLimitDialog.kt:49-71, 175-179`, `HomeWidgetController.kt:658-670, 781-789` | four hand-assembled sheets contrary to AGENTS.md | `BottomSheetMenu(ctx).title().customView().show()` | ~80 | **medium** |
| 67 | `ui/FocusModeDialog.kt:393-428` | `createTitle/SectionLabel/MutedLine/ActionRow` re-implement BottomSheetMenu | migrate (drops `dialog_focus_mode.xml`) | ~45 + 23 XML | **medium** |
| 68 | `ui/GrayscaleSheet.kt:181-191` | `actionRow` = `option(dismissOnClick=false)` | `menu.option(text, dimmed, dismissOnClick = false)` | 12 | low |

## 5. Defensive code that swallows failures — do NOT delete the guards; make failure visible

Pattern: `fun reportNonFatal(tag, e)` = `Log.e` and, when `prefs.crashReportsEnabled`, append to CrashReporter's file.

| # | location | what is hidden |
|---|---|---|
| 69 | `helper/AppListSource.kt:80-83` (`getAppsList` → `mutableListOf()`), `:123-126` | any exception in app-list build returns an EMPTY list → home slots cleared and `HomeSlotsController:116-119` WRITES "" into slot prefs (destructive on transient failure). Most plausible "only wallpaper" producer besides the theme loop. |
| 70 | `ui/HomeWidgetController.kt:189-194, 217-219, 559-561, 830-833, 858-861, 883-886` | per-widget restore failures logged only |
| 71 | `MainActivity.kt:203, 222` | `startListening()/stopListening()` failures → Log.e |
| 72 | `ui/AppDrawerAdapter.kt:94-111` (bind catch-all), `:187` (`catch (_: Exception) {}` auto-launch) | blank rows; masked data bugs |
| 73 | `helper/UsageStatsHelper.kt:40` (no log), `helper/WallpaperUtils.kt:110-111` (no log), `MainViewModel.kt:337-341` (no log) | silent |
| 74 | `OnSwipeTouchListener.kt:107-109`, `ViewSwipeTouchListener.kt:97-99` (`dispatchSwipe` catch-all) | gesture handler exceptions eaten |
| 75 | `ui/HomeFragment.kt:119-124`, `:231-241` (nav fallback) | fallback hides a back-stack bug |
| 76 | `HomeGesturesController.kt:94` (empty catch), `:84-86`, `:274-276`, `:426-428` | — |
| 77 | `ui/home/HomeClockController.kt:188-203` | graph sheet failure silently opens Digital Wellbeing |
| 78 | `ui/AppDrawerFragment.kt:171-176, 203-214, 270-284, 637-639` | filter/CJK/alignment catch-alls |
| 79 | `FocusModeManager.kt:172-175`, `SystemActions.kt:166-171`, `:185-193` | fail-open, fine, add a log |
| 80 | `ThemeScheduleWorker.kt:20-25`, `IconPackManager.kt:114-120, 156-160` | worker reports success on exception |
| 81 | `helper/CrashReporter.kt:23-37` | intentional, keep |

(110 `catch (… Exception/Throwable)` sites in main; 6 fully empty.)

## 6. Medium-risk items (owner/design sign-off)

| # | item | −lines | why medium |
|---|---|---|---|
| 58 | Remove (or finish) per-slot swipe-up | ~110 | roadmap says "finish or remove": decision |
| 57 | Merge the two swipe listeners | ~90 | home touch path |
| 66, 67 | Five hand-built sheets onto BottomSheetMenu | ~150 (+23 XML) | pixel parity per sheet |
| 4 | Dead rows in `layout-land/fragment_settings.xml` | ~55 | landscape untested |
| — | `layout/fragment_settings.xml` (1505) vs `layout-land` (1057): 864 differing lines, 22 ids missing from land (contact search, quiet notifications, grayscale, focus, widget placement, export/import) so landscape silently lacks those settings | ~1000 XML | a design task, not WP42 |
| 65 | `WIDGET_ID` legacy migration | 14 | data |

## Totals

- Risk none/low, removable now: ≈ 925 Kotlin + ≈ 700 XML ≈ 1,600 lines, 0 deps.
- Medium (sign-off): ≈ 420 Kotlin + ~80 XML, plus the ~1,000-line settings-layout duplication.

Load-bearing and deliberately NOT listed: `R.id.lock -> {}` + `lock_layout_description` (trap #1), `WIDGET_IDS/WIDGET_PROVIDERS/WIDGET_HEIGHTS` + `PendingOp` (trap #2), `FLAG_SET_HOME_APP_1/_8` rename path (trap #3), `UnmatchedCloseEventGuardian`/`getForegroundStatsByTimestamps` (trap #4), `PackageChangeTracker` and both snapshot caches (trap #5), `KEY_LAUNCHER_RECREATE_TIMESTAMP`/`PERIODIC_SELF_RECREATE_ENABLED` (kept for import typing), CrashReporter's `catch (Throwable)`.
