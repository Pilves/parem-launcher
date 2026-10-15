---
wp: M2-WP5
pr: 0
commit: 2ce8517
branch: feat/accessibility-consent (merged f7635ce)
date: 2026-10-03
---
Every route into Accessibility settings for the lock gesture (double-tap and swipe pickers in Settings, home double-tap and lock swipes) first shows `ui/LockConsentSheet.kt`, a BottomSheetMenu disclosure with "Agree and open settings" / "Not now"; `canRetrieveWindowContent` is gone from `res/xml/accessibility_service_config.xml`.
Contract: no consent pref — the sheet shows every time before routing; declining reverts the gesture to the prior action, or NONE when it was already Lock screen; `MyAccessibilityService` matches `event.className == "android.widget.FrameLayout"` and `event.contentDescription == lock_layout_description` because `event.source` is null without the flag; `lock_consent_*` strings are `translatable="false"`; the restricted-settings line shows on API 33+ for every install source (DECISIONS.md).
Not verified: double-tap lock on a real device without `canRetrieveWindowContent` (trap #1); the wording of the system enable dialog; whether Play accepts an English-only disclosure in localized builds.
Easy to get wrong: the invisible `lock` view, its no-op handler and `lock_layout_description` are now what the service matches on — rename or restyle any of them and the lock silently stops; `accessibility_service_description` doubles as the Play declaration text.
