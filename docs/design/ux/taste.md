# Parem 6.0: taste critique

Author: taste critic pass, using the `design-taste-frontend` skill, translated to native Android.
Source read: `origin/next` (layouts, styles, drawables, strings, `ui/` Kotlin) plus the 2026-10-04 tablet QA shots in the session scratchpad (`qa/`).
Limits: no new screenshots. The capture run was blocked because the Tab S8 was locked. Drawer, sheets, onboarding and light theme are judged from code only, not from pixels. Everything already filed as M4-WP22..30 is left out, and overlaps are named where a recommendation touches one of those rows.

## 1. Design read

**Reading this as:** a daily-use native utility (a home screen) for everyone from minimalists to people who are easily distracted, older, or have low vision. The language is calm, text-first and monochrome. It leans on the platform (AppCompat + Material BottomSheetDialog, system wallpaper) with a small set of Parem tokens of its own. It is not a web landing page.

The skill was written for marketing pages. Most of its rules do not apply here: hero rules, image mandates, logo walls, GSAP. What does carry over is its discipline: no defaults by accident, one type system, one shape system, one accent (here: none), a copy self-audit, motion only when it has a reason, and full states (empty, error).

Dials, adjusted for a launcher instead of taking the skill's baseline:

| Dial | Value | Why |
|---|---|---|
| DESIGN_VARIANCE | 3 | A home screen gets opened hundreds of times a day. Predictability matters more than surprise. Any asymmetry belongs to the wallpaper, not to the layout. |
| MOTION_INTENSITY | 2 | "Calm phone". Motion is only for feedback and state change. No looping animation anywhere. It already respects animator scale 0 and e-ink (`skipAnimations()`). |
| VISUAL_DENSITY | 2 on home, 4 in settings and sheets | Home is a gallery. Settings is a working surface. |

Where I push back on the skill:
- **Pure black is fine here.** The skill bans `#000000`. Parem's dark theme draws text on the wallpaper, and its solid surfaces (sheets) are OLED-black on purpose. Keep it.
- **"Real images" does not apply.** The wallpaper is the image. A text-only home screen is the product, not an unfinished page.
- **The serif and "avoid Inter" rules are taste noise for a launcher.** The real type question is legibility and identity (issue 3).

## 2. What Parem's identity should be

One sentence: **"A quiet sheet of type on your own wallpaper."**

Concretely:
1. **Type is the interface.** Every interactive thing is a word. Icons are optional, monochrome when present, and never the only label.
2. **One voice, one face, five sizes.** Display (clock), Title, Body, Label, Caption. Light weight for display and titles, regular weight for body. Bold is for the current value and nothing else.
3. **No colour from Parem.** The only colour on screen comes from the wallpaper or from content the user chose (widgets, which can also be calmed, see issue 7).
4. **One start keyline.** Clock, date, apps and screen time all hang from the same left edge (or right edge, or centre, following the alignment setting), and that edge scales with screen width.
5. **One container shape.** One radius for surfaces, one for small controls.
6. **Plain, sentence-case copy.** It says what happens ("Hide notifications from other apps?"). No slogans.
7. **Still by default.** Nothing moves unless the user did something.

Parem already meets about half of this. The screen-time graph is strictly monochrome. The sheets go through one builder (`BottomSheetMenu`). The 6.0 sheet copy (`quiet_*`, `grayscale_*`, `lock_service_off_*`) is plain and honest. The search hint `___` is a distinctive, terminal-like touch worth keeping. What it lacks is a system: today Parem looks like Olauncher with more settings.

## 3. Audit: where it looks generic, inconsistent or unfinished

