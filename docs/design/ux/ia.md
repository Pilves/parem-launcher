# Information architecture and onboarding for 6.0

Specialist report: information architecture and onboarding. Source: `origin/next` at `649fbc9`, read 2026-10-03.

**Inputs.** Layouts, strings and Kotlin on `origin/next`, plus `docs/ROADMAP.md` (M4), `docs/design/DECISIONS.md` and the M4 designs. For screenshots, only the existing QA set was available (`01`, `02`, `bold-off-settings`). The UX capture was blocked because the tablet was locked, so this report has **no fresh screenshots** of any screen. Every claim below comes from the code unless it cites a QA shot.

**Out of scope.** M4-WP22..30 are already filed and are not repeated here. Where an issue here touches one of them, the overlap is named.

**Constraints followed.** Native Views and ViewBinding only, and `ui/BottomSheetMenu` for every sheet. No tour (DECISIONS). The rotating drawer hint belongs to M4-WP15. Trap #1 is untouched: nothing below renames `lock_layout_description` or moves `@id/lock`.

---

## 1. Inventory: every setting and surface today

### Settings screen (`fragment_settings.xml`, cards in `ui/settings/`)

| Card (title string) | Rows | Interaction style |
|---|---|---|
| Header (`app_name`, no section title) | **tap the title = Hidden apps** (invisible affordance); info icon; Set as / Change default launcher; About & FAQs (hidden: URL empty); Export · Import (side by side); Crash reports | mixed |
| Home screen (`home_screen`) | Apps on home screen (inline 0–8 strip); Show date time (inline On/Off/Date only); App alignment (inline horizontal scroller incl. "Bottom: On/Off"); Lock home layout; Show icons; **Sort apps by usage**; **Contact search**; **Search history** (+ inline Clear); Widget placement | inline expanders + toggles |
| Appearance (`appearance`) | Theme mode (sheet + schedule); Text size (inline 1–7); Bold font; Daily new wallpaper; Notification bar; Weather (dialog) | mixed |
| Gestures (`gestures`) | Swipe left action; Swipe right action; Swipe down for (hidden, inline); Double tap action (sheet); **Auto show keyboard**; Gesture letters (dialog) | sheets + toggles |
| Digital Wellbeing (`wellbeing`) | Screen time (usage access); App limits (sheet); Focus mode (sheet incl. schedule); Grayscale (grant sheet / toggles sheet); "Hide from shade, keep for later" (+ inline Allowed apps, Make silent, hint paragraph) | sheets, some inline |
| Footer | Github; Privacy (hidden: URL empty) | links |

That is about 35 visible rows in 5 cards. Still to come on the settings screen: M4-WP18 "Lock method" row (Gestures). The roadmap's other ~10 6.0 options mostly live in sheets or app menus, not as settings rows. Examples: per-app grayscale (WP21) in the app menu, and mindful pause in the limit sheets.

### Other surfaces

| Surface | Entry | Contents |
|---|---|---|
| Home long-press | long-press empty home (the only way into Settings besides omnibox search) | `dialog_home_menu.xml`: Add widget, Settings (hand-built sheet, not `BottomSheetMenu`) |
| Home slot menu | long-press slot | `BottomSheetMenu`: Select/Change app, Add website…, Create folder, Set/Remove time limit (+ mindful pause when limited), Delete |
| Drawer row menu | long-press row | **inline row of text buttons** in `adapter_app_drawer.xml`: Delete, Set time limit, Rename, Hide, Info, Close |
| Omnibox modes | type in drawer | app match + auto-launch; calc; unit; currency (ECB); dial; contact (opt-in); web/!bang; settings (`setting_hint`, WP17); app shortcuts under app rows (WP14); recent history on empty query (WP16, opt-in). Landing: quick actions (WP15) |
| Gestures | home | swipe up = drawer; swipe left/right/down + double tap = configurable action (app, notifications, search, lock, camera, torch, quiet list); gesture letters; clock/date taps |
| Wellbeing sheets | settings / launch gate | screen-time graph + weekly review; app limits; mindful pause countdown; focus (timed + scheduled windows); grayscale grant + toggles; quiet list |
| Legacy dialogs | `MainActivity.showDialog` | HIDDEN ("tap 'Parem Launcher' at the top…"), KEYBOARD ("Can we request you to keep this On…"), DIGITAL_WELLBEING (usage access) |
| Hints | — | landscape-only `firstRunTips` (WP28); drawer `appDrawerTip` while `firstOpen`; queryHint `___` |

