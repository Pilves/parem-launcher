# Quality audit — 2026-10-04 (origin/next)

Read-only audit in five dimensions (lifecycle, a11y, ux, health, features); every finding survived an adversarial skeptic. Numbers are referenced by roadmap rows M4-WP31..42.

## A1. [crash/lifecycle] Settings sheets and dialogs outlive SettingsFragment after a Home press; their callbacks then crash (binding!!, requireActivity, findNavController, unregistered launcher)

`app/src/main/java/com/parem/launcher/ui/SettingsFragment.kt:134`

**Scenario.** Open Settings and open any card sheet or dialog: the Focus mode dialog, the theme picker (long-press the theme row), the weather city search, the swipe-action picker, the gesture-letter config, or the import confirmation. Press Home. MainActivity.onNewIntent -> backToHomeScreen() pops SettingsFragment (onDestroyView, then onDestroy and onDetach). The BottomSheetDialog windows belong to the activity, so nothing dismisses them and they stay on screen over home. Then: (a) dismissing the Focus mode dialog runs WellbeingSettingsCard.kt:364 onWellbeingChanged -> populateWellbeingSection -> `binding` (_binding!!) and you get an NPE. Picking a city in WeatherSettingsDialog (AppearanceSettingsCard.kt:94 onCityChosen) and GestureLetterConfigDialog 'Disable' (GesturesSettingsCard.kt:99) take the same path. (b) Tapping Light, Dark or System in the theme sheet reaches setAppTheme -> fragment.requireActivity() at AppearanceSettingsCard.kt:223 (and :284 for Sunrise/sunset or Scheduled), which throws IllegalStateException 'not attached to an activity'. (c) Choosing 'Open app' in the swipe or double-tap picker, or a letter in the gesture-letter sheet, reaches GesturesSettingsCard.kt:314 fragment.findNavController(), which throws IllegalStateException on a detached fragment. (d) 'Confirm' in the import sheet calls importSettingsLauncher.launch (AppInfoSettingsCard.kt:160) after the fragment's registry entry was unregistered at ON_DESTROY, which throws IllegalStateException 'Attempting to launch an unregistered ActivityResultLauncher'. Each case crashes the HOME activity, and per the WP22 note Android may then drop Parem as default launcher.

**Fix.** Tie every Settings sheet to the fragment's view lifecycle. In SettingsFragment, keep track of open dialogs and dismiss them in onDestroyView, or add a small helper that adds a viewLifecycleOwner ON_DESTROY observer which calls dialog.dismiss(), the same as GrayscaleSheet.showGrant already does. In addition, guard the cross-fragment callbacks: populateWellbeingSection and resetOpenPickers return early when _binding == null, and card callbacks use fragment.activity?.recreate() and check fragment.isAdded before findNavController() or launch().

**Skeptic.** I checked this against origin/next and the code backs it up. It is not a duplicate: WP22 is the rotation NPE in show/hideStatusBar, which is a different trigger and a different site.

1. **Home pops Settings.** MainActivity.onNewIntent calls backToHomeScreen(), which runs navController.popBackStack(R.id.mainFragment). That fully destroys and detaches SettingsFragment.

2. **Nothing closes the sheets.** BottomSheetMenu and the sheets built on it (theme picker, swipe and double-tap pickers, import confirm), plus FocusModeDialog, WeatherSettingsDialog and GestureLetterConfigDialog, are all plain dialogs on the activity's context. None of them watches the fragment's lifecycle; only GrayscaleSheet has the ON_DESTROY -> dismiss() observer. BaseFragment and MainActivity also never dismiss anything on onNewIntent or onDestroyView. So the sheets stay on screen and their callbacks still work.

3. **Each callback crashes:**
   - (a) WellbeingSettingsCard:172 sets `dialog.setOnDismissListener { onWellbeingChanged() }`, and onWellbeingChanged is `::populateWellbeingSection`. That function reads `binding` (`_binding!!`) with no guard, and `_binding` is null after onDestroyView, so it throws an NPE. The weather onCityChosen (AppearanceSettingsCard:94) and GestureLetterConfigDialog onDisabled (GesturesSettingsCard:99) callbacks lead to the same function.
   - (b) The theme sheet goes setManualTheme -> updateTheme -> setAppTheme, which calls `fragment.requireActivity().recreate()` (around line 223; line 284 for scheduled and sunrise). That throws on a detached fragment.
   - (c) showAppListForSwipe calls `fragment.findNavController()` with no isAdded guard (line 314), and the swipe, double-tap and gesture-letter options all lead there.
   - (d) confirmImportSettings calls `importSettingsLauncher.launch` (line 160). The launcher was registered against the fragment's lifecycle, which unregisters it at ON_DESTROY, so the call throws IllegalStateException.

One more crash in the same family: showDoubleTapActionPicker calls populateDoubleTapAction() unguarded inside each option, so binding.doubleTapAction hits a binding the fragment has already released.

The proposed fix (dismiss on viewLifecycleOwner ON_DESTROY, as GrayscaleSheet already does) addresses the root cause.

## A2. [blocker/a11y] TalkBack users cannot open the app drawer, settings or the home menu: these respond only to raw touch gestures on mainLayout

`app/src/main/java/com/parem/launcher/ui/home/HomeGesturesController.kt:98`

**Scenario.** With TalkBack on, one-finger swipes become TalkBack navigation and long-press becomes double-tap-and-hold on the focused node. mainLayout is not focusable, has no click or long-click listener and no AccessibilityAction. Swipe up (drawer, line 165), long-press (settings/home menu, lines 173-198) and swipe left/right/down therefore cannot be triggered. No ViewCompat.addAccessibilityAction call exists anywhere in the codebase. A TalkBack user can reach only the 8 home slots, the clock and the date, so they cannot reach other apps, settings or a way out of onboarding defaults.

**Fix.** Register custom accessibility actions on binding.mainLayout (or on a focusable home container) with ViewCompat.addAccessibilityAction: 'Open app list' -> showAppList(FLAG_LAUNCH_APP), 'Settings' -> showHomeLongPressMenu(), plus the configured swipe-left/right/down actions. Lower-effort option: an 'All apps' slot that is visible only when AccessibilityManager.isTouchExplorationEnabled.

**Skeptic.** The code on origin/next supports the finding. In HomeGesturesController.initSwipeTouchListener (around line 98), binding.mainLayout gets only setOnTouchListener(screenTouchListener). It has no click listener, no long-click listener and no accessibility action. A grep of app/src/main finds no addAccessibilityAction, AccessibilityDelegate or isTouchExplorationEnabled anywhere. The only route to the app drawer from home is the swipe-up gesture: onSwipeUp on mainLayout, plus a slot swipe-up fallback that also uses a touch listener. The only routes to settings are showHomeLongPressMenu() from onLongClick on mainLayout, the drawer (AppDrawerFragment:429, which is itself unreachable) and two edge paths: the lock SecurityException fallback and setDefaultLauncher long-press, which applies only while Parem is not the default launcher. The views TalkBack can reach do not cover these. Slot taps launch the slot's app or show a toast. Slot long-press opens the slot edit menu, whose app list only picks an app for that slot and does not launch it. Clock and date long-press open the list in clock-app or calendar-app pick mode. So a TalkBack user cannot launch an app outside the 8 slots and cannot reach settings or the widget picker. One qualifier: TalkBack passes a two-finger drag through as a one-finger drag, so a two-finger swipe up might still trigger onFling and open the drawer. That is obscure, not discoverable, and does not help with the long-press menu, so the gap is still real. Nothing in ROADMAP M4-WP22..30 or any other row covers TalkBack or accessibility actions on home. The accessibility rows (M2-WP5, M4-WP8, M4-WP18) are about the lock service.

## A3. [high/lifecycle] Widget rebind and add flows lose their pending state when the activity is recreated, so invalidated widgets are deleted for good and the new IDs leak

`app/src/main/java/com/parem/launcher/ui/HomeWidgetController.kt:156`

**Scenario.** After a reboot or OS update, a widget's ID is invalidated. restoreWidgets() deletes the old ID, writes WIDGET_IDS = validIds (without it) at line 156, and keeps the (oldId, provider) pair only in the in-memory widgetRestoreQueue. processNextWidgetRestore() stores its continuation as a lambda in MainActivity.onWidgetBindResult (line 189) and launches ACTION_APPWIDGET_BIND. That bind activity is a translucent dialog, so MainActivity stays visible behind it. Rotate the tablet (Tab S8; tablets are not orientation-locked) or let the process die. MainActivity is recreated with onWidgetBindResult == null, so the result reaches MainActivity.kt:111 and is dropped. The new ID is allocated and possibly bound but never used. The old ID is gone from WIDGET_IDS, so no later restore ever queues it again, and every other queued widget is lost with it. The user-initiated add or swap flow (bindWidget at line 756, onWidgetBound at line 786) fails the same way during the bind or configure screen: the widget is never added and the ID leaks. MainActivity.kt:99-105 restores pendingWidgetId/pendingWidgetInfo from savedInstanceState, but nothing reads them after a restore. This is trap #2 state going out of sync.

**Fix.** Persist the in-flight operation instead of holding it in a lambda. Keep invalid old IDs in WIDGET_IDS, or persist the restore queue in prefs, until the rebind either completes or is refused. Save {stage, widgetId, provider, replaceIndex/oldId} in onSaveInstanceState. When a bind or configure result arrives with no callback, have MainActivity forward it to the current HomeFragment's widgetController so it can finish or delete the widget from that saved state.

**Skeptic.** The code on origin/next backs up the finding, and it is not a duplicate of M4-WP22..30. WP22 is a different rotation crash in the settings screen, and WP26 and WP30 cover other widget problems.

The relevant facts, all on origin/next:
- In AndroidManifest.xml, MainActivity declares only configChanges="uiMode".
- setupOrientation() returns early on tablets, so tablets are not locked to portrait and a rotation recreates the activity.
- The widget callbacks onWidgetBindResult and onWidgetConfigureResult are plain lambda fields on the activity. onDestroy (MainActivity.kt:180-182) sets both to null.
- After recreation, the launchers registered in onCreate still receive the result, but they call onWidgetBindResult?.invoke on a null field, so the result is silently dropped (MainActivity.kt:111; the same happens for the configure result and for onActivityResult with REQUEST_CONFIGURE_PROFILE_WIDGET).

What happens on the restore path:
- restoreWidgets() deletes the invalidated old ID and writes setWidgetIdList(validIds) without it (HomeWidgetController.kt:156).
- The (oldId, provider) pair then exists only in the in-memory widgetRestoreQueue.
- When the activity is recreated, the next restoreWidgets() reads WIDGET_IDS, which no longer contains oldId, so that widget is never queued again.
- The newly allocated ID leaks, and the stale provider and height entries for oldId are never cleaned up.
- The widgets still waiting behind it in the queue are not lost for good. Their old IDs stay in WIDGET_IDS, so the next restore can find them again. Only the widget being rebound is lost permanently.

What happens on the add path:
- bindWidget and onWidgetBound fail the same way, and the allocated ID leaks.

The saved instance state does not help:
- pendingWidgetId and pendingWidgetInfo are saved and restored (MainActivity.kt:99-105, 162-166).
- A grep shows nothing reads them except the bindWidget lambda, and that lambda is gone after recreation.

Process death while the bind dialog is open gives the same result. Severity is maybe medium rather than high: it needs a rotation during the bind dialog, and on the restore path an invalidated widget on top of that. Still, it is a real and permanent data loss of a home widget, and it sits squarely in trap #2 (widget ID bookkeeping).

## A4. [high/a11y] In-app text size replaces the system font scale instead of multiplying it, so Android's font-size setting never reaches the app

`app/src/main/java/com/parem/launcher/MainActivity.kt:75`

**Scenario.** A low-vision user sets Settings > Display > Font size to 200% (fontScale 2.0). attachBaseContext copies the configuration and then sets newConfig.fontScale = Prefs.textSizeScale, which defaults to 1.0f (Constants.TextSize goes from 0.6 to 1.3). Every TextView, dialog and sheet inflated from MainActivity renders at 1.0x. The system setting has no effect and the in-app ceiling is 1.3x, so 200% font is unreachable anywhere in the launcher.

**Fix.** Multiply instead of overwrite: newConfig.fontScale = context.resources.configuration.fontScale * Prefs(context).textSizeScale. Then re-check the fixed-height and maxLines=1 layouts (drawer action row, home top padding of 112dp) at about 2.6x combined scale.

**Skeptic.** I checked this on origin/next and the finding holds. In MainActivity.kt:74-78, attachBaseContext copies context.resources.configuration and then sets `newConfig.fontScale = Prefs(context).textSizeScale`, which replaces the system value instead of multiplying it. Prefs.kt:213 defaults textSizeScale to 1.0f, and Constants.TextSize only goes from 0.6f to 1.3f. The result goes through applyOverrideConfiguration, so every view, dialog and sheet built from that activity's context uses the in-app scale alone. A user who sets Android's font size to 200% sees 1.0x by default and at most 1.3x. The code came in with upstream commit 5034c7e "Added text resize". No code multiplies by the system fontScale anywhere else. It is not one of M4-WP22..30: I read each row, including the WP30 polish list, and none mentions font scale. The ROADMAP font row is M3-WP5, the bold font option, which is a different thing. The proposed fix (system fontScale times the in-app scale) is right. The layout recheck at about 2.6x combined scale it calls for is a fair concern, since there are fixed-size texts and single-line layouts. High severity is justified for an a11y and Play-launch bar, because the system accessibility setting has no effect in the launcher at all.

## A5. [high/a11y] The invisible lock view is TalkBack-focusable: it reads a developer string, and a double-tap on it locks the phone

`app/src/main/res/layout/fragment_home.xml:11`

**Scenario.** The 1dp lock FrameLayout is clickable (setOnClickListener in HomeFragment.initClickListeners) and carries a contentDescription, so TalkBack includes it in linear navigation as the first home element. Swiping right from the top announces 'lock layout description to be used a unique id to lock screen, double tap to activate'. Double-tapping it fires performClick, and MyAccessibilityService locks the device. android:focusable="false" only blocks d-pad focus, not accessibility focus. Applies to layout-land/fragment_home.xml:17 too. Trap #1 is respected: the contentDescription must stay.

**Fix.** Keep the contentDescription and hide the node from screen readers only. Either (a) ViewCompat.setAccessibilityDelegate on binding.lock with onInitializeAccessibilityNodeInfo calling info.isVisibleToUser = false, info.isScreenReaderFocusable = false and info.removeAction(ACTION_CLICK) (the TYPE_VIEW_CLICKED event from performClick is still sent), or (b) set importantForAccessibility=no and add flagIncludeNotImportantViews to accessibility_service_config.xml so the service still receives the event. Check double-tap-to-lock on a device after either change.

