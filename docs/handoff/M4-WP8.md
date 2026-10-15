---
wp: M4-WP8
pr: 0
commit: 7aa471f
branch: feat/lock-service-revoked
date: 2026-10-03
---
When the lock accessibility service has worked on this device and is now off (or listed enabled but not bound), the lock gesture shows one explanation sheet (full disclosure, routes to Accessibility settings) and a short toast on later taps until the service connects again.
Contract: `helper/LockServiceCheck.decide(enabled, bound, connectedBefore, offExplained, adminActive)` returns `LOCK | LOCK_ADMIN | CONSENT | EXPLAIN_OFF | OFF_EXPLAINED`; prefs `LOCK_SERVICE_CONNECTED` (set by the service on connect) and `LOCK_SERVICE_OFF_EXPLAINED` are export-excluded on purpose.
Not verified: that Advanced Protection revokes the service (sourced from press coverage of Android 17, not developer.android.com); the flow on a device.
Easy to get wrong: importing `LOCK_SERVICE_CONNECTED` would turn a fresh phone's first-time consent into "your service was turned off", so keep it excluded; `EXPLAIN_OFF` cancel keeps `LOCK_SCREEN` stored, unlike M2-WP5's decline, because here Android undid the user's choice.
