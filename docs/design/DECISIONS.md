# Product-owner decisions on design open questions

Recorded 2026-10-03 after the design/manager review round. These override the
"open questions" sections of the individual designs.

- **M4-WP5 grayscale grant.** Patric's target: the click happens in Parem. Primary
  is (b) in-app wireless-debugging self-pairing (Android 11+, no cable). Fallback
  for a cable is (a) the WebUSB page; (d) the adb command always ships. Finishing
  the grant turns grayscale on (the "Grayscale" tap was the intent). No static QR
  drawable. Enabling GitHub Pages for the WebUSB page is done by the orchestrator.
- **M4-WP1 filtered notifications.** Snoozed-key state goes in a second
  SharedPreferences file that export never reads (and is excluded from backup),
  not `noBackupFilesDir`. Implement after M2-WP1 (compileSdk 36). Gate 0
  (sideload + Play Protect check) is an owner device task.
- **M2-WP5 consent.** Show the restricted-settings line to every install source.
- **M3-WP7 e-ink.** Ship with e-ink paths marked unverified; no e-ink device is
  required to merge. Keep the existing light-theme override only when the theme
  pref is unset.
- **M4-WP3 scheduled focus.** A blocked app shows the toast; opening the Focus
  sheet directly is out of scope for 6.0.
- **M4-WP6 Private Space.** Keep `includePrivate` (default false) on
  `getAppsList`. Hide "time limit" on private rows.
- **M4-WP7 website shortcuts.** First whitelisted browser opens the site, no
  chooser. No globe icon.
- **M3-WP4 currency.** The M3-WP4 implementer adds ECB to the privacy-policy
  destinations in the M2-WP3 row.
