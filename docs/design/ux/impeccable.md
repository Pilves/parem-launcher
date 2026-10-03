# Parem 6.0 — Impeccable critique + native audit

DEGRADED: single-context. This ran as a workflow subagent with no sub-agent tool, so Assessment A (design review) and Assessment B (evidence) were done one after the other in the same context. `impeccable detect` was not run: it scans web markup, and the native audit (`audit.native.md`) says to audit from source.

- **Date:** 2026-10-03
- **Code read:** `origin/next` at 3915b38
- **Evidence:** the QA shots in `scratchpad/qa/` (Tab S8, Android 15, portrait and landscape, bold on/off). No new captures: the device was locked (see `scratchpad/ux-shots/INDEX.md`). There are no phone captures, so phone-only findings come from reading the code.
- **Scope rule:** anything already filed as M4-WP22..30 is left out. If a finding touches one of those rows, the row is named and only the part that is not filed is described.
- **Platform translation:** every recommendation is in Android Views terms: XML layouts, `dimens`/`styles`/theme attrs, `ui/BottomSheetMenu`, and the Material 1.12 / AppCompat 1.7 components that are already dependencies. No CSS, no Compose, no new frameworks.

---

## Design health score (Nielsen, 0–4)

| # | Heuristic | Score | Key issue |
|---|---|---|---|
| 1 | Visibility of system status | 2 | Settings values look like labels, not state. The screen-time figure "1m" has no unit or context. Toasts are the only feedback. |
| 2 | Match system / real world | 2 | "Delete" means uninstall in the drawer but means remove-from-home on home. Text size is shown as "4". The card is called "Digital Wellbeing", which is Google's app name. |
| 3 | User control and freedom | 2 | Removing a home slot or folder can't be undone. Toasts can't be acted on. |
| 4 | Consistency and standards | 2 | There are three long-press menu systems: an inline icon strip, a hand-built dialog and BottomSheetMenu. The type scale depends on screen density. Case style is mixed (Title Case vs sentence case). |
| 5 | Error prevention | 2 | A destructive action ("Delete") sits next to harmless ones with no confirmation or undo. |
| 6 | Recognition rather than recall | 1 | Every route out of home is an invisible gesture. Empty slots read "App". The drawer field's hint is "___" and its cursor is hidden. |
| 7 | Flexibility and efficiency | 3 | The omnibox, gesture letters and swipe apps are strong for experts. |
| 8 | Aesthetic and minimalist design | 3 | The text-first home is honest and calm. Settings is plain and has no hierarchy. |
| 9 | Error recovery | 2 | A failed widget shows "Can't show content" with no fix offered in place. Messages that should suggest a fix only say what went wrong. |
| 10 | Help and documentation | 1 | The "About and FAQs" row is hidden because its URL is empty. No setting has an explanation line. |
| **Total** | | **20/40** | **Acceptable: significant work needed** |

## Native audit score (0–4)

| # | Dimension | Score | Key finding |
|---|---|---|---|
| 1 | Accessibility | **1** | `MainActivity.attachBaseContext` **replaces** the system font scale with the in-app scale. TalkBack users have no way to reach the drawer or settings. Settings rows announce "Off" without saying what is off. |
| 2 | Performance | 3 | Not the focus of this audit. Nothing UI-visible stood out. |
| 3 | Appearance & theming | 2 | Mono tokens exist (`primaryColor*` attrs), but BottomSheetMenu and some layouts hard-code sizes, alpha and weights. |
| 4 | Platform conformance | 2 | On targetSdk 36 the app still uses deprecated translucent-window flags and fixed dp margins, with no `WindowInsets` handling. Feedback is a Toast where Material expects a Snackbar. |
| 5 | Adaptivity | 1 | Text sizes are keyed on density buckets, so the tablet gets *smaller* text than a phone. The tablet shows a phone column with about 75% empty space. M4-WP13/24/28 cover the layout half of this. |
| **Total** | | **9/20** | **Poor**, driven by accessibility and adaptivity |

## Design specificity verdict

**This is authored, not generic.** A big thin clock, a left-aligned list of words and nothing else is a real point of view. It suits "Calm phone" better than any Material grid would, and the home screen in `home-bold-off.png` is quietly beautiful. Keep it.

The voice breaks the moment the user goes one level deeper:

