# M2-WP5 — Accessibility disclosure + consent (design)

Status: proposal, awaiting Patric's sign-off. Branch base: `origin/chore/agent-harness`.

## Problem

Play's AccessibilityService policy says launchers are explicitly "not accessibility tools". So Parem needs an in-app prominent disclosure that describes the data accessed and how it is used, sits outside a privacy policy, is not bundled with other disclosures, and is accepted by an "affirmative user action". It also needs a Console declaration plus a video ([policy](https://support.google.com/googleplay/android-developer/answer/10964491)).

Today there is exactly one `startActivity(ACTION_ACCESSIBILITY_SETTINGS)`: `GesturesSettingsCard.openAccessibilityService()`. That runs from the hand-built `accessibilityLayout` overlay in `fragment_settings.xml` (portrait and land). These are the routes:

| Route | Today |
|---|---|
| Settings → Double-tap action → Lock screen | `ensureLockPermission()` shows the overlay (Close / Enable) |
| Settings → Swipe left/right → Lock screen | **No disclosure, no prompt.** The action is saved and the service stays off |
| Home double-tap (default action is LOCK_SCREEN on fresh install) or a lock swipe with the service off | `lockPhone()` → `lockNow()` throws `SecurityException` → toast, then navigate to Settings. The user has to find the picker again |

The overlay text ("does not collect or share any data") never says what the service actually receives. It also isn't a `BottomSheetMenu`, and the home route never shows it at all.

## Options

**A. A shared consent sheet on every route (recommended).** A single `BottomSheetMenu` sheet says what is read and why, with "Agree and open settings" / "Not now". All three routes call it, and the overlay is deleted. Cost: one small new file and edits in 2 Kotlin files and 2 layouts. It also closes the swipe gap.

**B. Reword the overlay and add a checkbox.** Smallest diff. But it is settings-only, so the home route stays toast-then-hunt. It keeps a second sheet language, against the AGENTS.md rule. It is also harder to film for the declaration video.

**C. Drop accessibility and use device admin `lockNow()` on every API level.** Removes the policy burden entirely. Cost: device admin is deprecated for consumer use. Reported side effect (not verified): `lockNow()` forces PIN/pattern on the next unlock, so biometrics are skipped. Worse UX, and it swaps one sensitive permission for another.

## Recommendation

Option A, plus dropping `canRetrieveWindowContent` with a 3-line service change.