**Skeptic.** The code on origin/next supports the finding. In both fragment_home.xml (line 11) and layout-land/fragment_home.xml, the lock FrameLayout has a contentDescription (@string/lock_layout_description = "lock layout description to be used a unique id to lock screen", translatable=false). Its only accessibility setting is android:focusable="false". HomeFragment.kt:180 calls binding.lock.setOnClickListener(this), which makes the view clickable, and its R.id.lock branch deliberately does nothing so the click event reaches the lock service. With importantForAccessibility left at auto, a clickable view with a contentDescription is a screen-reader target. focusable=false only stops keyboard/d-pad focus, not accessibility focus. So TalkBack will stop on it and read the developer string. Double-tapping it calls performClick, which sends TYPE_VIEW_CLICKED with className FrameLayout and that contentDescription. MyAccessibilityService.onAccessibilityEvent matches exactly those two fields and calls performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN). Nothing in the code hides the node: no accessibility delegate and no importantForAccessibility. This is not a duplicate. The known rows M4-WP22..30 cover the rotation crash, onboarding, tablet sheets, landscape peek, widget add, digit auto-launch, landscape layout, stale date and polish, and none mentions TalkBack or the lock node. M2-WP5, M4-WP8 and M4-WP18 deal with consent, the service being revoked and a fallback lock, not with exposure to screen readers. On the proposed fix: option (b) is sound. A service only receives events from views marked importantForAccessibility=no if its config sets flagIncludeNotImportantViews, and accessibility_service_config.xml currently has flagDefault only. Both options need a device check because of trap #1. One caveat: I could not confirm the exact TalkBack announcement order or the swipe position without a device, but the code path is clear.

## A6. [high/a11y] Settings toggles are focusable 'On'/'Off' values with no label or switch semantics

`app/src/main/res/layout/fragment_settings.xml:873`

**Scenario.** Settings rows are a label TextView plus a separate clickable value TextView. Examples: dailyWallpaperUrl at 863 with dailyWallpaper at 873 (AppearanceSettingsCard.kt:56), and 'Text size' at 723 with textSizeValue at 733 (AppearanceSettingsCard.kt:53). TalkBack focuses the value alone and says 'On, double tap to activate' or '4, double tap to activate', with no setting name and no checked state. A blind user swiping through settings hears a string of identical 'On'/'Off' controls. The pattern repeats across every settings card in both portrait and land layouts.

**Fix.** Set labelFor or contentDescription on each value view (e.g. "${label}: ${value}"), or make the row container the clickable node with the label as its text and the state exposed via ViewCompat.setStateDescription / AccessibilityNodeInfo checkable+checked. Applying this in the populate*() helpers covers the value text updates too.

