# Parem 6.0: inclusive-design walk

Specialist: inclusive design. Code read at `origin/next` @ `649fbc9`. I took no new screenshots
because the capture run was blocked: the Tab S8 was behind its lock screen. The evidence is the QA shots in
`scratchpad/qa/` plus the layouts, strings and Kotlin. Every claim below cites the code it rests on.
Anything that needs a device says so under "Not verified".

The skills (impeccable, taste, web design) are written for the web. Each recommendation here is
translated into terms that already exist in this project: XML layouts, `dimens`/`styles`/theme attrs,
`ViewCompat` accessibility APIs from androidx.core (already a dependency), `BottomSheetMenu`, and
Material `BottomSheetDialog`. Nothing here needs Compose or a new dependency.

Out of scope: anything already filed as M4-WP22..30. Where this overlaps the sibling reports
(`impeccable.md`, `taste.md`), I name the issue they already cover and add only what is new from an inclusion angle.

---

## 1. Persona × flow matrix

Legend: **OK**: works. **Weak**: works, but with friction or confusion. **Fail**: the user cannot finish, or ends up somewhere harmful.

| Flow | 1 Low vision + TalkBack | 2 One-handed (large phone / tablet) | 3 Left-handed | 4 Tremor / motor | 5 ADHD / compulsive | 6 RTL (ar/he) | 7 Power user | 8 Anxious minimalist |
|---|---|---|---|---|---|---|---|---|
| First run + set default home | Fail (WP23, plus the lock-view hazard on home) | OK | OK | Weak (swipe-only pager) | OK | Weak (mixed language) | OK | **Fail** (no reassurance, no hint left on portrait home) |
| Launch from home | Weak (works, but first focus is the lock view) | OK (bottom alignment exists) | OK | **Fail** (px swipe thresholds) | Weak (no friction unless opted in) | OK | OK | OK |
| Launch from search | **Fail** (no route to the drawer; auto-launch fires mid-typing) | Weak (results far from the thumb) | OK | **Fail** (typo plus auto-launch opens the wrong app) | Weak | Weak (Arabic-Indic digits ignored) | OK | Weak (hidden apps vanish from search) |
| Add home app / folder / widget | **Fail** (settings and widgets reachable only by gesture) | Weak (sheet centred on tablet) | OK | Weak (long-press only, no alternative) | OK | Weak | Weak | Weak |
| App limit + mindful pause | Fail (15 identical "No limit" buttons; countdown silent) | OK | OK | Weak | **Fail** (wrong default emphasis; pause buried 3 levels deep; limits only for today's top 15) | **Fail** (hard-coded English) | Weak | OK |
| Focus mode | Weak (silent 5-app cap) | Weak (nested scroll) | OK | **Fail** (scroll inside a scroll inside a sheet) | **Fail** (one-tap escape, no pause) | Fail (English) | Weak (no gesture shortcut) | OK |
| Find a setting | **Fail** (no headings; label/value split; impeccable #2) | OK | Weak (values at far right; WP30) | Weak | OK | Weak ("Left" means start) | OK (WP17 search) | **Fail** (hidden apps behind an unmarked title tap) |

---

## 2. Walks, one per persona

### 2.1 Older / low-vision user, 200% font, TalkBack

1. **System font size is discarded.** `MainActivity.attachBaseContext` sets
   `newConfig.fontScale = Prefs.textSizeScale` (default 1.0, range 0.6–1.3), which *replaces* the system
   value. At 200% system text, Parem renders at 100%. This is already impeccable #1. Inclusion adds two
   points. First, **multiply** the two scales, don't replace one with the other, and clamp only the
   *product*'s lower bound (≥ 0.85). Clamping the upper bound would defeat the user's own setting.
   Second, Android 14 nonlinear scaling is only applied to the system `fontScale`, so the multiply keeps
   it working. Files: `MainActivity.kt`, `data/Constants.kt` (`TextSize`), `ui/settings/AppearanceSettingsCard.kt`.
2. **The first thing TalkBack says on home is an internal ID, and activating it locks the phone.**
   `@id/lock` (both `layout/` and `layout-land/fragment_home.xml`) is the first child of `mainLayout`. It
   is 1dp tall and `focusable="false"`, but `HomeFragment.initClickListeners` gives it a click listener,
   so it is *clickable*, and its contentDescription is
   `"lock layout description to be used a unique id to lock screen"`. TalkBack puts clickable,
   described views into linear navigation. A TalkBack user who swipes right on home hears that sentence
   first. Double-tapping it calls `performClick()`, which emits `TYPE_VIEW_CLICKED` with the matching
   description, and `MyAccessibilityService` locks the screen (when the lock service is on).
   Trap #1 forbids renaming the description or removing the view. See the top issue below for the
   safe fix.
3. **There is no route to the drawer, settings, notifications or widgets.** All four are touch gestures
   on `mainLayout` through `OnSwipeTouchListener`, and TalkBack consumes those gestures. A grep for
   `addAccessibilityAction` finds nothing. Slots do work, because `initClickListeners` wires
   `setOnClickListener`/`setOnLongClickListener` for "d-pad/keyboard (and accessibility)". This is
   impeccable #2. The inclusive addition is that Switch Access and Voice Access users hit the same
   wall, and that custom actions alone are not discoverable (see top issue 2).
4. **Auto-launch fires while TalkBack is still echoing the keystroke.** `AppDrawerAdapter.autoLaunch`
   opens the app the moment one row matches. A TalkBack user typing "chr" for Chrome Remote Desktop
   gets Chrome mid-word, without ever hearing the result list.
5. **Settings is one 1,478-line scroll with no headings.** There is no `accessibilityHeading` on the card
   titles ("Home screen", "Appearance", …), so TalkBack's heading navigation can't skip between cards.
   `appInfo` (ImageView) is unlabelled. The label/value split is covered in impeccable #2 and WP30.
6. **Sheets give no state or context.**
   - The `BottomSheetMenu.title()` TextView is not a heading.
   - In the app-limits sheet, each row's clickable element is only the `limitView` ("No limit"). TalkBack reads "No limit, button" fifteen times, with no app name.
   - The mindful-pause countdown row (`BadHabitDialogs.showMindfulPause`) changes its text every second, then silently becomes clickable. Nothing announces "ready".
   - The focus picker's 6th checkbox unticks itself silently (`AppPickerAdapter` cap of 5).
7. **Long text goes into toasts.** On Android 12+, text toasts are cut to 2 lines, and at 200% font
   `app_blocked_focus_scheduled` ("Focus scheduled until … Pause it in Settings › Digital Wellbeing ›
   Focus mode.") loses its second half. DECISIONS keeps the toast for 6.0. The minimum is to shorten the
   string so it fits 2 lines at 200%.

### 2.2 One-handed: large phone (6.7") and large tablet (Tab S8 11")

- **Phone:** home is mostly fine. Apps are vertically centred, and `homeBottomAlignment` exists, but only
  while Parem is default (`HomeFragment` resets it otherwise). Long-press anywhere and swipe-up anywhere
  are thumb-friendly.
- **Drawer, phone:** the search header sits at `marginTop=88dp`, results start at `180dp` and grow
  downward, and the keyboard covers the bottom half. The best match is the row farthest from the thumb.
  This is mitigated because Enter launches the top match and auto-launch needs no tap. Recommendation:
  a "Results near keyboard" option (`LinearLayoutManager.reverseLayout = true` plus gravity bottom on
  the RecyclerView while the IME is visible). It's a low priority, since Enter covers most of it.
- **Tablet, landscape, held with two hands:** every sheet is a centred `BottomSheetDialog` capped at
  Material's 640dp width, so on a ~1280dp-wide screen its rows span roughly 320–960dp, out of reach of
  both thumbs. Settings uses `paddingHorizontal=100dp` (WP13 replaces it). WP13/WP24 handle width but
  not reach. Recommendation (WP13 follow-up): in `values-w840dp`, set
  `BottomSheetDialog.behavior.maxWidth` to about 480dp. Optionally anchor the sheet to the side of the
  long-press (`window.attributes.gravity = BOTTOM|START/END` from the touch x). I'd leave the second
  part for 6.1.
- **Focus sheet:** a fixed 300dp RecyclerView sits inside a ScrollView inside a sheet. A thumb that
  starts on the list scrolls the list, not the sheet, and "Enable focus" is below the list. See 2.4.

### 2.3 Left-handed

Parem mostly serves left-handed users well:
- `homeAlignment` uses START/END with Left/Center/Right choices, and there is an app-label alignment pref.
- Swipe left and right are user-assignable.
- Sheets use full-width rows.

The remaining friction:
- Settings values sit at the far right (`layout_gravity="end|bottom"`) and are the only tap target. WP30 makes the whole row tappable, which fixes this.
- The drawer search text is `gravity="end"`, so typed text appears at the right edge, away from a left thumb. That's cosmetic. Make it follow `appLabelAlignment`.
- The drawer long-press strip puts "Delete" (uninstall) first, at the left. A left thumb resting there is the most likely accidental tap (see 2.8).

Verdict: no blocking issue. I list nothing in the top 12.

### 2.4 Motor impairment / tremor

1. **Swipe thresholds are raw pixels.** `OnSwipeTouchListener` and `ViewSwipeTouchListener` use
   `SWIPE_THRESHOLD = 100` px and `SWIPE_VELOCITY_THRESHOLD = 100` px/s. That is about 25dp on an
   xxxhdpi phone and about 50dp on the Tab S8, and 100 px/s is slower than a tremor drift.
   - A shaky tap on a home slot becomes a swipe: swipe down opens the shade, swipe left opens the camera, swipe right opens the dialer, swipe up opens the drawer or the slot's swipe-up app. The slot never launches.
   - The thresholds also differ by device density, so behaviour is inconsistent across phones.
2. **Double-tap on empty home locks the phone or toggles the torch.** `GestureDetector`'s double-tap
   timeout is fixed at 300ms, and a tremor bounce produces exactly that.
3. **Some things only open with a gesture.** Settings and widgets need a long-press on empty space,
   the drawer needs a swipe, and gesture letters need drawing. There is no tap alternative. Long-press
   does respect the system "Touch & hold delay", because GestureDetector uses
   `ViewConfiguration.getLongPressTimeout()`, which is good.
4. **The focus sheet nests scrolls.** See 2.2. With tremor, a drag inside the 300dp list never reaches
   "Enable focus".
5. **Small targets inside sheets.**
   - `ScreenTimeLimitDialog` `limitView` is 14sp with 4dp vertical padding, about 30dp tall.
   - The focus radio rows use 4dp padding.
   - The drawer long-press strip has six 12sp targets, one sixth of the row each.

   The settings rows are WP30. These sheet rows are not filed.

### 2.5 ADHD / compulsive phone use (the Calm-phone audience)

1. **The choice architecture points the wrong way.**
   - `BadHabitDialogs.showLimitWarning`: "Open anyway" is the first row at full colour, and "Go back" is second and `dimmed = true`.
   - The mindful-pause sheet dims "Go back" too.

   For this audience the calm action should be the prominent, first, thumb-resting choice.
2. **Escaping focus costs nothing.** `FocusModeDialog`'s "Disable focus" row disables focus at once.
   Opening an over-limit app costs at least one tap plus the warning. Leaving focus early costs one tap
   and no pause. The roadmap rules out hard blocking, but a pause is not a block.
3. **Mindful pause is buried.** The path is drawer long-press → "Set time limit" → pick a limit →
   long-press again → limit row → "Turn on mindful pause". `addMindfulPauseToggle` only shows the toggle
   once a limit exists. M4-WP12's onboarding screen 2 helps new users. Existing users and the drawer
   route stay three levels deep.
4. **You can only limit apps you used today.** `ScreenTimeLimitDialog` lists
   `appUsageMap…take(15)`, today's top 15 by usage. If you want to limit TikTok before tonight's
   relapse and haven't opened it yet today, it isn't in the list. The drawer route works, but you have
   to know it exists.
5. **No gesture starts focus or grayscale.** `Constants.GestureAction` has QUIET_LIST but no
   "Focus 25 min" or "Grayscale on/off". The moment of intent ("I need to stop now") should be one
   swipe away. Impeccable #8 proposes adding them to the home long-press menu. A gesture action is the
   power-user and ADHD complement to that.
6. **Single-match auto-launch defeats the drawer as a speed bump.** A habit app with no pause flag
   opens from two letters. That's fine for a user who opted out, but there is no way to opt out (see 2.7).

### 2.6 Non-English and RTL (Arabic, Hebrew)

1. **Calm phone is English-only in RTL.** `values-ar` has 104 strings and `values-he` 105. The base has
   about 174 translatable strings, plus **219 marked `translatable="false"`**, nearly all of them
   user-facing 6.0 copy (mindful pause, quiet list, grayscale, focus schedule, crash reports).
   - M4-WP9 targets de/es/pt-BR/fr/ru/it/pl/ja/zh/tr/et. **ar and he aren't in it**, yet `res/xml/locales_config.xml` offers both in the system per-app language picker.
   - An Arabic user gets a right-to-left shell with left-to-right English sheets. Mixed-direction strings with `%1$s` app names render with broken bidi ordering.
2. **More hard-coded English that WP9 can't reach, because it isn't in resources:**
   - `ScreenTimeLimitDialog`: "No limit", "Unlimited", `"${h}h"`/`"${m}m"`
   - `BadHabitDialogs.showTimeLimitPicker`: "15 minutes" … "2 hours"
   - `limitWarningText`: `"${hours}h ${mins}m"`
   - `formattedTimeSpent`: "0m"
   - `time_spent_hour`: `%1$sh %2$sm`, marked non-translatable

   Impeccable #11 covers the duration format. The listed literals are the extra.
3. **Physical labels on logical gravity.** Alignment options store `Gravity.START/END`, but
   `HomeScreenSettingsCard` labels them "Left"/"Right". In Hebrew, "Left" puts apps on the right. The
   fix is to label them by what the user sees: map START → "Right" when
   `layoutDirection == LAYOUT_DIRECTION_RTL`, or relabel as "Start/End" in translations.
4. **Digits are ASCII-only.** `ExpressionEvaluator.allowedChars` is `[0-9…]`, the `UnitConverter` regex
   uses `\d` (ASCII in Java regex), and `OmniboxResolver.DIAL_REGEX` is `[0-9]`. Arabic-Indic (٠–٩) and
   Persian digits typed from an Arabic keyboard silently fall through: no calculator, no conversion, no
   dial. The fix is one normalising pass at the top of `OmniboxResolver.resolve`, mapping each char
   with `Character.getNumericValue` to ASCII when it `isDigit()`, plus JVM tests.
5. **The graph doesn't mirror.** `ScreenTimeGraphView` draws days left to right with `drawText` at
   computed x, ignoring `layoutDirection`. In RTL, time should run right to left. Low priority.

### 2.7 Power user wanting speed

Well served: omnibox (calc, units, currency, dial, web, contacts), gesture letters, keyboard and D-pad
(M3-WP6), settings search (WP17), history (WP16), Enter to launch.

The gaps:
- **No setting for auto-launch.** DECISIONS says "auto-launch stays king". For speed that's right as a default. But the same behaviour is the main failure for personas 1, 4 and 5. A toggle in Home screen settings, defaulting on except under touch exploration, serves everyone (top issue 4).
- **No gesture action for Calm features** (2.5 #5).
- **Mindful pause is a fixed 5 s** (`MindfulPause.DEFAULT_DELAY_SECONDS`). Fine for 6.0.

### 2.8 First-time minimalist from a stock launcher ("will I lose my apps?")

1. **Nothing says this is reversible.** M4-WP12 screen 1 says "Parem only works as your home screen".
   Its copy doesn't say that apps aren't removed, or how to switch back.
2. **Portrait home has no hint at all.** `@id/firstRunTips` ("1. Swipe up for all apps 2. Long press
   anywhere for settings") exists **only in `layout-land/fragment_home.xml`**. Portrait has no
   equivalent. WP12 drops the feature tour. So the portrait first-timer lands on four "App" hints
   (impeccable #6) and nothing says the drawer exists. That's exactly the "where did my apps go?" moment.
3. **Hidden apps are hard to get back.** You hide an app from the drawer strip. Getting it back means
   knowing what `hidden_apps_message` says: "Tap on 'Parem Launcher' at the top of this Settings page".
   `@id/paremHiddenApps` is the card *title*, with no affordance, and the omnibox never matches hidden
   apps (`AppDrawerFragment` ~line 328, deliberately, for privacy). Type the exact name of an app you
   hid and you get zero results with no explanation. That is the "lost app" fear made real.
4. **"Delete" means uninstall in the drawer strip and is its first item.** Impeccable #5 covers the wording.
   The inclusive addition is about position: destructive goes last, never first.

---

## 3. Top issues (highest impact first, all unfiled)

### 1. TalkBack lands on the invisible lock view first, reads an internal ID, and double-tap locks the phone
- **Who:** 1 (also Switch Access).
- **Evidence:** `layout/fragment_home.xml:11-16`, `layout-land/fragment_home.xml`, `HomeFragment.kt:180`
  (click listener, so clickable), `MyAccessibilityService.kt:27-29`.
- **Fix (trap #1 safe; the description, the view and its no-op handler all stay):** hide the view from
  the accessibility *node tree* while keeping its *events*. In `HomeFragment.initClickListeners`, add:
  ```kotlin
  ViewCompat.setAccessibilityDelegate(binding.lock, object : AccessibilityDelegateCompat() {
      override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
          super.onInitializeAccessibilityNodeInfo(host, info)
          info.isVisibleToUser = false
          info.removeAction(AccessibilityNodeInfoCompat.AccessibilityActionCompat.ACTION_CLICK)
      }
  })
  ```
  Don't use `importantForAccessibility="no"`. Services without `flagIncludeNotImportantViews` stop
  receiving events from not-important views, which would break the lock unless
  `accessibility_service_config.xml` also gains that flag. That's a bigger change to a Play-declared
  config.
- **Device check (mandatory):** with TalkBack on, linear navigation skips the view, and double-tap to
  lock (TalkBack off) still locks. Add both to `docs/RELEASE_CHECKLIST.md`.
- **Files:** `ui/HomeFragment.kt`, `docs/RELEASE_CHECKLIST.md`. Size S.

### 2. Make every home gesture reachable without a gesture
- **Who:** 1, 4, 8. Impeccable #2 covers the custom actions, so this is the part it lacks.
- **Problem:** custom actions on `mainLayout` are invisible unless you know TalkBack's actions menu.
  Tremor and Switch Access users have no route either.
- **Fix:**
  - (a) Add the `ViewCompat.addAccessibilityAction` set impeccable #2 lists, on `mainLayout` **and on every slot view**. TalkBack users spend their time focused on slots, not on empty space.
  - (b) Rebuild the home long-press menu through `BottomSheetMenu` with "All apps", "Notifications", "Add widget", "Settings". The long-press menu then becomes the tap alternative to every swipe.
  - (c) When `AccessibilityManager.isTouchExplorationEnabled` is true, show one visible, focusable `TextView` row "All apps" (style `TextLarge`, `primaryColorTrans50`) under the slots, in both home layouts. It costs nothing visually for everyone else.
- **Files:** `ui/home/HomeGesturesController.kt` (`showHomeLongPressMenu`), `ui/HomeFragment.kt`,
  `layout/fragment_home.xml` and the `layout-land` copy, `layout/dialog_home_menu.xml` (delete if (b)),
  `strings.xml`. Size M.

### 3. Swipe and double-tap thresholds are raw pixels, so tremor taps become swipes
- **Who:** 4, and indirectly everyone on high-density phones.
- **Fix:**
  - In both listeners, derive thresholds from `ViewConfiguration.get(context)`: distance at least `max(scaledTouchSlop * 3, 48.dpToPx())`, velocity at least `scaledMinimumFlingVelocity * 4`.
  - Require the off-axis movement to be under half the on-axis movement, so diagonal jitter doesn't count.
  - For the slot listener, add one more rule: a gesture that never left touch slop is a tap even if it lasted long.
- **Optional:** a "Gesture sensitivity: Normal / Steady hands needed" row that doubles the distance
  threshold and requires a 500ms double-tap window for lock.
- **Files:** `listener/OnSwipeTouchListener.kt`, `listener/ViewSwipeTouchListener.kt` (plus optionally
  `Prefs`, `GesturesSettingsCard.kt`). JVM-testable if the threshold maths moves into a small pure
  function. Size S.

### 4. Single-match auto-launch can't be turned off and fires mid-typing for screen-reader and typo-prone users
- **Who:** 1, 4, 5, and 7 benefits from the toggle. WP27 only guards digits and maths.
- **Fix:**
  - Add an "Open single match automatically" row (Home screen card), default on, respecting DECISIONS.
  - Force it off when `AccessibilityManager.isTouchExplorationEnabled` or any enabled service has
    `FEEDBACK_SPOKEN`.
  - When on, wait about 300ms after the last keystroke before launching (`Handler.postDelayed`,
    cancelled on the next `performFiltering`), so "chr" → "chro" doesn't launch on the "r".
  - Gate it in `AppDrawerAdapter.autoLaunch` via the existing `autoLaunchGuard` lambda set in
    `AppDrawerFragment`.
- **Files:** `ui/AppDrawerAdapter.kt`, `ui/AppDrawerFragment.kt`, `data/Prefs.kt` (append key),
  `ui/settings/HomeScreenSettingsCard.kt`, `layout/fragment_settings.xml` (+land),
  `ui/settings/SettingsSearchIndex.kt`. Size S/M.

### 5. Calm sheets push "Open anyway", and leaving focus has no pause
- **Who:** 5 (the core 6.0 audience).
- **Fix:**
  - In `BadHabitDialogs.showLimitWarning`, swap the order: "Go back" first at full colour, "Open anyway" second and `dimmed = true`. In `showMindfulPause`, "Go back" becomes the first, undimmed row, with the countdown row below it.
  - In `FocusModeDialog`, route "Disable focus" through the same countdown mechanism, extracted from `showMindfulPause` as `BadHabitDialogs.showPause(context, message, confirmLabel, onConfirm)`. The message: "End focus early? (n min left)". This is a pause, not a block, so it stays within M4's "no hard blocking" rule.
- **Files:** `ui/BadHabitDialogs.kt`, `ui/FocusModeDialog.kt`, `strings.xml`. Size S.

### 6. Arabic and Hebrew are offered but get an English Calm phone
- **Who:** 6.
- **Fix (pick one, Patric's call):**
  - (a) Add `ar` and `he` to M4-WP9's target list (machine draft plus a native-review list, as for the others).
  - (b) Until they're complete, drop `ar`/`he` from `res/xml/locales_config.xml`, so the system picker doesn't promise them. The system locale still falls back.

  Either way:
  - Move the Kotlin literals listed in 2.6 #2 into `strings.xml`/`plurals`.
  - Relabel alignment for RTL (2.6 #3).
  - Mark mixed-direction format strings with `BidiFormatter.getInstance().unicodeWrap(appName)`, used where `%1$s` is an app label (`mindful_pause_*`, `app_limit_warning`).
- **Files:** `res/values-ar/strings.xml`, `res/values-he/strings.xml`, `res/xml/locales_config.xml`,
  `ui/ScreenTimeLimitDialog.kt`, `ui/BadHabitDialogs.kt`, `helper/Extensions.kt`,
  `ui/settings/HomeScreenSettingsCard.kt`, `docs/ROADMAP.md` (WP9 row). Size M (L with full translations).

### 7. Tell the first-timer their apps are safe and the drawer exists (portrait has no hint)
- **Who:** 8, 1.
- **Fix:**
  - (a) Add `@id/firstRunTips` to `layout/fragment_home.xml`, the same view as in land, bottom-anchored above `setDefaultLauncher`. Show it until the drawer has been opened once, using an existing pref or one appended. WP28 owns the land copy's visibility bug, so coordinate with it.
  - (b) Feed M4-WP12 screen 1 the reassurance line: "Your apps stay installed. Switch back anytime in Settings › Default apps." Use `primaryColorTrans80`.
  - (c) On the drawer's empty-result state, when the query matches a hidden app's label, show one dim row "Not in the list? Check hidden apps" that opens the hidden-apps list. It reveals nothing about *which* app, so the privacy intent at `AppDrawerFragment` ~328 holds. Skip it when Private Space is locked.
- **Files:** `layout/fragment_home.xml`, `ui/HomeFragment.kt` or `ui/home/HomeSlotsController.kt`,
  `ui/AppDrawerFragment.kt`, `strings.xml`, `docs/design/M4-WP12.md` (copy note). Size S/M.

### 8. Hidden-apps recovery sits behind an unmarked title tap
- **Who:** 8, 1.
- **Fix:** add an explicit row "Hidden apps (n)" in the Home screen card, with the count from
  `prefs.hiddenApps.size`. Keep the title tap as a legacy shortcut. Reword `hidden_apps_message` to
  point at the row. Give `@id/appInfo` a `contentDescription` too.
- **Files:** `layout/fragment_settings.xml` (+land), `ui/settings/AppInfoSettingsCard.kt` or
  `HomeScreenSettingsCard.kt`, `ui/settings/SettingsSearchIndex.kt`, `strings.xml`. Size S.

### 9. Sheets don't speak: no headings, row context, ready announcement or cap feedback
- **Who:** 1.
- **Fix, all inside existing builders:**
  - `BottomSheetMenu.title()`: `ViewCompat.setAccessibilityHeading(view, true)`. Settings card titles: `android:accessibilityHeading="true"` (API 28+, fine at minSdk 29).
  - Drag handles: `importantForAccessibility="no"` (in `BottomSheetMenu` and the hand-built sheets: `ScreenTimeLimitDialog`, `FocusModeDialog`).
  - `ScreenTimeLimitDialog`: make the **row** the click target, with `row.contentDescription = "$appName, ${usage} today, limit ${limitLabel}"`. Mark the child texts `IMPORTANT_FOR_ACCESSIBILITY_NO`.
  - Mindful pause: `openRow.accessibilityLiveRegion = ACCESSIBILITY_LIVE_REGION_POLITE`, set only when the countdown ends, so TalkBack hears "Open X" once rather than five ticks.
  - `AppPickerAdapter` cap: on rejection, call `holder.checkBox.announceForAccessibility(...)` and show a toast "Up to 5 apps".
- **Files:** `ui/BottomSheetMenu.kt`, `ui/ScreenTimeLimitDialog.kt`, `ui/BadHabitDialogs.kt`,
  `ui/AppPickerAdapter.kt`, `ui/FocusModeDialog.kt`, `layout/fragment_settings.xml` (+land),
  `strings.xml`. Size M.

### 10. Focus sheet: scroll inside a scroll inside a sheet, plus tiny radio rows
- **Who:** 4, 2, 1 (at 200% font the 300dp list shows about 3 rows).
- **Fix:**
  - Drop the inner fixed-height RecyclerView. Show the 5 selected apps as rows, then an "Choose apps…" row that opens the picker in its own `BottomSheetMenu.customView` sheet (the folder picker pattern), where the list is the only scroller.
  - Give the radios `minHeight="48dp"` and 12dp vertical padding.
  - Put "Enable focus" first, under the duration choices. Schedule settings come after.
- **Files:** `ui/FocusModeDialog.kt` (WP24 owns its width; coordinate), `ui/AppPickerAdapter.kt`
  (unchanged API). Size M.

### 11. Let people limit any app and reach mindful pause in one step
- **Who:** 5.
- **Fix:**
  - `ScreenTimeLimitDialog`: below the top-15, add "Add another app…", which opens the existing app picker (single-select) and then the limit picker.
  - Show the mindful-pause toggle inside the limit picker even when no limit is set yet. Choosing "Pause only" sets the pause with an `Unlimited` limit, which needs `AppLimitManager` to allow pause without a limit. Check `MindfulPause.decide`: it already handles `pause && !overLimit` → PAUSE.
- **Files:** `ui/ScreenTimeLimitDialog.kt`, `ui/BadHabitDialogs.kt`, `helper/AppLimitManager.kt`, plus JVM
  test. Size M.

### 12. Arabic-Indic and Persian digits break the omnibox
- **Who:** 6.
- **Fix:** normalise digits once at the top of `OmniboxResolver`'s resolve entry point. Use
  `buildString { s.forEach { append(if (it.isDigit()) ('0' + Character.getNumericValue(it)) else it) } }`,
  and also map the Arabic decimal separator `٫` to `.`. Add must-parse tests for `٥+٥`, `١٠ km in mi`,
  `+٣٧٢ ٥٥٥ ١٢٣٤`.
- **Files:** `helper/OmniboxResolver.kt`, `app/src/test/.../OmniboxResolverTest.kt`. Size S.

Below the cut, worth a line each:
- Gesture actions "Start focus 25 min" and "Toggle grayscale" in `Constants.GestureAction` and `HomeGesturesController.executeGestureAction` (2.5 #5).
- Tablet sheet reach: a `maxWidth` around 480dp at w840dp (2.2).
- Shorten `app_blocked_focus_scheduled*` to fit 2 toast lines at 200%.
- `ScreenTimeGraphView` RTL mirroring.
- Drawer search `gravity` follows `appLabelAlignment`.
- Drawer strip: destructive action last.

---

## 4. Overlap map

| This report | Already covered by | What this adds |
|---|---|---|
| 2.1 #1 font scale | impeccable #1 | multiply, no upper clamp |
| top 2 | impeccable #2 (actions, settings semantics) | slot-level actions, the long-press menu as the tap route, visible "All apps" under touch exploration |
| 2.8 #4 Delete wording | impeccable #5 | ordering |
| 2.8 #2 portrait hint | impeccable #6 (empty slots) | the missing portrait `firstRunTips` |
| top 6 literals | impeccable #11 (duration format) | the other literals, ar/he missing from WP9, bidi |
| settings tap targets | M4-WP30 | none (left to WP30) |
| sheet width on tablet | M4-WP24, WP13 | reach, not width |
| digit auto-launch | M4-WP27 | the general auto-launch toggle |

## 5. Not verified (needs a device)

- Top 1: that TalkBack skips a node with `isVisibleToUser=false` and no click action, and that the lock
  still fires. My reasoning is from the framework: `TYPE_VIEW_CLICKED` comes from `performClick`, not from the node.
  Trap #1, so test both directions before merging.
- How TalkBack actually orders the home screen on Android 15. The claim that the lock view comes first
  rests on view order, not on a recording.
- The thresholds in top 3 are proposals. Tune them on the Tab S8 and a 420dpi phone.
- Toast truncation at 200% font on the Tab S8 (One UI may differ from AOSP).
- Every screen state. No new screenshots exist because the capture was blocked (see INDEX.md).
