# Changelog

## [Unreleased]

- Release APKs no longer carry Google's encrypted dependency-info block, a
  step towards F-Droid

- Quiet notifications use less battery: notifications that are never hidden
  (ongoing, calls, media, allowed apps) skip the system lookups

- New first-run screens: Parem asks to become your home screen only when you
  tap "Set as home screen" (a second tap opens the system's home-app settings
  if the dialog doesn't come back), then offers optional Calm setup: screen
  time, slowing down an app, quiet notifications, double tap to lock and
  grayscale, each skippable. Shown once to updating users too. The feature tour
  is gone, and granting home no longer bounces you into Default apps settings
- Per-app grayscale: long-press an app and tap Grayscale to see it in
  grayscale. The screen turns grey while that app is in front, including after
  recents and notification switches, and color comes back on Home or in any
  other app. Needs the grayscale permission, usage access and Parem as default
  home; the menu walks through each one
- Tablets, foldables and wide windows: Parem rotates freely from 600dp up
  (phones stay portrait), the portrait lock is released when a foldable
  unfolds, and settings and the app drawer keep a readable width instead of
  stretching edge to edge
- Lock without accessibility: when Android turns Parem's accessibility service
  off (e.g. Advanced Protection), the lock gesture can fall back to device
  admin. Opt-in from the lock explanation or Settings › Gestures; the tradeoff
  is stated first: every unlock after such a lock needs your PIN, not your
  fingerprint. Turn it off from the same Gestures row
- Quick actions in search: type "alarm 7:30", "timer 10m", "event friday 3pm
  dentist" or "remind me 5pm call mum" (Estonian too: "äratus", "taimer",
  "kohtumine", "tuleta meelde") and press Enter or tap the line to set it in your
  clock app or open your calendar prefilled. No new permission. While you type
  one, an app whose name starts the same way doesn't auto-launch; the empty
  search field now shows a different example of what it can do each time
- App shortcuts in search: with Parem as the default home app, typing a
  shortcut's name (e.g. "liked" for Spotify's Liked Songs, "incog" for Chrome's
  New Incognito tab) lists it under the app results; tap or Enter opens it,
  through the same limits, pause and focus rules as the app
- Settings search: type a setting's name in the drawer (e.g. "keyboard") and
  tap the "Settings ›" line to open Settings at that row; Enter opens it too
  when no app matches
- Search history (Settings > Home Screen, off by default): when on, the empty
  drawer lists your last five drawer launches and searches; tap one to open it
  again. It stays on this phone (not in the settings export or cloud backup),
  Clear wipes it, and turning it off deletes it
- Crash reports (Settings, off by default): when on, a crash is saved on the
  phone and offered on the next start; you send it yourself through an app you
  pick, or discard it. Nothing is collected or sent automatically
- Parem now needs Android 10 or newer (was Android 7.0)
- Grayscale (Settings > Digital Wellbeing): turn the screen grey now, during
  focus, or after opening an app past its limit until you come back to Parem.
  Needs a one-time permission: on Android 11+ Parem pairs with Wireless
  debugging on the phone itself (type the code into its notification); with a
  computer, a WebUSB page or one adb command does it. Parem restores your own
  color-correction setting exactly and backs off if you change it yourself
- Hide from shade, keep for later (off by default): pick the apps
  allowed to alert; notifications from every other app leave the shade and wait
  in a quiet list you open with a swipe gesture. Calls, alarms, media,
  navigation and emergency alerts are never hidden, and hidden items come back
  on their own within 8 hours. Needs notification access; no notification
  content is stored or sent anywhere
- Private space (Android 15+): with Parem as the default home app, private
  space apps appear in their own section at the bottom of the drawer, with a
  header to lock and unlock the space. Locked apps are not shown or searchable,
  and private apps cannot be put on home slots, swipes, gestures or folders
- Website shortcuts: long-press a home slot (or use "Add website…" when creating
  a folder) to put a website there; it opens in your browser, or in an
  installed web app that claims the address, and survives settings export/import
- Scheduled focus: the Focus mode sheet can now turn focus on by itself in
  weekly windows (for example work 09:00–17:00 Mon–Fri, sleep 22:00–07:00),
  with the same allowed apps. Disable pauses it until the current window ends;
  a blocked app's message says when the schedule ends and where to pause it
- When Android turns off Parem's lock service after it has worked (an update,
  a crash or a security setting), the lock gesture now explains that once
  instead of doing nothing; later taps show a short note until it is back on
- The screen-time sheet now compares this week with last week: daily average
  up or down against last week, and the app that rose and fell the most.
  Android keeps a limited usage history (often about ten days), so last week
  can be compared over only the days still on record (the sheet says how many)
- Mindful pause: for an app with a time limit, turn on "mindful pause" (home
  slot long-press or the App limits sheet) and every launch of it — home slot,
  folder, drawer, search, gesture letter, swipe app — first asks why you are
  opening it and waits 5 seconds; going back returns home. App limits now also
  apply on those routes, not just home slots and the drawer