**Skeptic.** The code on origin/next supports this finding. In fragment_settings.xml, each settings row is a plain FrameLayout holding a label TextView and a separate value TextView: "text_size" at ~723 with textSizeValue at ~733, and dailyWallpaperUrl at ~863 with dailyWallpaper at ~873 (android:text="@string/off"). AppearanceSettingsCard.initClickListeners puts click listeners on the value views (textSizeValue, dailyWallpaper, boldFont, statusBar and others), so each one becomes its own focusable control. Nothing gives the value views a name or state: a grep of every ui/settings/*.kt card and of the settings layouts finds no labelFor, contentDescription, stateDescription, AccessibilityDelegate, importantForAccessibility or screenReaderFocusable. The only "accessibility" hits are about the lock service. TalkBack will therefore announce the value control as just "Off, double tap to activate" or "8, double tap to activate", with no name and no checked/switch state, which fails WCAG 4.1.2 (name, role, value). This is not a duplicate: none of the known rows M4-WP22..30 and no other roadmap row covers TalkBack labelling. One correction on severity: the label is a separate non-clickable TextView that comes just before the value, so a blind user swiping in order does hear the setting name first. Severity is closer to medium than high, but the defect itself is real.

## A7. [high/ux] Time limits set from the drawer or a home slot silently never fire without usage access

`app/src/main/java/com/parem/launcher/ui/AppDrawerAdapter.kt:413`

**Scenario.** Fresh install, usage access not granted. Long-press an app in the drawer, tap 'Set time limit', pick 15 minutes. Nothing complains. Use the app for an hour, then relaunch it: no warning. BadHabitDialogs.gateLaunch reads UsageStatsHelper.getUsageForApp, and getPerAppUsageToday returns emptyMap() without the permission (UsageStatsHelper.kt:30), so usage is always 0 and overLimit is never true. The home-slot path (HomeSlotsController.kt:307) has the same gap. Only the Settings > App limits row checks the permission (WellbeingSettingsCard.kt:178).

**Fix.** In BadHabitDialogs.showTimeLimitPicker (or both callers), check appUsagePermissionGranted() first. If it is missing, show the usage-access explanation sheet (with the limit as its reason) before saving the limit, the way showScreenTimeLimitsDialog already does.

**Skeptic.** The code on origin/next supports the finding. BadHabitDialogs.showTimeLimitPicker (BadHabitDialogs.kt:167) calls AppLimitManager.setLimit without checking any permission. Both callers reach it without a check: AppDrawerAdapter.kt:469 (the appBadHabit click) and HomeSlotsController.kt:307. gateLaunch then reads UsageStatsHelper.getUsageForApp, and getPerAppUsageToday returns emptyMap() when appUsagePermissionGranted() is false (UsageStatsHelper.kt:30). So usage is always 0, overLimit is never true, and the LIMIT_WARNING path cannot fire. Nothing tells the user. Only WellbeingSettingsCard.showScreenTimeLimitsDialog (line 178) checks the permission and sends the user to the DIGITAL_WELLBEING dialog. A mindful pause set on the limited app still works because it does not depend on usage, but the limit itself does nothing. This does not duplicate M4-WP22..30: WP30's polish list covers the drawer's "Close" button overlapping usage text and inconsistent totals, not missing usage access, and no roadmap row mentions limits without the permission. Line 413 is slightly off (the call is at 469), but the claim holds.

## A8. [high/ux] The 'Screen time' row is really the usage-access permission: wrong copy, stale label, and turning it off breaks limits

`app/src/main/java/com/parem/launcher/ui/settings/WellbeingSettingsCard.kt:56`

**Scenario.** 1) Tap Screen time (Off) > Okay > grant usage access > back. The row still says 'Off': the ON_RESUME observer (lines 55-60) refreshes quiet notifications and grayscale but never calls populateScreenTimeOnOff(). 2) Tap 'App limits' without the permission: the dialog says 'To show/hide your screen time on the home screen…' (strings.xml:93), which is not what the user tapped. 3) A user who wants the home screen-time label gone is told to revoke the permission, which silently turns off app limits, sort-by-usage (its row disappears, HomeScreenSettingsCard.kt:266) and the weekly graph. The positive button reads 'Okay' but opens system settings (MainActivity.kt:273).

**Fix.** Call populateScreenTimeOnOff() in the ON_RESUME observer. Split this into a 'Show screen time on home' pref and a separate 'Usage access: Allow' row. Give the usage-access sheet a reason argument (home label, app limits, sort) and an 'Open settings' button, built with BottomSheetMenu.

**Skeptic.** I checked this against origin/next and it holds up. It is not a duplicate: no row from M4-WP22 to WP30 covers it. WP28 only mentions where the screen-time label sits in landscape, and WP30's polish list has nothing on this.

1) The stale label is real. In WellbeingSettingsCard.kt, bind() calls populateScreenTimeOnOff() once (line 49). The ON_RESUME observer (lines 55-60) only calls populateQuietNotif() and populateGrayscale(). SettingsFragment has no onResume or other refresh path for this row. MainActivity no longer pops to home when you leave the app (see the comment at lines 199-204), so the fragment survives the trip to system settings. When you come back, the row still says "Off" after you grant usage access.

2) The copy mismatch is real. showScreenTimeLimitsDialog() (lines 177-180) posts Constants.Dialog.DIGITAL_WELLBEING when the permission is missing. MainActivity.kt:269-277 shows R.string.screen_time with app_usage_message: "To show/hide your screen time on the home screen, please allow or disallow the app usage permission". That text says nothing about app limits. The positive button is R.string.okay, and it starts ACTION_USAGE_ACCESS_SETTINGS.

3) The coupling is real. There is no separate pref for showing screen time on home. HomeClockController:100 shows the label only when the permission is granted, so revoking the permission is the only way to hide it. The same permission gates the app limits dialog, sort by usage (AppDrawerFragment:498/530, HomeScreenSettingsCard:262, SettingsSearchIndex:63) and the weekly graph (ScreenTimeGraphDialog:94).

The stale-label part is a clear one-line bug. The split into two settings is a design change for the product owner to decide.

## A9. [high/health] Double-tap 'Open app' launches outside MainViewModel.selectedApp, skipping focus mode, limits, mindful pause and open counts

`app/src/main/java/com/parem/launcher/helper/DoubleTapActionManager.kt:57`

**Scenario.** Set double-tap action to Open app and pick Instagram. Give Instagram a 15m limit with mindful pause, or start a focus session that doesn't allow it. Double-tap home: DoubleTapActionManager.execute calls launcherApps.startMainActivity directly (lines 57-71), so there's no focus-blocked toast, no limit warning, no pause, and AppOpenCounter isn't incremented. Every other home route (slots, folders, swipe L/R, swipe-up, gesture letters, clock/calendar) goes through HomeSlotsController.launchApp, which runs gateLaunch and then selectedApp; its KDoc says 'Every home launch route comes through here'. execute() also duplicates HomeGesturesController.executeGestureAction (line 305) action by action. Related, lower priority: QuietNotificationListener.open (line 132) sends contentIntent for a focus-blocked app with no gate either.

**Fix.** Delete DoubleTapActionManager.execute. In HomeGesturesController.onDoubleClick (line 187), call executeGestureAction(DoubleTapActionManager.getAction(context), onLockDecline = { setAction(NONE) }) { fragment.slotsController?.launchApp("", getAppPackage, getAppActivity, getAppUser) }. That removes the duplicate action switch and routes the launch through gateLaunch and selectedApp.

**Skeptic.** I tried to refute this against origin/next and couldn't: the bypass is real.

The launch path:
- HomeGesturesController.onDoubleClick (line 187) calls DoubleTapActionManager.execute.
- For OPEN_APP, execute (lines 57-71) calls LauncherApps.startMainActivity directly.
- It never calls BadHabitDialogs.gateLaunch, which is the step that shows the limit warning and the mindful pause.
- It never calls MainViewModel.selectedApp/launchApp either. That is where the FocusModeManager.isActive / isAppAllowed check, the showFocusBlocked toast and AppOpenCounter.increment live.

Every other home route goes through HomeSlotsController.launchApp, and its KDoc says so: "Every home launch route ... comes through here so app limits and the mindful pause apply to all of them." So a double-tap set to Open app gets around focus mode, the limit warning, the mindful pause and the open count.

The duplication is also real. execute() repeats HomeGesturesController.executeGestureAction (line 305) action by action, and that function already takes an openAppFallback lambda. The proposed fix plugs straight into it: call fragment.slotsController?.launchApp with the stored package, activity and user.

Not a duplicate. None of M4-WP22..30 cover this, and the double-tap rows that mention it (M2-WP5, M4-WP8, M4-WP18) only deal with the lock action.

One small point on the fix: passing "" as appName looks fine, because gateLaunch derives its label with appLabel(context, appName, packageName). I didn't open appLabel to confirm it falls back to the package.

I didn't verify the QuietNotificationListener.open aside.

## A10. [high/features] Double-tap "Open app" skips focus mode, app limits and the mindful pause

`app/src/main/java/com/parem/launcher/helper/DoubleTapActionManager.kt:67`

**Scenario.** Settings > double-tap action > Open app > Instagram. Start a focus session (timed or scheduled) whose whitelist leaves out Instagram, or give Instagram a 15 min limit with the mindful pause on. Double-tap empty home: execute() calls launcherApps.startMainActivity directly, so Instagram opens with no focus toast, no pause and no over-limit sheet. The "after a limit" grayscale trigger never fires and AppOpenCounter is not incremented. Every other home route (slots, folders, letters, swipe L/R/up, clock, calendar) goes through HomeSlotsController.launchApp -> BadHabitDialogs.gateLaunch -> MainViewModel.selectedApp.

**Fix.** Route OPEN_APP through the same gate. In HomeGesturesController.onDoubleClick, handle OPEN_APP with fragment.slotsController?.launchApp("", pkg, activity, user), as the swipe and letter routes do. Keep DoubleTapActionManager for the non-app actions only.

**Skeptic.** The finding holds on origin/next. When the double-tap action is OPEN_APP, DoubleTapActionManager.execute() (lines 57-71) calls LauncherApps.startMainActivity directly. HomeGesturesController.onDoubleClick (around line 185) calls execute() with no gate around it. Every other home route uses HomeSlotsController.launchApp (line 227), which goes through BadHabitDialogs.gateLaunch and then viewModel.selectedApp. That covers the swipe-left/right fallback inside executeGestureAction and the per-slot swipe-up. gateLaunch is where the limit warning, the mindful pause and the GrayscaleController.onLimitOverride grayscale trigger happen. selectedApp is where the focus-mode toast happens. So a double-tapped app skips all of them. This is not one of the known rows: M4-WP22..30 and the double-tap rows M4-WP8/WP18 are about the lock action, not opening an app. One correction to the proposed fix: launchApp takes four arguments (appName, packageName, activityClassName, userString). The call should be launchApp("", pkg, activity, user), with activity coming from DoubleTapActionManager.getAppActivity. An empty name is fine because gateLaunch looks up the app's label when none is given.

## A11. [high/features] Opening a hidden notification from the quiet list skips focus and limits

`app/src/main/java/com/parem/launcher/helper/notifications/QuietNotificationListener.kt:132`

**Scenario.** Turn on filtered notifications. Instagram is not on the alert allowlist, so its notification gets snoozed into the quiet list. Start focus without Instagram whitelisted, or give Instagram a limit with the mindful pause. Open the quiet list (home gesture) and tap the Instagram entry: open() sends contentIntent with background-start privileges and Instagram opens. Focus, the limit warning and the pause all run on the home and drawer routes but not here. The quiet list is a Parem surface, so this is a Parem launch route, not a shade tap Parem can't see.

**Fix.** Before sending the PendingIntent in QuietListSheet/open(), check FocusModeManager.isAppAllowed(sbn.packageName). If blocked, show the same toast as MainViewModel.showFocusBlocked. Run BadHabitDialogs.gateLaunch(context, ..., sbn.packageName, open = { listener.open(...) }) so the limit and pause apply too.

**Skeptic.** The code supports the finding. Severity should drop from high to medium, and it needs a product call before anyone fixes it. On origin/next, QuietListSheet.showList (ui/QuietListSheet.kt:225) calls listener.open(context, sbn.key) with no gate. QuietNotificationListener.open (line ~126-143) sends contentIntent straight away with ALLOW_IF_VISIBLE/ALLOWED background-start options. Nothing on this path calls FocusModeManager.isAppAllowed or BadHabitDialogs.gateLaunch. grep finds those calls only in MainViewModel (131, 254), AppDrawerFragment:655 and HomeSlotsController:229. ARCHITECTURE.md lines 46-49 say "every launch route calls gateLaunch", and the quiet list is a Parem surface that opens a third-party app, so this breaks that convention. The finding does not duplicate M4-WP22..30 or the in-flight rows. Two caveats argue for lower severity. (1) open() is documented as "Opens a hidden notification as tapping it in the shade would", and Focus has never gated shade taps, so shade parity may be the intended behaviour. (2) docs/design/M4-WP1.md 'Focus mode' says "no new coupling in v1", but that is about the two allowlists, not about gating launches, so it does not clearly cover this. It is a policy-consistency gap, not a crash or data issue. The proposed fix (focus check plus gateLaunch wrapping open) fits the existing patterns, but whether notification opens should be gated is Patric's decision.

## A12. [medium/lifecycle] With the 'System default' theme, switching system dark mode never restyles Parem (uiMode handled in the manifest)

`app/src/main/AndroidManifest.xml:68`

**Scenario.** MainActivity declares android:configChanges="uiMode". Set Parem's theme to System default, leave daily wallpaper off, then toggle dark mode from quick settings. AppCompat 1.7 sees that the activity handles uiMode and does not recreate it. MainActivity.onConfigurationChanged (MainActivity.kt:229-238) recreates only when dailyWallpaper is on. checkTheme() (line 312) checks only MODE_NIGHT_YES/NO. So home, the drawer and Settings keep the old colors (for example black text on a now-dark wallpaper), and new views inflated from the updated resources mix with the old ones, until some unrelated recreate happens.

**Fix.** In onConfigurationChanged, when the uiMode night bits changed and prefs.appTheme == MODE_NIGHT_FOLLOW_SYSTEM, call recreate() whether or not dailyWallpaper is on, and keep the wallpaper and worker part conditional. The alternative is to remove uiMode from configChanges and move the wallpaper logic to onCreate.

**Skeptic.** I could not refute it on origin/next. MainActivity declares android:configChanges="uiMode" (AndroidManifest.xml:68), so neither the framework nor AppCompat 1.7.0 recreates it when the system switches between dark and light. AppCompat sees that the manifest handles uiMode and only updates the resources configuration. MainActivity.onConfigurationChanged (MainActivity.kt:229-238) calls recreate() only when prefs.dailyWallpaper is on and the mode is FOLLOW_SYSTEM. checkTheme() runs on onStart and is the only other recreate path, but it compares colors only for MODE_NIGHT_YES and MODE_NIGHT_NO, so FOLLOW_SYSTEM is never corrected. A grep found no other onConfigurationChanged or uiMode handler in app/src; Utils.kt:71 only reads the uiMode bits. With System default and daily wallpaper off, views already inflated keep the old theme colors until some unrelated recreate happens. The manifest entry came with commit 2e110a0 "Added system default theme mode". I found no ROADMAP row about theme or dark mode, and M4-WP22..30 does not cover this (WP29 is the stale date and battery). The proposed fix, recreating whenever the night bits change under FOLLOW_SYSTEM and keeping the wallpaper and worker part conditional, is minimal and fits the code.

## A13. [medium/lifecycle] The hidden-apps drawer is empty after process death because nothing reloads viewModel.hiddenApps

`app/src/main/java/com/parem/launcher/ui/AppDrawerFragment.kt:480`

**Scenario.** Go to Settings > Hidden apps (AppInfoSettingsCard.showHiddenApps calls viewModel.getHiddenApps() and then navigates with FLAG_HIDDEN_APPS). Background the launcher and let the system kill the process. On return, Navigation restores AppDrawerFragment with FLAG_HIDDEN_APPS and a new MainViewModel. MainActivity.onCreate (MainActivity.kt:156) calls only getAppList(), and the drawer just observes hiddenApps, which is never filled. The drawer shows a blank list and the user cannot unhide anything until they leave and re-enter.

**Fix.** In AppDrawerFragment.onViewCreated, call viewModel.getHiddenApps() when flag == FLAG_HIDDEN_APPS, or at least when savedInstanceState != null. Picker flags that rely on getAppList(true) (with hidden apps) have the same problem and need the same treatment.

**Skeptic.** The core claim holds on origin/next, but it triggers less often than the finding says. On the hidden-apps flag, AppDrawerFragment only observes viewModel.hiddenApps (lines 508-513) and never loads it. getHiddenApps() has three callers: AppInfoSettingsCard.kt:117 (before navigating), the hide/unhide handler at AppDrawerFragment.kt:432, and the MainViewModel profile-availability receiver (lines 94-96). MainActivity.onCreate:156 calls only getAppList(). A brand-new MainViewModel therefore leaves hiddenApps null, and the restored hidden-apps drawer shows an empty list. The "press Home to return" route in the scenario is wrong, though. After the activity is recreated, onNewIntent runs once it is STARTED and calls backToHomeScreen(), which pops to mainFragment, so the user never lands on the hidden drawer that way. Launching an app, opening app info, and Back from inside the drawer all pop to main as well (lines 340, 395). What is left: returning to the launcher without a HOME intent while the drawer is on top. Examples are pressing Back from an app opened from the notification shade, or the "Don't keep activities" developer option, which destroys the activity and its ViewModel without killing the process. It is real but low-to-medium severity and unlikely to happen often. The second claim is overstated: settings pickers that rely on getAppList(true) (GesturesSettingsCard:313, WellbeingSettingsCard:86) would still get a list after recreation, because onCreate's getAppList() fills it, just without hidden apps. It is not an empty screen. None of M4-WP22..30 covers this.

## A14. [medium/a11y] Drawer search field has '___' as its accessible hint, and 'No apps found' is never announced

`app/src/main/res/layout/fragment_app_drawer.xml:30`

**Scenario.** In the normal launch flow, AppDrawerFragment.initViews (line 134-137) overrides queryHint only for hidden-apps and pick-an-app flags, so the SearchView keeps app:queryHint="___". TalkBack announces 'Edit box, underscore underscore underscore' or similar, with no indication that the field searches apps. When a query has no matches, appDrawerTip is set to 'No apps found' (AppDrawerFragment.kt:232) without being a live region, so a TalkBack user typing gets no feedback.

**Fix.** Keep the visual '___' if wanted, but set searchTextView.contentDescription (or ViewCompat.setAccessibilityPaneTitle / hint for accessibility) to a localized 'Search apps'. Add android:accessibilityLiveRegion="polite" to appDrawerTip.

**Skeptic.** I checked this against the code on origin/next and the finding holds. In fragment_app_drawer.xml line 30, the SearchView has app:queryHint="___". In AppDrawerFragment.initViews, queryHint is only replaced for FLAG_HIDDEN_APPS and for the FLAG_SET_HOME_APP_1..FLAG_SET_CALENDAR_APP pick-an-app flags. The normal FLAG_LAUNCH_APP drawer keeps "___". Nothing sets a contentDescription or accessibility hint on search_src_text either: initViews only sets its gravity. So TalkBack's spoken hint for the main app search field is the underscores, or nothing useful, rather than "Search apps".

appDrawerTip has no accessibilityLiveRegion in the layout, and a grep of app/src/main finds no liveRegion or announceForAccessibility anywhere. It is set to "No apps found" at AppDrawerFragment.kt:259-260 (the finding says :232). Only its text and visibility change, so TalkBack does not announce it.

This is not a duplicate. M4-WP22..30, including the WP30 polish list, contain no accessibility or TalkBack item, and the ROADMAP's accessibility rows (M2-WP5, M4-WP8, M4-WP18) are about the lock service. The proposed fix holds up: a localized description or hint on the search text view, plus a polite live region on appDrawerTip.

## A15. [medium/a11y] Settings info icon has no contentDescription and a touch target of about 30dp

`app/src/main/res/layout/fragment_settings.xml:54`

**Scenario.** ImageView appInfo (ic_info is 22dp plus 4dp padding = 30x30dp) is clickable (AppInfoSettingsCard.kt:67, opens the launcher's system App info) but has no contentDescription. TalkBack reads 'Unlabeled button' or skips it, and sighted motor-impaired users get a 30dp target, below the 48dp minimum. The same defect is in layout-land/fragment_settings.xml:53.

**Fix.** Add android:contentDescription="@string/app_info" (or the existing info string) and android:padding="13dp", or minWidth/minHeight 48dp.

**Skeptic.** Confirmed on origin/next. In both layout/fragment_settings.xml (lines ~53-59) and layout-land/fragment_settings.xml, ImageView @id/appInfo is wrap_content with padding 4dp, uses src @drawable/ic_info (a 22dp vector), and has no contentDescription. That makes a 30x30dp target with no label. AppInfoSettingsCard.kt:67 sets its click listener, and line 89 opens openAppInfo for the launcher itself, so TalkBack sees an unlabeled clickable element. Nothing in the Kotlin code sets a contentDescription or accessibility label either. This is not a duplicate. M4-WP30's "tiny value-only tap targets in settings rows" is about the value text in settings rows, not this icon, and no roadmap row mentions the missing label. Medium a11y severity is fair. Fix: add a contentDescription and make the target 48dp (minWidth/minHeight, or padding of about 13dp).

## A16. [medium/a11y] Screen-time graph is a custom View with no accessibility semantics, and day selection is touch-only

`app/src/main/java/com/parem/launcher/ui/ScreenTimeGraphView.kt:128`

**Scenario.** ScreenTimeGraphView draws 7 bars and labels on the canvas and selects a day only through onTouchEvent x-coordinate hit testing (tappedIndex, line 155). It sets no contentDescription and has no ExploreByTouchHelper virtual views. TalkBack reads nothing for the week's data, and a double-tap triggers performClick at the view centre (no tappedIndex), so per-day drill-down (ScreenTimeGraphDialog.kt:206 onDaySelected) cannot be used.

**Fix.** Set a summary contentDescription in setData (e.g. 'Mon 1.2h, Tue 3.0h, ...'). For selection, implement an ExploreByTouchHelper with one virtual node per bar whose ACTION_CLICK toggles selectedIndex and calls onDaySelected.

**Skeptic.** I checked this on origin/next and the finding holds. ScreenTimeGraphView (app/src/main/java/com/parem/launcher/ui/ScreenTimeGraphView.kt) is a plain View that draws its bars, hour labels and day labels straight onto the canvas in onDraw. The class never sets contentDescription, has no accessibility delegate or ExploreByTouchHelper, and never calls setFocusable or setClickable. The only way to select a day is onTouchEvent's ACTION_UP path, which uses tappedIndex(event.x) to work out the day from the touch position and then sets selectedIndex and calls onDaySelected. performClick() is only overridden to return true and selects nothing, so an accessibility click (ACTION_CLICK goes to performClick) never picks a day. TalkBack probably skips the view altogether, because it has no text, no description and is not clickable or focusable. Either way the week's numbers are not read out and the per-day drill-down that ScreenTimeGraphDialog wires through graphView.onDaySelected cannot be reached. ScreenTimeGraphDialog.kt does not add a description or any other way in when it creates the view. This is not a duplicate: ROADMAP M4-WP22..30 contain no graph or TalkBack row, and the only accessibility rows (M2-WP5, M4-WP8, M4-WP18) are about the lock service. One correction to the scenario: whether TalkBack's double-tap reaches the view at all is uncertain, but in either case the outcome is the same, so the finding stands. The proposed fix fits: a summary contentDescription set in setData, plus an ExploreByTouchHelper with one virtual node per bar whose ACTION_CLICK selects that day.

## A17. [medium/a11y] Work-profile indicator is an unlabeled 4dp dot, so TalkBack users cannot tell duplicate personal and work apps apart

`app/src/main/java/com/parem/launcher/ui/AppDrawerAdapter.kt:276`

**Scenario.** With a work profile, the drawer lists e.g. two 'Chrome' rows. The only difference is otherProfileIndicator (adapter_app_drawer.xml:34, a 4dp ImageView with no contentDescription) being visible. TalkBack reads both rows as 'Chrome', so the user cannot tell which profile they will launch. The new-app marker ' ✦' appended to the label (line 274) is read as a symbol name instead of 'new'.

**Fix.** In bind, set appTitle.contentDescription to the label plus a localized ', work' when appModel.user != myUserHandle, and to ', new' instead of reading the ✦ glyph. Mark otherProfileIndicator importantForAccessibility=no.

**Skeptic.** I checked this against origin/next and it holds. AppDrawerAdapter.kt:320-322 sets appTitle.text to appLabel plus " ✦" when the app is new. The only thing that marks a row as the other profile is otherProfileIndicator.isVisible = appModel.user != myUserHandle. Nothing in the adapter sets appTitle.contentDescription. In adapter_app_drawer.xml, otherProfileIndicator is a 4dp ImageView with no contentDescription. appTitle is a separate sibling inside the FrameLayout and is the focusable view, so TalkBack only reads its text. A personal "Chrome" row and a work "Chrome" row therefore sound the same. The ✦ glyph has no spoken label either, so it gets read out as a symbol. This is not covered by M4-WP22..30. WP30's "widget picker disambiguates same-name providers" is about the widget picker, not drawer rows. None of the in-flight rows (WP9..21) are about drawer accessibility labels either. The bug does need a work profile to show up, and it is accessibility-only, so medium severity is fair. One small correction to the finding: the line numbers are off. The binding is at about line 320, not 274/276.

## A18. [medium/a11y] Drawer long-press menu loses TalkBack focus, and its action labels truncate even at default font

`app/src/main/java/com/parem/launcher/ui/AppDrawerAdapter.kt:313`

**Scenario.** Double-tap-and-hold on an app row sets appTitle INVISIBLE and shows appHideLayout. Accessibility focus was on appTitle, so it drops to nothing and no announcement says that a menu opened. The 6 actions (adapter_app_drawer.xml:58-146) each get 1/6 of the row with maxLines=1, ellipsize=end, 12sp text and 8dp side padding. On a 360dp phone that leaves about 44dp per label, so 'Remove time limit' or 'Set time limit', and German 'Verstecken'/'Umbenennen', render as 'Rem…' or 'Vers…'. After the font-scale fix, larger fonts make this worse.

**Fix.** After showing appHideLayout, call appDelete.sendAccessibilityEvent(TYPE_VIEW_FOCUSED) or performAccessibilityAction(ACTION_ACCESSIBILITY_FOCUS) on the first visible action, and announce it via appHideLayout.accessibilityPaneTitle. Let the labels wrap to 2 lines (maxLines=2) or drop the always-hidden actions from the weighted row.

**Skeptic.** I couldn't refute it; the code on origin/next backs both parts. In AppDrawerAdapter.kt, the long-press handler (around lines 363-383) sets appTitle to INVISIBLE and appHideLayout to VISIBLE. The adapter has no accessibility calls anywhere: no sendAccessibilityEvent, no performAccessibilityAction, no announceForAccessibility, no accessibilityPaneTitle. So TalkBack focus stays on a view that is no longer visible, and nothing announces that a menu opened.

In adapter_app_drawer.xml, the six actions each have width 0dp, weight 1, maxLines=1, ellipsize=end, 12sp text and 8dp side padding. For normal (not hidden) apps, all six are visible: isVisible=false only applies to private apps or the hidden-apps list. On a 360dp phone that leaves about 44dp of text width per label. The strings are "Set time limit" / "Remove time limit" (English only, translatable=false) and German "Verstecken" / "Umbenennen" / "Schließen". All of these are wider than 44dp at 12sp, so they will cut off.

It is not a duplicate. The closest known row is M4-WP30's "drawer long-press 'Close' overlaps usage text", which is a different overlap problem; WP25 covers the home-screen long-press menu, not the drawer.

One correction to the finding: it says to drop the "always-hidden" actions, but none are always hidden. They are only conditionally hidden, which is why all six share the row in the common case.

## A19. [medium/a11y] Widget overlay has a hardcoded English 'Widget' label that hides the widget's content, and resizing is drag-only

`app/src/main/java/com/parem/launcher/ui/HomeWidgetController.kt:278`

**Scenario.** Every home widget is covered by a full-size overlay View with contentDescription = "Widget" (not localized). In TalkBack explore-by-touch the topmost labelled node wins, so touching a weather or calendar widget announces 'Widget' instead of its content. The resize handle (line 315, 24dp tall, drawn as a 3dp bar) responds only to drag in its OnTouchListener. showWidgetOptionsDialog (line 447) offers swap/remove/move but no resize, so switch-access and TalkBack users cannot resize widgets.

**Fix.** Remove the contentDescription from the overlay (or mark it importantForAccessibility=no) so the hostView's own nodes are reached, and expose 'Widget options' as an accessibility action on the wrapper. Add 'Taller'/'Shorter' entries to the widget options sheet or accessibility actions on the resize handle that step wrapper height by e.g. 40dp within minHeightPx..maxHeightPx.

**Skeptic.** The code on origin/next matches the finding. In HomeWidgetController.kt (around line 278), the full-size overlay View placed over hostView sets `contentDescription = "Widget"`. That string is hardcoded and not a string resource, so it is not localized. The resize handle (24dp FrameLayout holding a 40x3dp bar) has only an OnTouchListener that handles DOWN, MOVE and UP drags. It has no click handler, no accessibility actions and no contentDescription. showWidgetOptionsDialog offers only swap, remove, move up and move down; there is no resize entry. A user who cannot drag (switch access, TalkBack without the pass-through gesture) therefore has no way to change a widget's height, which fails WCAG 2.5.7 (dragging movements). The roadmap does not cover this: M4-WP26 is about capacity and insets on widget add, M4-WP28 about landscape layout, M4-WP30 about pruning WIDGET_HEIGHTS and picker names, and M3-WP8 about the work profile; no a11y or TalkBack row exists. One part I could not prove: that TalkBack explore-by-touch always announces 'Widget' instead of the widget's own nodes. The overlay is non-clickable, so whether hover passes through to hostView depends on how the framework dispatches hover events, and I have not tested it on a device. Even so, a labelled overlay that covers everything is a real a11y smell, and the drag-only resize with no alternative is confirmed outright. Severity medium is reasonable.

## A20. [medium/a11y] Many tap targets are well under 48dp (export/import ~19dp, settings rows ~35dp, time-limit chips ~27dp)

`app/src/main/res/layout/fragment_settings.xml:93`

**Scenario.** exportSettings and importSettings (lines 93-106) are wrap_content TextSmall (14sp) with no padding, so they are about 19dp tall. Standard settings rows use paddingVertical=8dp, about 35dp tall (e.g. line 728). ScreenTimeLimitDialog.kt:132-140 limitView is 14sp with 4dp vertical padding, about 27dp tall, and sits in a dense list of apps. Users with tremor or with a home text scale of 1 (0.6x) mis-tap neighbouring rows. Home slots at text size 1 are about 36dp.

**Fix.** Give clickable rows android:minHeight="48dp" with gravity center_vertical (for example via a SettingsRow style), add paddingVertical to export/import, and setMinimumHeight(48.dpToPx()) on limitView.

**Skeptic.** Partly a duplicate. The code on origin/next supports the claim, but the settings-row part is already filed under ROADMAP M4-WP30 ("tiny value-only tap targets in settings rows"), so that part should not be filed again. Two parts are not covered by WP30 and are real. (1) fragment_settings.xml lines 93-106: exportSettings and importSettings are wrap_content TextViews with style TextSmall, which inherits from TextDefault and only sets textSize. They have no padding and no minHeight, so each tap target is about one text line tall, roughly 19-20dp. Only an 8dp horizontal margin (marginEnd 24dp) separates them. Clicks are wired in ui/settings/AppInfoSettingsCard.kt:69. (2) ScreenTimeLimitDialog.kt, limitView: textSize 14f with 4dp top and bottom padding, minWidth 72dp and no minimum height, so it is about 27dp tall. Each one sits in a row of the dense per-app list. No layout file on origin/next sets minHeight, which confirms there is no 48dp floor anywhere. Settings rows such as text_size use paddingVertical 8dp, about 35dp tall, and that matches WP30. Recommendation: add export/import and the limit chip to WP30's tap-target item rather than opening a new row. Fix: paddingVertical on export/import (or minHeight 48dp with gravity center_vertical), and setMinimumHeight(48.dpToPx()) on limitView. I did not check the claim that home slots are 36dp at text size 1.

## A21. [medium/ux] In-app setting paths are mixed up with Android system paths and use two different separators

`app/src/main/res/values/strings.xml:386`

**Scenario.** quiet_card_hint and quiet_disclosure_body say 'Settings > Gestures', but Android also has Settings > System > Gestures, and a user who opens the phone's Settings won't find a quiet-list option. grayscale_toggles_hint uses 'Settings > Accessibility > Color correction' for the system app in the same style, and app_blocked_focus_scheduled uses '›' for Parem's own settings. Nothing tells the user which Settings is meant.

**Fix.** Pick one convention, e.g. 'Parem settings › Gestures' for in-app and 'Phone settings › Accessibility › Color correction' for system. Better still, make in-app references tappable rows that call openSetting(anchor), which already exists for settings search.

**Skeptic.** I checked this on origin/next and it holds, though I'd rate it low severity rather than medium. In app/src/main/res/values/strings.xml, setting_hint (line 58) and app_blocked_focus_scheduled (lines 257-258) use '›'. quiet_card_hint (386), quiet_disclosure_body (395), grayscale_toggles_hint (409) and grayscale_grant_body/_wifi (411-412) use '>'. The clearest case is quiet_disclosure_body: 'Settings > Gestures' means Parem's own settings, while 'Settings > Notification access' in the same string means the phone's settings. Nothing marks which is which. The grayscale strings use the same 'Settings >' for Android paths only. app_blocked_focus_scheduled points to Settings › Digital Wellbeing, a system path, with the '›' that setting_hint uses for Parem's own settings, so the separator does not tell them apart either. None of the known M4-WP22..30 rows covers this; WP30's polish list has no copy or separator item. It is a UX copy issue, not a functional bug.

## A22. [medium/ux] Wellbeing status rows say 'Off' while scheduled focus or automatic grayscale is set up

`app/src/main/java/com/parem/launcher/ui/SettingsFragment.kt:167`

**Scenario.** Set a focus schedule of 22:00-07:00 and Save. At noon the Focus mode row says 'Off' because populateWellbeingSection only checks FocusModeManager.isActive. Likewise, turn on Grayscale 'During focus' and 'After a limit' but leave 'Now' unchecked: the row says 'Off' (WellbeingSettingsCard.kt:116-124 shows On only for isManual). Users think the feature is off and set it up again, or never find out it's armed.

**Fix.** Use a third state label: 'Scheduled' for focus when the schedule is on, and 'Auto' (or 'During focus') for grayscale when a trigger is checked but manual is off.

**Skeptic.** I couldn't refute it. The code on origin/next backs the finding.

1. **Focus row.** SettingsFragment.populateWellbeingSection (lines ~165-167) sets focusModeToggle to On/Off from FocusModeManager.isActive only. That function returns timedActive || scheduleActive, so a saved 22:00-07:00 schedule outside its window shows "Off". Nothing else in the row shows that a schedule exists.
2. **Grayscale row.** WellbeingSettingsCard.populateGrayscale (lines ~116-124) shows one of three labels: "Set up" when the grant is missing, On when isManual, and Off for everything else. GrayscaleController has the trigger prefs KEY_ON_FOCUS and KEY_ON_LIMIT (isOnFocus / isOnLimit), but the label never reads them. Granted, trigger-only setups therefore show "Off".
3. **Not a duplicate.** No ROADMAP row M4-WP22..30 covers this. WP24 is about sheet width on tablets, and WP30's polish list covers the theme of the focus-schedule pickers, not the status label. The in-flight rows WP14-21 don't touch these labels either.
4. **Design docs don't decide it.** docs/design/M4-WP3.md and M4-WP5.md specify text inside the sheet ("Scheduled until ...") but not the label on the settings row.

So the gap is real: a user can't tell from Settings that a feature is armed. Medium severity seems fair, because the sheets do show the state once opened.

## A23. [medium/ux] Omnibox tip line lies for !bang and no-match queries, and Dial/Web tips can't be tapped while others can

`app/src/main/java/com/parem/launcher/ui/AppDrawerFragment.kt:238`

**Scenario.** Type '!w berlin'. OmniboxResolver returns None, the filter finds no app, and the tip reads 'No apps found', but Enter opens DuckDuckGo (line 220). Type 'qwzx': the tip says 'No apps found' and Enter opens a web search (openSearch) with no hint that it will. A space-prefixed query shows '↵ Google: x' and a number shows '↵ Call: …', yet tapping those tips does nothing (initClickListeners 522-546 handles only Calc, Conversion, Currency, Contact and Setting), even though tapping a Contact tip dials. The onboarding omnibox page (strings 323-327) never mentions unit/currency conversion, contacts or settings search.

**Fix.** Add a Bang tip ('↵ DuckDuckGo: !w berlin') and change the no-match tip to '↵ Search the web: qwzx'. Make the Dial and WebSearch tips run the same action as Enter. List conversions, contacts and settings search on the omnibox onboarding page.

**Skeptic.** I checked this against origin/next and it holds, except for one part. In AppDrawerFragment.kt, when OmniboxMode is None, onQueryTextSubmit sends a query starting with "!" to DuckDuckGo (URL_DUCK_SEARCH), and a query with no matching app goes to openSearch. The filter callback, though, sets the tip to "No apps found" whenever adapter.itemCount == 0 and the mode is None. So "!w berlin" and "qwzx" both say "No apps found" while Enter actually runs a web search. In initClickListeners, the appDrawerTip handler only covers Calc, Conversion, Currency, Contact and Setting. Dial and WebSearch fall into `else -> {}`, so tapping "↵ Google: x" or "↵ Call: …" does nothing, while tapping a Contact tip dials. The omnibox onboarding strings 321-327 list apps, fuzzy matching, calculator, dial and web/!bang, but not unit or currency conversion, contacts or settings search. The one part that doesn't hold: the onboarding does mention !bang. None of this is covered by M4-WP22..30. WP30 is a list of other polish items, and WP27 is about digit auto-launch. The omnibox rows still landing (WP14-17) add shortcuts, quick actions, history and settings search, not tip text or tip taps. Medium is probably too high: the tip text is misleading but nothing breaks.

## A24. [medium/ux] Turning on contact search or pairing notifications does nothing once the permission is permanently denied

`app/src/main/java/com/parem/launcher/ui/settings/HomeScreenSettingsCard.kt:286`

**Scenario.** Deny READ_CONTACTS twice (or tap 'Don't allow' on Android 11+). From then on, tapping Contact search 'Off' launches the request, which returns denied at once with no UI, and onContactsPermissionResult leaves the row at Off. The tap looks broken. Grayscale 'Start pairing' has the same problem after POST_NOTIFICATIONS is denied: it only toasts 'Allow notifications so you can type the pairing code' (WellbeingSettingsCard.kt:113), with no way to the setting.

**Fix.** When the result is denied and shouldShowRequestPermissionRationale() is false, show a BottomSheetMenu explaining it, with an 'Open app settings' option (openAppInfo for Parem / ACTION_APP_NOTIFICATION_SETTINGS).

**Skeptic.** I checked this against origin/next and it holds up. It is not a duplicate of M4-WP22..30.

1. Contact search (HomeScreenSettingsCard.kt:278-293). When contact search is off and READ_CONTACTS has not been granted, toggleContactSearch() always calls requestContactsPermissionLauncher.launch(READ_CONTACTS). onContactsPermissionResult(false) then only calls populateContactSearch(), which leaves the row at Off. No code anywhere in app/ calls shouldShowRequestPermissionRationale. Once the permission is permanently denied, Android returns denied at once without showing a dialog, so every tap on the toggle does nothing visible. The comment "Denial leaves the feature off with no further nagging" covers the first denial only. It does not cover the user tapping the toggle on purpose afterwards. M1-WP1's checklist lists "contact-search toggle + denial" as something to test on a device, not as a fix.

2. Grayscale pairing (WellbeingSettingsCard.kt:104-113). startPairing() requests POST_NOTIFICATIONS. If that is denied, onNotificationPermissionResult only shows the toast R.string.grayscale_pair_needs_notif, and nothing opens the notification settings. After a permanent denial the toast is the only feedback, so the user has no way to unblock pairing.

Severity is medium to low UX, not a crash. The proposed fix (a sheet with an "Open app settings" option when the rationale check returns false) fits the existing BottomSheetMenu pattern.

## A25. [medium/ux] Hidden apps live behind an unlabeled 'Parem Launcher' title, and the only hint points to a hidden About page

`app/src/main/res/values/strings.xml:110`

**Scenario.** Hide an app for the first time. An AlertDialog says 'Tap on 'Parem Launcher' at the top of this Settings page… More details available in our About page.' The About row is hidden because URL_ABOUT_PAREM is empty (AppInfoSettingsCard.kt:56), so that's a dead reference. After the dialog is dismissed, the only way back to hidden apps is a row whose label is the app name (fragment_settings.xml:47-52, text=@string/app_name). Nothing in Settings says 'Hidden apps'. With none hidden, tapping it gives a 'No hidden apps' toast.

**Fix.** Add a visible 'Hidden apps' row (count as its value, e.g. '3'). Drop the About sentence from hidden_apps_message, or the dialog entirely once the row is labeled.

**Skeptic.** I checked this on origin/next and the core claim holds. In fragment_settings.xml, the only way into hidden apps is the TextView @id/paremHiddenApps, and its text is @string/app_name ("Parem Launcher"). Nothing in it is labeled "Hidden apps". AppInfoSettingsCard.kt:88 sends that tap to showHiddenApps(), and line 114 shows the no_hidden_apps toast when nothing is hidden. The first-hide dialog in MainActivity.kt:253-258 uses hidden_apps_message (strings.xml:110), which points to "our About page". That About row is hidden while Constants.URL_ABOUT_PAREM is "" (Constants.kt:123, AppInfoSettingsCard.kt:56), so today the reference leads nowhere.

Two things soften it. First, the dead About reference is temporary: roadmap row M2-WP3 will fill in URL_ABOUT_PAREM and show the row again, though nothing in M2-WP3 changes this string. Second, settings search (SettingsSearchIndex.kt:18) maps R.string.hidden_apps to paremHiddenApps, so typing "hidden apps" does find the row.

The discoverability problem stays, though: an unlabeled title is the only entry point in Settings. No M4-WP22..30 row covers it, including the WP30 polish list, and neither do WP14-21 or WP9-13. So it is not a duplicate.

## A26. [medium/ux] App limits sheet has no empty state, hides limited apps not used today, and labels differ from the other limit picker

`app/src/main/java/com/parem/launcher/ui/ScreenTimeLimitDialog.kt:75`

**Scenario.** Open Settings > App limits first thing in the morning, or right after granting usage access: appUsageMap is empty, so the sheet shows only the title. Set a 30m limit on Instagram from the drawer, don't open it today, and open App limits: Instagram isn't listed (only the top 15 by today's usage), so this overview can't show or remove it. Picking 'Unlimited' makes the row read 'No limit' (lines 127/174, hardcoded). The drawer/home picker titled the same 'Select daily time limit' offers '15 minutes…2 hours' with no Unlimited (BadHabitDialogs.kt:170), while this one offers '15m…Unlimited'. Mindful pause can only be switched on from this sheet or a home slot, not from the drawer.

**Fix.** Build the list from union(getAllLimits keys, top-N usage). Add an empty-state line ('No app use recorded today, so set limits from an app's long-press menu'). Use one shared option list and label set (strings minutes_15…, 'No limit') for both pickers.

**Skeptic.** I checked this against origin/next and the main claims are real. They don't duplicate M4-WP22..30. M4-WP24 covers only the sheet width on tablets, and nothing in WP30 touches this sheet.

1. Limited apps can drop out of the overview. In ScreenTimeLimitDialog.kt (lines 75-78) the rows come only from appUsageMap: today's usage, sorted, top 15. currentLimits is used just to label those rows. So an app with a limit that hasn't been opened today, or that ranks below 15th, has no row. This sheet can't show or remove its limit.

2. There is no empty state. If the map is empty, the sheet shows the title and nothing else. WellbeingSettingsCard.kt:181-185 falls back to prefs.getCachedUsageStats(), but only when perAppScreenTime is null. That doesn't help when the map exists but is empty (early morning), or when there is no cache yet (usage access just granted).

3. The two pickers don't match. Both use the select_time_limit title. This one offers 15m/30m/1h/2h/Unlimited. BadHabitDialogs.showTimeLimitPicker (line 170) offers '15 minutes'...'2 hours' with no way to remove a limit. Picking Unlimited makes the row read a hardcoded 'No limit' (line 174). Lines 126-127 also mix 'No limit' and 'Unlimited' as row labels.

4. Mindful pause is missing from the drawer. addMindfulPauseToggle is called only from ScreenTimeLimitDialog:181 and HomeSlotsController:310. The drawer path (AppDrawerAdapter:469 -> showTimeLimitPicker) never calls it.

Minor correction: the hardcoded label is at line 174, plus 126-127 for the row labels, not 127/174 as the finding says. Medium severity seems right.

## A27. [medium/health] Legacy hidden-app upgrade drops the '|' separator, so migrated entries never match and those apps reappear

`app/src/main/java/com/parem/launcher/helper/AppListSource.kt:135`

**Scenario.** upgradeHiddenApps writes hiddenPackage + myUserHandle().toString(), e.g. 'com.fooUserHandle{0}'. Everything else matches on package + "|" + userString (AppListRebuilder.kt:57, AppDrawerFragment hide/unhide ~397-401). Importing a settings file with bare package names and no HIDDEN_APPS_UPDATED flag (old or hand-edited backups; import clear()s prefs, so the upgrade runs again) un-hides those apps. The hidden-apps drawer can't remove the mangled entry either: its backward-compat remove only targets the bare package. This is pure string logic in a file with no test.

**Fix.** Use hiddenPackage + "|" + myUserHandle(). Move the key format into one tested helper, e.g. HiddenAppKey.of(pkg, user) plus an upgrade(set) function, and use it from AppListRebuilder, AppDrawerFragment (historyKey and the hide listener) and the upgrade.

**Skeptic.** The code defect is real, but it is much harder to reach than the finding says. On origin/next, AppListSource.kt:201 (inside upgradeHiddenApps, lines 196-205) writes `hiddenPackage + android.os.Process.myUserHandle().toString()` with no "|". Every other place builds the key as package + "|" + user: AppListRebuilder.kt:57, AppDrawerFragment.kt:360 (historyKey) and AppDrawerFragment.kt:418/420 (the hide listener). So a migrated entry never matches and the app shows as unhidden. The listener's backward-compat line `newSet.remove(appModel.appPackage)` cannot remove the mangled string, so it stays in prefs as dead data. This dates back to commit a7e5381, which added the fallback. Not a duplicate of M4-WP22..30. How often it can happen: importFromJson (Prefs.kt:565) does clear() and refuses any file without __parem_export_version. A real Parem export also includes HIDDEN_APPS_UPDATED=true, because getAppsList sets it on the first load. That leaves two triggers: a hand-edited backup that has bare package names and lacks the flag, or an upgrade from a build older than a7e5381, which is unlikely since the app is not on Play. Severity should be low, not medium. The fix is one character (add "|"). The proposed HiddenAppKey helper is optional.

## A28. [medium/health] Two separate limit pickers with hardcoded English labels that disagree; time formatting copied in four places

`app/src/main/java/com/parem/launcher/ui/BadHabitDialogs.kt:170`

**Scenario.** Home slot menu or drawer -> limit uses BadHabitDialogs.showTimeLimitPicker, with the literal labels '15 minutes'/'30 minutes'/'1 hour'/'2 hours'. Settings -> Wellbeing -> limits uses ScreenTimeLimitDialog.limitOptions (lines 32-38): '15m'/'1h'/'Unlimited', plus 'No limit' at lines 126/174. A German user sees English in both, and the same limit has two names. The string resources minutes_15/minutes_30/hour_1/hours_2/unlimited exist and nothing uses them. The `currentLimit < 0 -> "Unlimited"` branch (line 127) can't run because -1 is never stored. Hours/minutes formatting is also hand-rolled in BadHabitDialogs.limitWarningText (43-45), ScreenTimeLimitDialog (126-130), FocusModeManager.kt:227 and ScreenTimeGraphView.kt:120, while Context.formattedTimeSpent (Extensions.kt:166) already formats with localized strings.

**Fix.** Keep one options list (minutes to string res) in BadHabitDialogs and use it from both pickers. Route all duration text through formattedTimeSpent, or a pure helper next to it with a test. Delete the unreachable <0 branch.

**Skeptic.** The core finding holds on origin/next, but the localization part is overstated.

Confirmed:
- BadHabitDialogs.kt:170 hardcodes "15 minutes" / "30 minutes" / "1 hour" / "2 hours".
- ScreenTimeLimitDialog.kt (lines ~32-38) keeps a separate limitOptions list with "15m" / "30m" / "1h" / "2h" / "Unlimited", and writes "No limit" in two places: the row label and the picker's removeLimit branch. So the same limit has two names, depending on whether it is set from the home/drawer menu or from Settings.
- The `currentLimit < 0 -> "Unlimited"` branch is unreachable. The "Unlimited" option calls AppLimitManager.removeLimit rather than storing -1, and the only setLimit callers pass 15/30/60/120.
- Duration formatting is hand-rolled in four places: limitWarningText (lines 41-45), the ScreenTimeLimitDialog label `when`, FocusModeManager.kt (~226) and ScreenTimeGraphView.kt (~117), even though Context.formattedTimeSpent exists in Extensions.kt.
- No M4-WP22..30 row covers this. WP24 is only the tablet width of the same sheet.

Overstated:
- strings.xml has minutes_15 / minutes_30 / hour_1 / hours_2 / unlimited, all unused, but they are translatable="false" and hold the short English forms ("15m", "Unlimited").
- formattedTimeSpent's time_spent_min / time_spent_hour are also translatable="false", and time_spent_min wraps its text in <b> markup.
- So switching to those resources would fix the inconsistency but would not localize anything for a German user.

Real but low impact: the fix is a consistency and duplication cleanup, not an i18n fix.

## A29. [medium/health] Per-slot swipe-up app is half a feature: nothing can set it, but every swipe-up reads it

`app/src/main/java/com/parem/launcher/ui/home/HomeGesturesController.kt:227`

**Scenario.** Nothing navigates with FLAG_SET_SWIPE_UP_APP_1..8. The only reference is the MainViewModel.selectedApp branch (line 176). The strings set_swipe_up_app/swipe_up_app/clear_swipe_up have never been used (git log -S shows they arrived with b5be1ee and no UI). SwipeUpAppManager.clearSwipeUpApp has no callers. Even so, every swipe-up on a home app does 1-4 prefs reads (lines 227-232). If a SWIPE_UP_PACKAGE_n key arrives through settings import, that slot opens a different app on swipe-up and nothing in the UI shows or clears it.

**Fix.** Delete SwipeUpAppManager, FLAG_SET_SWIPE_UP_APP_1..8 (Constants 97-104), the selectedApp branch and the three strings. onSwipeUp then just opens the drawer. If the feature is wanted, file a roadmap row instead of keeping the read path.

**Skeptic.** I tried to refute this on origin/next and couldn't. Nothing in app/src passes FLAG_SET_SWIPE_UP_APP_1..8 (21-28) to showAppList. The only showAppList callers use FLAG_LAUNCH_APP, the clock and calendar flags, the left/right swipe flags (GesturesSettingsCard), and a home slot index from HomeSlotsController:277, which is a slot number, not 21-28. So the MainViewModel.kt:180-188 branch that calls SwipeUpAppManager.setSwipeUpApp can't be reached. SwipeUpAppManager.clearSwipeUpApp has no callers. The strings set_swipe_up_app, swipe_up_app and clear_swipe_up (strings.xml:226-228) are not referenced anywhere. Even so, HomeGesturesController.onSwipeUp (lines 225-237) reads prefs through hasSwipeUpApp on every swipe-up from slots 1-8, plus 4 more reads when a key exists. The import scenario is real too: Prefs.importFromJson copies every non-excluded JSON key into the main prefs file, and SwipeUpAppManager reads that same file name ("com.parem.launcher"). A SWIPE_UP_PACKAGE_n key in an imported file would therefore silently send that slot's swipe-up to a different app, and nothing in the UI shows or clears it. That only happens with a hand-made file, because no build could ever write the key, which makes it low impact. This is a dead-code finding, not a user-facing bug. It doesn't overlap M4-WP22..30, and the roadmap has no swipe-up-app row. The proposed deletion is correct and small.

## A30. [medium/features] Website shortcuts skip the limit and mindful pause of the app that handles the URL

`app/src/main/java/com/parem/launcher/ui/home/HomeSlotsController.kt:229`

**Scenario.** Give YouTube a 30 min limit with the mindful pause on. Pin a website slot (or folder entry) for https://youtube.com, which App Links resolve to the YouTube app. Tapping it calls gateLaunch with packageName "", so getLimit returns null and the URL opens right away in YouTube: no pause, no over-limit sheet. A limited Chrome behaves the same for any site. MainViewModel.launchUrl does resolve the handler for focus mode (lines 244-265), so focus is enforced on this route but limits and the pause are not.

**Fix.** For url != null, resolve the ACTION_VIEW handler package first (the same resolveActivity as launchUrl) and pass that package to gateLaunch instead of "". Pull the resolver into a small helper shared with launchUrl.

**Skeptic.** I couldn't refute it; the code on origin/next backs the finding. Website slots store packageName "" (the WebsiteDialog path, `AppModel(label, null, "", ...)`). homeAppClicked and toggleFolderExpansion both pass that "" with the url into launchApp, and launchApp calls BadHabitDialogs.gateLaunch(ctx, ..., appName, packageName = "", ...). gateLaunch begins with `AppLimitManager.getLimit(context, packageName)`. For "" that returns null, so it calls open() straight away and skips both the limit check and the mindful pause. The handler is only resolved later, in MainViewModel.launchUrl (lines 248-276), and only when FocusModeManager.isActive(). So focus mode is enforced on this route but limits and the pause are not, which is the asymmetry the finding describes. None of the known rows M4-WP22..30 cover this. M4-WP7, the row that added website shortcuts, only requires that the site opens in the browser/PWA and survives export/import. One caveat: if no default handler exists (the system chooser appears), there is no single package to gate, so the fix would still let that case through. The main scenario (App Links to YouTube, or a limited default browser like Chrome) is real.

## A31. [medium/features] Web search routes open a focus-blocked browser that the website-slot route blocks

`app/src/main/java/com/parem/launcher/ui/AppDrawerFragment.kt:215`

**Scenario.** Start focus with Chrome (the default browser) not whitelisted. A website slot is refused because launchUrl checks the browser. But a leading-space omnibox query (WebSearch), a "!bang" query (line 220), the no-results submit (openSearch, line 222), swipe-down = Search (HomeGesturesController:264) and the OPEN_SEARCH gesture (:309) all call ctx.openUrl/openSearch with no focus check, and Chrome opens in front of the user.

**Fix.** Send the drawer's WebSearch and bang submits through viewModel's launchUrl path, which already does the handler and whitelist check. For ACTION_WEB_SEARCH, resolve the handler and check FocusModeManager.isAppAllowed before startActivity. If blocked, show the focus-blocked toast.

**Skeptic.** The code on origin/next supports the finding. MainViewModel.launchUrl (line 248) resolves the ACTION_VIEW handler and calls showFocusBlocked() when FocusModeManager.isAppAllowed is false. Its comment about the chooser offering "browsers focus blocks" shows that blocking browsers is intended. The other routes skip that check:
- AppDrawerFragment.kt:235 (WebSearch) and :240 (the "!" bang query) call Context.openUrl (Utils.kt:81), which is a plain startActivity(ACTION_VIEW) with no focus check.
- AppDrawerFragment.kt:241 (no results) calls Context.openSearch (Extensions.kt:86), a plain startActivity(ACTION_WEB_SEARCH).
- HomeGesturesController.kt:264 (swipe-down SEARCH), :309 (OPEN_SEARCH) and DoubleTapActionManager.kt:74 also call openSearch.
FocusModeManager states that callers must do the check themselves, and it has no foreground or accessibility enforcement as a fallback. So in focus mode, with a non-whitelisted default browser, a website slot is refused but any of these search routes opens the browser. One line is off: the file is ui/home/HomeGesturesController.kt, not ui/HomeGesturesController.kt. This is not in M4-WP22..30 or the work in flight; ROADMAP has no row about focus and search or the browser.

## A32. [medium/features] Saving the focus sheet drops hidden and Private Space apps from the whitelist

`app/src/main/java/com/parem/launcher/ui/settings/WellbeingSettingsCard.kt:163`

**Scenario.** The whitelist picker is built from getAppsList(includeHiddenApps = false), and includePrivate defaults to false. FocusModeDialog line 176 keeps only whitelist entries that are on offer, and saveSettings() writes selectedIds back. So a user who whitelisted WhatsApp and later hid it from the drawer (still launchable from the hidden-apps drawer, which is focus-gated in selectedApp) loses it the next time they tap Save or Enable focus. WhatsApp is then blocked during focus and can't be re-added because it isn't listed. A Private-Space-only app can never be whitelisted at all, so it is always blocked during focus.

**Fix.** Build the picker with includeHiddenApps = true, plus private apps while the space is unlocked. Or keep current whitelist entries that aren't on offer: selectedIds plus (currentWhitelist minus offered ids), capped at 5.

**Skeptic.** I checked this against the code on origin/next and it holds.

1. **The picker leaves out hidden apps.** WellbeingSettingsCard.kt:163 builds the list with `getAppsList(context, prefs, includeRegularApps = true, includeHiddenApps = false)` and does not pass `includePrivate`, so it uses the default `false` from AppListSource.kt:40. In AppListRebuilder.rebuild, a main-profile app is dropped when it is hidden and `includeHiddenApps` is false. A Private Space app is dropped when `includePrivate` is false.

2. **Only apps on the list start out selected.** FocusModeDialog.kt:175-177 sets `initiallySelected` to entries in `allApps` that are also in `currentWhitelist`. The comment at lines 167-168 says the drop is deliberate: "Whitelist entries for apps no longer offered are dropped on enable, as before".

3. **Saving overwrites the whole whitelist.** `saveSettings()` at line 290 calls `FocusModeManager.setWhitelist(ctx, pickerAdapter?.selectedIds?.toSet() ?: emptySet())`. Both Save (line 296) and Enable (line 308) call it. So any whitelisted app that is hidden, or Private Space only, is removed from the whitelist.

4. **Those apps are still blocked by focus.** MainViewModel.selectedApp handles `FLAG_HIDDEN_APPS` the same way as `FLAG_LAUNCH_APP` and calls `FocusModeManager.isAppAllowed`. A hidden app the user had whitelisted gets blocked during focus after the next Save or Enable, and they can't add it back because it isn't listed. A Private Space app can never be whitelisted. Note that MainViewModel:347 builds the drawer with `includePrivate = true`, so these apps can be launched but not allowed.

One small caveat: hidden status is stored per package and user (`pkg|user`), so the same app in another profile that isn't hidden would still appear in the picker. That doesn't cover the usual single-profile case.

This is not covered by the known rows M4-WP22..30. WP24 is about the sheet's width on tablets and WP30 about the theme of the schedule pickers; neither touches whitelist contents. It is also not part of WP14..21, WP9..13 or WP19.

## A33. [medium/features] Revoking usage access silently turns off every app limit

`app/src/main/java/com/parem/launcher/helper/UsageStatsHelper.kt:30`

**Scenario.** Set a 15 min limit on TikTok, then revoke Usage access in system settings (or lose it on a device transfer). getPerAppUsageToday returns emptyMap, so gateLaunch sees usage 0, overLimit is never true, and the over-limit warning and the "after a limit" grayscale trigger never fire. The drawer and home menus still mark the app as limited, so the user thinks the limit is enforced. Only the screen-time limits dialog checks the permission, when opened.

**Fix.** In gateLaunch, when the app has a limit but appUsagePermissionGranted() is false, say so: a one-line note in the sheet or a toast pointing to Usage access. Don't treat usage as 0.

**Skeptic.** The code on origin/next supports the finding. UsageStatsHelper.getPerAppUsageToday (line 30) returns emptyMap() when appUsagePermissionGranted() is false, so getUsageForApp returns 0. BadHabitDialogs.gateLaunch never checks the permission: it computes usageMinutes = 0, so overLimit is false and MindfulPause.decide returns LAUNCH (or PAUSE with no limit note). The limit warning and GrayscaleController.onLimitOverride (the "after a limit" grayscale trigger) never run, and the user is told nothing. Other permission checks exist only in settings and dialog screens (WellbeingSettingsCard, ScreenTimeGraphDialog, HomeClockController, AppDrawerFragment sorting), never on the launch gate. One correction to the scenario: if the app also has a mindful pause, the pause still shows, because it does not depend on usage. So the limit is lost silently, but the pause is not. This is not covered by ROADMAP M4-WP22..30. The nearest row is M4-WP8, which handles the lock accessibility service being revoked, not usage access.

## A34. [medium/features] Weekly review compares today's partial day against full days and reads low

`app/src/main/java/com/parem/launcher/helper/WeeklyReview.kt:62`

**Scenario.** Open the screen-time sheet at 08:00. thisWeek holds 6 full days plus 8 hours of today, but thisAvg divides by 7. lastWeek holds 7 complete days divided by recorded.size. Someone with exactly the same daily use both weeks sees "down ~10%" in the morning, and the bias is up to 1/7 just after midnight. Per-app risers and fallers carry the same bias, so apps tip under or over the 1 min/day threshold. The design goal is "numbers match the graph", but the percentage is an apples-to-oranges comparison.

**Fix.** Compare like with like: either the last 7 complete days (offsets 1..7) against 8..14, or count today as a fraction (elapsed/24h) when averaging this week. Keep the "/day average" line separate if it has to stay total/7.

**Skeptic.** I tried to refute this and couldn't, so it stands. ScreenTimeGraphDialog.kt on origin/next loops over offsets 13 down to 0. Offset 0 is today's partial usage, taken from UsageStatsHelper.getPerAppUsageToday, and it goes into dayMaps. Offsets 7 to 13 are complete past days and go into lastWeekMaps. WeeklyReview.compare (lines 62-68) then does `thisAvg = sum(thisWeek)/7` and `lastAvg = sum(recorded)/recorded.size`, where every recorded day is complete. With identical daily use, the morning percentage therefore reads low by up to 1/7 (about -14% just after midnight, about -10% at 08:00). The per-app deltas use the same /7 against /recorded.size, so apps can cross the ±1 min/day riser/faller threshold for no real reason. Nothing in the code corrects for elapsed time. The KDoc says /7 is deliberate so the figure matches the "/day average" line, but that only explains the display number, not the percentage, which compares unlike periods. It is not a duplicate. M4-WP30's "weekly vs today totals consistent" is about the two totals agreeing with each other, not about the week-over-week percentage being biased by a partial day. M4-WP4 (the weekly review row) is the feature that introduced this. Medium severity seems right: the number misleads, but nothing crashes.

## A35. [low/lifecycle] The gesture-letter assignment is lost on process death because the pending letter lives only in a ViewModel field

`app/src/main/java/com/parem/launcher/MainViewModel.kt:72`

**Scenario.** In Settings > Gesture letters, tap a letter. GestureLetterConfigDialog sets viewModel.pendingGestureLetter and opens the drawer with FLAG_SET_GESTURE_LETTER_APP. If the process dies while the picker is open, or while the user briefly switches apps, the restored drawer still has the flag but pendingGestureLetter is null. Picking an app runs selectedApp() line 196 `pendingGestureLetter?.let {}`, which does nothing, and the drawer pops back as if the save worked.

**Fix.** Pass the letter as a navigation argument (Constants.Key.* in the bundle) next to the flag and read it in selectedApp, instead of using a ViewModel field.

**Skeptic.** The code on origin/next supports the finding. MainViewModel.kt:72 declares `var pendingGestureLetter: Char? = null`. It is a plain field with no SavedStateHandle; no SavedStateHandle is used anywhere in app/src/main. GestureLetterConfigDialog.kt:64 sets it, and GesturesSettingsCard.kt:98 then opens the drawer with FLAG_SET_GESTURE_LETTER_APP as the only navigation argument. selectedApp() (MainViewModel.kt:199-208) wraps the save in `pendingGestureLetter?.let {}`, so when the field is null nothing happens and nothing tells the user. After process death the NavController restores the drawer destination with its flag, but the new ViewModel has a null letter, so picking an app saves no mapping. MainActivity.onCreate already restores pendingWidgetId/pendingWidgetProvider from savedInstanceState for the same reason, which shows the codebase treats this kind of pending state as needing persistence. The gesture letter was simply missed. Note that pressing home returns through onNewIntent, which calls popBackStack to mainFragment, so the bug only shows when the user comes back through recents, not through home. That keeps severity low. It does not duplicate M4-WP22..30: no roadmap row mentions gesture letters or process death.

## A36. [low/lifecycle] Onboarding resets the page indicator and the Get started/Skip buttons after rotation

`app/src/main/java/com/parem/launcher/ui/OnboardingFragment.kt:63`

**Scenario.** On a tablet, page to the last onboarding page (Get started shown, Skip hidden), then rotate. The RecyclerView restores its scroll position on the last page, but dots and buttons are set up for page 0 and updatePage() runs only on SCROLL_STATE_IDLE. The last page now shows Skip and the first dot until the user swipes again. currentPage is not saved.

**Fix.** After attaching the adapter, post updatePage(layoutManager.findFirstVisibleItemPosition().coerceAtLeast(0)), or save and restore currentPage in onSaveInstanceState.

**Skeptic.** I couldn't refute this; the code on origin/next backs it up. In OnboardingFragment.kt, each dot starts with `alpha = if (i == 0) 1.0f else 0.3f`, and the XML leaves Skip shown and Get started hidden. updatePage() is only called from onScrollStateChanged when the state is SCROLL_STATE_IDLE. currentPage is assigned but never saved or restored. The MainActivity manifest entry only lists configChanges="uiMode", so a rotation rebuilds the activity and the fragment. The RecyclerView has an id (@+id/onboardingPager), and the adapter and LinearLayoutManager are attached in onViewCreated before view state is restored. That means the LayoutManager's saved scroll position comes back as the last page, but no idle scroll event fires, so the dots stay on page 0 and the buttons stay as Skip. This is not a duplicate of M4-WP23, which is about the onboarding closing within a second on first open, or M4-WP22, the Settings rotation crash. One caveat: M4-WP12 (design first) plans to rebuild onboarding under ui/onboarding/, which may replace this file. The fix is small either way: call updatePage on the restored position after layout, or save currentPage. Low severity.

## A37. [low/lifecycle] The screen-time graph's load coroutine is cancelled only on dismiss, so it outlives activity recreation and holds the dead activity

`app/src/main/java/com/parem/launcher/ui/ScreenTimeGraphDialog.kt:97`

**Scenario.** Tap the screen-time label on home: loadJob = CoroutineScope(Dispatchers.Main + Job()).launch starts a 14-day event-log scan, and it is cancelled only in onDismiss. Rotate the tablet (or change theme or text size, which calls recreate()) while it runs. The dialog window is torn down without onDismiss, so the scan runs to completion on IO and then writes into views of the destroyed dialog. That holds the old activity context for the length of the scan, and a pass that rotates repeatedly piles up several scans.

**Fix.** Launch the job from the owner's lifecycleScope (pass a LifecycleOwner like WeatherSettingsDialog does), or cancel loadJob in a Dialog.onStop/onDetachedFromWindow override as well as in onDismiss.

**Skeptic.** The code on origin/next backs this up, but the real impact is smaller than the finding says. Checked against the source:
- ScreenTimeGraphDialog.kt:97 runs `loadJob = CoroutineScope(Dispatchers.Main + Job()).launch`. The scope is not tied to any lifecycle.
- The only cancel is in `.onDismiss { loadJob?.cancel() }`, which BottomSheetMenu.onDismiss turns into `dialog.setOnDismissListener`.
- In AndroidManifest.xml, MainActivity declares only `configChanges="uiMode"`, so rotating does recreate the activity. MainActivity.onDestroy does not dismiss open sheets, so the BottomSheetDialog window leaks without its dismiss listener ever running.
- The coroutine keeps the activity Context alive (`context.getString` inside the IO block, plus graphView, averageView and the columns, which all hold it). It then writes into detached views.

Caveats that cut the severity:
- The leak lasts only as long as the scan. It does not crash, because writing into detached views is harmless.
- The "piles up scans" claim is mostly wrong. Completed days are cached in the static completedDayStats map and today comes from the shared 60s cache, so only the first open after process start runs the full 14-day scan. Later rotations finish almost at once. Two overlapping runs can still both miss getOrPut for the same day.
- The theme or text-size recreate() calls come from the settings screens, so the screen-time sheet is unlikely to be open then. Rotation is the realistic trigger.

It is not a duplicate: M4-WP22 is the settings rotation crash and covers different code. I'd keep it at low severity, a valid hygiene fix. The cheapest fix is to pass a LifecycleOwner and use its lifecycleScope (the same pattern as WeatherSettingsDialog). A more general fix would make BottomSheetMenu dismiss itself when the activity is destroyed.

## A38. [low/lifecycle] Double-tapping a Settings row that navigates crashes with 'navigation action is unknown'

`app/src/main/java/com/parem/launcher/ui/settings/AppInfoSettingsCard.kt:118`

**Scenario.** Quickly double-tap 'Hidden apps' (or 'Silent apps', WellbeingSettingsCard.kt:279). The first navigate() moves currentDestination to appListFragment straight away, but SettingsFragment's view is still attached and clickable during the fade-out. The second click calls navigate(R.id.action_settingsFragment_to_appListFragment) from appListFragment, which has no such action, and that throws IllegalArgumentException. HomeFragment.showAppList already wraps the same pattern in try/catch, but the Settings cards do not.

**Fix.** Guard each navigate with `if (findNavController().currentDestination?.id == R.id.settingsFragment)`, or reuse a small safeNavigate helper in the settings cards.

**Skeptic.** Confirmed on origin/next. The finding holds, but its Wellbeing line number is wrong. In AppInfoSettingsCard.kt:112-122, showHiddenApps() calls fragment.findNavController().navigate(R.id.action_settingsFragment_to_appListFragment, ...) without checking currentDestination and without a try/catch. The same unguarded pattern is in WellbeingSettingsCard.kt line 87 (quietNotifSilent, not line 279), GesturesSettingsCard.kt:314 and HomeScreenSettingsCard.kt:137. In nav_graph.xml, appListFragment defines only action_appListFragment_to_settingsFragment2. Once the first navigate() has moved currentDestination to appListFragment, a second navigate with the settings action cannot be resolved, and NavController throws IllegalArgumentException. The fade_enter/fade_exit animations keep the SettingsFragment view attached and clickable while it fades out. A second tap can also land before the async fragment transaction even runs, so a fast double-tap reaches that second navigate. HomeFragment.showAppList (lines 203-) wraps its navigate in try/catch, which shows the codebase already knows about this race; the settings cards do not have that guard. No click debounce or currentDestination guard exists in ui/settings (grep found guards only in MainActivity). Nothing in docs/ROADMAP.md covers it: M4-WP22 is a rotation crash, not a double-tap navigation crash. Low severity, needs a fast double-tap.

## A39. [low/a11y] Low-contrast secondary text in light theme: onboarding subtitle and graph labels below 4.5:1

`app/src/main/res/layout/item_onboarding_page.xml:29`

**Scenario.** pageSubtitle uses alpha 0.5 on primaryColor over a solid primaryInverseColor background. In light theme that is black at 50% on #FFFFFF, effectively #808080 at 3.95:1, below WCAG AA 4.5:1 for 14sp text. ScreenTimeGraphView.kt:48-49 draws past-day labels at alpha 130 (about #7D7D7D on the white sheet, about 4.1:1 at 10-12sp) and past-day bars at alpha 90 (about 2.5:1, below the 3:1 non-text minimum). Dark theme passes; the e-ink default (MODE_NIGHT_NO, MainActivity.kt:84) and light-theme users get the failing values.

**Fix.** Use alpha of at least 0.6 for small secondary text (#666 on white is 5.7:1) and at least 0.45 for the dimmed bars, or define dedicated secondary-text colors per theme instead of alpha on primaryColor.

**Skeptic.** I checked this against origin/next and the code supports it. In item_onboarding_page.xml (lines 25-33), pageSubtitle is 14sp text set to ?attr/primaryColor with alpha 0.5. fragment_onboarding.xml:5 gives the onboarding a ?attr/primaryInverseColor background. In the light theme (values/styles.xml), primaryColor is black and primaryInverseColor is #FFFFFF. Black at 50% on white blends to about #808080, which is about 3.95:1, below the 4.5:1 that WCAG AA asks for at this size. In ScreenTimeGraphView.kt, dimAlpha is 90 and textDimAlpha is 130, both applied on primaryColor. That puts the past-day bars at about 2.5:1 and the 10sp labels at about 4.1:1, both below their thresholds. One part of the scenario is overstated. The app's default theme is MODE_NIGHT_YES (Prefs.kt:206), and MainActivity.kt:84 only forces MODE_NIGHT_NO on e-ink devices that have no saved theme. So only light-theme users and e-ink devices are affected, which fits the low severity. None of the known rows M4-WP22..30 mention contrast or alpha, including the WP30 polish list, so this is not a duplicate. The same pattern also appears elsewhere: ScreenTimeGraphDialog's average line uses primaryColorTrans50, so fixing only the two cited spots would leave other failing text.

## A40. [low/ux] Error states show raw exception text, and a network failure reads as 'No results found'

`app/src/main/java/com/parem/launcher/ui/settings/AppInfoSettingsCard.kt:188`

**Scenario.** Import a non-JSON file: the toast reads 'Failed to import settings: Value <!DOCTYPE of type java.lang.String cannot be converted to JSONObject'. Widget failures do the same with 'Couldn't add widget: null' (HomeWidgetController.kt:776/817/842). Search a city in Weather settings while offline: WeatherManager.searchCities catches the IOException and returns emptyList(), so the dialog shows 'No results found' (WeatherSettingsDialog.kt:137) and the user thinks the city doesn't exist.

**Fix.** Map exceptions to plain messages ('That file isn't a Parem settings export', 'Couldn't add this widget'). Make searchCities return a Result/null on failure and show 'Can't reach the weather service, check your connection' separately from an empty result.

**Skeptic.** The code on origin/next supports every part of the finding. AppInfoSettingsCard.kt:188 calls settings_import_failed with e.message, and that string is "Failed to import settings: %s". So a non-JSON file shows the raw JSONException text. HomeWidgetController.kt:776/817/842 pass e.message into couldnt_bind_widget, widget_setup_failed and couldnt_add_widget (strings.xml:171-173, all "...: %s"). A null message renders as "null". WeatherManager.searchCities catches every exception, logs it and returns emptyList(), the same value it returns for no matches. A non-200 response does the same. WeatherSettingsDialog then shows R.string.no_results ("No results found") for any empty list, so an offline search looks like the city doesn't exist. None of M4-WP22..30 cover error-message copy or the weather search offline state. WP26 is about widgets hiding apps, not error text. WP30's polish list doesn't mention it. It's a low-severity UX issue, but real.

## A41. [low/ux] Settings nags and hidden gestures: keyboard toggle needs two taps, swipe rows need a secret long press, and old AlertDialogs remain

`app/src/main/java/com/parem/launcher/ui/settings/GesturesSettingsCard.kt:139`

**Scenario.** Tap Auto show keyboard 'On' to turn it off: instead of toggling, an AlertDialog says 'Hi, keyboard is the fastest way… Can we request you to keep this On for a few more days' (strings.xml:90), and a second tap is needed. A disabled swipe-left/right row only toasts 'Long press to enable' (lines 199-206), and nothing on screen shows that rows can be long-pressed. Double tap set to an app shows 'Open app' (getDoubleTapLabel), but swipe rows show the app's name. The keyboard, hidden-apps and usage dialogs are platform AlertDialogs (MainActivity.kt:254-277), as is the focus days picker (FocusModeDialog.kt:324), unlike every BottomSheetMenu sheet.

**Fix.** Make the keyboard toggle act on the first tap (drop the nag or turn it into a one-line hint under the row). Replace long-press enable with a 'None/Off' choice in the action picker, which already has NONE. Show the app name for double tap OPEN_APP. Move the three MainActivity dialogs and the days picker to BottomSheetMenu.

**Skeptic.** I checked each part of the finding against origin/next. Everything except the days-picker part holds up, and none of it is already filed.

- **Keyboard toggle:** GesturesSettingsCard.toggleKeyboardText (around line 139) does not toggle on the first tap. If autoShowKeyboard is on and keyboardMessageShown is false, it posts Dialog.KEYBOARD, sets the flag, and returns. The user has to tap a second time to turn it off. The nag text is strings.xml keyboard_message.
- **Swipe rows:** showSwipeActionPicker returns early with a toast (long_press_to_enable) when a swipe row is disabled. The only other sign of the disabled state is text drawn at 50% alpha. Enabling happens only through onLongClick → toggleSwipeLeft/Right.
- **NONE choice:** gestureActionChoices offers no NONE option. gestureActionLabel handles NONE, so a 'None' choice could replace the long press.
- **Label mismatch:** getDoubleTapLabel shows R.string.open_app for OPEN_APP. gestureActionLabel shows the app name for the swipe rows.
- **Old dialogs:** MainActivity's showDialog observer builds three platform AlertDialogs (HIDDEN, KEYBOARD, DIGITAL_WELLBEING). FocusModeDialog.editWindow uses an AlertDialog with setMultiChoiceItems for the days picker.

Overlap with known rows: M4-WP30 already lists "focus-schedule pickers use app theme", so the FocusModeDialog days-picker part is partly covered. M4-WP30's "tiny value-only tap targets in settings rows" is about tap-target size, not these behaviours. Nothing else in M4-WP22..30, or in WP9..21, covers the keyboard nag, the long-press enable, the double-tap label, or the MainActivity AlertDialogs.

Severity is low, as reported: these are UX issues and nothing breaks.

## A42. [low/health] About 55 unused strings, many translated into 20 locales

`app/src/main/res/values/strings.xml:114`

**Scenario.** I grepped every <string name> in values/strings.xml against R.string.X and @string/X across java/, layouts, xml, navigation, styles and the manifest. 55 have no reference, including accessibility_disclosure (114) and not_working (121), both left by M2-WP5, plus admin_permission_message, digital_wellbeing_message, tip_start_typing_for_rename, unable_to_launch_app, search_for_Launcher_or_home_app, remove_widget_confirm, remove_folder, icon_pack, no_icon_packs, screen_time_limits, set_time_limits, time_limit_reached, theme_schedule, manual, light_time, dark_time, focus_mode_off, allowed_apps, note, write_something, clear_note, draw_letter_to_launch, assign_app, letter_detected, app_options, about, follow, email, donate, instagram, roadmap, public_roadmap, affiliate, copy, open, black, white, cool, accept, permission, enable, learn_more, app_text_color, welcome_to_parem_settings. Translated ones such as accessibility_disclosure show up in values-de/-hu/-es-rUS and others, so translators keep paying for strings nobody sees, and stale consent copy can be revived by mistake.

**Fix.** Remove them from values/ and every values-*/ file in one commit. Verify with lint UnusedResources or by re-running the grep.