- **Settings** (`bold-off-settings.png`) is a stack of identical rounded cards with right-aligned bold values. Any Olauncher fork looks like this. Parem's 6.0 differentiator, the Calm-phone card, is the **last** card on the page.
- **Long-press menus** come in three different visual languages.
- **The drawer** opens on an empty field whose hint is literally `___`.

The brand's calm comes from removing things. The product's confusion comes from removing the wrong things: labels, affordances and undo.

## What's working

1. **Home composition.** The clock is the one focal point. App names are set at a single size with generous 10–14dp vertical padding, and nothing competes. This is the right look for a Calm-phone launcher and should not be redesigned.
2. **The omnibox concept.** Search, maths, conversion, currency, dialling and settings search in one field is the best power feature here. It only needs to be discoverable (issue 6).
3. **BottomSheetMenu as the single sheet builder.** It's the right abstraction (handle + title + rows), and it already handles e-ink and turned-off animations. Issues 8 and 9 make it the *only* path and give it the two row types it lacks.

---

## Priority issues (ranked)

### 1. [P0] Parem overrides the system font size
- **What:** `MainActivity.attachBaseContext` sets `newConfig.fontScale = Prefs.textSizeScale`, which ranges from 0.6 to 1.3. That *replaces* the user's Android font size instead of multiplying it. A low-vision user at system 1.5× or 2.0× (Android 14+ allows up to 200%) gets Parem at 1.0×. Every sheet and dialog built from the activity context inherits the override. The in-app picker labels its seven steps "1"–"7", and settings shows "Text size 4". Setting "1" (0.6) makes 14sp text render at about 8.4sp.
- **Why it matters:** this fails every user who relies on large text: older users and low-vision users. It also fails Play's accessibility expectations for a home app, which is the one app a person can't avoid.
- **Fix:** in `MainActivity.attachBaseContext`, use `newConfig.fontScale = context.resources.configuration.fontScale * prefs.textSizeScale`. Optionally clamp the product to 0.85..2.0 so the home column still fits. With nonlinear font scaling on API 34+, `sp` sizes stay sane. Relabel the picker in `AppearanceSettingsCard` and `strings.xml` with relative steps ("Smaller", "Small", "Default", "Large", "Larger" …) and show the chosen label as the row value instead of "4". Consider dropping the 0.6 step.
- **Files:** `MainActivity.kt`, `ui/settings/AppearanceSettingsCard.kt`, `data/Constants.kt` (`TextSize`), `res/values/strings.xml`, `layout/fragment_settings.xml` (+`layout-land`)
- **Command:** `/impeccable harden`

### 2. [P0] TalkBack users can't get around
- **What:** the only `contentDescription` in any layout is the lock view's, which is load-bearing (Trap #1; leave it alone). Every way out of home is a gesture that TalkBack takes over: swipe up for the drawer, long-press on empty space for settings, swipe down for notifications. No `ViewCompat.addAccessibilityAction` exists anywhere. The settings label and value are separate TextViews, and only the value is clickable, so TalkBack reads "Off, double-tap to activate" with no label. The `appInfo` ImageView has no description. The screen-time text "1m" is read as "one metre". The 4dp work-profile dot in drawer rows (`otherProfileIndicator`) is unlabeled.
- **Why it matters:** a blind or low-vision user who installs Parem as their home app can launch the 4–8 slot apps and nothing else. They can't reach the drawer, settings or the Calm-phone features. For a default home app this is a hard blocker.
- **Fix (no new framework):**
  - On `binding.mainLayout` in `HomeFragment` / `HomeGesturesController`, add custom accessibility actions with `ViewCompat.addAccessibilityAction(mainLayout, getString(R.string.a11y_open_app_drawer)) { showAppList(...); true }`. Add the same for "Settings", "Notifications" and, when configured, swipe-left and swipe-right apps. TalkBack lists these in its actions menu. Do **not** touch `@id/lock` or `lock_layout_description`.
  - Settings: give each row's container (the `FrameLayout` around label and value) `android:screenReaderFocusable="true"` (API 28+, fine at minSdk 29). Mark the label `importantForAccessibility="no"`. In each settings card's `bind()`, set `ViewCompat.setStateDescription(row, "On"/"Off")` and the role through `ViewCompat.setAccessibilityDelegate` (`info.className = Switch::class.java.name` for toggles, `Spinner` for pickers). WP30 makes the whole row the tap target; this is the announcement half, which WP30 does not cover.
  - Set `contentDescription` on `appInfo` ("App info") and on `otherProfileIndicator` ("Work profile"). Give `tvScreenTime` a spoken form: "Screen time today, 1 minute".
