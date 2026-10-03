# Release checklist

The device pass Patric runs before tagging `v*`. Agents prepare the release;
nobody tags until every box below is ticked on a real device. Copy this file's
checklists into the release PR or issue and tick them there — don't edit this
file to record a pass.

A failure becomes a roadmap row (`M<n>-WP<k>`, one per finding) and blocks the
tag until it is fixed and the affected section is re-run.

## Setup

- [ ] Release-candidate APK installed over the previous release (not a fresh
      install), so prefs migration is exercised
- [ ] Parem is the default launcher; accessibility service enabled
- [ ] At least one folder, two widgets and one app limit already configured
- [ ] Phone in portrait; tablet (or a large-screen device) at hand for the
      landscape sections

## Core — every release

### 1. Home gestures

- [ ] Swipe up opens the drawer
- [ ] Swipe down runs its configured action
- [ ] Swipe left / right launch their apps; disabled ones do nothing
- [ ] Long press on empty home: haptic tick, menu with Add widget and Settings
- [ ] Draw each mapped gesture letter — the right app launches; a fast letter
      does not also fire a swipe
- [ ] Flashlight action toggles the torch, and stays in sync after toggling it
      from quick settings

### 2. Double-tap lock (trap #1)

- [ ] Double tap locks the screen (Android 9+, accessibility service on)
- [ ] With the service off, double tap falls back to device-admin lock, or
      toasts and opens settings — never fails silently
- [ ] Other double-tap actions (app, none) do what they say

### 3. Slots, menus, folders

- [ ] Tap each filled slot — the app launches
- [ ] Long press a slot — change app, create folder, set/remove time limit
      and delete all work
- [ ] Open a folder, launch from it, edit it, delete it
- [ ] A slot with an exhausted app limit shows the limit warning, not the app

### 4. Clock, date, screen time

- [ ] Tap clock → clock app; long press → pick a different clock app
- [ ] Tap date → calendar app; long press → pick a different calendar app
- [ ] Tap the screen-time row → screen-time view; numbers look plausible
- [ ] Weather shows, dims when stale (3–24 h), hides past 24 h

### 5. Widgets — portrait and landscape

- [ ] Add a widget (bind + configure screens complete and return home)
- [ ] Resize it; reorder two widgets; remove one
- [ ] Rotate: widgets survive, landscape widgets start just below the clock
- [ ] Reboot or force-stop: widgets come back (no blank boxes)
- [ ] Home apps that don't fit beside widgets are never silently hidden

### 6. Capacity-capped apps picker

- [ ] Settings → number of home apps: counts that don't fit beside current
      widgets are dimmed and rejected with a hint
- [ ] Remove a widget — a previously rejected count becomes selectable

### 7. Settings walk

- [ ] Open every settings card and toggle each row once; nothing crashes,
      every change shows on home without a restart
- [ ] Theme switch / scheduled theme applies without a restart loop
- [ ] Back from settings returns home; Home button from any sub-screen resets
      to home

### 8. Export / import

- [ ] Export settings to a file
- [ ] Change slots, gestures and an app limit; import the file
- [ ] Everything restores, including app limits — enforced immediately,
      without reopening settings

### 9. Contact search

- [ ] Toggle on → permission prompt; grant → a typed contact name shows a
      call row below the apps
- [ ] Toggle on, deny → toggle stays off, no contact rows, no re-prompt
- [ ] Revoke Contacts in system settings → toggle shows off on next visit
- [ ] Toggle off → no contact rows; no other screen ever asks for Contacts

### 10. Tablet landscape

- [ ] Widgets sit in the right-hand column, apps on the left
- [ ] Gesture letters work in landscape
- [ ] Rotating back to portrait restores the stacked layout

### 11. Sheet scrolling

- [ ] Tall sheets (screen time, app limits, focus mode, folder creation) open
      fully expanded and scroll on a short/landscape screen
- [ ] Long-press menus in the drawer and on slots reach their last row

## This release touched

Rewrite this section for each release from `[Unreleased]` in CHANGELOG.md and
the roadmap rows that landed: one line per change, saying what to try on the
device. Delete it again once the tag is pushed.

**v5.7.0 (candidate `f4b55cf`)** — the home screen was split into
`ui/home/` controllers, so sections 1–5 are the new risk; run them twice
(portrait, then landscape). Also:

- [ ] Capacity picker (section 6) — new in this release
- [ ] Landscape widgets below the clock; tall sheets scroll (sections 5, 11)
- [ ] Gesture letters in tablet landscape (PAREM-120)
- [ ] Removing an app limit shows "No limit" at once
- [ ] Import applies app limits at once; import file picker returns to settings
- [ ] Drawer typing with contact search on and a large address book is smooth
- [ ] No periodic self-restart over a few hours of use (PAREM-108)

## Sign-off

- [ ] Every box above ticked on: device / Android version ___
- [ ] Tablet pass on: device / Android version ___
- [ ] CHANGELOG `[Unreleased]` renamed, fastlane changelog added, version
      bumped — then tag `v*` and push