**Skeptic.** This holds up on origin/next. I extracted the app/ tree with git archive and did a loose word-boundary grep for each of the 393 names in values/strings.xml. I searched .kt, .java, .xml and .kts files and left out the values* directories. Even with that loose match, 43 names have no reference anywhere. They include the ones the finding names: accessibility_disclosure, not_working, admin_permission_message, digital_wellbeing_message, the icon_pack and time-limit strings, the note strings, the draw-letter strings, roadmap/affiliate/donate/email, learn_more, cool, app_text_color and welcome_to_parem_settings. The grep also turned up some the finding missed: minutes_15/30, hour_1, hours_2, screen_time_7_days, and set_swipe_up_app/swipe_up_app/clear_swipe_up. Some names in the finding are common words (about, copy, open, black, white, manual, accept, enable, permission, follow, instagram), so a word grep can't confirm them; the finding's total of about 55 is plausible but unverified for those. Nothing looks up strings dynamically: the only getIdentifier calls are against icon-pack drawables in IconPackManager. accessibility_disclosure is still present in 18 of the 25 values-* locale folders, so the translation-cost point stands. This is not a duplicate of M4-WP22..30. M4-WP9 (translation coverage) is related, and doing this cleanup first would shrink WP9's work. Severity is low: shrinkResources is on, but resource shrinking keeps unused string values, and they still cost lint and translation effort.

