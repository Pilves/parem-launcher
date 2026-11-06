# Parem Launcher v6.0.0-beta.1 — "Calm phone"

First release since v5.5.1. Everything new is optional, on-device and off by
default. Requires Android 10 or newer.

## Calm tools
- Quiet notifications: pick the apps allowed to alert; everything else leaves
  the shade and waits in a list you open with a swipe. Calls, alarms, media
  and navigation are never hidden.
- Mindful pause: a limited app asks why you are opening it and waits 5 s —
  on every route (home, folder, drawer, gesture, swipe app).
- Scheduled focus: weekly windows (work 09–17 Mon–Fri, sleep 22–07) with the
  same allowed apps as a timed session.
- Grayscale: the whole screen, during focus, after an app hits its limit, or
  per app while it is in front. One-time permission; a walkthrough shows how.
- Weekly review in the screen-time sheet: this week vs last, biggest risers
  and fallers.
- Usage access is now a named permission — limits that could never fire are
  no longer saved silently.
- Every launch route — double-tap, quiet-list notification, website
  shortcut, web search — honours Focus mode, app limits and the mindful
  pause.

## Search
- Quick actions: "alarm 7:30", "timer 10m", "event friday 3pm dentist",
  "remind me 5pm call mum" (Estonian too).
- App shortcuts ("liked" → Spotify Liked Songs), settings search
  ("keyboard" → that settings row), currency conversion with ECB rates,
  optional search history (local only).
- Numbers, sums, conversions and phone numbers no longer auto-launch an app
  that happens to match; a new setting turns single-match auto-launch off.

## Home and gestures
- Website shortcuts on home slots and in folders.
- Gesture letters recognise shape, not pixels: size, speed, slant and
  reversed strokes all work; ambiguous marks are rejected.
- Lock home layout, bold font, keyboard and D-pad navigation.
- Lock without the accessibility service (device-admin fallback, opt-in,
  PIN-only unlock afterwards), and a clear explanation whenever Android turns
  the lock service off.
- Private Space section in the drawer (Android 15+).

## Fit and feel
- New first-run flow: set as home, then optional Calm setup and feature
  explanations. Shown once to updating users.
- Text follows the system font size up to 200%; edge-to-edge on Android 16;
  tablets and foldables rotate freely with readable widths; e-ink phones get
  no animations.
- TalkBack, Switch Access and Voice Access users can leave home and reach
  every action; the screen-time graph is navigable.
- Touch feels the same on every device: swipe thresholds follow system touch
  settings, shaky taps are taps, haptics follow the system setting.
- Accessibility pass: settings rows read once as label, role and state,
  section titles are headings, every row and icon is at least 48dp.
- "System default" theme restyles at once when the system dark mode flips.

## Fixes
- Home date and battery refresh while visible (midnight, timezone change).
- Widgets survive rotation and process death during setup; invalidated
  widgets are re-asked for instead of vanishing.
- Settings sheets close on Home; rotating with Settings open no longer
  crashes; onboarding comes back if interrupted.
- A fixed Dark/Light theme that disagreed with the system dark mode could
  leave home showing only the wallpaper; fixed.
- The hidden-apps drawer and a pending gesture-letter assignment survive
  process death.
- "1,000 usd" converts a thousand, the weekly review compares like days, and
  device-only lock/grayscale state is cleared after a backup restore.
- From v5.7.0 (never shipped): capacity-aware home-app picker, landscape
  widgets below the clock, scrolling tall sheets, gesture letters in tablet
  landscape, import applies app limits at once, smoother drawer typing with
  contact search.

## Privacy and platform
- No longer asks to see every installed package. Notification access and the
  grayscale permission are requested only when you turn those features on.
- Opt-in crash reports: saved on the phone, you choose to share them.
- Targets Android 16. Android 7–9 are no longer supported.
- Release APKs carry no encrypted dependency block (F-Droid / IzzyOnDroid).

Beta: not on Google Play yet. Privacy policy page, translations and store
listing follow in v6.0.0.
