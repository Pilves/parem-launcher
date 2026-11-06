# Parem design system (6.0 "Calm phone")

Status: proposal by the lead product designer, 2026-10-03, revised the same day
after the platform/roadmap review (section 15), waiting for Patric's sign-off. It is based on the five specialist reports in `docs/design/ux/`
(impeccable, taste, motion, inclusive, ia), checked against `origin/next` @ 3915b38.
None of it was verified on a device: the screenshot capture was blocked because
the Tab S8 was locked (see the scratchpad `ux-shots/INDEX.md`).

This file is the reference every 6.0 UI change is reviewed against. Everything is
in native Android terms: XML layouts, `res/values*` dimens/styles/attrs/integers,
`ui/BottomSheetMenu`, and the Material 1.12 / AppCompat 1.7 / androidx.core
components the app already uses. No Compose, no new UI framework, no new
dependency.

Where a token doesn't exist yet, it is marked **new**. The work package that
introduces it is named in section 13.

---

## 1. Principles

1. **A quiet sheet of type on your own wallpaper.** Type is the interface. Every
   interactive thing is a word. Icons are optional, monochrome, and never the only
   label. Parem adds no colour of its own.
2. **Calm is the default path.** Whatever reduces use (go back, wait, stay focused)
   is the first, most prominent choice. Whatever increases use (open anyway, end
   focus) is available, quieter, and second. Parem pauses; it never blocks and
   never guilts.
3. **Every user, every route.** Every feature can be reached by touch gestures,
   by a tap, by TalkBack/Switch Access, and by keyboard/D-pad. A gesture is a
   shortcut, never the only door.
4. **The user's settings win.** System font size, Remove animations, touch &
   hold delay, the touch-feedback setting, dark theme and layout direction are
   inputs. Parem multiplies or follows them and never replaces them.
5. **Teach at the place, at the moment, once.** No tours (DECISIONS). A hint sits
   where the action happens and disappears when the user has *done the thing*.
6. **Still by default.** Nothing moves unless the user did something. Motion is
   feedback or a state change, under 300 ms, and absent on e-ink or when
   animations are off.
7. **Recoverable.** Nothing destructive happens without an Undo or a clear,
   specific label. Hidden things can always be found again.

## 2. Voice and copy