Evidence I counted on `origin/next`:
- **Typefaces in use:** `sans-serif-light` (home, drawer, card headings via `?attr/mainFontFamily`), the system default regular (`TextSmall`, every programmatic sheet), `Typeface.BOLD` in 9 files, `Typeface.MONOSPACE` (grayscale command), and a bold 28sp title in onboarding. No sheet uses `mainFontFamily`; `git grep mainFontFamily` over `ui/` returns nothing.
- **Text sizes:** 10, 12, 13, 14, 16, 18, 20, 28, 30, 66 sp. 13 Kotlin files set `textSize = Nf` directly. `TextMedium` hard-codes 20sp instead of using a dimen.
- **Corner radii:** 24 (`rounded_rectangle_dark`, which nothing uses), 20 (settings cards), 16 (sheet top), 10 (value-picker gradient), 8 (focus ring), 2 (widget handle).
- **Scrim behind the drawer and settings:** `?attr/primaryShadeDarkColor` = `#40` (25%) over whatever the wallpaper is, including a random daily wallpaper.
- **Duplicated theme:** `values/styles.xml` and `values-night/styles.xml` are full copies that differ only in colours. Any new token has to be added twice.
- **Settings:** `layout/fragment_settings.xml` (1478 lines) and `layout-land/fragment_settings.xml` (1031 lines) are maintained by hand and differ by 815 lines.
- **Copy:** Title Case in onboarding ("One Search Bar", "You're All Set", "Get Started"), sentence case everywhere else. Slogans ("Powerful tools, minimal interface", "Your home screen, your rules"). `<u>` HTML underline on "Set as default launcher". "Tip: Start typing for app RENAME option".

## 4. Top issues (highest impact first)

### 1. The drawer and settings sit on a 25% scrim. Legibility depends on the wallpaper
- **Problem:** `fragment_app_drawer.xml` and `fragment_settings.xml` use `?attr/primaryShadeDarkColor` (`#40000000` dark, `#40FFFFFF` light). The text shadow in `TextDefault` is what makes text readable. On a bright or busy wallpaper (the blue tree shot, or any "Daily new wallpaper" result) body text at 18sp falls well under WCAG AA. This hits low-vision and older users first, and it is the single biggest "every user" risk in the visual layer.
- **Recommendation:** add a `surfaceScrimColor` attr (`attrs.xml`). Set it to about 85% inverse (`#D9000000` dark, `#D9FFFFFF` light) in the theme and use it as the background of the drawer and settings roots. Home keeps the raw wallpaper plus shadow. Keep the shadow for home text only by moving `shadow*` from `TextDefault` into a `TextOnWallpaper` style. On a solid scrim the shadow just blurs the glyphs.
- **Files:** `res/values/attrs.xml`, `res/values/colors.xml`, `res/values{,-night}/styles.xml`, `layout/fragment_app_drawer.xml`, `layout{,-land}/fragment_settings.xml`, `layout-land/fragment_app_drawer.xml`.

### 2. There is no type system: 4 faces and 10 sizes, mostly hard-coded in Kotlin
- **Problem:** home uses Light, settings rows Regular, sheet titles Bold, onboarding a bold 28sp display. Sheets are built in code with `textSize = 14f/16f/18f` and `setTypeface(null, BOLD)` spread across `BottomSheetMenu`, `FocusModeDialog`, `ScreenTimeLimitDialog`, `CreateFolderDialog`, `WebsiteDialog`, `GrayscaleSheet` and `ScreenTimeGraphDialog`. Each sheet reads like a different app, and future sheets will drift further.
- **Recommendation:** define five text appearances once, in one base theme:
  `TextAppearance.Parem.Display` (clock, `@dimen/time_size`, mainFontFamily), `.Title` (`@dimen/text_large`, mainFontFamily), `.Body` (`@dimen/text_small`), `.Label` (16sp), `.Caption` (13sp, 60% alpha via `?attr/primaryColorTrans80`).
  Move the shared theme items into `Base.AppTheme` in `values/styles.xml`, and have `values-night` override only the colour attrs. In code, replace `textSize = Nf` and `setTypeface` with `TextViewCompat.setTextAppearance(view, R.style.TextAppearance_Parem_X)`. Start in `BottomSheetMenu` (`title`/`message`/`option`), which fixes most sheets at once. Bold is for the selected value only.
- **Files:** `res/values{,-night}/styles.xml`, `res/values*/dimens.xml`, `ui/BottomSheetMenu.kt`, then the sheet files listed above, plus `item_onboarding_page.xml`.

