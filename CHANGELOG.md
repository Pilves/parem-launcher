# Changelog

## v5.5.0 (unreleased)

### Omnibox — the search bar is now the single point of truth
- Fuzzy app search: initials (`gm` → Google Maps) and word-prefix tokens
  (`s ki` → Shaurmas Kitchen), diacritics-insensitive
- Inline calculator: type `2+2` and see `= 4` live; tap or enter to copy
- Dial mode: type a phone number, enter opens the dialer
- Space-prefixed queries search Google on enter; `!bang` queries go to
  DuckDuckGo (fixed: previously pointed at the defunct duck.co)
- App drawer shows daily open counts next to usage time ("1h 23m · 7×")

### Fixed
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
