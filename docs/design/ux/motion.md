# Motion and interaction audit: Parem 6.0 "Calm phone"

Specialist: motion and interaction. Lenses: Apple *Designing Fluid Interfaces* (response, 1:1 tracking,
interruptibility, velocity handoff, spatial consistency, multimodal harmony, reduced motion) and Emil
Kowalski's design-engineering rules (how often a motion is seen, ease-out on enter, under 300 ms, press
feedback, momentum dismissal, keeping it subtle). Every web-specific idea has been translated into Android
View-system terms. That means no CSS, no Compose and no new UI frameworks.

Source: `origin/next` @ 3915b38. The UX capture was blocked because the tablet was locked, so I couldn't
use screenshots for motion. Everything below comes from reading the code, plus QA shot 10 for the sheet frame.
Items already filed as M4-WP22..30 are skipped. So are sheet width (WP24) and the home menu peek state (WP25).

## How Parem moves today (map)

| Surface | Mechanism | Duration / curve | Gated by `skipAnimations()` |
|---|---|---|---|
| Home → drawer | Nav action anims: drawer `fade_enter`, home `slide_out_top` (-1 %) | `config_shortAnimTime` (200 ms), **linear** | yes (`BaseFragment.onCreateAnimator` swaps in a 0 ms Animator) |
| Drawer rows | `layout_anim_from_bottom`: 20 % translate, alpha, **scaleY 110 %**, 6 % stagger | 200 ms decelerate | yes |
| Drawer → home | Overscroll > 10 px in `scrollVerticallyBy` while DRAGGING → `popBackStack()` | instant trigger, no tracking | yes for the glow only (e-ink sets `OVER_SCROLL_NEVER`) |
| Home ↔ settings / onboarding | fade / fade | 200 ms linear | yes |
| Bottom sheets | Material `BottomSheetDialog` window slide plus `BottomSheetBehavior` drag | Material defaults | yes (`disableAnimationsOnEink()` → `setWindowAnimations(0)`) |
| Home swipes | `GestureDetector.onFling`, raw **100 px** distance and **100 px/s** velocity | n/a, only the end state is reported | n/a |
| Gesture letters | Overlay trail at 40 % alpha, cleared instantly on UP | n/a | not gated (user ink) |
| Settings omnibox jump | Row alpha 0.2 → 1 | 700 ms | yes |
| App launch | `LauncherApps.startMainActivity(component, user, null, null)` | system default | n/a |
| Haptics | `LONG_PRESS` on empty-home long-press; `KEYBOARD_TAP` when a letter is recognised | n/a | system setting |

What already works well: the single `skipAnimations()` gate (e-ink, or any animation scale at 0), the
Animator substitution that avoids Olauncher #713, slot presses that show their pressed state on DOWN, the
expanded and skip-collapsed setting in `BottomSheetMenu`, and swipe and letter strokes that are
disambiguated by turn angle. The bones are good. The gaps are in how continuous the motion feels, how
consistent the feedback is, and a few sharp edges.

`androidx.dynamicanimation:dynamicanimation:1.0.0` comes in **transitively** through `material:1.12.0`
(compile scope, confirmed in the gradle cache POM). `SpringAnimation` is therefore already on the classpath
and adds nothing to the APK. If code uses it directly, declare it in `libs.versions.toml`. That is a
dependency-hygiene decision for Patric. Every recommendation below also has a `ViewPropertyAnimator` fallback.

House motion tokens (proposed). The theme is AppCompat, so Material motion attrs aren't available. Use the
platform interpolators, which need no new files:
- enter / respond: `@android:interpolator/fast_out_slow_in`, or `linear_out_slow_in` for things that appear
- durations: press 0 ms in, 150 ms out; small changes 150 ms; screen changes 200 ms (`config_shortAnimTime`); nothing over 300 ms except the omnibox-jump highlight, which explains something
- springs, where a gesture hands off velocity: `SpringForce.STIFFNESS_MEDIUM` (≈ Apple response 0.35 s), `DAMPING_RATIO_NO_BOUNCY` (1.0) by default, and `DAMPING_RATIO_LOW_BOUNCY` (0.75) only after a flick

---

## Top issues (highest impact first)

