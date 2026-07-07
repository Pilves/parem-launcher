# TODO — post v5.5.1

Ticket backlog for the next cycle. Work top-down within "Next up" first.
Process for every ticket: branch off master, plain imperative commits, no
signatures, stay inside the ticket's scope, tick the box in this file as
part of the PR. Flag scope surprises before diving in. Release checklist
lives in ARCHITECTURE.md.

## Next up

### [x] PAREM-101 — Folder creation only shows first 50 apps, main profile only

**Priority:** P2 · **Estimate:** ~half a day · **Type:** Bug/limitation
**Branch:** `fix/folder-picker-limits` · **Order:** do this first (warm-up)

**Background.** The folder app picker lists only the first 50 apps
alphabetically and skips work-profile apps entirely. Anyone with >50 apps
can't add later-alphabet apps to a folder. On the known-issues list since
the audit.

**Scope.**
- Remove the 50-app cap in the folder app picker (locating it is part of
  the task — start around `FolderManager` and whatever HomeFragment invokes
  for folder creation).
- Include apps from all user profiles, consistent with how the main app
  drawer already handles profiles. Match the drawer's existing path; don't
  invent a new one.

**Out of scope.** Folder picker UI redesign, folder sorting, folder icons.
If the uncapped list is unusable without search — flag it, don't build it.

**Acceptance criteria.**
1. A device with >50 apps shows all of them in the folder picker.
2. Work-profile apps appear iff they appear in the app drawer.
3. Existing folders unaffected (open, launch, rename still work).
4. `compileDebugKotlin` + `testDebugUnitTest` pass; `assembleDebug` builds.

**Notes.** Check whether the 50 cap exists for a performance reason (code +
git blame) before deleting it; report findings in the PR description.

---

### [x] PAREM-102 — Split SettingsFragment into per-section components

**Priority:** P1 · **Estimate:** 2–3 days · **Type:** Refactor
**Branch:** `refactor/settings-split` · **Order:** after PAREM-101

**Background.** `SettingsFragment` is ~1.2k lines; every upcoming feature
(unit conversion, contact search) grows it. Split now, at cycle start,
while there's time to catch regressions. Named in ARCHITECTURE.md as the
next refactor.

**Scope.**
- **Hard gate:** propose section boundaries first (sections, target file
  names, what stays in the fragment) and get sign-off before writing code.
- Pure restructuring. Zero behavior change, zero string changes, zero
  pref-key changes.
- Follow existing conventions: ViewBinding, `_binding` null-guards,
  `BottomSheetMenu` for sheets.

**Out of scope.** Fixing settings bugs found along the way (file separate
tickets), renaming prefs, redesigning any picker.

**Acceptance criteria.**
1. No single settings file over ~400 lines.
2. Every settings row manually walked on a device, stated in the PR
   section by section. "It compiles" is not verification for this.
3. Settings export/import still round-trips (verify with backup/restore).
4. Full build green: `compileDebugKotlin`, `testDebugUnitTest`,
   `assembleDebug`.
5. ARCHITECTURE.md code map updated; debt entry removed.

## Features

### [x] PAREM-103 — Omnibox unit conversion

**Priority:** P2 · **Estimate:** 1–2 days · **Type:** Feature
**Branch:** `feat/omnibox-unit-conversion` · **Blocked by:** PAREM-102 if
it needs a settings toggle (decide during design; flag before building UI).

**Background.** Omnibox already does calc, dial, and web. Unit conversion
("5 km in mi", "100 f to c") is the natural next mode and fits the
codebase pattern exactly.

**Scope.**
- Parser/converter as an Android-free object in `helper/` (sibling to
  `ExpressionEvaluator`), with JVM unit tests next to the existing ones.
- Wire into the existing omnibox dispatch in `AppDrawerFragment` the same
  way calculator results surface.
- Start with a defined unit set: length, mass, temperature, volume, speed,
  data size. List the supported units in the PR.

**Out of scope.** Currency (needs network + rates — separate ticket if
wanted), timezone math, a settings toggle unless design says it's needed.

**Acceptance criteria.**
1. Common phrasings parse: "5km in mi", "5 km to miles", "100f c".
2. Ambiguous/garbage input degrades gracefully to normal search (no crash,
   no false positives on plain app searches).
3. Unit tests cover each category + at least 5 non-conversion strings that
   must NOT parse.
4. Full build green.

---

### [ ] PAREM-104 — Contact search in omnibox

**Priority:** P3 · **Estimate:** 2 days · **Type:** Feature
**Branch:** `feat/omnibox-contact-search` · **Status:** UNPARKED 2026-07-07
(approved by Patric). **Blocked by:** merge of `refactor/settings-split` —
the opt-in toggle goes in the post-split settings cards.

**Background.** Searching contacts from the omnibox needs `READ_CONTACTS`,
which changes the app's privacy story (currently no sensitive permissions
beyond usage stats). Approved on the condition below.

**Decision (made 2026-07-07).** Build it. Runtime-request only when the
user enables the feature toggle, never at install/onboarding.

**Scope.** Contact provider query behind a helper, results in omnibox
dispatch, opt-in toggle in settings (post-split), permission denied state
handled gracefully.

**Acceptance criteria.**
1. Settings has an off-by-default contact-search toggle; flipping it on
   triggers the READ_CONTACTS runtime request. Denial leaves the toggle
   off, no crash, no repeated nagging.
2. READ_CONTACTS is never requested at install, onboarding, or any path
   other than enabling the toggle.
3. Toggle off → zero contact-provider queries (verify the code path, not
   just the UI).
4. Toggle on + permission granted: omnibox search surfaces matching
   contacts without displacing app results (apps rank first).
