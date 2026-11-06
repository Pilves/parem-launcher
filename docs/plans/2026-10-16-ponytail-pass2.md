# WP62 — code health pass 2

Based on `origin/next` at `31ef2c9`, after WP42 and the Fable correction lanes. Scope is the requested audit items 69–80 and a fresh none/low-risk Kotlin/resource pass. WP42 items 1–56 were not reimplemented. No dependencies, new UI, device commands or changes to the five architecture traps. No merge or push to next.

## Recovered failures (ledger section 5)

`CrashReporter.reportNonFatal(tag, error)` logs the original throwable and, only while crash reports are enabled, adds diagnostics to the existing no-backup `crash.txt`. It reuses the existing formatter and 64 KiB cap, retains the beginning of an existing report, serializes nonfatal writers, and catches persistence failure without replacing caller recovery. The application context comes from the existing Application installation. The uncaught-exception handler and its intentional catch-all (81) remain unchanged. No preference, export key or automatic upload was added.

| Item | Applied scope and preserved recovery |
|---|---|
| 69 | App-list build/query catches report and retain their empty/partial result. Home's installed-app lookup now reports an exception and preserves its saved name/package. Cache stamp, snapshot and complete-query rules are unchanged. |
| 70 | Six widget restore/rebuild/bind/configure catches report; nulls, queues, IDs, providers and existing toasts are unchanged. |
| 71 | Widget-host start/stop catches report. |
| 72 | Drawer bind and both auto-launch catches report; posted callback and filter behavior stay intact. |
| 73 | Usage scan, wallpaper set/fallback, and app-launch fallback catches report; existing empty map, false and fallback returns remain. |
| 74 | Both swipe dispatch catches report; listener dispatch and gesture thresholds are untouched. |
| 75 | Home click catch and navigation fallback report. Navigation logs before trying the existing fallback, so a second navigation exception cannot hide the first. |
| 76 | Torch registration/unregistration, home long-press menu and flashlight catches report; the lock path is untouched. |
| 77 | Screen-time graph and Digital Wellbeing fallback catches report. |
| 78 | Drawer alignment, filtering, CJK detection, dial and quiet-profile catches report; false/true/toast behavior remains. |
| 79 | Focus default-dialer lookup and accessibility settings/bound-state catches report; whitelist and fail-open/fail-closed decisions remain. |
| 80 | Theme worker still returns success on failure. Icon-pack catches retain WP42's single catch and partial-result storage. |

**Correction to item 69's diagnosis:** on the inspected base, `populateHomeScreen` does not consume `getAppsList`'s empty result. It clears a slot only when `setHomeAppText` returns false after `isPackageInstalledCached` makes an independent live lookup. A thrown live lookup previously escaped population; it did not return false. The added catch preserves and renders the saved name while allowing later slots to populate. A successful system answer that the package is absent retains the previous clearing behavior. No global “last query failed” flag or stale-cache fallback was introduced. Transient OEM replies that successfully report an empty installed-app list cannot be distinguished from uninstall here without changing this contract; no such inference was added.

## Fresh pass and deletion evidence

Every main Kotlin file was screened for declarations with no references, imports with no uses, hand-built collection operations, duplicate statement blocks, wrappers, optional parameters and catch/fallback contracts. Candidates were checked against callers, tests, manifest/XML references and the completed WP42 diff. Every resource XML was parsed and scanned for code/XML references and qualifier duplicates; generated ViewBinding references, manifest entry points, resource parents and dynamic icon-pack lookups were accounted for. The appendix records the file inventory rather than implying device validation.

| Change | Proof before deletion | Result |
|---|---|---|
| Retained `Contact.lookupKey` payload | `rg -n 'lookupKey\|ContactMatcher.Contact\(name, number, lookup\)' app/src` found only the field and constructor. All consumers use name/number/searchKey. | Drop the unused payload; keep LOOKUP_KEY in the provider query and keep deduplication by it. |
| `open` and `instagram` string resources | `rg -n -e 'R\.string\.(open\|instagram)\b' -e '@string/(open\|instagram)\b' app/src` found no consumers; the only main getIdentifier calls resolve external icon-pack resources. | Remove 37 elements across 19 locale files. Test fixture text “instagram” is unrelated and remains. |
| Contact normalization copy | Grep of `normalize`, `DIACRITICS`, `SEPARATORS` and `Normalizer` found the private implementation; both it and SearchMatcher normalize NFD, remove diacritics, lowercase and strip the same separators. | Use `SearchMatcher.key(q).normalized` once per query; preserve matching, stable ranking and limit. |
| Six unused imports | Whole-file grep of each imported simple name found its import only. | Remove showToast, Uri, dpToPx, getColorFromAttr, RequiresApi and TextView from their respective five files. |
| Eight identical night style overrides | XML structural comparison verified identical parents and every item name/value against values/styles.xml; grep retained every default declaration and consumer. | Inherit defaults for WindowAnimationTransition, AppSearchText and six Text* styles. Night AppTheme parent, colors and system-bar flags remain. |
| Unused color resolver options | All `getColorFromAttr` calls pass only the attribute; no named typedValue/resolveRefs arguments or two-argument calls. | Allocate TypedValue inside and resolve references exactly as the former defaults did. |
| Duplicate selected/focused text colors | Grep showed both equal the final default, behind the pressed arm. Exhaustive comparison of all eight state combinations returned identical colors. | Keep pressed plus default. |
| Orphan 90% inverse-color chain after WP42 | `rg -n 'primaryColorInverseTrans90\|blackInverseTrans90\|whiteInverseTrans90' app/src` found only the attr, two theme assignments and two color definitions after the sole drawable consumer was removed by WP42. | Remove those five declarations, preserving all other theme values. |