- E-ink phones (Boox/Onyx, Hisense A-series, Mudita and other e-ink brands)
  are now recognised and get no screen transitions, drawer or sheet animations;
  light theme is only the first-run default there, so a dark-theme choice sticks.
  With any system animation scale at 0, bottom sheets also open without sliding
- Parem no longer asks to see every installed package (`QUERY_ALL_PACKAGES`);
  it declares only the app kinds it uses: launchable apps, launchers, icon
  packs and widget providers
- Target Android 16 (API 36).
- The widget picker lists work-profile widgets (under the badged app name) and,
  on Android 15+, shows a live preview for widgets that publish one
- Lock screen gestures (double tap, swipe) now show a clear disclosure of what
  the accessibility service sees and ask for consent before opening
  Accessibility settings, from Settings and from the home screen; declining
  leaves the lock off, and the service no longer reads screen content
- Fixed: with system animations turned off, the app drawer (and other
  screens) could stay stuck on screen instead of closing (from Olauncher #713)
- Fixed: phones with adaptive refresh rate (LTPO) screens could be mistaken
  for e-ink displays when idle, forcing the light theme and turning off the
  drawer animation (from Olauncher #724)
- New "Bold font" toggle under Appearance swaps the light typeface for a
  heavier one on home, drawer and settings text (from Olauncher)
- The drawer search converts currencies ("10 eur in usd") using the European
  Central Bank's daily reference rates, showing the rates' date. Typing a
  currency conversion may make one anonymous download a day of the public
  rates from www.ecb.europa.eu; nothing you type leaves the phone
- New "Lock home layout" toggle under Home Screen: while on, long-pressing a
  home app or folder no longer opens the change/folder/limit/delete menu, so
  the layout can't be edited by accident (from Olauncher #726)
- Keyboard and D-pad: home apps, clock and drawer rows can be reached with the
  arrow keys and opened with Enter (long-press Enter for the menu); typing a
  letter or digit on the home screen opens the drawer search with it, and the
  Menu key opens the drawer (from Olauncher)

## v5.7.0

- The number-of-apps picker now knows how much room the home screen actually
  has: counts that don't fit next to your widgets are dimmed and rejected
  with a hint, instead of apps silently disappearing behind a widget
- Landscape widgets now start straight below the clock (cleared dynamically
  from the clock's real height) instead of centering beside the app list
- Fixed: tall bottom sheets (screen time, app limits, folder creation) now
  scroll and open fully expanded on landscape/short screens instead of
  clipping or peeking half-hidden
- Fixed: gesture letters now work in tablet landscape — the landscape home
  layout never had the drawing overlay (PAREM-120)
- Removing an app limit now shows "No limit" immediately, matching the
  reopened dialog
- Fixed: imported app limits now apply immediately — a stale in-memory cache
  survived the post-import restart and kept enforcing the old limits
- Fixed: typing in the drawer could stutter with contact search enabled on
  large address books (contact match keys are now computed once at load)
- Fixed: a rare mis-sort or crash when the app list was loaded from two
  places at once (shared collator was not thread-safe)
- Fixed: the app-limits dialog no longer opens empty when tapped right
  after opening settings
- Fixed: importing exported settings did nothing — leaving the launcher for
  the file picker (or any settings-launched dialog) popped the settings
  screen behind it, so the picked file's result had nowhere to return to.
  Pressing home still resets to the home screen
- Omnibox: opt-in contact search. Turn on "Contact search" under Home Screen
  settings (grants Contacts access when you enable it) and the drawer surfaces
  a matching contact to call — apps still list first. Off by default; the
  toggle is the only thing that ever asks for the permission (PAREM-104)
- On tablets in landscape, widgets now sit in their own column to the right
  of the home apps instead of stacking above/below them; portrait and phone
  layouts are unchanged
- Removed the inherited 4-hour self-recreate + cache wipe (PAREM-108
  phase 3) — the launcher no longer restarts itself periodically; the
  theme-mismatch recreate stays
- Redesigned bottom sheets: rounded corners, drag handles, tap ripple, and
  one consistent style across the screen-time, focus-mode, app-limit,
  folder, and widget dialogs (checkboxes/radios now match the mono theme)
- The screen-time sheet now shows the week's most-used apps (icon, name,
  weekly total) under a cleaner 7-day graph that highlights today and
  keeps zero-usage days visible, plus a daily average; tapping a day's
  bar switches the list to that day's top apps (tap again for the week)
- The app list is now re-queried from the system only after a package
  change (install/uninstall/update/profile toggle) instead of on every
  drawer open, and returning to the home screen no longer runs per-slot
  installed-app system calls — PAREM-117, no user-visible change
- The folder-creation and focus-whitelist app pickers have a search field:
  typing filters the list live (same matching as the drawer omnibox),
  clearing it shows all apps again — PAREM-114
- App icons (drawer rows, home-screen slots) are now decoded once and
  reused instead of being re-decoded on every list bind and every return
  to home — PAREM-116, no user-visible change
- Today's usage-stats scans (home-screen total, drawer per-app times,
  app-limit checks) now share one short-lived cache instead of running
  three separate event-log scans — PAREM-115, no user-visible change
- The 7-day screen-time graph now caches completed days for the session
  and re-scans only today on open (PAREM-115, no user-visible change)
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