### 3. Decide on a brand face, because right now Parem looks identical to Olauncher (design first)
- **Problem:** Roboto Light on the wallpaper, the same slot list, the same clock: a Play user who has seen Olauncher, or the dozen forks like it, can't tell Parem apart. For a text-first product, the typeface *is* the brand. It is the only big identity lever left that adds no clutter.
- **Recommendation:** bundle one OFL variable font in `res/font/` and point `mainFontFamily` (and the new text appearances) at it. My pick is **Atkinson Hyperlegible Next**. It was designed for low-vision readers (distinct I/l/1 and O/0), has light-to-bold weights, and backs up the "for every user" claim. Alternative: IBM Plex Sans (more technical voice). Keep a "System font" choice in Appearance for people who want Roboto or the OEM font. Costs: about 100 to 300 KB of APK against the 10 MB budget, F-Droid-safe (OFL), no network (downloadable fonts need GMS, so no). Patric decides on the face; keep it to one family.
- **Files:** `res/font/` (new), `res/values{,-night}/styles.xml` (`mainFontFamily`, `BoldFontOverlay`), `ui/settings/AppearanceSettingsCard.kt`, `data/Prefs.kt` (append key), strings.

### 4. Sheet titles are the weakest text on the sheet, and the controls are stock widgets
- **Problem:** `BottomSheetMenu.title()` is 14sp, bold, 50% alpha. That is the web "eyebrow" pattern, and it is the weakest element on every sheet. Options under it are 16sp at full colour, so nothing anchors the sheet. Focus, App limits, Grayscale and Folder sheets add stock `CheckBox`/`RadioButton` widgets and a bold "Save" / "Enable" pair, which looks like generic AppCompat inside a text-only app.
- **Recommendation:** make `title()` use `TextAppearance.Parem.Title` at full colour. Add `sectionLabel()` (Caption) for in-sheet groups. Add `primaryAction(text)`: a full-width Label row at the bottom, separated by 16dp of space and not by a line. For multi-select lists, replace `CheckBox` with a text row whose selected state is the `ic_check` glyph aligned to the end, tinted `?attr/primaryColor`. That matches the settings value column. Single choice (focus duration) gets the same treatment. One sheet grammar, everywhere.
- **Files:** `ui/BottomSheetMenu.kt`, `ui/AppPickerAdapter.kt`, `ui/FocusModeDialog.kt`, `ui/GrayscaleSheet.kt`, `ui/CreateFolderDialog.kt`, `ui/ScreenTimeLimitDialog.kt`, `ui/WebsiteDialog.kt`. Overlap: WP24 fixes the sheet width and WP30 the picker themes. This item is the visual grammar on top of those fixes.

### 5. App options come in two paradigms: an icon strip in the drawer, sheets everywhere else
- **Problem:** long-pressing an app in the drawer swaps the row for six Material icons with 12sp labels (`appHideLayout` in `adapter_app_drawer.xml`). It is the only icon-led UI in Parem, the labels sit below a readable size, and six equal-weight targets in one row are hard to hit for anyone with motor issues. Long-pressing on home, and all the 6.0 features, use bottom sheets instead.
- **Recommendation:** drawer long-press opens a `BottomSheetMenu` with the app's name as the Title and these rows: Rename, Time limit, Hide, App info, Uninstall. Rename uses an `EditText` via `customView`. Then delete `appHideLayout`, `renameLayout`, and any `ic_*` drawables they orphan. Fewer views per row also helps drawer scroll.
- **Files:** `layout/adapter_app_drawer.xml`, `ui/AppDrawerAdapter.kt`, `ui/AppDrawerFragment.kt`, `res/drawable/ic_*.xml`. Overlap: WP30's "Close overlaps usage text" goes away with this change.

### 6. Home icons break the type column (wrong size, full colour, missing for folders and websites)
- **Problem:** with "Show icons" on, `HomeSlotsController` adds a full-colour 20dp icon next to a 30sp label, and only when `packageName` is non-empty. Folders ("Tools") and website slots ("DDG") get none, so the left edge goes ragged (landscape shot `08`). Full-colour icons are also the loudest colour Parem itself puts on screen.
- **Recommendation:** when icons are on, use `AdaptiveIconDrawable.monochrome` (API 33+) tinted `?attr/primaryColor`. Fall back to the normal icon desaturated with `ColorMatrix().setSaturation(0f)`. Size the icon from the label's line height (`textView.lineHeight * 0.8`), not a fixed 20dp. Give folders and websites a transparent placeholder with the same bounds (or a single mono glyph) so every label starts on the same keyline. Icon packs keep their own colours, since the user chose them.
- **Files:** `ui/home/HomeSlotsController.kt`, `helper/AppIconCache.kt`, `ui/AppDrawerAdapter.kt` if drawer icons follow.