| Two unused custom theme attributes | `rg -n 'primaryColorInverseTrans50\|dialogShadeColor' app/src` found only declarations and day/night assignments. | Remove six unused declarations/assignments; retain 50% inverse colors used by text shadows. |

Exact initial grep output and XML comparison results are in `/home/pi/parem-qa/wp62-*-proof.txt`. No deletion relies solely on a symbol-frequency count. The final declaration scan's only single-reference production class names are ParemApp (manifest entry) and GestureLetterOverlayView (XML inflation); both remain. Duplicate blocks remaining after WP42 are the swipe listeners, hand-built sheet frames and framework lifecycle wiring. The first two are reserved below; replacing lifecycle wiring would introduce an abstraction to remove ordinary boilerplate, so it is retained.

Other retained code: preference/export migrations and typed keys; byte-level ADB/SPAKE2 protocol and compatibility fallbacks; notification cancellation ordering; usage-event aggregation; package-change caches; widget allocation/restore; fragment lifecycle guards; parsing rejection paths; layout qualifier differences; animation behavior. Shortening any of these without equivalent evidence would violate the risk or behavior constraint. Existing JVM/Android test fixtures and framework callback overrides are contracts, not unused production code.

## Section 6 proposals — no implementation authorized

**58 — Per-slot swipe-up:** retain stored bindings, execution and Undo data until Patric chooses removal or completion. If removal is approved, remove the inaccessible selection branch and runtime behavior together, explicitly decide how old exports are handled, and test slot undo/import before releasing. If completion is approved instead, add the missing picker entry through selectedApp and document the interaction with ordinary drawer swipes. These are observable feature decisions, so neither is part of WP62.

**57 — Swipe listener merge:** consider one existing listener implementation with an explicit pressed-state option only after a gesture parity specification is approved. Preserve letter forwarding, cancellation, velocity tracking, long press, double tap and view-versus-background dispatch, and exercise those combinations on a device. A line-count reduction alone cannot establish parity on the home touch path.

**66 — Hand-built sheet frames:** migrate CreateFolderDialog, WebsiteDialog, ScreenTimeLimitDialog and the widget picker individually to BottomSheetMenu after approval of their layout parity. First record width, padding, expanded state, keyboard resizing, scrolling and dismissal ownership; then preserve each callback and input contract. Widget allocation/provider/restore code must remain outside the migration. This is presentation work requiring screenshots and device checks, not a safe mechanical deletion.

**67 — Focus sheet:** propose a separate migration of createTitle/createSectionLabel/createMutedLine/createActionRow and dialog_focus_mode.xml to the shared sheet API. Agree on whitelist/schedule scrolling and reachability first, preserve all duration and schedule save semantics, and compare keyboard and large-font behavior in both orientations. Keep the current implementation until that approval and evidence exist.

**4 — Landscape dead rows:** propose deleting only appThemeSelectLayout and toggleLock after checking that their visible labels/spacing are intentionally unwanted in landscape. An unreferenced view ID does not prove the view is invisible; retain the rows until Patric approves the resulting layout and a landscape pass confirms it. WP62 removes no layout view.

**Unnumbered — Portrait/landscape settings duplication:** propose consolidating the settings content only as part of the approved settings structure, after mapping missing landscape controls and choosing responsive spacing. Preserve row IDs, state semantics, search anchors and accessibility. Adding formerly missing settings is a behavior change, so a roughly thousand-line reduction is not a reason to fold it into a code-health patch.

