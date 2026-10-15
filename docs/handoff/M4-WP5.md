---
wp: M4-WP5
pr: 0
commit: 6a35098
branch: feat/grayscale
date: 2026-10-03
---
Grayscale (Settings › Digital Wellbeing) switches the Secure-settings daltonizer now, during focus, or after a limit override until Parem is returned to; `GrayscaleController` is the only writer and `reconcile()`s on start, home resume, trigger changes and a focus-end WorkManager job; `WRITE_SECURE_SETTINGS` is granted inside Parem on Android 11+ by `GrayscalePairing` (a SPAKE2/TLS adb client in `helper/adb/`, pairing code typed into a notification), with the WebUSB page `web/grant/` (vendored Tango, published by `pages.yml`) and the adb command as fallbacks.
Contract: `GrayscalePolicy.desired(...)`/`plan(...)` decide; `GRAYSCALE_ON_FOCUS`, `GRAYSCALE_ON_LIMIT`, `GRAYSCALE_APPS` are exported while `GRAYSCALE_MANUAL`, `_APPLIED`, `_SUPPRESSED`, `_LIMIT_OVERRIDE`, `_LEFT_SINCE_OVERRIDE`, `_PREV_ENABLED`, `_PREV_MODE` are device-only; Parem saves the user's colour-correction state before its first write, restores it exactly, and backs off if the user changes it while grey is applied.
Not verified: pairing against a real phone's Wireless debugging; grant persistence across reboot and OTA; Xiaomi's extra "USB debugging (Security settings)"; whether a running process sees `pm grant` without a restart; enabling GitHub Pages for the grant page is an owner step.
Easy to get wrong: uninstalling while grey leaves the phone grey (the sheet text and the system Color correction tile are the escape hatch); `POST_NOTIFICATIONS` is asked for only when pairing starts; the adb client is hand-rolled crypto — change it only with `Spake2Test`/`AdbWireTest` (BoringSSL vectors) green.