### 7. Widgets bring loud colour and a system error box onto a calm screen
- **Problem:** a single calendar widget takes over the home screen with saturated chips (shots `06`, `08`). A widget that fails to render shows the system's grey "Can't show content" box (shot `06`), a broken state that Parem never styles.
- **Recommendation:** (a) add an opt-in "Calm widgets" toggle that renders each `AppWidgetHostView` with `setLayerType(LAYER_TYPE_HARDWARE, Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) })`. That is on-brand for 6.0's grayscale story and costs no permission. (b) Subclass `AppWidgetHost`/`AppWidgetHostView` and override `getErrorView()` to show a Body text in Parem's style: "Calendar can't load. Long press to remove." Trap #2: this changes rendering only, no widget-ID bookkeeping.
- **Files:** `ui/HomeWidgetController.kt`, `data/Prefs.kt` (append key), `ui/settings/HomeScreenSettingsCard.kt`, `layout{,-land}/fragment_settings.xml`, strings.

### 8. Home has no shared keyline or vertical rhythm, and phone margins carry over to tablets
- **Problem:** the clock and apps both use a 20dp start margin, which is generous on a phone and cramped on a 1600px tablet. Apps are `gravity="center_vertical"` with `paddingTop="112dp"`, leaving a large dead band between date and apps on tall screens (shot `home-bold-off`). In landscape the clock is centred while apps sit at the start. Nothing ties the parts together.
- **Recommendation:** add `@dimen/home_keyline` (20dp) with `values-sw600dp/dimens.xml` (48dp) and `values-sw840dp` (64dp), and scale `time_size` and `text_large` there too. Clock, date, screen time and apps all use the keyline. The clock follows the App alignment setting, so there is one edge. Consider an "Apps position: middle / bottom" option with bottom as the default for new installs, since that is the thumb zone on phones. This is the design rule; WP28 fixes the current landscape bugs and should adopt it.
- **Files:** `layout{,-land}/fragment_home.xml`, `res/values-sw600dp/dimens.xml` (new), `ui/home/HomeClockController.kt`, `ui/home/HomeSlotsController.kt`. Coordinate with WP28 and WP13.

### 9. Settings opens with housekeeping, and the value column mixes kinds of values
- **Problem:** the first card is "Parem Launcher": default launcher, export, import and crash reports. The first thing a new user sees is maintenance. Every card has a 0.8dp stroke and a 30sp Light heading, so five outlined boxes compete. The value column mixes states (On/Off), numbers, nouns and a verb ("Configure"). Some labels read as code names: "Show date time", "Notification bar" (it hides the status bar), "Hide from shade, keep for later".
- **Recommendation:** reorder the cards to Home screen, Appearance, Gestures, Digital wellbeing, then "Parem" (about, backup, crash reports) last. Drop the card strokes in `rounded_rect_shade_color` and group by heading plus 24dp spacing (skill 4.4: cards only when elevation means something). Values describe state only: "Configure" becomes a count ("3 apps") or "None". Labels: "Clock and date", "Status bar", "Quiet notifications". Append new strings; WP9 translates them.
- **Files:** `layout{,-land}/fragment_settings.xml`, `res/drawable/rounded_rect_shade_color.xml`, `ui/settings/*Card.kt` (value text), `res/values/strings.xml`. Overlap: WP30 owns the tap targets, and WP17 search indexes the labels, so update `SettingsSearchIndex` with the renames.

### 10. Onboarding looks and sounds like a SaaS template (a visual spec for M4-WP12)
- **Problem:** an opaque black page (the only screen with the wallpaper hidden), a bold 28sp system-font title (the only bold display text in Parem), Title Case, slogans ("Powerful tools, minimal interface", "Your home screen, your rules"), four bullet lists that inventory features, and dot pagination. It is the first screen a Play user sees and the least Parem-like.
- **Recommendation:** WP12 owns the flow. This is the look it should use. Show the wallpaper behind `surfaceScrimColor`. Title appearance, sentence case. One action per page, and the user performs it ("Swipe up to search": the page advances when they do). No feature lists; settings and the omnibox teach the rest. Replace dots with a Caption like "2 of 4", or nothing. Copy: "Set Parem as your home screen", "Swipe up and type", "Pick your apps", "Done".
- **Files:** `layout/fragment_onboarding.xml`, `layout/item_onboarding_page.xml`, `ui/OnboardingPagerAdapter.kt`, `ui/OnboardingFragment.kt`, onboarding strings. Hand this to WP12; don't make it a separate package.

