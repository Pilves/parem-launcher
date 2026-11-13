# Changelog

## [Unreleased]

- Explain when lost access prevents a grayscale restore or an app-limit check.

- Refresh screen time after the device clock moves backwards.
- Close grayscale and quiet notification sheets when their screen is dismissed.
- Show unavailable widgets as removable placeholders instead of opening empty bind dialogs.
- Prevent invisible-only home, folder and website labels from creating inaccessible slots.

- Preserve picker checkbox accessibility after filtering and recycling rows.
- Reject unsafe or invalid settings imports without changing existing preferences.
- Apply Focus, app limits and mindful pause to the default date, clock and Digital Wellbeing launch paths while preserving the camera gesture and other documented exceptions.

- Limit new folder, home-slot and website labels and keep long home labels within two lines.
- Clarify the selected swipe app, reject non-web shortcut schemes, debounce home setup and align picker search.
- Avoid repeating unchanged first-run tip layout work when returning home.
- Show the correct default-home settings row after checking the current role.

- Announce single-choice sheet rows as checked radio buttons without misleading On/Off states.
- Dismiss first-run tips when the drawer or Settings is opened by any route.
- Remove unused settings views and drawer animation resources.
- Keep picker actions visible while scrolling long app lists.
- Show the number of selected apps in folder and Focus picker rows.

- Retry a daily wallpaper after its download or application fails.
- Add 25-minute Focus and manual grayscale quick actions to the home menu and gestures.
- Guide new users with independently dismissed home tips and a short, static drawer search tip.
- Use checked choice sheets for settings, toggle the keyboard in one tap, and show access and app-limit state.
- Organize settings into Calm, Home screen, Search, Gestures, Appearance and Backup and about.
- Correct the quiet-notification access disclosure and snooze-return guidance.

- Make drawer dismissal deliberate and navigation transitions seekable.
- Show current choices consistently in sheets and use one scrolling list in app pickers.
- Keep web searches and links closed during Focus when no allowed receiving app can be identified.
- Close lock consent when its owning screen leaves the foreground.

- Bring hidden notifications back to the shade at once when their app is allowed or quiet notifications are turned off, which no longer asks Keep or Dismiss. Show a new message in the quiet list when its chat was opened or dismissed there, instead of losing it, and keep the quiet list after a restart.

## v6.0.2 — 2026-11-11

- Recover stale app launcher entries in their original profile without duplicate crash reports.
- Find Private Space by searching its name, then tap or press Enter to unlock or open it; respect Android’s hide-when-locked preference.
- Remove an app from the drawer as soon as its uninstall from the drawer finishes, instead of keeping it listed on some phones until a restart.
- Scroll app pickers back up when dragging down in the list, instead of closing the folder, focus mode, quiet list or app limit sheet.

## v6.0.1 — 2026-11-06

- Stop showing search examples in the empty drawer after 20 opens.

## v6.0.0 — 2026-11-06

- Retry locally discovered wireless debugging connect ports when stale entries refuse the connection.
- Exclude Android screensaver activity from screen-time totals and app limits.
- Keep home widgets and app slots clear of the clock without counting the top inset twice.

- Show one translated add-app hint for empty home slots and hide empty slots when the home layout is locked.
- Align the portrait drawer search bar with the app list.
- Add quieter transitions and press feedback throughout onboarding, settings and sheets.

- Keep drawer Enter responsive while results load and keep hidden-app search tips consistent.

- Wait for the current drawer search results before handling Enter.

- Keep picker and hidden-app searches off the web, and limit weather latitude to 84 degrees before rounding.
- Reject malformed settings imports before replacing preferences, erase search history when imported settings turn it off, and restrict saved links to web addresses.


- Keep first-use and default-home prompts clear of landscape widgets.

- Align landscape screen time with the home app column.

- Start the mindful-pause countdown after the sheet begins drawing, so time spent preparing the sheet does not shorten the visible pause.

- Show landscape first-run tips only during first use, without overlapping the default-home prompt.

- Add draft translations of permission disclosures for the shipped locales; native review is required.

- Translate the German capture screens and consent disclosures.

- Use the green Parem icon across launcher and store assets.

