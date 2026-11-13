# Onboarding — finding your way around Parem Launcher

Read `ARCHITECTURE.md` first: it has the code map, the conventions, and the
"Traps" section. This document is the complement: how to *navigate* the code,
trace a feature end-to-end, and ship your first change without stepping on
anything. It assumes you're comfortable with Kotlin basics and Neovim.

## The 60-second mental model

- **One activity** (`MainActivity`), three main screens as fragments:
  `HomeFragment` (home), `AppDrawerFragment` (swipe up), `SettingsFragment`.
  Navigation-component handles transitions; `FakeHomeActivity` supports
  default-launcher switching (see Traps).
- **State**: core launcher state is in `"com.parem.launcher"` SharedPreferences,
  through `data/Prefs.kt` or feature managers in `helper/`. Search history and
  quiet-notification keys use separate, non-exported preferences files. Opt-in
  crash reports use `noBackupFilesDir`. There is no database, DataStore or DI framework.
- **One shared ViewModel**: `MainViewModel`, activity-scoped, reachable from
  every fragment. App list loading and app launching live here.
- **Pure logic is separated on purpose**: parsers/matchers/calculators live in
  `helper/` as Android-free objects (`ExpressionEvaluator`, `UnitConverter`,
  `SearchMatcher`, `WeatherStaleness`) with JVM tests in `app/src/test`.
  Instrumented Android tests run in CI; a device pass is still needed for
  behavior those tests do not cover.

## Three traces to do on your first day

Don't read files top to bottom — trace a user action through the layers.
These three cover most of the architecture. Use `gd` (go to definition) and
`gr` (references) to follow the chain; that's faster than guessing filenames.

**1. "I tap an app in the drawer and it opens."**
Start in `AppDrawerAdapter` (the row click), then follow its callback to
`AppDrawerFragment.onAppClick()`. The fragment handles private-space headers
and checks the app's launch gate before it calls
`MainViewModel.selectedApp(appModel, flag)`. The `flag` is the key idea: the
same drawer is reused for *every* "pick an app" flow (launch it, assign it to
home slot 3, set it as the swipe-left app…) and the flag says which one is
happening. Find the `FLAG_` constants in `data/Constants.kt`, then look at the
`when` over them in `MainViewModel`.

**2. "I type `5 km in mi` in the drawer search."**
Start at `AppDrawerFragment` search handling → `updateOmniboxState()` →
`helper/OmniboxResolver.resolve()`. The resolver checks calculator, unit and
currency conversion, dial, leading-space web search, quick actions, contacts
and settings, in that order; the drawer also maintains the app filter. Read
`UnitConverterTest` and `OmniboxResolver` tests for the parser contracts.

**3. "I flip a toggle in settings."**
Start at `SettingsFragment`, which delegates row behavior to controller
classes in `ui/settings/` (e.g. `WellbeingSettingsCard`). A controller can
own rows in more than one visual section; it takes the fragment, binding and
preferences and wires its rows' clicks and state. The fragment also owns
cross-card logic. To find which controller owns a row, search for the row's
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
./gradlew compileFullDebugKotlin compilePlayDebugKotlin  # fast: does it compile
./gradlew testFullDebugUnitTest testPlayDebugUnitTest    # JVM tests (pure logic only)
./gradlew assembleFullDebug assemblePlayDebug            # debug APKs before calling anything done
node --test scripts/build-handoff.test.mjs scripts/claim-wp.test.mjs  # harness scripts
```

Two rules that will bite you if skipped:
- `./gradlew … | tail` reports **tail's** exit code. Check gradle's own exit
  code, or run without the pipe.
- "It compiles" is not verification for UI changes — install the APK
  (`app/build/outputs/apk/full/debug/app-full-debug.apk`) and touch the actual screen.
  CI runs a debug APK build on master pushes; the artifact is uploaded if the
  build and preceding checks succeed.

Building on the Pi has its own setup (qemu-wrapped aapt2) — documented in
ARCHITECTURE.md under "Building on ARM64 hosts".

## How a change ships

1. Claim a work package: `scripts/claim-wp.sh next` (the queue is
   `docs/ROADMAP.md`; workflow in `AGENTS.md`). Read its row AND what it
   doesn't own — staying inside the box is the skill.
2. Branch off master (`fix/…`, `feat/…`, `refactor/…`, `chore/…`).
3. Make the change. Match surrounding style; comments explain *why*, not what.
4. If behavior changed: add a line under `## [Unreleased]` in CHANGELOG.md.
5. If the PR closes a work package, add `docs/handoff/<WP-id>.md` and run
   `node scripts/build-handoff.mjs` (format in `docs/handoff/README.md`) —
   commit both generated files and never tick the roadmap by hand.
6. Run the full verify loop above. Commits: plain imperative mood
   ("remove the cap", not "removed"), no signatures.
7. Release process (version bump, fastlane, tag) is in ARCHITECTURE.md —
   you won't need it for normal tickets.

## A ladder of first tasks

1. **Read-only**: do the three traces above; write down one question each.
2. **Docs**: fix anything this file or ARCHITECTURE.md got wrong — docs PRs
   are real PRs.
3. **Dead code**: verify a candidate has no callers before proposing removal;
   archived tickets describe past state and are not a current work queue.
4. **Pure logic**: add a unit alias or category to `UnitConverter` + tests —
   no Android surface, tests prove you right.
5. **UI change**: pick a roadmap package that touches one fragment,
   after doing 1-4.