### 11. Small calm violations: endless marquee, web underline, caps, a stagger on every drawer open
- **Problem:** the drawer tip uses `marqueeRepeatLimit="marquee_forever"`, which never stops moving. "Set as default launcher" is styled with an HTML `<u>`, a web-link signal. The first-run hint is a numbered list ("1. Swipe up... 2. Long press..."). "RENAME" uses caps (`textAllCaps`, plus the `tip_start_typing_for_rename` string). `layout_anim_from_bottom` staggers the rows in on every drawer open, which delays the most repeated action in the app.
- **Recommendation:** make the tip static (`ellipsize="end"`, no marquee) and hide it after the first successful search. Render the default-launcher prompt as a Label row at 80% alpha without the underline. Make the first-run hint one Caption line ("Swipe up for apps. Long press for settings."). Remove `textAllCaps` and the caps string. Run the drawer layout animation only on the first open of a session, or drop it.
- **Files:** `layout{,-land}/fragment_app_drawer.xml`, `layout{,-land}/fragment_home.xml`, `res/values/strings.xml`, `ui/AppDrawerFragment.kt`. Overlap: WP28 fixes the tips' position. This item is about their style.

### 12. No shape system: six radii and a gradient pill
- **Problem:** radii are 24, 20, 16, 10, 8 and 2dp. The settings value pickers use `rounded_primary_gradient`, a horizontal fade that reads as decoration (shot `07`). `rounded_rectangle_dark` is not used anywhere.
- **Recommendation:** add `@dimen/corner_surface` (16dp: sheets, settings groups, widget clip) and `@dimen/corner_control` (8dp: focus ring, pickers). Replace the gradient with a solid `?attr/primaryInverseColor` surface. Delete `rounded_rectangle_dark.xml`.
- **Files:** `res/drawable/{bg_bottom_sheet,rounded_rect_shade_color,rounded_primary_gradient,bg_focus_highlight,rounded_rectangle_dark}.xml`, `res/values/dimens.xml`, `ui/HomeWidgetController.kt` (widget handle).

## 5. Pre-flight checklist for any 6.0 screen (native translation of skill section 14)

Use this in PR review for every new or changed surface:
- [ ] Text uses a `TextAppearance.Parem.*` style. No `textSize =` or `setTypeface` in new Kotlin.
- [ ] Only the selected value is bold.
- [ ] Parem adds no colour. Icons are monochrome or come from the user's icon pack.
- [ ] Corners are `corner_surface` or `corner_control` only.
- [ ] Text sits on a solid surface or `surfaceScrimColor`. Shadowed text only directly on the wallpaper.
- [ ] Body text passes 4.5:1 on a bright and a dark wallpaper, in light and dark theme.
- [ ] Sheets go through `BottomSheetMenu`: Title, optional message, rows, one primary action at the bottom.
- [ ] Copy is sentence case, says what happens, has no slogans, and keeps one label per intent.
- [ ] Empty, loading and error states exist and are written in Parem's voice (widget errors, an empty quiet list, no usage access).
- [ ] No looping animation. Every transition is feedback or a state change and respects `skipAnimations()`.
- [ ] Tap targets are at least 48dp. Labels are at least 13sp at the default text size.
- [ ] Checked at sw600dp in both orientations (until WP13 lands).

## 6. Asides (not issues, flagged for later)

- **Duplicated layouts and themes are where drift comes from.** The portrait and landscape settings layouts differ by 815 lines, and the theme exists in two full copies. Issues 2 and 12 get much cheaper if the theme is merged first, and WP13 (adaptive layouts) is the natural place to merge the settings layouts.
- **About 80 `Toast` calls.** On Android 12+ toasts carry the app icon and are capped at two lines. They are acceptable, but inline confirmation (for example, the value changing in place) fits the text-first voice better for settings changes. Not worth a package before 6.0.
- **Em-dashes in strings** (`onboarding_omnibox_*`, `apps_dont_fit`, `screen_time_week_title`). They are grammatically fine in native copy, but WP9 should normalise them for translators.