## A43. [low/health] Dead code: unused constants, a LiveData that is never posted, unused helpers

`app/src/main/java/com/parem/launcher/data/Constants.kt:125`

**Scenario.** No callers on origin/next: Constants.URL_DOUBLE_TAP (125), LONG_PRESS_DELAY_MS (115), object CharacterIndicator (65-68), THEME_SCHEDULE_WORKER_NAME (138; ThemeScheduleManager uses its own WORKER_TAG), DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME/ACTIVITY (135-136). MainViewModel.launcherResetFailed (66) is never posted, yet MainActivity observes it (241) and keeps openLauncherChooser (302) for it. Also unused: Utils.getChangedAppTheme (49) and View.animateAlpha (113), Extensions Long.hasBeenDays/hasBeenHours (201/204), EventLogWrapper getAllSimpleUsageStats/getIncrementalSimpleUsageStats/aggregateSimpleUsageStats (329-362), SimpleUsageStat.asSimpleStats (34), FolderManager.getAllFolders (102), Prefs.getWidgetProvider (357). Each is a false lead for the next agent: URL_DOUBLE_TAP suggests a help link exists, and launcherResetFailed suggests a reset failure is handled.

**Fix.** Delete them. For launcherResetFailed, delete the field, the observer and openLauncherChooser together.