- **Files:** `ui/HomeFragment.kt`, `ui/home/HomeGesturesController.kt`, `ui/home/HomeClockController.kt`, `ui/settings/*SettingsCard.kt`, `layout/fragment_settings.xml` (+land), `layout/adapter_app_drawer.xml`, `strings.xml`
- **Command:** `/impeccable harden` → `/impeccable audit` re-run

### 3. [P1] Window insets are ignored, and the app targets SDK 36
- **What:** `AppTheme` still sets `windowTranslucentStatus` and `windowTranslucentNavigation`, both deprecated. All placement uses fixed dp: home `marginTop=56dp`, `paddingTop=112dp`, `paddingBottom=48dp`; drawer `88dp`/`180dp`; settings `paddingVertical=64dp`. There's no `setOnApplyWindowInsetsListener` anywhere. With targetSdk 36, edge-to-edge is enforced and the opt-out is gone. Phones in landscape have the camera cutout on a short edge (`shortEdges` is set), so the 20dp side margin can put "Calculator" under the punch-hole. A 3-button nav bar (48dp) or a large display-cutout status bar shifts everything.
- **Not covered by WP26/WP13:** WP26 is limited to slot *fitting* against the nav bar. This issue is about system-wide inset padding on every screen and sheet.
- **Fix:** remove the two translucent flags from `styles.xml`. Keep the transparent bar colours. Call `WindowCompat.setDecorFitsSystemWindows(window, false)` once in `MainActivity`. In each fragment root (`fragment_home`, `fragment_app_drawer`, `fragment_settings`, `fragment_onboarding`), apply `ViewCompat.setOnApplyWindowInsetsListener` and pad by `systemBars() or displayCutout()`. Then make the fixed dp values *additive design spacing*: keep 20dp gutters plus the inset, and drop the 56/88/112dp "guess the status bar" offsets. Drawer search needs `ime()` too, so results never sit under the keyboard.
- **Files:** `res/values/styles.xml`, `MainActivity.kt`, `ui/HomeFragment.kt`, `ui/AppDrawerFragment.kt`, `ui/SettingsFragment.kt`, `ui/OnboardingFragment.kt`, the four layouts and their `layout-land` versions
- **Command:** `/impeccable adapt`

### 4. [P1] Text sizes follow screen density, not screen size, and the tablet loses
- **What:** `text_large`, `text_small`, `time_size` and `date_size` live only in `values-{l,m,h,xh,xxh,xxxh}dpi/dimens.xml`. `sp` is already density-independent, so this scales text with *pixel density*. A 420dpi phone gets 30sp app names and a 66sp clock. The Tab S8 (xhdpi bucket) gets 28sp and 60sp, so the largest screen has the smallest type. Within one screen, the 60sp clock over a 16sp date is a 3.75:1 jump, and the date reads like a footnote (see `home-bold-off.png`: "Sat, 3 Oct, 84%" is tiny under the clock).
- **Fix:** move to `values/dimens.xml` (base, phone) plus `values-sw600dp/dimens.xml` (tablet/unfolded), and optionally `values-w840dp` for landscape on large screens. Delete the density folders. Suggested base: `text_large 28sp`, `text_small 16sp`, `time_size 64sp`, `date_size 18sp`. For sw600dp: `32/18/88/22sp`, `home_app_padding_vertical 14dp`. Express these as named text appearances in `styles.xml` (`TextAppearance.Parem.Display`, `.Title`, `.Body`, `.Label`) so BottomSheetMenu and the settings cards stop hard-coding 12/14/16f.
- **Files:** `res/values*/dimens.xml`, `res/values/styles.xml`, `res/values-sw600dp/dimens.xml` (new resource-qualifier file)
- **Command:** `/impeccable typeset`