**65 — Legacy WIDGET_ID migration:** recommend retaining the migration. Removal needs Patric's explicit support-policy decision and proof that no supported install/export can contain only the legacy key; otherwise an upgrade can silently lose a widget. Any future approved removal must test a real upgrade with host allocation, widgetIds and widgetProviders intact. WP62 leaves all three stores and the migration untouched.

## Validation

Per-group compile checks passed before rebase; the complete pre-rebase compile/unit/assemble run also passed. Rebase conflicts were resolved by preserving WP42's CSV parser, collapsed icon-pack catches and shared navigation arguments, then adding only the diagnostic calls. No Fable launch-routing correction was replaced. Final post-rebase `compileDebugKotlin testDebugUnitTest assembleDebug` exited 0: 388 JVM tests, zero failures/errors/skips. The handoff harness and diff whitespace check exited 0. Production diff versus 31ef2c9 is 121 additions/163 removals (net 42 lines removed), including the requested diagnostics; simplification-only commits remove 97 production lines. No adb, emulator, device, screenshot, crash-consent UI or real OEM failure injection was exercised.

## File inventory after rebase

120 production Kotlin files, 43 JVM test files, two instrumentation test files and 91 resources were included in the reference/duplicate/qualifier screen. “Retain” means no additional none/low-risk candidate survived that screen and caller review; it does not claim device or full semantic verification.