**Skeptic.** I checked every listed symbol with git grep across the whole origin/next tree, and each one appears only at its own declaration. That covers URL_DOUBLE_TAP, LONG_PRESS_DELAY_MS, CharacterIndicator, THEME_SCHEDULE_WORKER_NAME, the two DIGITAL_WELLBEING_SAMSUNG_* constants, getChangedAppTheme, animateAlpha, hasBeenDays, hasBeenHours, the three EventLogWrapper SimpleUsageStat helpers, asSimpleStats, FolderManager.getAllFolders and Prefs.getWidgetProvider. launcherResetFailed shows up only in its declaration (MainViewModel.kt:66) and the observer (MainActivity.kt:241). Nothing ever sets or posts it, so the observer and openLauncherChooser (MainActivity.kt:302) can never run. The false-lead risk is real: ARCHITECTURE.md:132 still says URL_DOUBLE_TAP has a hidden settings row, but docs/design/M2-WP5.md says it stopped being used in that work package. None of this overlaps with M4-WP22..30. It is low-severity cleanup, but the finding is accurate. Deleting URL_DOUBLE_TAP should also fix the ARCHITECTURE.md:132 line.

## A44. [low/health] Gesture-letter recognizer is ~200 lines of pure geometry in a View, untested, with duplicate and mislabelled patterns