- Fixed: tapping the "Swipe down for" value in Settings shows its picker instead of hiding the whole row.
- Android cloud backup no longer copies Parem's settings, usage counts or weather place. Use Export settings to move them; phone-to-phone transfer on Android 12 and later still carries them.
- Daily wallpaper reads its list from Parem's own site instead of the upstream author's gist, and a blank list entry falls back to the built-in picture.
- Weather sends coordinates rounded to the nearest 0.05 degrees, says so in its sheet and credits Open-Meteo. Turning weather off removes the saved temperature and, unless the sunrise and sunset theme uses it, the place.
- Settings links to the privacy policy and the project page.
- Two builds: the Google Play version grants grayscale from a computer only; GitHub and F-Droid builds keep on-phone pairing.
- The Google Play version no longer shows the sideload-only "Allow restricted settings" hint.
- Contact search explains what it reads before Android asks for the contacts permission.
- Quiet notifications never hides Android Settings, system or error notifications. Its disclosure button and the per-app grayscale prompt now ask you to agree.
- Limit warnings and the mindful pause put Go back first, with Open dimmed below it. Ending focus early asks through the same countdown. Any installed app can get a limit from Add another app, and Pause only pauses an app with no time limit.
- Durations follow the phone's language. Left and Right mean what you see under right-to-left languages. Arabic and Persian digits work in search. App names stay readable inside right-to-left text.
- Folders can be edited, and a folder made from an occupied slot starts with what the slot holds. Same-name widgets show their size in the picker, and debug builds are labelled Parem Debug.
- Search tips say what Enter will do, and tapping a call or web tip does it. Errors read in plain words, including a failed city search. Contact search points to app settings once Android stops asking. Swipes are turned off by choosing None instead of a hidden long-press, and more prompts are sheets.
- Guard against delayed touch delivery turning a swipe into a home long-press; a synthetic delayed-touch test passes, while the cause of the observed drawer failures remains open.
- Privacy wording in usage access, crash reports, the lock service, device admin and quiet notifications now says exactly what is read, kept and sent.

## v6.0.0-beta.2 — 2026-10-16

- App-picker search keeps the first matching rows above the keyboard, including in Focus mode.
- Landscape Settings now includes App limits, Focus mode, Grayscale and quiet-notification controls.

- Drawer suggestions stay below the search field instead of overlapping typed text, including with large fonts.

- App limits and Focus mode use the available tablet window width while retaining sheet width limits.

- Widgets on Android 12 and later receive their actual size list instead of legacy minimum and maximum dimensions alone.

- Settings, drawer and sheets use a warm monochrome palette with moss-teal actions, clearer type and tablet sizing; home keeps monochrome wallpaper text. E-ink stays monochrome.
- Wallpaper dimming can be controlled independently of daily wallpaper.
- Remove the inactive legacy lock toggle from landscape settings; the working lock controls are unchanged.

- Remove the unfinished per-slot swipe-up binding; swiping up on any home slot opens all apps. Existing backup values remain transferable.

- Preserve home slot names when an installed-app lookup fails; record recovered failures in opt-in crash diagnostics.
- Settings has a "Hidden apps" row showing how many apps are hidden; tapping it opens the list (the title tap still works). Hiding an app keeps you in the drawer with a short confirmation instead of a Settings dialog, and a search that matches only a hidden app offers "Not in the list? Check hidden apps" (never naming the app, and not while Private Space is locked).
- Battery: the per-app grayscale watch ticks every 5 s instead of every second while the screen is off, home re-renders on battery changes only when the battery % is shown, and the quiet-notification filter no longer parses its allowlist for notifications it never hides
- Fixed: the empty-home long-press menu opens fully expanded in landscape on phone and tablet, so Settings is reachable without dragging the sheet up.
- Adding a widget no longer hides home apps silently: a widget that would push an app off screen is refused with a message (like the apps-count picker), any apps the fitting pass still has to hide are named in a message, and the home column re-fits when the system-bar insets change so no slot ends under the gesture bar.
- Smaller release APK and a lighter cold start: WorkManager no longer initialises on every launch, build-tool leftovers are no longer packaged.

## v6.0.0-beta.1 — 2026-10-16