### 1. Drawer closes when a pull at the top crosses 10 px, with no tracking, threshold or rubber band (high, M)
`AppDrawerFragment` overrides `LinearLayoutManager.scrollVerticallyBy` and calls `popBackStack()` as soon as
`overScroll < -10` (raw pixels, about 3 dp on a phone) while the list is DRAGGING. Scrolling a long list up
by finger and hitting the top dismisses the drawer in the middle of the gesture. The user never sees it
coming (no tracking), can't change their mind (no release decision), and 10 px is smaller than the touch
slop. On Android 12+ the system stretch effect starts and then is cut off by the pop.

**Android fix:** replace the override with a `RecyclerView.EdgeEffectFactory`. For `DIRECTION_TOP`, return an
`EdgeEffect` subclass:
- `onPull(deltaDistance)`: accumulate the pull (× rv height). Set `binding.root.translationY = rubberband(pull)`
  (`pull * h * 0.55 / (h + 0.55 * pull)`) and fade alpha to about 0.6 at the threshold. When the pull first
  crosses **64 dp**, fire `HapticFeedbackConstants.GESTURE_THRESHOLD_ACTIVATE` (API 34) or `CLOCK_TICK`
  (29–33) once, so the user knows releasing will close.
- `onRelease()`: past the threshold, `popBackStack()`. The exit anim starts from the current offset, so
  there's no jump. Otherwise animate back from the **current** translation:
  `SpringAnimation(root, TRANSLATION_Y, 0f)` with `STIFFNESS_MEDIUM` and `NO_BOUNCY`, or
  `animate().translationY(0f).alpha(1f).setDuration(200).setInterpolator(fast_out_slow_in)`.
- `onAbsorb(velocity)` (a fling into the top edge): close only if the velocity is above about 2×
  `ViewConfiguration.scaledMinimumFlingVelocity` **and** the gesture started at the top. Ordinary flings
  that reach the top must never close.
- `skipAnimations()`: keep the same threshold logic, but leave the translation and alpha alone.

Files: `ui/AppDrawerFragment.kt` (layout manager setup, about line 438; remove `checkMessageAndExit`'s
trigger there).

### 2. Press feedback is nearly invisible, and missing in places (high, S)
`text_colors_default` dims a pressed label only from 100 % to 80 % alpha. With the text shadow over a
wallpaper, that is barely visible on home slots, drawer rows and all 96 settings TextViews (none have
`selectableItemBackground`). The home long-press menu (`dialog_home_menu.xml`) hard-sets
`android:textColor` and has no background, so its rows give **no** feedback at all. Apple's rule #1 is that
the interface responds on touch-down. Right now Parem barely looks like it heard you.

**Android fix:**
- In `drawable/text_colors_default.xml`, make the pressed item `?attr/primaryColorTrans50`. The change is
  instant on press and instant on release, which suits a calm text UI. Use the colour selector, **not** a
  `StateListAnimator` on alpha. Several views set alpha in code (`appDelete.alpha`, `appUsageTime`, weather
  dimming), and an alpha animator would overwrite those values.
- In `dialog_home_menu.xml`, give the two rows `android:background="?android:attr/selectableItemBackground"`
  (as `BottomSheetMenu.option` does) and the colour selector instead of the flat colour. Better still,
  rebuild this sheet with `BottomSheetMenu`, which also helps WP25.
- `ViewSwipeTouchListener`: clear `view.isPressed` in `onScroll` once movement passes the touch slop. Today a
  swipe that starts on a slot label keeps the label dimmed for the whole stroke and through the swipe action.

Files: `res/drawable/text_colors_default.xml`, `res/layout/dialog_home_menu.xml`,
`listener/ViewSwipeTouchListener.kt`.

### 3. Long-press haptics are inconsistent (medium-high, S)
Empty-home long-press fires `LONG_PRESS` on purpose (the comment in `HomeGesturesController` notes that
GestureDetector bypasses the framework haptic). **Home-slot long-press goes through the same bypass**
(`ViewSwipeTouchListener.onLongPress` → `fragment.onLongClick` → `showHomeSlotMenu`) and is silent. Clock,
date and drawer-row long-presses use `setOnLongClickListener`, so they do buzz. The same gesture should feel
the same everywhere.