`app/src/main/java/com/parem/launcher/ui/GestureLetterOverlayView.kt:328`

**Scenario.** analyzeGesture, isCircular, extractDirections, resamplePoints, classifyAngle and matchDirectionPattern (193-385) only need List<PointF> and two dp thresholds, yet they live in ui/ with no test, against the AGENTS.md rule that matchers go in helper/ with tests. Visible problems: line 343 (Z) is a strict subset of line 344; line 380 (A) is a strict subset of 377-379; the second S pattern (348) is documented as 'RIGHT, DOWN_LEFT, RIGHT reversed', but that sequence matches Z at 344 first, so a mirrored S always launches the Z app. isCircular's comment says 30% but the code uses 0.35. Without tests, tuning any threshold can silently change which app a letter opens.

**Fix.** Move the recognizer into helper/GestureLetterRecognizer, an Android-free object taking (xs, ys, segmentPx, minDimPx), with a unit test per supported letter. Drop the subset branches (343, 380) and fix or remove the S pattern at 348.

**Skeptic.** I checked this against origin/next and most of it holds. It is a low-severity health issue, and it is not a duplicate of M4-WP22..30.

Confirmed in GestureLetterOverlayView.kt:
- **Untested geometry in ui/:** analyzeGesture, isCircular, extractDirections, resamplePoints, classifyAngle and matchDirectionPattern are pure point geometry. The only Android dependencies are PointF and dpToPx. No test file covers them; the only gesture/letter Kotlin files are helper/GestureLetterManager.kt and the ui/ files. AGENTS.md line 37 says new pure matchers go in helper/ with unit tests. That rule is written for new code, so this is debt, not a rule breach.
- **Z at line 343:** it is a strict subset of line 344, because isDownLeftish (line 402) includes DOWN_LEFT.
- **A at line 380:** UP_RIGHT,DOWN_RIGHT already passes 377-379. isUpish(UP_RIGHT) and isDownish(DOWN_RIGHT) are true, and hasRightComponent is true for both. Line 380 can never be reached.
- **isCircular:** the comment says 30%, but line 236 uses 0.35.

One part needs correcting. The comment on line 348 says the second S pattern is 'RIGHT, DOWN_LEFT, RIGHT', but the code actually checks isRightish, isDownRightish, isRightish. That is a staircase shape, not a mirrored S. The 'RIGHT, DOWN_LEFT, RIGHT' sequence the comment describes would hit Z at 343/344 first, so that S alternative can never fire. A mirrored-S stroke is the same shape as a Z, so calling Z for it is expected. Saying a mirrored S 'always launches the Z app' overstates it. The real defect is that line 348 is mislabelled and recognises a shape nobody draws as S.

None of this changes behaviour today; the risk is that someone tunes a threshold later with no tests to catch it.

## A45. [low/health] The app-picker search field is copy-pasted in three sheets (a fourth copy in the widget picker)

`app/src/main/java/com/parem/launcher/ui/QuietListSheet.kt:117`

**Scenario.** The same EditText block (search_apps hint, 16sp, primaryColor, trans50 hint color, null background, single line, empty before/onTextChanged, afterTextChanged -> adapter.filter) is repeated at CreateFolderDialog.kt:110-124, FocusModeDialog.kt:183-197 and QuietListSheet.kt:117-131, plus a variant at HomeWidgetController.kt:691. All three feed AppPickerAdapter. Polish fixes such as e-ink, IME action or a clear button (M4-WP30-style) need three edits and have already started to drift (padding differs).

