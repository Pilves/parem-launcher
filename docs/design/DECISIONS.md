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
- **minSdk 29** (Patric, 2026-10-03): drop Android 7–9 in 6.0 without waiting for
  install data — M4-WP20.
- **Per-app grayscale** (Patric, 2026-10-03): in 6.0, without the accessibility
  service — Parem-launch trigger plus a session-only UsageEvents check — M4-WP21.
- **M3-WP11 crash reports.** Hand-rolled handler (Option B), no ACRA. Send goes
  through the share sheet only — no hard-coded destination address in 6.0
  (a published address is Patric's call; add it later if wanted).
- **M4-WP12 onboarding.** Drop the feature tour; no "Tips" link. Build after
  M4-WP5 so every Calm-phone setup entry point exists.
- **M4-WP13 adaptive layouts.** Table-top deferred to 6.1; split screen and
  desktop window are manual checks, not emulator legs. WP13 lands after WP20
  (minSdk 29). WP12 applies `content_side_padding` to its own layout.
- **M4-WP14 app shortcuts.** Auto-launch stays king: shortcuts surface only when
  the app word matches 0 or 2+ app rows. Enter on a shortcut-only match launches
  the shortcut. Roadmap examples become "liked" / "incog".
- **M4-WP15 quick actions.** "remind me" with only a time → labelled alarm, with
  a day → calendar event. Auto-launch is suppressed on keyword prefixes of 3+
  characters. Omnibox features are discovered via a rotating hint in the empty
  drawer (owned by WP15). Estonian keywords ship as drafted; Patric reviews.
- **M4-WP18 lock fallback.** Accepted as designed incl. settings row + toast
  pointer. The fastlane admin sentence waits for Patric's wording.
- **Owner review list (Patric):** Estonian quick-action keywords and all
  Estonian strings; fastlane admin sentence; crash-report destination address.