**Android fix:** in `ViewSwipeTouchListener.GestureListener.onLongPress`, call
`view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)` before `onLongClick(view)`. Also clear
`isPressed` there so the label doesn't stay dimmed under the sheet. When `homeLayoutLocked` shows the toast
instead, use `REJECT` (API 30+, nothing on 29) so the haptic means "nothing will change".

Files: `listener/ViewSwipeTouchListener.kt`, `ui/home/HomeSlotsController.showHomeSlotMenu`.

### 4. Gesture letters: a failed stroke gives no feedback at all, and the trail cuts out (medium-high, S)
`GestureLetterOverlayView` clears the trail on ACTION_UP. If `analyzeGesture` returns null, nothing happens:
no haptic, no toast, the ink just disappears. The user can't tell "not recognised" from "didn't register".
On success, `KEYBOARD_TAP` is the weakest constant there is, for what is a commit.

**Android fix:**
- On success: `CONFIRM` (API 30+, else `KEYBOARD_TAP`).
- On failure: `REJECT` (API 30+, else `LONG_PRESS` at most, or nothing), and keep the trail for one beat. On
  UP, copy the path into a `fadePath`, then `ValueAnimator.ofInt(102, 0)` for 150 ms with
  `linear_out_slow_in`, updating `trailPaint.alpha` and calling `invalidate()`. Skip the fade (clear
  instantly) when `skipAnimations()` is true. A fade on e-ink ghosts.
- Expose the failure through a callback such as `onLetterRejected`, so `HomeGesturesController` owns the
  haptic policy as it already does for success.

Files: `ui/GestureLetterOverlayView.kt`, `ui/home/HomeGesturesController.initGestureLetterOverlay`.

### 5. Swipe thresholds are in raw pixels, so swipes feel different on every device (medium, S)
Both swipe listeners use `SWIPE_THRESHOLD = 100` and `SWIPE_VELOCITY_THRESHOLD = 100` in **px**. That is
about 28 dp on a 3.5× phone and about 50 dp on the Tab S8, so small phones get accidental swipes and tablets
need long ones. The velocity floor (100 px/s) is also well below the platform's minimum fling, so slow drags
count as swipes.