- Fixed: with a fixed Dark or Light theme that disagreed with the system dark mode, home could render invisible text and rebuild itself in a loop (only the wallpaper visible); the text-size step no longer overrides the activity's theme configuration, and the theme-check retry cap now survives recreate()
- TalkBack reads every settings row once as label, role and state, section titles are headings, and settings rows, sheet rows, limit rows and the info icon are at least 48dp; the drawer search field says what it is, "No apps found" is announced, app-limit rows name their app with usage and limit, the focus picker says why a sixth app will not tick, and the mindful-pause "Open" row is announced when it becomes available.
- Every launch route now runs the same gate: double-tap "Open app", opening a notification from the quiet list, website shortcuts, omnibox web search / "!bang" / no-results search and the search gestures all honour Focus mode, app limits and the mindful pause (web opens are gated on the browser that receives them; under Focus with no default browser, the first allowed browser opens instead of the chooser).
- Fixed: with the System default theme, toggling system dark mode restyles the launcher at once; the hidden-apps list, a pending gesture-letter assignment and the screen-time graph now survive process death and rotation.
- Fixed: the Focus "allowed apps" picker now lists hidden apps and unlocked Private Space apps, and saving keeps allowed apps the picker could not show instead of silently blocking them.
- Fixed: the weekly review weighs today by the hours elapsed, so an unchanged habit no longer reads as "down 10%" every morning.
- Fixed: today's screen time (and app limits) reset at midnight instead of up to a minute later; "1,000 usd in eur" converts a thousand, not one.
- Fixed: hidden apps from very old backups stay hidden after import; lock-service and grayscale device state is cleared after an Android backup restore or device transfer.
- Gesture letters match resampled shapes, tolerating drawing size, speed, modest slant and reversed strokes; ambiguous marks and straight swipes are rejected.
- App actions use consistent labelled sheets on home and in the app list; home keeps its replacement actions, removal offers Undo, and a locked layout offers Unlock. Renames stay local to home or the personal app list, and package-only controls no longer affect work-profile copies.
- Onboarding offers optional explanations for home, search, gestures, Calm tools, appearance and backup; setup states and spoken captions now explain what is enabled.

- Usage access is named as the permission it is: setting an app limit without it asks for it instead of saving a limit that never fires, the App limits row reads "Needs access" when it is missing, and the Focus and Grayscale rows say "Scheduled" / "Automatic" instead of "Off" while a schedule or trigger is armed

- Text follows the Android font size (up to 200%); the in-app text size is now
  a relative step on top of it (Smaller / Default / Large / Larger) instead of
  replacing the system setting

- Fixed: the home screen's date and battery % now refresh while it stays
  visible (midnight rollover, timezone change) instead of only on resume

- Screen-time graph is accessible: TalkBack reads each day as its own item,
  keyboard D-pad/Enter selects a day, and secondary text (graph labels, sheet
  captions, onboarding caption) is darkened to 60% for 4.5:1 contrast in the
  light theme
- Edge-to-edge done properly for Android 16: home, the app drawer, Settings,
  onboarding and every bottom sheet pad by the real status bar, navigation bar
  and display cutout instead of fixed guesses, the keyboard no longer covers
  drawer results, and Back outside home is delegated to Navigation for the
  upcoming predictive-back transitions
- Fixed: rotating the tablet or losing the app in the background while a
  widget's permission or setup screen is open no longer loses the widget;
  a widget the system invalidated (reboot, app update) is asked for again
  next time instead of vanishing, keeps its place and height, and leftover
  widget IDs are released
- New setting "Open single match automatically" (on by default) turns off the
  drawer's single-match auto-launch; it is also off while TalkBack or another
  screen reader is on, so Enter or a tap always launches

- Typing a number, a sum, a unit or currency conversion or a phone number in
  the drawer no longer auto-launches the one app that happens to match it
- Swipes and taps feel the same on every phone and tablet: swipe thresholds
  now scale with the device's touch settings, a shaky or long tap no longer
  turns into a swipe, pressed labels dim on touch and clear once a swipe
  starts, and haptics (long-press, letter recognised or rejected, locked
  layout) follow the system touch-feedback setting

- TalkBack, Switch Access and Voice Access users can leave home: the home
  screen and every app row offer All apps, Settings, Notifications, Add widget
  and the configured swipe apps as actions, an "All apps" row appears under
  the apps while TalkBack is on, and TalkBack no longer stops on the hidden
  lock helper (where a double tap locked the phone). Screen time, the
  settings info icon and the drawer's work-profile dot now have spoken labels

- Onboarding stays until you finish or skip it, instead of never coming back
  if Parem was closed or restarted partway through

- Pressing Home with a Settings sheet open closes the sheet instead of
  leaving it over the home screen, where using it could crash Parem; a fast
  double-tap on a Settings row that opens a list no longer crashes either

- Rotating the screen with Settings open no longer crashes Parem (which
  could make Android drop it as the home app)

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
