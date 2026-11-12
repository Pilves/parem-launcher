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
- **UX round (2026-10-04).** DESIGN.md is the design system for 6.0. UX-1/UX-2/UX-5
  are the specs for M4-WP34/WP32/WP35; UX-9 is M4-WP43 (blocks 6.0); the rest are
  M4-WP44..55 with tiers. Decision 5 (DESIGN §14): no 250 ms commit beat — UX-4
  ships only the setting plus the TalkBack/touch-exploration gate.
- **Play build without adb pairing (2026-11-05).** Overrides the M4-WP5 decision
  above that in-app pairing is primary, for the Play channel only, to avoid a
  malware-policy finding on a new personal account (`docs/play/compliance.md` L1).
  The `play` flavour grants through the WebUSB page or the adb command. `full`
  keeps pairing for GitHub, F-Droid and IzzyOnDroid. M4-WP65 weighs bringing it back.
- **Restricted-settings line (2026-11-05).** Reverses the M2-WP5 decision above for
  the Play channel: `play` drops the line, because Play installs don't need it and
  it tells a reviewer how to lift a platform control. `full` still shows it.
- **Quiet notifications release, never cancel (2026-11-12, M6-WP18).** Overrides
  the M4-WP1 turn-off question. A listener's cancel is a no-op on a snoozed key,
  and the key swallows every later post from that conversation until it ends.
  Turning the filter off releases every hidden notification to the shade at once
  (a 1 ms re-snooze), so the Keep/Dismiss sheet is gone; only when no listener is
  connected does the "within 8 h" toast remain. Opening a quiet-list item or
  "Dismiss all" no longer cancels or forgets the key: Parem records the item's
  `Notification.when`, hides it until the app posts a newer one under that key,
  and cancels the unchanged item when it comes back at expiry.