### 5. [P1] One word for two actions, and no undo
- **What:** `R.string.delete` ("Delete") labels **uninstall** in the drawer row overlay (`appDelete`) and **remove from home** in `HomeSlotsController.showHomeSlotMenu`. On home it also clears a folder immediately. There's no confirmation and no undo. About 70 `showToast` calls carry all feedback, including the empty-slot hint and "Home layout is locked. Unlock it in Settings." Neither offers an action.
- **Why it matters:** a first-timer clearing a slot reads "Delete" as uninstall and hesitates. A user who clears a folder by mistake loses its whole contents for good.
- **Fix:** add the strings `remove_from_home` ("Remove from home") and `uninstall` ("Uninstall"). On removal, show a `com.google.android.material.snackbar.Snackbar` anchored to `mainLayout` with "Undo"; restoring means writing back the five slot prefs and the `FolderData` saved before clearing. For the locked-layout case, a Snackbar with the action "Unlock" calls `prefs.homeLayoutLocked = false`. Keep Toasts only where no view is attached.
- **Files:** `ui/home/HomeSlotsController.kt`, `ui/AppDrawerAdapter.kt`, `layout/adapter_app_drawer.xml`, `strings.xml`, `helper/FolderManager.kt` (read-only snapshot for undo)
- **Command:** `/impeccable clarify` + `/impeccable harden`

### 6. [P1] Nothing on screen says what to do
- **What:**
  - A fresh install shows four slots that each read **"App"** (`android:hint="@string/app"`; see `02-onboarding-dismissed…png`). That looks like a bug, not an invitation.
  - The drawer search has `queryHint="___"` and `cursorVisible=false` (`AppSearchText`). The omnibox, the best feature, opens as a blank screen with no visible field.
  - `app_drawer_tips` is a marquee one-liner that only mentions auto-launch.
  - After first run, the only way to reach settings is long-pressing empty space, and nothing on screen says so. Onboarding is a brochure of 20+ bullets, and its redesign is M4-WP12.