5. Permission revoked in system settings while the toggle is on degrades
   gracefully (no crash; feature silently off or toggle reset).
6. Matching/ranking logic lives Android-free in helper/ with JVM unit
   tests, including strings that must NOT surface contacts.
7. Full build green: `compileDebugKotlin`, `testDebugUnitTest`,
   `assembleDebug`.

## Smaller fixes

### [x] PAREM-105 — Focus-mode whitelist picker capped at 10 most-used apps

**Priority:** P3 · **Estimate:** ~half a day · **Type:** Bug/limitation

**Background.** The whitelist picker in focus mode offers only the 10
most-used apps (default dialer always allowed regardless). Users can't
whitelist an app they rarely open but need during focus (e.g. maps).

**Scope.** Same shape as PAREM-101: lift the cap or make the full app list
reachable, following whatever pattern PAREM-101 lands on. Keep the
always-allowed dialer behavior.

**Acceptance criteria.**
1. Any installed app can be whitelisted.
2. Existing whitelists still load and enforce.
3. Full build green.

---

### [x] PAREM-106 — Weather: stale cached temperature shown silently on fetch failure

**Priority:** P3 · **Estimate:** ~half a day · **Type:** UX bug

**Background.** When an Open-Meteo fetch fails, `WeatherManager` silently
keeps showing the last cached temperature — could be hours old with no
indication.

**Scope.**
- First: propose the treatment (e.g. dim/asterisk the reading past a
  staleness threshold, or hide past a max age) with the threshold values.
  One-paragraph proposal, sign-off, then implement. This is a design
  decision, not just code.
- Implementation stays inside `WeatherManager` + the home-screen binding.

**Out of scope.** Retry logic changes, new weather providers, per-fetch
error toasts (home screen must stay quiet).

**Acceptance criteria.**
1. A failed fetch with old cache is visually distinguishable from fresh
   data (per agreed treatment).
2. Airplane-mode test: enable, wait past threshold, verify treatment.
3. Full build green.

---

### [ ] PAREM-107 — Residual gesture-letter vs swipe conflicts

**Priority:** P3 · **Estimate:** investigation first · **Type:** Bug watch

**Background.** v5.5.1's direction-change capture fixed the main conflict
where a fast letter draw also registered as a swipe. Both systems still
see the same touch stream; edge cases may remain.

**Scope.**
- Investigation ticket: reproduce on device (fast letters, slow letters,
  letters starting with a straight stroke like L/D). Document what still
  misfires, if anything.
- If a concrete misfire is found: report it with repro steps; fix gets its
  own ticket with sign-off. Do NOT rework the touch pipeline inside this
  ticket.

**Acceptance criteria.** A written repro list (or "no repro found") in the
ticket notes below; no code changes unless approved.

**Notes:** _(fill in findings here)_

## Long-running / observation

### [ ] PAREM-108 — Put the 4-hour self-recreate behind a pref, observe, then remove

**Priority:** P3 · **Estimate:** small code change + weeks of observation
**Type:** Experiment

**Background.** `MainActivity.restartLauncherOrCheckTheme` recreates the
launcher every 4 hours and wipes `cacheDir` — an inherited Olauncher
stability hack, unproven, and a listed trap in ARCHITECTURE.md.

**Scope.**
- Phase 1 (now): gate the recreate + cache wipe behind a hidden/debug pref,
  default ON (current behavior). Ship it.
- Phase 2: maintainer runs OFF on daily-driver device for 2–4 weeks.
  Watch for: memory growth, theme drift, widget breakage, mystery states.
- Phase 3 (separate PR): remove the hack if nothing regresses; also remove
  trap #2 from ARCHITECTURE.md.

**Acceptance criteria (phase 1).**
1. Pref OFF → no 4-hour recreate, no cacheDir wipe; ON → unchanged.
2. `checkTheme()` force-recreate on wrong colors stays active either way
   (it's a separate mechanism).
3. Full build green.

**Observation log:** phase 1 landed 2026-07-07, pref default ON.

---

### [ ] PAREM-109 — Periodic upstream Olauncher cherry-pick review

**Priority:** P4 · **Estimate:** ~1h per pass · **Type:** Recurring chore

**Scope.** `git fetch upstream`, review commits since last port (last
ported at bec09c7), shortlist anything worth cherry-picking, one ticket
per pick. No blind merges — this fork has diverged.

**Log:** last reviewed 2026-07-03 (3 fixes ported for v5.5.0).
Reviewed 2026-07-06: nothing to pick — upstream HEAD (2026-07-01) predates
the last port (bec09c7); remaining upstream commits touch features this
fork removed (home-button recents, upstream regex search, region branding).

## Process / housekeeping

### [x] PAREM-110 — Delete merged refactor/maintainability branch

**Priority:** P4 · **Estimate:** 5 min

Fully merged (master is ahead; branch has nothing unique — verified
2026-07-03). Delete local + origin:
`git branch -d refactor/maintainability && git push origin --delete refactor/maintainability`

---

### [x] PAREM-111 — CHANGELOG "unreleased" section flow

**Priority:** P4 · **Estimate:** 15 min

Add an `## [Unreleased]` section at the top of CHANGELOG.md; every PR that
changes behavior adds a line there instead of reconstructing history at
release time. At release, rename the section to the version. Document the
rule in ARCHITECTURE.md's release steps.

---

### [ ] PAREM-112 — Onboarding pager rebuilds views on every bind

**Priority:** P5 · **Estimate:** ~1h · **Type:** Cosmetic

One-time screen, purely cosmetic. Only worth touching if already in
`OnboardingPagerAdapter` for another reason. Lowest priority in the file.