**Fix.** Add one factory next to AppPickerAdapter, e.g. AppPickerAdapter.searchField(context, adapter): EditText using doAfterTextChanged, and call it from the three sheets.

**Skeptic.** I checked this on origin/next and the duplication is real. QuietListSheet.kt:116-132, FocusModeDialog.kt:182-198 and CreateFolderDialog.kt:109-125 each build the same search field from scratch. All three use the R.string.search_apps hint, 16sp text, primaryColor text, a primaryColorTrans50 hint, a null background, TYPE_CLASS_TEXT and single-line input. Each also adds an anonymous TextWatcher with empty before/onTextChanged and calls AppPicker adapter.filter() in afterTextChanged. The drift the finding claims is real. CreateFolderDialog pads the field 0/8/0/8dp, while the other two pad it 8/4/0/8dp. It also sets size with textSize = 16f, not setTextSize(COMPLEX_UNIT_SP). HomeWidgetController.kt:602-611 has a looser fourth copy: a search_widgets hint, 16/12/16/12dp padding, and its own TextWatcher at line 691. That one feeds a ListView, not AppPickerAdapter, so it belongs only loosely with the other three. No helper for this exists. None of the M4-WP22..30 rows covers it, and WP30 does not mention the picker search field. The proposed fix is sound: one factory next to AppPickerAdapter, using androidx doAfterTextChanged. Severity is low because this is a maintenance cost, not a user-facing bug.

## A46. [low/health] First-hide, keyboard and usage-access prompts are system AlertDialogs, outside the BottomSheetMenu convention

`app/src/main/java/com/parem/launcher/MainActivity.kt:254`

**Scenario.** The showDialog observer builds three AlertDialog.Builder dialogs (HIDDEN 254, KEYBOARD 262, DIGITAL_WELLBEING 270). Every other prompt goes through BottomSheetMenu, whose ScreenTimeLimitDialog comment notes that Material styling clashes with the mono look. On e-ink these dialogs don't get disableAnimationsOnEink. Hiding your first app or tapping Screen time in settings therefore shows a Material-tinted, animated dialog unlike the rest of the app.

**Fix.** Replace each with BottomSheetMenu(this).title(..).message(..).option(okay){..}.option(not_now, dimmed = true){}.show(). That is about the same number of lines and drops the AlertDialog import.

**Skeptic.** The code supports the finding, with a few overstatements. On origin/next, the showDialog observer in MainActivity.kt (lines 250-280) builds three androidx.appcompat AlertDialog.Builder dialogs: HIDDEN at 254, KEYBOARD at 262 and DIGITAL_WELLBEING at 270, which opens ACTION_USAGE_ACCESS_SETTINGS and has a not_now button. These dialogs never get disableAnimationsOnEink(). BottomSheetMenu.show() calls it (BottomSheetMenu.kt:123), and so do the other sheets (ScreenTimeLimitDialog, FocusModeDialog, CreateFolderDialog, WebsiteDialog, HomeWidgetController, HomeGesturesController). The proposed fix works with the existing API: BottomSheetMenu has title(String), message(String), option(text, dimmed=false, onClick) and show(). It needs getString(...) because the methods take String, not a resource ID. No known row covers this. M4-WP30 lists "focus-schedule pickers use app theme", which is a different item, and M4-WP24 and WP25 are about sheet width and peek. Overstatements: (1) AppTheme extends Theme.AppCompat.Light.NoActionBar with colorAccent black, so the dialogs are AppCompat-styled with black accents, not Material-tinted. The real gaps are the look not matching the sheets and the e-ink animation. (2) MainActivity is not the only place using AlertDialog: FocusModeDialog.kt:324 also uses AlertDialog.Builder for its days picker, so "every other prompt goes through BottomSheetMenu" is not strictly true. Low-severity consistency and e-ink issue, but real.

## A47. [low/health] ThemeScheduleManager duplicates its mode constants and the time-parse fallback, and its pure logic is untested

`app/src/main/java/com/parem/launcher/helper/ThemeScheduleManager.kt:78`

**Scenario.** MODE_MANUAL/MODE_SCHEDULED/MODE_SUNRISE_SUNSET (22-24) shadow Constants.ThemeScheduleMode (Constants.kt:147-151), and both sets are used, so changing one value silently breaks the other. The same try/LocalTime.parse/catch default-07:00/19:00 block appears three times in shouldBeDark (80-86, 92-97, 104-109). isInDarkPeriod (125) handles midnight wrap but has no test, even though SunriseSunsetCalculator next to it does.

**Fix.** Delete MODE_* and use Constants.ThemeScheduleMode. Pull the fallback into one private scheduledTimes(context) function. Move isInDarkPeriod into a pure, tested function (e.g. in SunriseSunsetCalculator or its own helper object).

**Skeptic.** I checked this against origin/next and every part of the finding holds. ThemeScheduleManager.kt lines 22-24 define MODE_MANUAL/SCHEDULED/SUNRISE_SUNSET = 0/1/2, which repeat Constants.ThemeScheduleMode (Constants.kt, MANUAL/SCHEDULED/SUNRISE_SUNSET). Both sets are in use. ThemeScheduleWorker.kt:16 and shouldBeDark use the local MODE_* constants. AppearanceSettingsCard.kt:241-267 writes and compares Constants.ThemeScheduleMode.*. So if one set changes, the other breaks without any error. The block that parses the time and falls back to 07:00/19:00 appears word for word three times in shouldBeDark (lines 80-85, 92-97, 104-109). isInDarkPeriod (line 125) is private and handles the case where the dark period wraps past midnight. There is no test for it: the only related test file is app/src/test/.../SunriseSunsetCalculatorTest.kt. There is one more duplicate the finding misses: WORKER_TAG = "THEME_SCHEDULE_WORKER" repeats Constants.THEME_SCHEDULE_WORKER_NAME. This has nothing to do with the known M4-WP22..30 rows. It is low severity and only a code-health issue. The values match today, so users see no bug.

## A48. [low/health] Prefs conventions drift: 13 helpers redeclare the prefs filename, and GrayscaleController reads FocusModeManager's private key by string

`app/src/main/java/com/parem/launcher/helper/GrayscaleController.kt:55`

**Scenario.** GrayscaleController declares its own KEY_FOCUS_END_TIME = "FOCUS_MODE_END_TIME" (55) and reads it raw at line 188, duplicating FocusModeManager's private KEY_END_TIME (FocusModeManager.kt:18). If focus storage is renamed or moved, grayscale's focus-end scheduling quietly stops (it reads -1 and schedules nothing) with no compile error. Separately, `private const val PREFS_NAME = "com.parem.launcher"` is copied into WeatherManager, ThemeScheduleManager, SwipeUpAppManager, FolderManager, GestureLetterManager, GrayscaleController, DoubleTapActionManager, CurrencyRates, FocusModeManager, ContactSearchManager, AppLimitManager, QuietNotificationsManager and AppOpenCounter, instead of using Prefs.PREFS_NAME, which ARCHITECTURE.md names as the single source.

**Fix.** Expose FocusModeManager.timedEndMs(context): Long? and use it from GrayscaleController.focusEndMs. Replace the 13 local PREFS_NAME constants with Prefs.PREFS_NAME, a mechanical one-line change in each file.

**Skeptic.** The facts check out on origin/next, though the finding overstates the convention and the risk, so it holds only as a narrowed low-severity health item. Confirmed: GrayscaleController.kt:55 declares `private const val KEY_FOCUS_END_TIME = "FOCUS_MODE_END_TIME"`, and focusEndMs (around line 188) reads it raw with getLong(..., -1L). That copies FocusModeManager's private KEY_END_TIME (FocusModeManager.kt:18). The same string literal also appears in Prefs.kt:536, in the export-exclude list, and in PrefsRoundTripTest. All 13 listed files declare `private const val PREFS_NAME = "com.parem.launcher"`; FolderManager and QuietNotificationsManager also carry the local copy. Caveats: ARCHITECTURE.md:89 does not tell managers to use Prefs.PREFS_NAME. It says everything lives in the single file "com.parem.launcher" (Prefs.PREFS_NAME), and that helper managers "read/write their own keys directly". So the 13 local copies are tolerated drift, not a broken documented rule. There is no behaviour bug today, and renaming a stored key is unlikely because it would wipe user data, so the failure scenario is hypothetical. The one real coupling is grayscale reaching into FocusModeManager's private key. Exposing a FocusModeManager accessor fixes that cheaply. Swapping the 13 PREFS_NAME copies is optional mechanical cleanup. This does not duplicate M4-WP22..30.

## A49. [low/features] Launcher-wide 60 s usage cache crosses midnight, so yesterday's total blocks today

`app/src/main/java/com/parem/launcher/helper/UsageStatsHelper.kt:33`

**Scenario.** At 23:59:40 the user opens a limited app (usage 31/30 min); the cache is filled. At 00:00:20 they tap it again: cachedAt is under 60 s old, so yesterday's 31 min is returned and the "limit reached" sheet shows on a fresh day. The screen-time sheet's "today" bar (ScreenTimeGraphDialog offset 0 uses the same map) also shows yesterday's total next to a freshly scanned yesterday bar, counting it twice.

**Fix.** Store the day (LocalDate.now().toEpochDay()) next to cachedAt and treat the cache as stale when the day changed.

**Skeptic.** This is a real bug, but a minor one. In UsageStatsHelper.getPerAppUsageToday (lines 29-45 on origin/next), the cache is only checked by age: `now - cachedAt > CACHE_TTL_MS`. Nothing stores which day the cached map belongs to, and nothing clears it at midnight. A grep for DATE_CHANGED or for any reset of cachedAt finds nothing.

Two places read this cache and go wrong in the first 60 seconds after midnight:
- The limit check. BadHabitDialogs gets its usage through UsageStatsHelper.getUsageForApp, which reads this map. If the map was filled shortly before midnight, an app that was over its limit yesterday still shows as over the limit on the new day. MindfulPause.decide(pause, overLimit) then shows the limit warning when it should not.
- The screen-time sheet. ScreenTimeGraphDialog uses the cached map for today (offset 0). For yesterday it runs a fresh scan with explicit day bounds. The code comment there says those bounds exist to handle midnight, but the today map has no such guard. So in that window the today bar shows yesterday's total, and yesterday's minutes are counted twice in the weekly sums.

This is not a duplicate of M4-WP29. That row only covers the date and battery display in HomeClockController.

Severity is low. The wrong state lasts at most 60 seconds after midnight, and the cache must have been filled shortly before it. The proposed fix is right and small: store the day next to cachedAt and treat the cache as stale when the day has changed.

## A50. [low/features] Device-only lock and grayscale state rides along in Android backup and device transfer

`app/src/main/res/xml/data_extraction_rules.xml:4`

**Scenario.** Prefs.exportExcludeKeys marks LOCK_SERVICE_CONNECTED, LOCK_SERVICE_OFF_EXPLAINED and GRAYSCALE_* as device-moment state. data_extraction_rules.xml and backup_rules.xml still include the whole com.parem.launcher prefs file. On a new phone set up from cloud backup or device transfer, the first double-tap goes through LockServiceCheck.decide(connectedBefore = true) to EXPLAIN_OFF, so the user reads "Android turned the lock service off" for a service they never enabled on that phone. Every later double-tap only shows the OFF_EXPLAINED toast and never the first-time consent sheet.

**Fix.** Move the device-moment keys (lock-service flags, GRAYSCALE_APPLIED/PREV_*/SUPPRESSED/LIMIT_OVERRIDE, FOCUS_SCHEDULE_SKIP_UNTIL) into a no-backup prefs file, like quiet_keys and omnibox_history. Or clear them in a BackupAgent restore hook.

**Skeptic.** The code on origin/next backs up the finding. data_extraction_rules.xml (both cloud-backup and device-transfer) and backup_rules.xml include the whole sharedpref domain with `path="."`. They exclude only quiet_keys.xml and omnibox_history.xml. The main com.parem.launcher prefs file, which holds LOCK_SERVICE_CONNECTED and LOCK_SERVICE_OFF_EXPLAINED (Prefs.kt:39-40) and the GRAYSCALE_* keys, therefore goes into Android backup and device transfer. Prefs.exportExcludeKeys (Prefs.kt:530-541) treats these keys as device-only, but only the manual JSON export/import uses that set. It does not affect Android backup, and there is no BackupAgent. MyAccessibilityService.kt:17 is the only code that sets lockServiceConnected=true. So on a restored phone the flag is true even though the service never connected there. If Android has not restored the service as both enabled and bound, HomeGesturesController.lockPhone calls LockServiceCheck.decide(connectedBefore=true, offExplained=false), which returns EXPLAIN_OFF. That shows the 'service turned off' sheet instead of the first-time consent sheet. After that, offExplained is true, so every later tap gets only the OFF_EXPLAINED toast. If OFF_EXPLAINED was also true on the old phone, the user gets only the toast from the very first tap. None of M4-WP22..30 and no other roadmap row covers backup or restore of these keys. Severity is low: the user still reaches accessibility settings from the off-explanation sheet. The restored GRAYSCALE_APPLIED/PREV_* state is probably harmless on a new phone without the WRITE_SECURE_SETTINGS grant. Even so, the mismatch between exportExcludeKeys and the backup rules is real.

## A51. [low/features] Currency omnibox reads "1,000 usd in eur" as 1 USD

`app/src/main/java/com/parem/launcher/helper/CurrencyConverter.kt:71`

**Scenario.** CURRENCY_REGEX accepts \d+(?:[.,]\d+)?, so "1,000 usd in eur" matches with amount "1,000", which replace(',', '.') turns into 1.0. The tip confidently shows "= 0.86 EUR" and submit copies it. The answer is off by 1000x for anyone typing a thousands separator, and Enter copies the wrong number.

**Fix.** Treat a comma followed by exactly three digits (and no other decimal mark) as a thousands separator, or reject that ambiguous shape. Add a JVM test for "1,000 usd in eur" and "1,5 eur in usd".

**Skeptic.** I tried to refute this and couldn't. On origin/next, CURRENCY_REGEX in CurrencyConverter.kt (around lines 52-55) is `^(\d+(?:[.,]\d+)?)\s*([a-zA-Z]{3})\s+(?:(?:to|in)\s+)?([a-zA-Z]{3})$`. The input "1,000 usd in eur" therefore matches with group 1 = "1,000". At line 71, convert() runs `replace(',', '.').toDoubleOrNull()` on it, which gives 1.0. Nothing checks for a thousands separator. OmniboxResolver.kt:56-58 passes the trimmed query straight to looksLikeCurrency/convert, so the omnibox shows a wrong but confident result that is 1000x too small. CurrencyConverterTest only covers the decimal-comma case "1,5 GBP to jpy" and has no thousands-separator case. ROADMAP has no row for this: M3-WP4 is the original feature and M4-WP27 is about digit auto-launch, so neither duplicates it. Severity is low but the bug is real. The fix has to keep "1,5" working as a decimal comma, which Estonian users need, so treating "1,000" as a thousands separator is the natural shape. Rejecting comma-plus-exactly-three-digits as ambiguous also works.