**Android fix:** in `init`, read `ViewConfiguration.get(c)`. Use distance ≥ `2 * scaledTouchSlop` (≈ 16 dp)
**or** 40 dp, whichever is larger, and velocity ≥ `scaledMinimumFlingVelocity * 4`. Better still, accept a
short fast flick **or** a long slow drag (Emil's momentum dismissal: `distance ≥ 72dp || velocity ≥ fast`).
Merge the two copies of `GestureListener` while you're there. They are identical apart from the view
argument (flagged aside, optional).

Files: `listener/OnSwipeTouchListener.kt`, `listener/ViewSwipeTouchListener.kt`.

### 6. Back is never predictive: the activity callback swallows it (medium, M)
Parem targets SDK 36, so Android 14+ users expect to peek behind a back swipe. `MainActivity` registers an
always-enabled `OnBackPressedCallback` that calls `navController.popBackStack()`. Because it was added last,
it takes priority over NavHostFragment's own predictive callback. The nav actions also use `<alpha>` /
`<translate>` **Animation** XML, which FragmentManager can't scrub. The result is that drawer and settings
snap away on commit. Material 1.12 sheets already animate predictive back on their own.

**Android fix:**
1. Enable the activity callback only while `currentDestination == mainFragment`, so Back at home is
   swallowed (correct for a launcher), with `navController.addOnDestinationChangedListener { … isEnabled = … }`.
   Everywhere else, NavHostFragment handles Back.
2. Move the six `res/anim/*.xml` files to `res/animator/` as `<objectAnimator propertyName="alpha|translationY">`
   and point `nav_graph.xml` at them. Fragment 1.7+ then scrubs them with the back gesture.
   `BaseFragment.onCreateAnimator` already returns `null` in the normal case, so FragmentManager loads the
   animator resource, and its 0 ms substitute still covers e-ink and animations-off.
3. Check that `onNewIntent → backToHomeScreen()` still pops correctly (it calls the NavController directly,
   so it isn't affected).

Files: `MainActivity.kt` (about line 137), `res/navigation/nav_graph.xml`, `res/anim/*` → `res/animator/*`.

### 7. A double swipe-up can stack two drawers (medium, S, plausible: verify on device)
`HomeFragment.showAppList` catches the `navigate(action_mainFragment_to_appListFragment)` failure and falls
back to `navigate(R.id.appListFragment)`. If a second fling reaches HomeFragment's still-attached view during
the 200 ms exit transition, the action is unknown from the drawer destination. The catch then pushes a
**second** drawer, and Back returns to a drawer instead of home. Interruptibility should mean redirecting
the same motion, not doubling it.

**Android fix:** at the top of `showAppList`, add
`if (findNavController().currentDestination?.id != R.id.mainFragment) return`. Restrict the fallback to the
case it exists for (documented as "navigation to app list failed") or delete it.

Files: `ui/HomeFragment.kt` `showAppList`.

### 8. Drawer entry is busy for something opened dozens of times a day (medium, S)
Emil's frequency rule says a surface opened 30+ times a day should be nearly instant. Today the drawer
fades in linearly over 200 ms while every row plays `item_anim_from_bottom`: a 20 % translate, **scaleY from
110 %**, and a 12 ms stagger, with the IME appearing 100 ms in. Vertically stretching text glyphs looks
cheap. The total settle time grows with the number of visible rows (about 380 ms for 15 rows), and the
linear interpolator delays the moment of first response.

**Android fix:**
- `item_anim_from_bottom.xml`: delete the `<scale>`. Keep translate (`fromYDelta="12%"`) and alpha, 150 ms,
  `@android:interpolator/linear_out_slow_in`. In `layout_anim_from_bottom.xml`, use `android:delay="4%"`
  and consider `android:animationOrder="normal"` with a cap. Apple and Emil agree that stagger must never
  block input, and here it doesn't.
- `fade_enter.xml` / `fade_exit.xml`: switch the interpolator from `linear` to
  `@android:interpolator/fast_out_slow_in` (enter) and `fast_out_linear_in` (exit, 150 ms). Exits should be
  faster than entries.
- Keep the home `slide_out_top` / `slide_in_top` direction. Up to open and down to return is spatially
  consistent, which is correct.

Files: `res/anim/item_anim_from_bottom.xml`, `res/anim/layout_anim_from_bottom.xml`,
`res/anim/fade_enter.xml`, `res/anim/fade_exit.xml` (or their `res/animator/` versions after #6).

### 9. Apps open from nowhere: no source bounds or launch animation (medium, M, verify on device)
`MainViewModel` calls `launcherApps.startMainActivity(component, user, null, null)`. The system has no
origin, so every launch uses the generic open animation, unconnected to the label that was tapped. This is
the one transition every user sees most, and Apple's spatial-consistency rule says it should grow from its
source.

**Android fix:** pass the tapped view's on-screen `Rect` as `sourceBounds`, and pass
`ActivityOptions.makeClipRevealAnimation(view, 0, 0, view.width, view.height).toBundle()` (or
`makeScaleUpAnimation`) as `opts`. `selectedApp(appModel, flag)` has no view, so add an optional
`sourceView: View? = null` parameter used only for `FLAG_LAUNCH_APP`. Pass `null` when `skipAnimations()`
is true. A clip-reveal on e-ink is a full-screen ghost. Shell transitions on Android 12+ honour these
options differently across OEMs, so A/B it on the Tab S8 and a Pixel before committing. `sourceBounds` is
worth passing regardless, because it costs nothing.

Files: `MainViewModel.kt` (about lines 268 and 308), `ui/home/HomeSlotsController.launchApp/openApp`,
`ui/AppDrawerFragment.onAppClick`.

### 10. E-ink still gets ripples, the sheet's predictive-back scale and pager smooth-scroll (medium, S)
`skipAnimations()` covers window, fragment and layout animations, but three things still animate on e-ink,
which ghosts on every one:
- `BottomSheetMenu.option` rows use `selectableItemBackground` (a RippleDrawable). Ripples run at normal
  speed unless the animator scale is 0. On e-ink, use a plain `StateListDrawable` (pressed =
  `?attr/primaryShadeDarkColor`, otherwise transparent) built in `selectableBackgroundRes()` when
  `isEinkDisplay()`.
- Material 1.12's predictive-back scale on sheets (API 34+). When `skipAnimations()` is true, opt out by
  intercepting with an `OnBackPressedCallback` on the dialog's dispatcher that calls `dismiss()`. Verify the
  exact opt-out on device first, since Material's API here is internal.
- `OnboardingFragment` uses `PagerSnapHelper` smooth scroll. Fold this into M4-WP12 and use instant
  `scrollToPosition` on e-ink.
EinkDetector is still marked unverified (DECISIONS M3-WP7). The real fix is a test on a real Boox device.

Files: `ui/BottomSheetMenu.kt` (`selectableBackgroundRes`, `show`), `ui/OnboardingFragment.kt` (via WP12).

### 11. Auto-launch on a single match fires the instant the list narrows (medium-low, S)
`AppDrawerAdapter.autoLaunch` launches as soon as `submitList` leaves one item. WP27 covers digits and
maths. Ordinary words have the same problem: a novice typing "ch" toward "chess" gets Chrome if it's the
only match, with no sign that a launch was about to happen and no way to stop it. Power users rely on the
current speed (the tip string advertises it), so the fix has to keep it fast.

**Android fix:** add a commit beat. When the list collapses to one item, set that row's `isPressed = true`,
which uses the stronger pressed colour from #2, and `postDelayed` the launch by about **180 ms**. Cancel it
if another character arrives or the query changes. On e-ink or with `skipAnimations()`, keep the delay but
leave the highlight static. This is a product call: put it behind a setting, or ship it as the default.

Files: `ui/AppDrawerAdapter.kt` (`autoLaunch`, about line 160). Coordinate with WP27's guard.

### 12. The inline row menu and mindful-pause unlock swap states without a transition (low, S)
- Drawer long-press swaps `appTitle` INVISIBLE to `appHideLayout` VISIBLE in one frame. Use a 120 ms
  crossfade (`appHideLayout.alpha = 0f; animate().alpha(1f).setDuration(120)`, and the reverse on Close),
  gated by `skipAnimations()`.
- Mindful pause: when the countdown ends, the "Open X" row jumps from 50 % to 100 % colour. Use a 150 ms
  `ValueAnimator.ofArgb` text-colour tween so the change reads as the row becoming available rather than a
  glitch. Deliberately **no** haptic, because rewarding the wait undermines the pause.

Files: `ui/AppDrawerAdapter.kt` (about line 307), `ui/BadHabitDialogs.showMindfulPause`.

---

## Haptics policy (proposed, keep it sparse: utility over decoration)

| Moment | Constant (API 30+) | API 29 fallback |
|---|---|---|
| Any long-press that opens a menu | `LONG_PRESS` | same |
| Gesture letter recognised | `CONFIRM` | `KEYBOARD_TAP` |
| Gesture letter rejected / locked layout refused | `REJECT` | none |
| Drawer pull crosses the close threshold | `GESTURE_THRESHOLD_ACTIVATE` (34) | `CLOCK_TICK` |
| Taps, toggles, sheet rows, launches | none | none |

Always go through `View.performHapticFeedback`, which respects the user's touch-feedback setting. Never use
`Vibrator` directly. Fire the haptic on the same frame as the visual change (Apple's harmony rule).

## Reduced motion / animations off / e-ink: checklist for every new motion
1. Gate it with `context.skipAnimations()`. Set the end state immediately, not over a 0 ms animation,
   except where an Animator is needed for fragment-callback correctness, as in `BaseFragment`.
2. Directly built `BottomSheetDialog`s call `disableAnimationsOnEink()` before `show()`. This is already a
   convention, and all current call sites comply.
3. With reduced motion, replace translation with a short opacity change. Don't remove the feedback itself:
   pressed colour, haptics and threshold ticks stay.
4. On e-ink, there are no fades, ripples, stretch effects or smooth scrolls. Use instant state swaps.

## Not in scope / deliberately not recommended
- A finger-tracked drawer that slides up 1:1 with the home swipe (Niagara-style). It is the Apple ideal, but
  it needs the drawer to be a view on home rather than a nav destination. That is an L-size architecture
  change with Trap #1 and focus risk, so it belongs after 6.0. #1 and #6 get most of the feel for much less.
- Bounce or overshoot anywhere except after a flick. The mono, calm brand wants critically damped motion.
- Sounds.