**Can the flag go?** Not as the service is written. When a service lacks window-content capability, the system strips the event source (`event.setSource((View) null)`, [AbstractAccessibilityServiceConnection L1867-1871](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/services/accessibility/java/com/android/server/accessibility/AbstractAccessibilityServiceConnection.java)). `getSource()` then returns null ([AccessibilityRecord](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/view/accessibility/AccessibilityRecord.java)), and `onAccessibilityEvent` returns before locking. The event itself still carries what we match on: `View.onInitializeAccessibilityEventInternal` sets `className` and `contentDescription` on the event ([View.java L9294-9299](https://github.com/aosp-mirror/platform_frameworks_base/blob/main/core/java/android/view/View.java)), and `FrameLayout.getAccessibilityClassName()` returns `android.widget.FrameLayout`. So matching on `event.className` / `event.contentDescription` keeps trap #1 working without the flag. It also lets us drop the node recycling. After the change the declaration can truthfully say "cannot read screen content".

No consent pref. The sheet appears every time before we route to system settings, which is the moment of consent. Storing it adds an export/import key for nothing.

## Exact scope

- **New `ui/LockConsentSheet.kt`**: `fun showLockConsent(context, onAccept: () -> Unit, onDecline: () -> Unit)`. It uses `BottomSheetMenu().title(double_tap_lock).message(lock_consent_body).option(lock_consent_accept){…}.option(lock_consent_decline, dimmed = true){…}`. Swipe-to-dismiss counts as decline. **Accept/dismiss race:** `option()` calls `dialog.dismiss()` before its `onClick` (`ui/BottomSheetMenu.kt:75-88`), and `Dialog` posts the `OnDismissListener`, so `onDismiss` runs after *every* option tap, including accept. A once-only guard is not enough, because decline would still run after accept and revert the gesture while the user is on their way to enable the service. So: a local `var decided = false`; each option callback sets `decided = true` first, then calls `onAccept` / `onDecline`; `onDismiss { if (!decided) onDecline() }`. Because of that flag, decline runs exactly once per sheet whether the user taps "Not now" or swipes the sheet away, and never after accept.
- **Sheet body on API 33+**: when `Build.VERSION.SDK_INT >= 33`, append one more line to the message: `lock_consent_restricted` = *"If the switch is greyed out: App info > menu > Allow restricted settings."* Without it, GitHub/sideload users get sent to a disabled toggle, which is the same toast-then-hunt problem this WP is meant to fix. It isn't gated on the installer, because the conditional wording is harmless for Play installs and a check on the install source would add code for no real gain.
- **`ui/settings/GesturesSettingsCard.kt`**: in `ensureLockPermission()`, replace `toggleAccessibilityVisibility(true)` with `showLockConsent(accept → startActivity(ACTION_ACCESSIBILITY_SETTINGS), decline → revert that gesture to its previous action, **unless the previous action was LOCK_SCREEN, in which case set NONE**). The exception is needed because `DoubleTapActionManager.getAction` defaults to LOCK_SCREEN (`helper/DoubleTapActionManager.kt:21`). Re-picking Lock screen and then declining would otherwise leave LOCK_SCREEN set with the service off. The same rule applies to the swipe pickers`. Call `ensureLockPermission()` from `showSwipeActionPicker` when LOCK_SCREEN is picked. Delete `toggleAccessibilityVisibility`, `openAccessibilityService`, and the `actionAccessibility` / `closeAccessibility` / `notWorking` wiring.
- **`ui/home/HomeGesturesController.kt` `lockPhone()`**: on P+ with the service off, call `showLockConsent` (guard `isAdded`) instead of falling through to `lockNow()`. Decline sets the triggering gesture to NONE so the user isn't nagged on every double-tap. `lockPhone` takes an `onDecline` lambda from its two callers. Pre-P keeps the device-admin path unchanged.
- **`helper/MyAccessibilityService.kt`**: match on `event.className` / `event.contentDescription`, and drop `source` and its recycling.
- **`res/xml/accessibility_service_config.xml`**: remove `android:canRetrieveWindowContent="true"`.
- **`res/layout/fragment_settings.xml`, `res/layout-land/fragment_settings.xml`**: delete the `accessibilityLayout` FrameLayout.
- **`res/values/strings.xml`** (append, `translatable="false"`): `lock_consent_body`, `lock_consent_accept`, `lock_consent_decline`, `lock_consent_restricted`. Reword `accessibility_service_description` in English only and leave the translations alone. `accessibility_disclosure`, `not_working` and `Constants.URL_DOUBLE_TAP` become unused; I'm flagging that and leaving them.
- `CHANGELOG.md` gets an Unreleased line. No manifest permission change and no new prefs.

Draft body: *"Double tap to lock uses Android's accessibility service. Android tells Parem's service when you tap inside Parem. The service ignores every tap except the one on its own invisible lock area, and when that tap comes it asks Android to lock the screen. It gets no events from other apps and cannot read your screen. Nothing is stored or sent anywhere. You can turn it off any time in Accessibility settings."*

**Play Console declaration draft**: *"Parem Launcher is a home-screen app. Its accessibility service only provides the optional 'double tap / swipe to lock screen' gesture by calling performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN), which has no alternative API for non-admin apps on Android 9+. The service receives only TYPE_VIEW_CLICKED events from Parem's own package (packageNames restricted), ignores every event except a tap on Parem's own invisible lock view, cannot retrieve window content, and collects, stores, and shares no data. Users see an in-app disclosure and must tap 'Agree and open settings' before we open accessibility settings. Declining leaves the feature off."* Not an accessibility tool (`isAccessibilityTool` stays unset).

## Done criteria

Every route in the table shows the sheet before system settings opens. Decline or dismiss never opens settings and leaves the lock off. This includes declining after re-picking Lock screen when it was already the stored action, which must end at NONE. Accept never reverts the gesture: after "Agree and open settings" the stored action is still LOCK_SCREEN. Double-tap lock still works on a P+ device with the flag removed. `compileDebugKotlin`, `testDebugUnitTest` and `assembleDebug` exit 0. The declaration text is in the PR.

## Risks & traps

- **Trap #1**: the invisible `lock` view, its no-op handler and `lock_layout_description` must survive untouched. Only the matching side changes.
- Android 13+ "restricted settings" grey out the toggle for sideloaded APKs, and GitHub releases count as sideloaded ([help](https://support.google.com/android/answer/12623953)). This is addressed by the API 33+ `lock_consent_restricted` line. The exact menu path differs between OEM skins, so the line names the setting and doesn't try to give a precise path.
- Layout edits collide with M2-WP1 (insets in layouts). Land one first and rebase the other.
- English-only disclosure on translated UI.

## Test plan

JVM: nothing new is pure logic, so no tests. Device (P+ and API 34+; pre-P if available): each route → sheet → accept → settings; decline/dismiss → no settings, gesture reverted/NONE; accept → return from settings without enabling → stored action is still LOCK_SCREEN (accept does not revert the gesture); fresh install, re-pick Lock screen for double-tap, decline → NONE; API 33+ sideloaded debug APK → restricted-settings line is visible and the toggle is greyed out as described; double-tap and lock-swipe lock with the service on; settings export/import is unaffected. Record the declaration video from this pass.

## Not verified

Lock without `canRetrieveWindowContent` on a real device. Whether the system enable dialog's wording changes once the flag is gone. The biometric side effect of `lockNow()`. Whether Play accepts an English-only disclosure in localized builds.

## Review notes

All five manager issues are accepted and applied. Two of them (the accept/dismiss race) describe the same bug and are handled by one `decided` flag. Where the issues left a choice open, I chose:
- Settings-route decline: revert to the prior action unless it was LOCK_SCREEN, then NONE. Plain NONE would also have worked, but it would throw away an action the user set on purpose (for example flashlight) when they only meant to back out of Lock screen.
- Restricted-settings line: show it on API 33+ for every install source. There is no separate decision to skip it for Play builds.