---

## 2. Proposed settings structure

### Principles
1. **Each card does one job, and the card's name says what that job is.** A user should guess the right card on the first try. Today "Contact search" sits under Home screen and "Auto show keyboard" sits under Gestures, so those guesses fail.
2. **A card has at most 7 rows.** Anything past a row's single value goes into the sheet that row opens. This is the existing pattern for Focus, Grayscale and App limits, now applied everywhere.
3. **Every row's value column uses one of four values.** Binary rows show `On`/`Off`. Multi-choice rows show the current choice. A row whose feature is not set up yet shows `Set up`. A row that lost a permission shows `Needs access` in `primaryColor`; the other three use the regular value style. These states already exist (`grayscale_set_up`, `quiet_needs_access`), so this rule just applies them everywhere.
4. **Binary rows toggle on tap. Rows with more than two choices open a `BottomSheetMenu`.** Inline expanding strips go away (see issue 10).
5. **The 6.0 story comes first.** Calm features sit at the top. Backup and About sit at the bottom.

### Structure (top to bottom)

```
Parem                                   (i)
  Set as home  ← only while not default, primary colour

Calm                      (was "Digital Wellbeing")
  Screen time                    On / Set up
  App limits                     3 apps        → sheet (limits + mindful pause per app)
  Focus                          Off / Until 17:00 / Scheduled → sheet (timed, schedule, allowed)
  Grayscale                      Off / Set up  → sheet (now, during focus, after limit)
  Quiet notifications            On / Needs access → sheet (allowed apps, make silent, gesture)

Home screen
  Apps on home screen            4             → sheet
  Clock and date                 Clock and date / Date only / Off → sheet
  Weather                        Tallinn / Off → dialog          (moved from Appearance)
  Alignment                      Left          → sheet
  Widgets                        Above apps    → sheet
  Icons                          Off
  Lock layout                    Off

Search                     (new; rows moved, no new prefs)
  Open keyboard automatically    On
  Sort by usage                  Off           (shown only with usage access, as today)
  Contacts                       Off
  Search history                 Off / On      → sheet (on/off, clear)
  Hidden apps                    2             → hidden-apps drawer (today: tap the title)

Gestures
  Swipe left                     Camera
  Swipe right                    App: Spotify
  Swipe down                     Notifications
  Double tap                     Lock screen / Lock screen · needs access
  Draw letters                   Off           → dialog
  Lock method                    Accessibility / Device admin   (WP18; shown only when a lock gesture is set)

Appearance
  Theme                          Dark / Auto   → sheet (+ schedule)
  Text size                      4             → sheet
  Bold text                      Off
  Status bar                     Off
  Daily wallpaper                Off

Backup and about           (was the header card + footer)
  Export settings
  Import settings
  Crash reports                  Off
  Change default home app
  About and FAQs · Privacy · GitHub   (as available)
```

Row counts per card: 5, 7, 5, 6, 5, 5+links. Total visible rows fall from ~35 to 33, even with the WP18 row and an explicit Hidden apps row added. Removing the inline sub-rows (quiet list ×3, search-history Clear, 4 inline pickers) is what pays for them.

### Why this is cheap to build
ViewBinding IDs are flat, so moving a row's XML into another card's `LinearLayout` needs **no Kotlin change**. `binding.contactSearchToggle` stays `binding.contactSearchToggle`. `SettingsSearchIndex` maps titles to view IDs, so it keeps working, and `SettingsSearchIndexTest` keeps guarding it. The work is:
- XML moves in `layout/` and `layout-land/fragment_settings.xml`
- one new section-title string (`search`, plus a renamed `wellbeing` value)
- the Hidden apps row
- the copy changes in issue 11