| File | Disposition |
|---|---|
| `app/src/androidTest/java/com/parem/launcher/PrefsRoundTripTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/androidTest/java/com/parem/launcher/SmokeTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/main/java/com/parem/launcher/MainActivity.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/MainViewModel.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ParemApp.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ParemBackupAgent.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/data/AppModel.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/data/Constants.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/data/FolderData.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/data/Prefs.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/AppIconCache.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/AppLimitManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/AppListRebuilder.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/AppListSource.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/AppMenuPolicy.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/AppOpenCounter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ContactMatcher.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/ContactSearchManager.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/CrashReportFormatter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/CrashReporter.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/CsvPairs.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/CurrencyConverter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/CurrencyRates.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/DoubleTapActionManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/DrawerRows.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/EinkDetector.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ExpressionEvaluator.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/Extensions.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/FakeHomeActivity.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/FocusModeManager.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/FocusSchedule.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/FolderManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/GestureLetterManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/GestureLetterRecognizer.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/GrayscaleController.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/GrayscalePairing.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/GrayscalePolicy.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/Haptics.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/HomeAccessibilityActions.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/HomeKeyInput.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/IconPackManager.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/LockServiceCheck.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/MindfulPause.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/MyAccessibilityService.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/helper/NightModeChange.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/OmniboxHistory.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/OmniboxResolver.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/PackageChangeTracker.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/helper/QuickActionParser.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ScreenTimeGraphText.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ScreenTimeSpeech.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SearchMatcher.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SettingsSearch.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ShortcutMatcher.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SingleLiveEvent.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SlotSnapshot.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SunriseSunsetCalculator.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SwipeThresholds.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SwipeUpAppManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/SystemActions.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/TextScale.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ThemeScheduleManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/ThemeScheduleWorker.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/UnitConverter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/UsageStatsHelper.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/Utils.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/WallpaperUtils.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/helper/WallpaperWorker.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WeatherManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WeatherStaleness.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WebLaunchTarget.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WebShortcut.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WeeklyReview.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WellbeingRowState.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/WidgetIdBookkeeping.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/helper/adb/AdbWire.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/adb/Spake2.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/adb/WirelessAdb.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/notifications/QuietFilter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/notifications/QuietNotificationListener.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/notifications/QuietNotificationsManager.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/helper/usageStats/ComponentForegroundStat.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/helper/usageStats/EventLogWrapper.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/helper/usageStats/UnmatchedCloseEventGuardian.kt` | Retain protected architecture contract |
| `app/src/main/java/com/parem/launcher/listener/DeviceAdmin.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/listener/OnSwipeTouchListener.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/listener/ViewSwipeTouchListener.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/AppDrawerAdapter.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/AppDrawerFragment.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/AppMenu.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/AppPickerAdapter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/BadHabitDialogs.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/BaseFragment.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/BottomSheetMenu.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/CrashReportSheet.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/CreateFolderDialog.kt` | Retain; section 6 proposal only |
| `app/src/main/java/com/parem/launcher/ui/FocusModeDialog.kt` | Retain; section 6 proposal only |
| `app/src/main/java/com/parem/launcher/ui/GestureLetterOverlayView.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/GrayscaleSheet.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/HomeFragment.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/HomeWidgetController.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/LockConsentSheet.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/OnboardingFragment.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/QuietListSheet.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/RecentHistoryAdapter.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/ScreenTimeGraphDialog.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/ScreenTimeGraphView.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/ScreenTimeLimitDialog.kt` | Retain; section 6 proposal only |
| `app/src/main/java/com/parem/launcher/ui/SettingsFragment.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/WebsiteDialog.kt` | Retain; section 6 proposal only |
| `app/src/main/java/com/parem/launcher/ui/home/HomeClockController.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/home/HomeGesturesController.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/home/HomeSlotsController.kt` | WP62 change described above; preserve other behavior |
| `app/src/main/java/com/parem/launcher/ui/settings/AppInfoSettingsCard.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/AppearanceSettingsCard.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/GestureLetterConfigDialog.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/GesturesSettingsCard.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/HomeScreenSettingsCard.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/SettingsRowAccessibility.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/SettingsSearchIndex.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/WeatherSettingsDialog.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/java/com/parem/launcher/ui/settings/WellbeingSettingsCard.kt` | Retain; no additional safe deletion candidate after WP42 exclusion |
| `app/src/main/res/anim/fade_enter.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/anim/fade_exit.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/anim/item_anim_from_bottom.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/anim/layout_anim_from_bottom.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/anim/slide_in_top.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/anim/slide_out_top.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/bg_bottom_sheet.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/bg_focus_highlight.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/ic_check.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/ic_info.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/ic_rounded.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/rounded_primary_gradient.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/rounded_rect_shade_color.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/drawable/text_colors_default.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/drawable/text_colors_light.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/activity_fake_home.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/activity_main.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/adapter_app_drawer.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/adapter_recent_history.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/dialog_focus_mode.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/dialog_weather_settings.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/fragment_app_drawer.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/fragment_home.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/fragment_onboarding.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/fragment_settings.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout/item_onboarding_row.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout-land/fragment_app_drawer.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout-land/fragment_home.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/layout-land/fragment_settings.xml` | Retain; section 6 proposal only |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-hdpi/ic_launcher.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-hdpi/ic_launcher_foreground.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-hdpi/ic_launcher_round.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-mdpi/ic_launcher.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-mdpi/ic_launcher_foreground.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-mdpi/ic_launcher_round.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_foreground.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_round.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_foreground.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_round.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_foreground.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.webp` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/navigation/nav_graph.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values/attrs.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values/bools.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values/colors.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values/ic_launcher_background.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values/menu_styles.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values/styles.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-ar/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-de/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-es-rES/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-es-rUS/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-fr/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-hdpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-he/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-hr/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-hu/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-in/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-it/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-ja/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-land/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-ldpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-mdpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-night/styles.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-pl/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-pt-rBR/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-ru-rRU/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-sv/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-sw600dp/bools.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-tr/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-uk/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/values-w1200dp/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-w1600dp/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-w600dp/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-w840dp/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-xhdpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-xxhdpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-xxxhdpi/dimens.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/values-zh/strings.xml` | WP62 change described above; preserve other behavior |
| `app/src/main/res/xml/accessibility_service_config.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/xml/backup_rules.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/xml/data_extraction_rules.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/xml/locales_config.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/main/res/xml/policies.xml` | Retain referenced resource/qualifier or framework icon variant |
| `app/src/test/java/com/parem/launcher/data/PrefsRegistrationTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/AppListRebuilderTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/AppMenuPolicyTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/ContactMatcherTest.kt` | WP62 change described above; preserve other behavior |
| `app/src/test/java/com/parem/launcher/helper/CrashReportFormatterTest.kt` | WP62 change described above; preserve other behavior |
| `app/src/test/java/com/parem/launcher/helper/CsvPairsTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/CurrencyConverterTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/DrawerRowsTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/EinkDetectorTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/ExpressionEvaluatorTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/FocusModeManagerTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/FocusScheduleTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/GestureLetterRecognizerTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/GrayscalePolicyTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/HomeAccessibilityActionsTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/HomeKeyInputTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/LockServiceCheckTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/MindfulPauseTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/NightModeChangeTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/OmniboxHistoryTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/OmniboxResolverTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/QuickActionParserTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/ScreenTimeGraphTextTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/ScreenTimeSpeechTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/SearchMatcherTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/SettingsSearchTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/ShortcutMatcherTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/SlotSnapshotTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/SunriseSunsetCalculatorTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/SwipeThresholdsTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/TextScaleTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/UnitConverterTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/UsageStatsHelperTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WeatherStalenessTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WebLaunchTargetTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WebShortcutTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WeeklyReviewTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WellbeingRowStateTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/WidgetIdBookkeepingTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/adb/AdbWireTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/adb/Spake2Test.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/helper/notifications/QuietFilterTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
| `app/src/test/java/com/parem/launcher/ui/settings/SettingsSearchIndexTest.kt` | Retain test contract/fixture; framework-invoked methods are not dead |