| Rule | Do | Don't |
|---|---|---|
| Sentence case everywhere, including onboarding and section titles | "Show date and time" | "Show Date Time", "You're All Set" |
| Actions are verbs that say what happens | "Remove from home", "Uninstall", "Go back" | "Delete" (ambiguous), "OK" |
| Values are state, never verbs | "On", "Off", "3 apps", "Left", "Set up", "Needs access" | "Configure", "Bottom: Off" |
| Plain words over feature names | "Quiet notifications", "Status bar", "Clock and date" | "Hide from shade, keep for later", "Notification bar" |
| Say the home screen, not the launcher | "Make Parem your home screen" | "Set as default launcher" (underlined) |
| Our own name for our own features | Section "Calm" | "Digital Wellbeing" (Google's app name) |
| No slogans, no exclamation marks, no "please", no guilt | "Notifications from other apps wait in a quiet list" | "Your home screen, your rules!", "Can we request you to keep this On…" |
| Errors say what to do next | "Calendar can't load. Long-press to remove it." | "Can't show content" |
| Durations come from `android.icu.text.MeasureFormat` (NARROW) plus `<plurals>` | "12 min today" | `"${m}m"` built in Kotlin |
| Settings paths are built from section-title strings with " › " | `getString(R.string.calm) + " › " + …` | hard-coded "Settings > Digital Wellbeing" |
| Toast text fits 2 lines at 200% font (about 60 characters) | "Focus is on until 17:00" | a sentence plus a settings path |
| App names inside format strings are wrapped with `BidiFormatter.unicodeWrap` | — | raw `%1$s` in RTL locales |
| Use "…" (U+2026) for "opens more input" | "Add website…" | "Add website..." |

Every new user-facing string is a resource, appended at the end of
`values/strings.xml` (hot file rule). Kotlin builds no user-visible text from
literals.

## 3. Type

### 3.1 Font scale

- Effective scale = **system `fontScale` × Parem's text-size step**. Parem never
  replaces the system value (`MainActivity.attachBaseContext` today does).
- Parem steps (label shown as the settings value): Smaller 0.85 · Default 1.0 ·
  Large 1.15 · Larger 1.3.
- Clamp: `effective = (system × step).coerceIn(0.85, max(system, 2.0))`. The
  system value is never reduced (a system 2.0 stays 2.0, and nonlinear scaling
  above it is Android's business). Parem's *extra* multiplier may not push past
  2.0, so system 2.0 × Larger is 2.0, not 2.6. "The user's setting wins" is about
  the system value; Parem's step is a convenience on top of it.
- Home slot fitting (`HomeSlotsController`, dynamic fitting pass) measures the
  rendered row height, so capacity already follows the effective scale. When a
  larger scale lowers capacity, the apps that drop off must be announced the
  way M4-WP26 announces it for widgets. No silent hiding.
- Nonlinear scaling (API 34+) works automatically on `sp` once the system scale
  is kept.
- Every text size is in `sp`. A layout must survive 200% system font. Rows grow
  taller, and nothing clips or overlaps (use `minHeight`, not a fixed height).

### 3.2 Scale

Sizes depend on **screen width, not density**. The `values-{l,m,h,xh,xxh,xxxh}dpi/dimens.xml`
folders go away: `sp` is already density-independent, and keying on density gives
the Tab S8 *smaller* text than a phone.

| Role | `TextAppearance` (**new**, `values/styles.xml`) | Size dimen | `values/` | `values-sw600dp/` | Weight | Colour |
|---|---|---|---|---|---|---|
| Display: clock | `TextAppearance.Parem.Display` | `time_size` | 64sp | 88sp | `?attr/mainFontFamily` (light) | `primaryColor` |
| Subtitle: date, battery, weather | `TextAppearance.Parem.Subtitle` | `date_size` | 18sp | 22sp | regular | `primaryColor` |
| Title: home and drawer apps, settings card headings, sheet titles | `TextAppearance.Parem.Title` | `text_large` | 28sp | 32sp | `?attr/mainFontFamily` | `primaryColor` |
| Body: settings labels and values, sheet rows, messages | `TextAppearance.Parem.Body` | `text_small` | 18sp | 20sp | regular | `primaryColor` |
| Label: buttons in sheets, inline actions | `TextAppearance.Parem.Label` | `text_label` (**new**) | 16sp | 16sp | regular | `primaryColor` |
| Caption: summaries, usage time, hints | `TextAppearance.Parem.Caption` | `text_caption` (**new**) | 14sp | 14sp | regular | `primaryColorTrans80` |

Rules:
- **No size below 14sp** at the default step. 12sp and 13sp disappear.
- **Bold means "current value"** (settings values, the checked row) and nothing
  else. The Bold text setting swaps `mainFontFamily` (light → regular) through
  `BoldFontOverlay`, as it does today.
- Light weight only at ≥ 28sp. Body and Caption are regular.
- Line height: Body `android:lineHeight="24sp"`, Caption `20sp` (API 28+). Title
  and Display keep their font metrics.
- In Kotlin, use `TextViewCompat.setTextAppearance(view, R.style.TextAppearance_Parem_X)`.
  **No new `textSize =` or `setTypeface(…)` calls.** `BottomSheetMenu` applies
  appearances, so sheets inherit them for free.
- `TextMedium` (hard-coded 20sp) is retired in favour of Label/Body.
- The brand typeface stays the system sans for 6.0. A bundled OFL face (taste
  #3: Atkinson Hyperlegible Next) is an open owner decision, listed in section 14.

## 4. Space, size and shape

4dp grid. Name tokens by role, not by number, so they can change per width.

| Token (`values/dimens.xml`, **new** unless noted) | `values/` | `values-sw600dp/` | `values-sw600dp-w840dp/` | Used for |
|---|---|---|---|---|
| `space_xs` / `space_s` / `space_m` / `space_l` / `space_xl` / `space_xxl` | 4 / 8 / 12 / 16 / 24 / 32dp | same | same | all padding and margins not covered below |
| `home_keyline` | 20dp | 48dp | 64dp | the start (or end, or centre) edge that clock, date, screen time and apps hang from |
| `home_app_padding_vertical` (exists) | 12dp | 14dp | 14dp | home slot rows |
| `app_padding_vertical` (exists) | 12dp | 12dp | 12dp | drawer rows |
| `row_padding_vertical` | 14dp | 14dp | 14dp | settings rows and sheet rows |
| `sheet_padding_horizontal` | 24dp | 24dp | 32dp | sheet content inset |
| `section_gap` | 24dp | 32dp | 32dp | between settings sections and sheet groups |
| `touch_target_min` | 48dp | 48dp | 48dp | `minHeight`/`minWidth` of anything tappable |
| `content_side_padding` (M4-WP13) | per WP13 | per WP13 | per WP13 | reading-width cap for settings, drawer, onboarding |
| `sheet_max_width` | 640dp | 640dp | 480dp | `BottomSheetDialog.behavior.maxWidth`. It is narrower on landscape tablets on purpose: thumb reach and line length |
| `corner_surface` | 16dp | 16dp | 16dp | sheets, settings groups, widget clip |
| `corner_control` | 8dp | 8dp | 8dp | focus ring, pickers, pressed highlight |

Qualifier rule: Android ranks smallest width (`sw`) above available width (`w`)
when picking a resource folder, so a dimen defined in both `values-sw600dp/` and
`values-w840dp/` always resolves from `values-sw600dp/` on a tablet, and a large
phone in landscape (sw < 600, w ≈ 900) would pick up the `w840dp` value meant for
tablets. Landscape-tablet overrides therefore live in **`values-sw600dp-w840dp/`**.
There is no plain `values-w840dp/` folder. A large phone in landscape gets the
phone values (`sheet_max_width` 640dp: a centred sheet, `home_keyline` 20dp).

Rules:
- **Touch targets are at least 48 × 48dp**, the whole row (label, value and
  summary), never just the value text. Adjacent targets are at least 8dp apart, or
  the rows are full-width with no gap.
- **Insets are added to design spacing, never guessed** (implemented by M2-WP1, see section 14). Each fragment root pads
  by `WindowInsetsCompat.Type.systemBars() or displayCutout()` (drawer: `or ime()`)
  through `ViewCompat.setOnApplyWindowInsetsListener`, *plus* the keyline. The
  fixed 56/88/112/180dp "status bar guesses" go away.
- **Two radii only.** `rounded_rectangle_dark.xml` and the
  `rounded_primary_gradient` pill are retired. A selected picker value uses a
  solid `?attr/primaryShadeDarkColor` at `corner_control`.

## 5. Colour roles (mono)

Parem has **no accent**. Every role maps to an existing theme attr, or a new one,
defined once in a merged `Base.AppTheme` with parent
`Theme.AppCompat.DayNight.NoActionBar` (so the parent no longer differs between
`values/` and `values-night/`). `values-night/styles.xml` overrides only colour
attrs. The two booleans that must flip in dark mode,
`android:windowLightStatusBar` and `android:windowLightNavigationBar`, read
`@bool/light_system_bars`, defined in `values/bools.xml` (true) and
`values-night/bools.xml` (false), both **new**.

| Role | Attr | Light | Dark | E-ink | Use |
|---|---|---|---|---|---|
| Text, primary | `primaryColor` | #000 | #FFF | #000 | everything readable |
| Text, secondary | `primaryColorTrans80` | 80% | 80% | #000 | captions, summaries, labels next to bold values |
| Text, quiet | `primaryColorTrans50` | 50% | 50% | #000 | hints and disabled rows only, never information the user needs |
| Surface (sheets, dialogs) | `primaryInverseColor` via `bg_bottom_sheet` | #FFF | #000 | #FFF | solid surfaces |
| Scrim over wallpaper (drawer, settings, onboarding) | `surfaceScrimColor` (**new**) | #D9FFFFFF | #D9000000 | #FFFFFFFF | readable text on any wallpaper |
| Home wallpaper dim (opt-in, default on with daily wallpaper) | `wallpaperDimColor` (**new**) | 0→35% inverse gradient | same | none | behind home text only |
| Text shadow | `primaryTextShadowColor` | 50% inverse | 50% inverse | none | **only** text drawn directly on the wallpaper (`TextOnWallpaper` style, **new**). Text on a scrim or surface has no shadow |
| Pressed | `primaryShadeDarkColor` (background) and `primaryColorTrans50` (text, in `text_colors_default`) | — | — | pressed bg only | touch-down feedback |
| Divider / handle | `primaryShadeColor` / `primaryColorTrans50` | — | — | #000 | sheet handle, rare dividers |
| Attention ("Needs access") | `primaryColor` + Bold | — | — | — | the only emphasis Parem has |

Contrast rules:
- Body and Caption must reach 4.5:1 against their actual background. On a
  surface or scrim that is guaranteed by the table. Directly on the wallpaper it
  isn't, so home text uses the shadow plus the optional dim, and alpha floors are
  80% (`tvScreenTime`, `appUsageTime` move from 0.5/0.6 to Trans80).
- 50% alpha (≈ 3.9:1 on white) only for ≥ 28sp, or for text that is not needed.
- E-ink (`isEinkDisplay()`) uses the light palette with no translucency below
  100% for text, no shadow, no scrim gradient and no ripple (section 8).
- Widgets and icon packs keep their own colours, since the user chose them.
  Home slot icons, when on, are monochrome (`AdaptiveIconDrawable.monochrome`,
  API 33+, tinted `primaryColor`, otherwise desaturated).

## 6. Components

### 6.1 Home rows (`fragment_home.xml` + `layout-land`)
- `TextAppearance.Parem.Title` + `TextOnWallpaper`. Padding `home_app_padding_vertical`, height at least `touch_target_min`.
- One edge: clock, date, screen time and apps all follow the App alignment setting from `home_keyline`.
- **Empty slot**: only the first empty slot shows a hint, "Long-press to add app" in `primaryColorTrans80`. The others stay `GONE` until it is filled. The literal "App" hint goes away.
- Pressed: text goes to `primaryColorTrans50` on touch-down and is cleared when a swipe passes touch slop.
- Long-press opens the shared app menu (6.4) with the haptic `LONG_PRESS`.
- Each slot exposes TalkBack custom actions (open drawer, settings), so a screen-reader user who is focused on a slot is never stuck.

### 6.2 Drawer rows (`adapter_app_drawer.xml`)
- Title appearance, `app_padding_vertical`, height at least 48dp. Usage time is a Caption at Trans80, not 12sp/50%.
- Long-press opens the **same** app menu as home (6.4). The inline icon strip (`appHideLayout`) is removed.
- The work-profile dot has `contentDescription="Work profile"`.
- Search field: visible cursor, hint owned by M4-WP15's rotating hint. Text gravity follows `appLabelAlignment`.
- Background: `surfaceScrimColor`, not the 25% shade.

### 6.3 Settings rows (`fragment_settings.xml` + `layout-land`)
- Row = label (Body, `primaryColor`) + value (Body, **bold**, end-aligned) + optional summary (Caption, Trans80, under the label). The **whole row** is the click target (M4-WP30), at least 48dp tall, with `selectableItemBackground` (a plain pressed drawable on e-ink).
- Screen readers: the row is `screenReaderFocusable`. The label child is `importantForAccessibility="no"`. State goes through `ViewCompat.setStateDescription`, and the role (Switch / Spinner-like) through an `AccessibilityDelegateCompat`.
- Value grammar: `On`/`Off` · the current choice · a count ("3 apps") · `Set up` · `Needs access` (bold).
- Interaction grammar: a binary row toggles on tap. A row with three or more choices opens a `BottomSheetMenu` with the current value checked. **No inline expanding strips.**
- Section heading: Title appearance with `android:accessibilityHeading="true"`, and `section_gap` above it. No card strokes; spacing groups the rows.
- Section order (IA): Calm → Home screen → Search → Gestures → Appearance → Backup and about.

### 6.4 Sheets: `BottomSheetMenu` is the only sheet builder
Anatomy, top to bottom:
1. Drag handle 40 × 4dp, `importantForAccessibility="no"`.
2. `title(text)`: Title appearance at full colour, set as an accessibility heading. On app menus the title is the app name.
3. `message(text)`: Body. Optional.
4. `sectionLabel(text)` (**new**): Caption, for groups inside a sheet.
5. `option(text, summary = null, checked = false, dimmed = false, destructive = false)` (**new parameters**): Body row, `row_padding_vertical`, at least 48dp. `checked` draws `ic_check` at the end in `primaryColor`, plus `isSelected` and a state description. `summary` adds a Caption line.
6. `primaryAction(text)` (**new**): a full-width Label row at the bottom, `space_l` above it, no divider.

Rules:
- Order: calm or safe actions first, destructive actions last (after a `section_gap`), with exact labels ("Uninstall", "Remove from home").
- Pickers always show the current value as `checked`. Multi-select uses check rows, not stock `CheckBox`/`RadioButton`.
- One scroller per sheet. A long list opens in its own sheet (the folder-picker pattern), never as a fixed-height RecyclerView inside the sheet's scroll.
- Width: `behavior.maxWidth = sheet_max_width`, so the sheet is full width on phones and centred on large screens. Opens `STATE_EXPANDED`, `skipCollapsed` (already true; M4-WP24/25 fix the outliers).
- Sheets built outside the builder (hand-built `BottomSheetDialog`) are legacy. New UI may not add any; existing ones migrate when touched.
- `dialog_home_menu.xml` is retired. The home long-press menu is a `BottomSheetMenu`.

### 6.5 Dialogs
- No new `AlertDialog`. Confirmations are sheets. System pickers (`TimePickerDialog`) use the app theme (M4-WP30).

### 6.6 Feedback: inline vs Snackbar vs Toast

| Situation | Use |
|---|---|
| A setting changed | the value changes in place. Nothing else |
| Something was removed or cleared, and the view is still on screen | `Snackbar` anchored to the root with "Undo" (Material, already a dependency) |
| Refused, with an obvious fix ("Home layout is locked") | `Snackbar` with the fix as its action ("Unlock") |
| Feedback after leaving the screen, or from a background path | `Toast`, at most 2 lines at 200% font |
| A failure inside content (widget error, no usage access, empty quiet list) | an inline empty or error state in Parem's voice that says what to do |

## 7. Motion

### 7.1 Tokens
`res/values/integers.xml` (**new**), plus platform interpolators, so no new files are needed for curves:

| Token | Value | Use |
|---|---|---|
| `motion_press` | 0ms in, 150ms out | pressed colour (colour selector, not an animator) |
| `motion_short` | 150ms | small state changes: crossfades, a row becoming available, exits |
| `motion_medium` | 200ms (= `config_shortAnimTime`) | screen changes: home ↔ drawer, settings |
| `motion_max` | 300ms | ceiling. Nothing longer except the settings-search highlight (700ms, explanatory) |
| Enter / respond | `@android:interpolator/fast_out_slow_in` (screens), `linear_out_slow_in` (things appearing) | |
| Exit | `@android:interpolator/fast_out_linear_in`, 150ms | exits are faster than entries |
| Spring (after a gesture hands off velocity) | `SpringForce.STIFFNESS_MEDIUM`, `DAMPING_RATIO_NO_BOUNCY`. `LOW_BOUNCY` only after a flick | `SpringAnimation` (dynamicanimation, already transitive via Material; declare it in `libs.versions.toml` if used directly) |

Rules:
- No `linear` interpolator on UI movement. No `scale` on text (drawer rows lose the 110% scaleY). No looping animation, including marquee.
- Navigation transitions (the `nav_graph` enter/exit/popEnter/popExit anims:
  `fade_*`, `slide_*_top`) move to `res/animator/` as `objectAnimator`, so
  predictive back can scrub them. Navigation 2.9.0 (already in
  `libs.versions.toml`) brings Fragment 1.8, which scrubs Animator transitions,
  so no dependency change is needed.
- The drawer's list stagger (`layout_anim_from_bottom`, `item_anim_from_bottom`)
  stays in `res/anim/`: `AnimationUtils.loadLayoutAnimation` and
  `LayoutAnimationController` take view Animations only. It gets the token
  duration and interpolator and loses the text `scaleY`.
- Spatial consistency: drawer comes up, home goes up, back comes down. App launch passes `sourceBounds` (and a clip-reveal where the OEM honours it) from the tapped label.
- Stagger never blocks input and never exceeds about 150ms in total.

### 7.2 Reduced motion, animations off, e-ink
Android's "Remove animations" sets the animator scale to 0, and `context.skipAnimations()`
already covers that plus e-ink. Every new motion:
1. checks `skipAnimations()` and sets the end state immediately (except the `BaseFragment` 0ms Animator substitute);
2. under reduced motion, swaps translation for a short opacity change. Feedback is never removed: pressed colour, haptics and threshold ticks stay;
3. on e-ink: no fades, ripples, stretch overscroll, smooth scroll or predictive-back scale. Instant swaps, and a plain pressed `StateListDrawable` instead of `selectableItemBackground`;
4. direct `BottomSheetDialog`s call `disableAnimationsOnEink()` before `show()`.

## 8. Haptics
Always use `View.performHapticFeedback(constant)` **without** `FLAG_IGNORE_GLOBAL_SETTING`. That is what makes "no vibration when system touch feedback is off" hold. Never call `Vibrator` directly. Fire on the same frame as the visual change. `CONFIRM`, `REJECT` (API 30) and `GESTURE_THRESHOLD_ACTIVATE` (API 34) sit behind a `Build.VERSION.SDK_INT` check with the API 29 fallback below, so lint `NewApi` stays clean at minSdk 29.

| Moment | Constant (API 30+) | API 29 |
|---|---|---|
| Any long-press that opens a menu (slot, empty home, drawer row, clock, date) | `LONG_PRESS` | same |
| Gesture letter recognised | `CONFIRM` | `KEYBOARD_TAP` |
| Gesture letter rejected; locked layout refused | `REJECT` | `LONG_PRESS` |
| Drawer pull crosses the close threshold | `GESTURE_THRESHOLD_ACTIVATE` (34+), else `CLOCK_TICK` | `CLOCK_TICK` |
| Taps, toggles, sheet rows, launches, mindful-pause completion | none (rewarding the wait undermines the pause) | none |

## 9. Gestures
- Thresholds come from `ViewConfiguration`, never raw px: swipe distance ≥ `max(3 × scaledTouchSlop, 48dp)` **or** velocity ≥ `4 × scaledMinimumFlingVelocity`. Off-axis movement must be under half the on-axis movement. A touch that never left the slop is a tap, however long it lasted.
- Long-press respects the system touch & hold delay (GestureDetector already does).
- Every home gesture has a non-gesture route: TalkBack custom actions on `mainLayout` and on each slot, the home long-press sheet listing All apps / Notifications / Add widget / Settings, and a visible "All apps" row when touch exploration is on.
- Single-match auto-launch stays the default (DECISIONS: "auto-launch stays king"). It becomes a setting and is forced off under touch exploration or spoken feedback. A short commit beat that the next keystroke cancels is **owner decision 5**: until Patric decides, the default behaviour is unchanged (no beat).

## 10. Accessibility baseline (release gate for every 6.0 surface)
- [ ] Respects system font scale up to 200%, with no clipping or overlap (section 3.1).
- [ ] Every interactive element is at least 48dp and reachable with TalkBack, Switch Access and a D-pad (M3-WP6).
- [ ] Every image or icon-only view has a `contentDescription`, or is `importantForAccessibility="no"` when decorative. **Exception: `@id/lock` keeps its `lock_layout_description` and click handler (Trap #1).** It is hidden from the accessibility node tree with an `AccessibilityDelegateCompat` (`isVisibleToUser = false`, `ACTION_CLICK` removed), never with `importantForAccessibility="no"`. Device-check both "TalkBack skips it" and "double-tap still locks".
- [ ] Toggles and pickers announce label, role and state as one node.
- [ ] Settings section titles and sheet titles are accessibility headings.
- [ ] Dynamic state that matters (mindful-pause "ready", picker cap reached) is announced once (`accessibilityLiveRegion = POLITE`, set when the state changes).
- [ ] No timing-only interactions without an alternative (auto-launch toggle, double-tap lock has a settings route).
- [ ] RTL: `start`/`end` everywhere. Alignment labels describe what the user sees ("Left"/"Right" mapped through `layoutDirection`). Mixed-direction strings use `BidiFormatter`. Native digits work in the omnibox.
- [ ] Contrast as section 5.
- [ ] Checked with TalkBack on, at font 200%, in dark and light, on a phone and at sw600dp in both orientations.

## 11. Tablet, landscape and large screens
- Width qualifiers (`sw600dp`, and `sw600dp-w840dp` for landscape tablets, per the qualifier rule in section 4), never density or `isTablet()` (M4-WP13).
- Home: `home_keyline` scales, and clock and apps share one edge. Landscape keeps the widget column beside the clock (M4-WP28 fixes the bugs and adopts the keyline).
- Settings, drawer and onboarding: reading width is capped by `content_side_padding` (WP13).
- Sheets: `sheet_max_width`, centred, expanded (section 6.4).
- Portrait lock below sw600 only (WP13).
- Insets: cutout on the short edge in phone landscape and the 3-button nav bar are handled by insets, not margins (section 4).

## 12. Review checklist (paste into PRs that touch UI)
- [ ] Text uses a `TextAppearance.Parem.*`. No new `textSize =`/`setTypeface`.
- [ ] Only the current value is bold. No colour added by Parem.
- [ ] Two radii (`corner_surface`, `corner_control`). Spacing from section 4 tokens.
- [ ] Text sits on a surface or `surfaceScrimColor`. Shadow only on the wallpaper.
- [ ] Sheets go through `BottomSheetMenu`. Calm or safe actions first, destructive last, current value checked.
- [ ] Copy follows section 2. Empty, loading and error states exist.
- [ ] Motion uses section 7 tokens and respects `skipAnimations()`. Haptics follow section 8.
- [ ] Section 10 baseline passes. Trap #1 untouched.

---

## 13. UX work packages (ranked)

Filed for the roadmap. They are deduplicated against M2-WP1 and M4-WP1…WP30.
Priority is the order to do them in. It follows the launch tier first, then the
file sequence in 13.2.

### 13.1 Packages and launch tier

The M4 cut-off rule ("any M4 row not green when the closed test ends moves to
6.1") drops rows by tier, lowest first, never at random.

- **Blocks 6.0**: every-user and Play accessibility-review issues. Launch waits for these.
- **Should land**: clear user value. Slips to 6.1 only if the closed test ends first.
- **Can slip**: consistency and polish. 6.1 is acceptable without breaking the Calm story.

| Pri | # | Package | Size | Design first | Tier | Depends on (rows, not files) |
|---|---|---|---|---|---|---|
| — | (was UX-9) | Window insets and predictive back | — | — | **Blocks** | **Folded into M2-WP1** as its spec (section 14). Not a separate row |
| 1 | UX-1 | System font size is multiplied, not replaced | S | no | **Blocks** | M4-WP22, M4-WP26 (message when capacity drops) |
| 2 | UX-2 | TalkBack users can leave home | M | no (Trap #1 owner gate before merge) | **Blocks** | M2-WP1, M4-WP25, M4-WP26 |
| 3 | UX-5 | Settings and dialogs speak to screen readers | M | no | **Blocks** | M4-WP24, M4-WP30, M4-WP3 (focus sheet) |
| 4 | UX-3 | Touch is steady and consistent | M | no | Should land | UX-2 |
| 5 | UX-11 | Hidden apps can always be found again | S | no | Should land | UX-5 |
| 6 | UX-4 | Auto-launch is a setting, off under TalkBack | S | no (the beat waits for decision 5) | Should land | M4-WP27, UX-11 |
| 7 | UX-7 | The calm path is the easy path | M | no | Should land | M4-WP2, M4-WP3, M4-WP24, UX-5 |
| 8 | UX-8 | Locale-correct copy | M | no | Should land | M4-WP27, UX-4, UX-7 |
| 9 | UX-10 | Home and drawer teach themselves on first run | M | yes (decision 2) | Should land | M4-WP23, M4-WP28, UX-3 |
| 10 | UX-6 | Design tokens and legibility | L | yes (this file) | Can slip | M2-WP1, UX-10 |
| 11 | UX-12 | Settings structure | L | yes | Can slip | UX-6, UX-4, UX-11 |
| 12 | UX-14 | `BottomSheetMenu` grammar | M | no | Can slip | UX-6, UX-5, UX-12 |
| 13 | UX-13 | One menu system | L | yes | Can slip | M4-WP25 (lands on its own first), M4-WP30, UX-14 |
| 14 | UX-16 | Calm quick actions (home hub and gestures) | S | no | Can slip | UX-13, M4-WP5 (grayscale action only) |
| 15 | UX-15 | Navigation motion | M | no | Can slip | M2-WP1 (owns predictive back and the back callback) |

Full outcome, owned files and done criteria are in the orchestrator's package list.
Every package's done bar includes the `AGENTS.md` checks exiting 0. Device-only
items are listed in the PR as **"not verified"** and close in the owner's UX
device pass (section 14), except UX-2's Trap #1 check, which gates the merge.

### 13.2 Shared files: who goes first

ROADMAP says packages are disjoint by file unless the row says otherwise. These
rows say otherwise, in this order. A later package rebases on the earlier one
and never edits the same lines in parallel. `strings.xml`, `Prefs.kt` and the
new "6.0 UX device pass" section of `RELEASE_CHECKLIST.md` are append-only and
are not sequenced.

| File | Order |
|---|---|
| `layout/fragment_settings.xml` + `layout-land` | UX-5 → UX-11 → UX-4 → UX-6 (root background) → UX-12 |
| `ui/settings/AppearanceSettingsCard.kt` | M4-WP22 → UX-1 → UX-5 → UX-6 → UX-12 → UX-14 |
| `ui/settings/HomeScreenSettingsCard.kt` | UX-5 → UX-4 → UX-8 → UX-12 → UX-14 |
| `ui/settings/GesturesSettingsCard.kt` | UX-5 → UX-12 → UX-14 → UX-16 |
| `ui/settings/SettingsSearchIndex.kt` | UX-11 → UX-4 → UX-12 |
| `ui/BottomSheetMenu.kt` | M2-WP1 (bottom inset) → UX-5 (heading, handle, 48dp) → UX-14 |
| `ui/ScreenTimeLimitDialog.kt` | M4-WP24 → UX-5 → UX-7 → UX-8 |
| `ui/FocusModeDialog.kt` | M4-WP3, M4-WP24 → UX-5 → UX-7 → UX-14 |
| `ui/BadHabitDialogs.kt` | M4-WP2 → UX-5 → UX-7 → UX-8 |
| `ui/AppPickerAdapter.kt` | UX-5 → UX-14 |
| `ui/home/HomeSlotsController.kt` | M4-WP26 → UX-2 → UX-3 → UX-10 → UX-13 |
| `ui/home/HomeGesturesController.kt` | M4-WP25 → UX-2 → UX-3 → UX-13 → UX-16 |
| `ui/home/HomeClockController.kt` | M4-WP29 → UX-2 → UX-8 |
| `ui/HomeFragment.kt` | M4-WP23 → M2-WP1 → UX-2 → UX-10 → UX-6 → UX-15 |
| `layout/fragment_home.xml` + `layout-land` | M2-WP1 → M4-WP28 → UX-2 → UX-8 → UX-10 → UX-6 |
| `ui/AppDrawerAdapter.kt` | M4-WP27 → UX-11 → UX-4 → UX-8 → UX-13 |
| `ui/AppDrawerFragment.kt` | M2-WP1 → UX-11 → UX-4 → UX-10 → UX-13 → UX-15 |
| `layout/fragment_app_drawer.xml` + `layout-land` | M2-WP1 → UX-10 → UX-6 |
| `layout/adapter_app_drawer.xml` | UX-2 → UX-6 → UX-13 |
| `MainActivity.kt` | M2-WP1 (decor, back callback, manifest flag) → UX-1 → UX-11 → UX-12 |
| `res/values/styles.xml`, `values-night/styles.xml` | M2-WP1 (removes `windowTranslucent*`) → UX-6 (theme merge) |
| `data/Constants.kt` | UX-1 → UX-16 |

## 14. Hand-offs to existing rows and owner decisions

**Amendments to existing rows (not new packages):**
- **M2-WP1 targetSdk 36 (design first, open): this file's insets and
  predictive-back spec is its proposal input. It replaces the former UX-9.**
  targetSdk is already 36 on `next` while `styles.xml` still sets
  `windowTranslucentStatus/Navigation`, so this is a live Play-readiness gap.
  It blocks 6.0. The proposal should cover:
  - Remove `windowTranslucentStatus`/`windowTranslucentNavigation` from both
    theme files. At targetSdk 36, API 35+ devices are edge-to-edge anyway, so
    `WindowCompat.setDecorFitsSystemWindows(window, false)` (set once in
    `MainActivity`) matters only on API 29–34. The inset listeners are the real
    work.
  - Each fragment root (home, drawer, settings, onboarding listener only) pads
    by `systemBars() or displayCutout()` through
    `ViewCompat.setOnApplyWindowInsetsListener`, plus the design keyline. The
    drawer adds `ime()`, so results never sit under the keyboard.
    `BottomSheetMenu` content adds the bottom inset.
  - Fixed offsets to replace, to be listed exactly in the proposal: home
    56/112/48dp, drawer 88/180dp, settings 64dp (portrait and land). On gesture
    navigation the visual spacing should match today's.
  - Interplay: M4-WP26's slot fitting already subtracts the nav-bar inset, so
    it must read the same inset source. M4-WP28's landscape fixes adopt the
    keyline on top.
  - Predictive back: `android:enableOnBackInvokedCallback="true"` on
    `<application>` in `AndroidManifest.xml` (absent today; required for
    Android 14/15, default only on 16). The `MainActivity` back callback is
    enabled only while home is the current destination, so drawer, settings
    and sheets get the system peek, and Back on home is still swallowed.
    Navigation 2.9.0 / Fragment 1.8 can already scrub Animator transitions.
    UX-15 supplies those animators afterwards and does not touch the callback
    or the manifest.
  - Check on the emulator: 3-button nav, gesture nav, display-cutout overlay,
    portrait and phone landscape.
- **M4-WP25** lands on its own (one-line expanded-sheet fix). UX-13 rebuilds
  the menu on top of it later and does not supersede it.
- **M4-WP12 onboarding:** use the visual spec here (scrim over the wallpaper,
  Title appearance, sentence case, no dots or bullet inventories). Screen 1 adds
  "Your apps stay installed. You can switch back anytime." Screen 2 rows, in
  dependency order: Screen time → Slow down an app → Quiet notifications → Double
  tap to lock → Grayscale ("one-time setup, about 2 min"). Footer: "All of this
  is in Settings › Calm" (or the current section name if decision 3 says no).
  The mindful-pause row needs the "any app" picker from UX-7.
- **M4-WP13 adaptive:** owns `content_side_padding`. Add `sheet_max_width`
  (section 4) to its sheet step if UX-14 hasn't landed. Extracting settings cards
  into `<include>` files is WP13's call, and makes UX-12 cheaper.
- **M4-WP9 translations:** decide `ar`/`he`. Either add them to the target list,
  or drop them from `res/xml/locales_config.xml` until they're complete (owner
  call). UX-8 makes the remaining literals translatable first.
- **M4-WP15 rotating hint:** filter the hint array by enabled features, and
  include "type a setting, e.g. grayscale".
- **M4-WP28 landscape home:** adopt `home_keyline` and the "one edge" rule.

**New owner task under M4 (no id): UX device pass.** UX-2 creates a
"6.0 UX device pass" section in `docs/RELEASE_CHECKLIST.md`. Every UX package
appends its device-only checks there (TalkBack walks and recordings, Tab S8 plus
phone runs, contrast readings on a white and a busy wallpaper, haptics with
touch feedback on and off, predictive-back peek). Agent PRs close with those
items listed as "not verified". Patric runs the section once per batch.
**Exception:** UX-2's Trap #1 pair (TalkBack skips `@id/lock`, double-tap still
locks with TalkBack off) is checked by Patric on a device **before** UX-2 merges.

**Owner decisions:**
1. Approve this file (gates UX-6, and by extension the visual half of UX-12/13/14).
2. Seed empty home slots from the default dialer, messaging, camera and browser on fresh install (UX-10), or use the dimmed hint only.
3. Rename "Digital Wellbeing" → **"Calm"** (UX-12).
4. Brand typeface: keep the system sans (default in this file), or bundle Atkinson Hyperlegible Next (OFL, about 100 to 300 KB) with a "System font" option. 6.1 candidate.
5. Auto-launch commit beat: about 250ms for everyone, only while the new setting is on, or no beat at all. **UX-4 ships without a beat**, and the default behaviour stays exactly as DECISIONS protects it. If Patric picks a beat, it is a follow-up row of about S size in `AppDrawerAdapter`.

**Deliberately not in 6.0:** finger-tracked drawer (needs the drawer as a view
on home, which is an architecture change), "Calm widgets" desaturation and
Parem-styled widget error view (taste #7; 6.1 candidate), results-near-keyboard
drawer option, screen-time graph RTL mirroring, sheet anchoring to the
long-press side, gesture sensitivity setting.

## 15. Review notes (2026-10-03 platform/roadmap review)

Accepted as raised:
- UX-9 duplicated M2-WP1 → folded into M2-WP1 as its spec (section 14). The predictive-back half of UX-15 (manifest flag, back callback) moved there as well. UX-15 keeps the drawer close, the double-drawer guard and the animator conversion, and it depends on M2-WP1.
- `res/anim` stagger can't become `objectAnimator` → only the nav-graph anims move (section 7.1).
- Predictive back under-owned → the manifest attribute and callback belong to M2-WP1. Dependency check done: Navigation 2.9.0 → Fragment 1.8 scrubs Animators, so no version bump is needed.
- UX-4 stated decision 5 as settled → UX-4 ships the setting plus the TalkBack gate only (section 14, decision 5).
- Qualifier precedence → `values-sw600dp-w840dp/`, no `values-w840dp/` (section 4). UX-6 owns and UX-14's done bar were updated to match.
- `values-night` can't be colour-only as written → DayNight parent plus `@bool/light_system_bars` (section 5), so the criterion holds literally.
- Haptics at minSdk 29 → version-gated with API 29 fallbacks, and no `FLAG_IGNORE_GLOBAL_SETTING` (section 8).
- UX-1 worst case → clamp of Parem's extra multiplier at max(system, 2.0) (section 3.1). The done bar tests system 2.0 × Larger. Capacity drop is announced via M4-WP26.
- UX-13 scope → the new gesture actions and Calm quick actions are split into UX-16. M4-WP25 lands independently.
- No launch tiering → section 13.1.
- Device-only acceptance → owner task "UX device pass" (section 14), with Trap #1 as a pre-merge gate.
- Theme rewritten by two packages → M2-WP1 removes the translucent flags first, then UX-6 merges the theme.

Accepted in a different form:
- **"UX-12 before UX-5" / "give the sheet title heading and 48dp rows to UX-14 only".**
  I disagree with the order, not with the problem. UX-5 blocks launch, and
  UX-12 and UX-14 are L/M "can slip" packages. Making launch-blocking
  accessibility wait on them means the cut-off could ship 6.0 with neither.
  The duplication is fixed differently:
  - **One owner for each piece.** UX-5 alone owns the `BottomSheetMenu` title
    heading, handle skip and 48dp row minimum. UX-14 builds on them and does
    not redo them.
  - **UX-5 goes first, and the order is written down (13.2).** UX-5's settings
    semantics are XML attributes on the row container
    (`screenReaderFocusable`, label `importantForAccessibility="no"`) plus one
    `setStateDescription` call in each `bind()`, so they move with the row when
    UX-12 moves it. For the rows UX-12 will rebuild (the inline strips), UX-5
    does only "label and value as one node" and skips the role delegate, so
    the work that gets thrown away is a few lines.
  - **UX-12's done bar** includes "the UX-5 TalkBack walk still reads every
    row as label, role, state". Rows added later (UX-11, UX-4) follow section
    6.3 when they are created.
  - The suggested order (UX-6 → UX-12 → UX-4/UX-11 → UX-5 → UX-14) would put
    two blocks-launch items behind an L design-first package. Section 13.2 is
    the order I'm committing to instead.
- **UX-10 tier.** The review left it unassigned. It is "should land": a Play
  newcomer's first screen is four "App" rows today, and that costs ratings.
  It is still design first only for decision 2.