Card classes can stay as they are: a `GesturesSettingsCard` that binds a row now shown under "Search" is still correct. Splitting the classes to mirror the cards is optional and can come later.

**Aside (flagged, not fixed).** Every IA change today means editing two ~1,470-line layouts that must stay in sync. Extracting each card into `layout/settings_card_<name>.xml` and `<include>`-ing it from both orientations would halve that cost. This overlaps with M4-WP13 (adaptive layouts), which should decide it.

---

## 3. Discoverability without a tour

DECISIONS rules out a tour and a "Tips" link, and gives the rotating drawer hint to WP15. What's left is **teach at the place, at the moment, once**:

| What must be learned | Where it is taught | Mechanism (native) | Disappears when |
|---|---|---|---|
| Swipe up opens apps | home, portrait and landscape | `@id/firstRunTips` TextView, already in land, added to portrait `fragment_home.xml` | first swipe up (pref `tipSwipeUpDone`) |
| Long-press opens Settings / widgets | same TextView, second line | same | first home long-press (pref `tipLongPressDone`) |
| Empty slots can be filled | each empty slot | slot text = dimmed `long_press_to_select_app` instead of "App" (QA `02` shows four literal "App" rows) | slot filled |
| Typing launches apps | drawer | `appDrawerTip` for the first 5 drawer opens (counter), not tied to `firstOpen` | counter reaches 5 or first auto-launch |
| Omnibox powers: calc, units, currency, settings, shortcuts, quick actions, `!bang` | empty drawer query hint | WP15's rotating hint, one `string-array` that is filtered by what is enabled (e.g. no contact example while contacts are off) | never (it is the hint) |
| Mindful pause exists | after the first limit is set | the limit sheet already re-opens with `addMindfulPauseToggle`; add one `BottomSheetMenu.message` line the first time | first pause toggled or 3 limit edits |
| Quiet list needs a gesture | at the end of the filter turn-on flow | inline step, see issue 7 | gesture chosen or "Not now" |
| Lock gesture broken | Gestures row value + WP8 sheet | `Needs access` value (issue 12) | service back on |
| Settings can be searched | drawer hint rotation entry "type a setting, e.g. grayscale" | WP15 array | — |

The rule for every tip: it disappears once the user has **done the thing**, not when they open some unrelated screen. Today `firstOpen` is cleared by opening Settings from the home menu, which also hides the drawer tip.

---

## 4. Ideal first-run path (amends M4-WP12)

The two-screen design (option B) is right. I'd change four things.

**Screen 1 — Make Parem your home.** Keep it as designed. Add one step with no screen: when screen 1 succeeds, **seed empty home slots once** from the default handler of four intents, found with `PackageManager.resolveActivity(…, MATCH_DEFAULT_ONLY)`:
- `ACTION_DIAL` for the phone app
- `CATEGORY_APP_MESSAGING` for messages
- `MediaStore.ACTION_IMAGE_CAPTURE` for the camera
- `ACTION_VIEW https:` for the browser

`RoleManager.getRoleHolders` is a system API, so it can't be used here.

That means 4 apps, using the existing `prefs.setHomeApp*`, done only when every slot is empty and only on fresh installs (not upgraders). Then the home screen a new user lands on is usable at once, instead of showing four "App" rows (QA `02`). If Patric rejects seeding, the fallback is the dimmed "Long press to select app" text.

**Screen 2 — Calm phone (optional).** Order rows by dependency and effort, and use the *same row layout and copy as the Calm settings card*. Then the user meets the same words later in Settings.

| # | Row | Why here | Calls |
|---|---|---|---|
| 1 | Screen time | prerequisite for limits, mindful pause, weekly review, per-app grayscale, sort by usage | existing DIGITAL_WELLBEING → `ACTION_USAGE_ACCESS_SETTINGS` |
| 2 | Slow down an app | WP12 lists "Mindful pause → habit-app picker", but no such picker exists: `addMindfulPauseToggle` only shows when the app already has a limit. One row: pick app (drawer picker flag) → limit sheet → pause on | `BadHabitDialogs.showTimeLimitPicker` + `AppLimitManager.setPause` |
| 3 | Quiet notifications | turn-on flow plus the gesture step from issue 7 | `QuietListSheet` turn-on |
| 4 | Double tap to lock | consent sheet | `showLockConsent` |
| 5 | Grayscale — "one-time setup, about 2 min" | slowest step (developer options), so it goes last and is clearly marked | WP5 grant sheet |

