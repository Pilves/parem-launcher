# Changelog

## [Unreleased]

- Today's usage-stats scans (home-screen total, drawer per-app times,
  app-limit checks) now share one short-lived cache instead of running
  three separate event-log scans — PAREM-115, no user-visible change
- Omnibox: unit conversion mode ("5 km in mi", "100 f to c") alongside the
  existing calculator, dial, and web search modes — length, mass,
  temperature, volume, speed, and data size
- The 4-hour self-recreate + cacheDir wipe is now gated behind a hidden pref
  (default ON, unchanged behavior) — phase 1 of PAREM-108, no user-visible
  change yet
- Fixed: a stale cached temperature after a failed weather fetch is no
  longer shown as if it were current — dimmed once it's 3-24h old, hidden
  once it's over 24h old (PAREM-106)

## v5.5.1

- Fixed: swipe up opens the drawer again while gesture letters are enabled
  (the overlay now only captures strokes that change direction — straight
  strokes stay swipes)
- Fixed: home screen re-fits its app rows after widgets load, so pinned
  apps no longer overflow behind widgets
- Fixed: onboarding now shows once per onboarding version — a fresh
  install restored via Android backup skipped it entirely
- Sort-by-usage falls back to the launcher's own open counts when the
  usage-access permission isn't granted

## v5.5.0

### New
- New app icon: a minimal `>_` prompt (with Android 13+ themed-icon support)
- Onboarding now introduces the omnibox
- App drawer shows daily open counts next to usage time ("1h 23m · 7×")

### Omnibox — the search bar is now the single point of truth
- Fuzzy app search: initials (`gm` → Google Maps) and word-prefix tokens
  (`s ki` → Shaurmas Kitchen), diacritics-insensitive
- Inline calculator: type `2+2` and see `= 4` live; tap or enter to copy
- Dial mode: type a phone number, enter opens the dialer
- Space-prefixed queries search Google on enter; `!bang` queries go to
  DuckDuckGo (fixed: previously pointed at the defunct duck.co)

### Removed
- Quick Notes: the note overlaid the last home slot, hiding the app under
  it and jumping slots during layout fitting — inherently janky, and a
  launcher doesn't need to be a notes app

### Fixed
- Widgets now work in landscape on tablets (the landscape layout was
  missing the widget containers entirely)
- Drawing a gesture letter no longer also triggers a swipe action, and
  strokes that start on an app label now track correctly
- Omnibox modes no longer hijack enter when the drawer is open as an
  app picker
- App info and uninstall now work for work-profile apps (from upstream
  Olauncher, #446)
- Auto-launch no longer fires mid-composition on CJK keyboards (from
  upstream, #629/#694)
- Focus Mode can no longer block the phone app, and opening it from
  Settings no longer shows an empty whitelist
- "Scheduled" theme mode now asks for light/dark times (previously silently
  used 07:00/19:00 with no way to change) and applies immediately
- Long-pressing clock/date no longer wipes the chosen app if you back out
- Deleting a Quick Note no longer deletes the app pinned under it
- Flashlight toggle stays in sync when quick settings switches the torch
- About/GitHub/Privacy settings rows no longer silently do nothing

### Performance & battery
- Bad-habit limit checks no longer re-scan the whole day's usage events on
  every app launch (60s cache)
- Icon-pack lookups no longer walk the pack's resource table on every
  drawer scroll (resolved-ID cache)
- Removed a LayoutTransition on the activity root that animated every
  screen change

### Internal
- HomeFragment split (widget system extracted to HomeWidgetController),
  shared BottomSheetMenu builder replaces ~12 hand-built dialogs, ~900
  lines of dead code removed
- First unit test suite (37 JVM tests); debug-build CI workflow with APK
  artifacts; ARCHITECTURE.md maintainer docs

## v5.4.0

- Settings restructure, feature simplification, Olauncher rebrand to
  Parem Launcher (see git history)
