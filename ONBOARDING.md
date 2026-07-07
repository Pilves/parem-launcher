# Onboarding — finding your way around Parem Launcher

Read `ARCHITECTURE.md` first: it has the code map, the conventions, and the
"Traps" section. This document is the complement: how to *navigate* the code,
trace a feature end-to-end, and ship your first change without stepping on
anything. It assumes you're comfortable with Kotlin basics and Neovim.

## The 60-second mental model

- **One activity** (`MainActivity`), three main screens as fragments:
  `HomeFragment` (home), `AppDrawerFragment` (swipe up), `SettingsFragment`.
  Navigation-component handles transitions; there is no other Activity you'll
  ever touch (ignore `FakeHomeActivity`, it's a trick — see Traps).
- **One state store**: a single SharedPreferences file, `"com.parem.launcher"`.
  Core launcher state goes through the typed facade `data/Prefs.kt`; each
  feature manager in `helper/` owns its own keys directly. There is no
  database, no DataStore, no DI framework. If you're wondering "where is this
  remembered?", the answer is always that prefs file.
- **One shared ViewModel**: `MainViewModel`, activity-scoped, reachable from
  every fragment. App list loading and app launching live here.
- **Pure logic is separated on purpose**: parsers/matchers/calculators live in
  `helper/` as Android-free objects (`ExpressionEvaluator`, `UnitConverter`,
  `SearchMatcher`, `WeatherStaleness`) with JVM tests in `app/src/test`.
  Everything else is verified by building + running on a device.

## Three traces to do on your first day

Don't read files top to bottom — trace a user action through the layers.
These three cover most of the architecture. Use `gd` (go to definition) and
`gr` (references) to follow the chain; that's faster than guessing filenames.

**1. "I tap an app in the drawer and it opens."**
Start in `AppDrawerAdapter` (the row click), which calls
`MainViewModel.selectedApp(appModel, flag)`. The `flag` is the key idea: the
same drawer is reused for *every* "pick an app" flow (launch it, assign it to
home slot 3, set it as the swipe-left app…) and the flag says which one is
happening. Find the `FLAG_` constants in `data/Constants.kt`, then look at the
`when` over them in `MainViewModel`. Once this clicks, half the app makes sense.

**2. "I type `5 km in mi` in the drawer search."**
Start at `AppDrawerFragment.initSearch()` → `updateOmniboxState()`. The search
field is an omnibox with a precedence chain: calculator → unit conversion →
dial → web search → plain app filter. Each mode has a cheap "looks like" gate
(`ExpressionEvaluator.looksLikeExpression`, `UnitConverter.looksLikeConversion`)
before the real parse, and every parse failure falls through to app search —
that's the pattern any new omnibox mode must follow. The parsers themselves
are Android-free; read `UnitConverterTest` to see the contract spelled out.

**3. "I flip a toggle in settings."**
Start at `SettingsFragment` — note it's only ~130 lines. Each visual card is a
class in `ui/settings/` (e.g. `WellbeingSettingsCard`) that takes the
fragment/binding/prefs in its constructor and owns that card's clicks and
state. The fragment keeps only cross-card logic (`resetOpenPickers`,
`populateWellbeingSection`). To find which card owns a row: grep the row's
view id (from the layout XML) across `ui/settings/`.

## Finding things fast

- **Which file handles X?** Grep for user-visible text first:
  `rg "some settings label" app/src/main/res/values/strings.xml` gives you the
  string name, then `rg R.string.that_name` finds the code. In Neovim:
  `<leader>fw` (Telescope live grep).
- **Where is this pref stored?** `rg "PREF_KEY_NAME"` — if it's in `Prefs.kt`
  it's core state; if it's in a `helper/` manager it's feature-owned. New keys
  that must survive export/import need registration (see Conventions in
  ARCHITECTURE.md).
- **Who calls this function?** `gr` (LSP references) beats grep for Kotlin
  symbols; grep still wins for view ids and pref-key strings.
- **Why is this code weird?** `git log -L :funcName:path/to/File.kt` or
  git-blame the line. This fork inherited code from Olauncher — some oddities
  are load-bearing (see Traps #1 especially before "cleaning up" anything that
  looks dead).

## The edit-verify loop

```
./gradlew compileDebugKotlin    # fast: does it compile
./gradlew testDebugUnitTest     # JVM tests (pure logic only)
./gradlew assembleDebug         # full debug APK before calling anything done
```

Two rules that will bite you if skipped:
- `./gradlew … | tail` reports **tail's** exit code. Check gradle's own exit
  code, or run without the pipe.
- "It compiles" is not verification for UI changes — install the APK
  (`app/build/outputs/apk/debug/app-debug.apk`) and touch the actual screen.
  CI also builds a debug APK artifact on every master push.

Building on the Pi has its own setup (qemu-wrapped aapt2) — documented in
ARCHITECTURE.md under "Building on ARM64 hosts".

## How a change ships

1. Pick a ticket from `TODO.md` (top-down within "Next up"). Read its scope
   AND its out-of-scope — staying inside the box is the skill.
2. Branch off master (`fix/…`, `feat/…`, `refactor/…`, `chore/…`).
3. Make the change. Match surrounding style; comments explain *why*, not what.
4. If behavior changed: add a line under `## [Unreleased]` in CHANGELOG.md.
5. Tick the ticket's box in TODO.md as part of the branch.
6. Run the full verify loop above. Commits: plain imperative mood
   ("remove the cap", not "removed"), no signatures.
7. Release process (version bump, fastlane, tag) is in ARCHITECTURE.md —
   you won't need it for normal tickets.

## A ladder of first tasks

1. **Read-only**: do the three traces above; write down one question each.
2. **Docs**: fix anything this file or ARCHITECTURE.md got wrong — docs PRs
   are real PRs.
3. **Dead code**: PAREM-113 in TODO.md (a confirmed-dead function) — small,
   real, teaches the verify loop and git archaeology.
4. **Pure logic**: add a unit alias or category to `UnitConverter` + tests —
   no Android surface, tests prove you right.
5. **UI change**: pick a P3+ ticket from TODO.md that touches one fragment,
   after doing 1-4.