Footer line: "All of this is in Settings › Calm." This replaces the tour's job of saying where things live. Upgraders (version 2 → 3) get screen 2 once, titled "New in 6.0". Use `content_side_padding` (DECISIONS) so the screen is not a narrow column on tablets.

**No pages after screen 2.** Gestures are taught by the home tips in section 3, and omnibox powers by the WP15 hint.

---

## 5. Top issues (highest impact first)

1. **Calm features are buried at the bottom, under a name that collides with Google's own "Digital Wellbeing" app.** Rename the card to "Calm" and move it to the top. Add a Search card, move Backup and About to the bottom, and move Weather to Home screen. All of these are XML moves (section 2). The hard-coded "Settings › Digital Wellbeing › Focus mode" strings must change at the same time.
2. **Hidden apps are reachable only by tapping the "Parem Launcher" title.** Worse, the first time an app is hidden, the user is pulled out of the drawer into Settings and shown a dialog that points at that invisible tap target and at an About page that is hidden (`URL_ABOUT_PAREM` is empty). Fix: an explicit "Hidden apps · N" row, and stay in the drawer with a toast.
3. **Portrait home teaches nothing.** The tour is being dropped and WP23 dismisses it anyway, and portrait has no `firstRunTips` at all. Nothing tells a new user that swipe up or long-press do anything, and long-press is the only way into Settings.
4. **A fresh home screen shows four literal "App" rows** (QA `02`). Seed the slots from role holders on first run. As a fallback, show a dimmed "Long press to select app".
5. **WP12's Calm screen has a dependency gap.** The mindful pause row assumes a habit-app picker that does not exist: the pause needs a limit, and a limit needs usage access. Reorder the rows to screen time → slow down an app → quiet → lock → grayscale, and reuse the settings row style.
6. **Quiet notifications span two cards.** The turn-on flow tells the user to choose a gesture in Settings › Gestures, where the option was hidden until now. Add the gesture choice to the turn-on flow. Fold the 3 inline sub-rows and the hint paragraph into the sheet the row opens.
7. **The drawer long-press menu is an inline row of 6 text buttons.** Mindful pause is missing from it, and WP21 adds a Grayscale entry it has no room for. Move it to a shared `BottomSheetMenu` used by both the drawer and home slots. This removes the root cause of WP30's "Close overlaps".
8. **Turning off "Auto show keyboard" takes two taps plus a guilt dialog** ("Can we request you to keep this On for a few more days"). That is a dark pattern in a product sold as calm. Delete the dialog.
9. **Choosing a value works three different ways** (inline strips, sheets, toggles). Standardise: binary rows toggle, rows with more choices open a `BottomSheetMenu` with the current value marked. This deletes about 250 lines from each settings layout.
10. **Labels are inconsistent** ("Show date time", "Notification bar", "Hide from shade, keep for later"), and settings paths are hard-coded, with › and > both used. Build paths from the section title strings, and add search synonyms.
11. **The drawer tips are tied to `firstOpen`**, which clears when the user opens Settings. Use a counter for the first 5 drawer opens, and make WP15's hint array the single place omnibox features are discovered, filtered to what is enabled.
12. **The lock gesture rows say "Lock screen" even when the accessibility service is off.** The user finds out only on a failed double-tap. Show "Lock screen · needs access" as the row value, linked to WP18's Lock method row.

## Not verified
- No 6.0 screen on `next` was seen on a device; the capture was blocked. In particular, how the onboarding, Calm sheets and drawer menu look is inferred from XML and Kotlin.
- Seeding slots: `resolveActivity` returns the system chooser (`ResolverActivity`) when no default is set. That case must be skipped. It also depends on package visibility (check the manifest's `<queries>`/`QUERY_ALL_PACKAGES`). Needs a device check.
- That `binding` IDs stay identical when rows move between cards was checked against the ViewBinding model, not built.