- **Fix (in-place affordances; onboarding stays WP12's job):**
  - Empty slot hint: "Long-press to add app" in `primaryColorTrans50`, or "+ Add app". Slot 1 only, with the rest hidden until slot 1 is filled.
  - Drawer: `queryHint="Search apps, 12×4, 5 km in mi…"` at `primaryColorTrans50`, set `cursorVisible=true`, and drop the marquee tip.
  - On a short tap on empty home (currently a no-op), show a Snackbar once per session for the first 3 days: "Long-press for settings · swipe up for apps".
- **Files:** `layout/fragment_home.xml` (+land), `ui/home/HomeSlotsController.kt`, `layout/fragment_app_drawer.xml` (+land), `res/values/styles.xml` (`AppSearchText`), `strings.xml`
- **Command:** `/impeccable onboard`

### 7. [P1] Settings structure buries the 6.0 story, and the card borrows Google's name
- **What:** the card order is Parem Launcher (export/import/crash reports) → Home screen → Appearance → Gestures → **Digital Wellbeing**. Housekeeping takes the top spot and the Calm-phone features are last. "Digital Wellbeing" is the name of Google's system app, which shows up in the same Settings search and the same Play category. That invites confusion and impersonation questions in Play review. No row has an explanation line, yet several labels are opaque without one: "Hide from shade, keep for later", "Gesture letters", "Lock home layout", "Contact search", "Bottom: Off", "Show date time".
- **Fix:**
  - Reorder the cards in `fragment_settings.xml` (+land): **Calm** (screen time, app limits, mindful pause, focus, quiet notifications, grayscale) → Home screen → Gestures → Appearance → About and backup (default launcher, export/import, crash reports, last).
  - Rename `wellbeing` to "Calm" or "Attention".
  - Add a `TextSmallLight` summary TextView (14sp, `primaryColorTrans80`) under each non-obvious label. For example: "Notifications from apps you didn't allow wait in a quiet list", "Draw a letter on home to open an app", "Stops long-press from changing home apps".
  - Use sentence case everywhere ("Show date and time"). Material uses sentence case, and the onboarding Title Case ("You're All Set", "Get Started") should match.
- **Files:** `layout/fragment_settings.xml`, `layout-land/fragment_settings.xml`, `res/values/strings.xml`, `ui/settings/SettingsSearchIndex.kt` (order and labels stay in sync for WP17 search)
- **Command:** `/impeccable distill` + `/impeccable clarify`

### 8. [P2] Three long-press menu systems
- **What:**
  - *Drawer row* long-press shows an inline overlay strip with six icon+12sp actions (Delete, Set time limit, Rename, Hide, Info, Close). The labels are ellipsized, and there are more than four options with no grouping.
  - *Home slot* long-press uses `BottomSheetMenu` with no title.
  - *Home empty* long-press inflates a hand-built `dialog_home_menu.xml` in a raw `BottomSheetDialog`, which bypasses the builder.
  - The same app gets a different menu depending on where it was long-pressed.
- **Partly filed:** WP25 (expanded state in landscape) and WP30 ("Close" overlap) treat the symptoms. Consolidating removes both.
- **Fix:** route all three through `BottomSheetMenu`, with `.title(appName)` and a stable order: Open info · Rename · Time limit / Mindful pause · Hide · (divider) · Uninstall / Remove from home. Delete `dialog_home_menu.xml`. Rebuild the home-empty menu as `BottomSheetMenu(...).option(Add widget).option(Wallpaper).option(Settings)`, and add the Calm quick actions there when enabled: "Focus for 25 min" and "Grayscale on/off". This turns the one gesture people do discover into the Calm-phone hub.
- **Files:** `ui/AppDrawerAdapter.kt`, `layout/adapter_app_drawer.xml` (remove `appHideLayout` strip), `ui/home/HomeGesturesController.kt`, `ui/home/HomeSlotsController.kt`, `layout/dialog_home_menu.xml` (delete)
- **Command:** `/impeccable distill`

### 9. [P2] BottomSheetMenu doesn't match the rest of the app and can't show state
- **What:** rows are built in code at `textSize = 14f/16f` in the default typeface. They ignore `?attr/mainFontFamily`, so home and drawer are light/regular per the bold setting while sheets are always regular. The title is 14sp bold at 50% alpha. There's no row type for a summary line or a selected state. The theme picker (`AppearanceSettingsCard` lines 252–256) lists Light, Dark, System, Scheduled and Sunrise/sunset without marking the current choice, so the user has to remember it.
- **Fix:** extend the builder, not new UI. Add `option(text, summary: String? = null, checked: Boolean = false, …)`. Show the summary as a second TextView with `TextSmallLight`. Show checked as `setCompoundDrawablesRelativeWithIntrinsicBounds(0,0,R.drawable.ic_check,0)` plus `isSelected`/`stateDescription`. Apply `TextAppearance.Parem.*` from issue 4 and `?attr/mainFontFamily` through `Typeface.create(fontFamilyAttr, …)`. Raise the title to `primaryColorTrans80`. Every picker (theme, gestures, widget placement, alignment) then shows "current = checked".
- **Files:** `ui/BottomSheetMenu.kt`, `res/values/styles.xml`, `ui/settings/AppearanceSettingsCard.kt`, `ui/settings/GesturesSettingsCard.kt`, `ui/settings/HomeScreenSettingsCard.kt`, `res/drawable/ic_check.xml` (new vector)
- **Command:** `/impeccable polish`

### 10. [P2] Home text can get lost on bright wallpapers
- **What:** home text is `sans-serif-light` (weight 300) when Bold is off, with only a 4px, 50% shadow (`TextDefault`). On the bright blue landscape wallpaper (`08-…png`), light-weight white text over sky is just about legible. The screen-time label runs at `alpha 0.6` and drawer usage time at `12sp`/`alpha 0.5`, which fail 4.5:1 over mid-tone wallpaper. Daily wallpaper (Bing/Unsplash style) makes this a daily lottery.
- **Fix:** add an opt-in "Dim wallpaper" row in Appearance, default **on** when daily wallpaper is on. It should draw a `GradientDrawable` scrim (0→35% `primaryColorInverse`) behind `homeAppsLayout` and `dateTimeLayout`, set from `HomeFragment`. Raise the alpha floors to 0.75 for `tvScreenTime` and `appUsageTime`. Move `appUsageTime` from 12sp to `text_small`. Keep light weight for the 64sp+ clock only, and use regular for app names at ≤ 30sp.
- **Files:** `res/values/styles.xml`, `layout/fragment_home.xml` (+land), `layout/adapter_app_drawer.xml`, `ui/HomeFragment.kt`, `ui/settings/AppearanceSettingsCard.kt`, `data/Prefs.kt`
- **Command:** `/impeccable polish`

### 11. [P3] Status copy is cryptic and English-only
- **What:** `Extensions.kt` builds "Xh Ym" and "<1m" from hard-coded literals, which aren't plural-aware or localizable and carry no "today" context. The home screen shows "1m" floating far from the clock in landscape. Other strings: "Github" (should be "GitHub"), `set_as_default_launcher_u` uses `<u>` underline (a web-link look; Material would use a text button), and "Bottom: Off". WP9 covers making strings *translatable*; this issue is about the duration *format* and the wording.
- **Fix:** format durations with `android.icu.text.MeasureFormat` (API 24+) in `FormatWidth.NARROW`, which gives localized "1 min" / "1 Min." / "1 мин". Show "1m today" through a `<plurals>` resource. Fix the wording in `strings.xml`. Replace the underlined default-launcher banner with a `TextMedium` + `selectableItemBackground` text button labelled "Make Parem your home app".
- **Files:** `helper/Extensions.kt`, `ui/home/HomeClockController.kt`, `ui/AppDrawerAdapter.kt`, `res/values/strings.xml`, `layout/fragment_home.xml`
- **Command:** `/impeccable clarify`

---

## Persona red flags

- **Jordan, first-timer** (installs from Play):
  - After setting Parem as home, Jordan sees four "App" labels and a clock.
  - Swipe-up is guessable, but then a blank screen with a `___` hint and no cursor appears.
  - Typing "5" launches some app (WP27).
  - Jordan never learns that long-press opens settings once the first-run tips are gone.
  - Most likely to quit at the empty home.
- **Ruth, 68, large system font + TalkBack sometimes:**
  - Her 1.5× font setting is ignored (issue 1).
  - With TalkBack on she can't open the drawer or settings at all (issue 2).
  - "Delete" scares her off clearing a slot (issue 5).
  - Blocked in the first minute.
- **Alex, power user:**
  - Loves the omnibox and gesture letters.
  - Is annoyed that the drawer long-press strip ellipsizes its labels, that pickers don't show the current value, and that there's no undo after clearing a folder.
  - Wants the home long-press menu to offer "Focus 25 min" without digging into settings card 5.
- **Mia, came for "Calm phone":**
  - The Play listing promised focus, mindful pause, quiet notifications and grayscale.
  - In settings she scrolls past backup, home, appearance and gestures before finding them under a Google-branded name.
  - None of them are one gesture away from home.

## Minor observations

- The settings rows' bold values (`TextSmallBold`) and the light labels have almost the same visual weight on the Tab S8 in bold-on mode (`bold-on-settings.png`), so the label/value hierarchy flattens. Use colour (`primaryColorTrans80` labels) rather than weight.
- A widget that fails to bind shows the system "Can't show content" placeholder (`06-…png`). The widget long-press could offer "Reconfigure / Remove" with a summary line ("This widget stopped responding").
- The onboarding page title is 28sp bold, a weight that appears nowhere else in the app. Feed this to WP12.
- The settings cards' `rounded_rect_shade_color` at `primaryShadeDarkColor` over a busy wallpaper gives weak card separation in the light theme. Check in the next capture pass.

## Questions to consider

- What if the home long-press sheet *were* the Calm-phone control centre, with settings one row below it?
- Does the drawer need an empty state at all, or should an empty query show the user's 5 most-opened apps (WP16 history, opt-in) plus one example query?
- Could "Text size" go away entirely once Parem respects the system font scale, leaving just one "Compact / Comfortable / Large" home density control?

## Recommended actions (order)

1. **[P0] `/impeccable harden`:** font-scale multiply (issue 1) and TalkBack actions and row semantics (issue 2).
2. **[P1] `/impeccable adapt`:** insets on every root (issue 3).
3. **[P1] `/impeccable typeset`:** size-qualified dimens and text appearances (issue 4).
4. **[P1] `/impeccable clarify`:** "Remove from home" / "Uninstall" plus Snackbar undo (issue 5). Also the settings copy, summaries and sentence case (issue 7) and durations (issue 11).
5. **[P1] `/impeccable onboard`:** in-place affordances (issue 6), coordinated with WP12.
6. **[P2] `/impeccable distill`:** one menu system and the home long-press hub (issues 7 and 8).
7. **[P2] `/impeccable polish`:** BottomSheetMenu summary and checked rows (issue 9), wallpaper scrim (issue 10).

Re-capture on the Tab S8 **and** one phone in both themes and at `font_scale 1.3` before calling this done. None of the findings above were verified on a phone.
