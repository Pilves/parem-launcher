# Parem Launcher — Agent Instructions

Parem is a minimal Android launcher, forked from Olauncher. Kotlin, ViewBinding,
SharedPreferences, no backend.

Live state: `HANDOFF.md` (short; read it first — it is generated from
`docs/handoff/`). Work queue: `docs/ROADMAP.md`. Code map, conventions and
traps: `ARCHITECTURE.md` — read its "Traps" section before touching home,
widgets or the lock path.

## Agent workflow (mandatory)

1. Read `HANDOFF.md`, then take your work package from `docs/ROADMAP.md` (or the task Patric gave you). Touch only the files it owns; hot files are listed there.
   **Claim it before you touch anything:** `scripts/claim-wp.sh next` (or `scripts/claim-wp.sh claim <id>` for a specific one; `scripts/claim-wp.sh list` shows what is taken). Other sessions work in this repo at the same time and the claim is the only thing that stops two of them building the same package. If the claim is refused, take the next unclaimed id and say so in your report. Release it with `scripts/claim-wp.sh release <id>` when the PR lands or you abandon the work.
2. Do that task and nothing else. Surgical scope only. Packages marked **design first** stop at a written proposal until Patric signs off.
3. Run the checks below. All must exit 0.
4. Write the handoff in the PR body using `.github/PULL_REQUEST_TEMPLATE.md`: concern, files owned, check output, what was and was not verified.
   **If the PR closes a work package it must also add one fragment, `docs/handoff/<WP-id>.md`** — front matter (`wp`, `pr`, `date`) and 3–8 lines: what now exists, the frozen contract, what was not verified, what is easy to get wrong. Format: `docs/handoff/README.md`. Never manually edit `HANDOFF.md` between its `handoff:begin` / `handoff:end` markers or flip a row in `docs/ROADMAP.md`. Run `node scripts/build-handoff.mjs` and commit both generated files with the fragment. `.github/workflows/handoff.yml` fails the PR if the output is stale.
5. Commits: plain imperative mood ("remove the cap", not "removed"), no conventional-commit prefix, no signature or co-author line.
6. Report back: commit hash + one line on what happened. If blocked, stop and report — don't invent scope.

## Checks

```
./gradlew compileDebugKotlin     # quick check
./gradlew testDebugUnitTest      # JVM suite (app/src/test — pure logic only)
./gradlew assembleDebug          # before calling anything done
node --test scripts/build-handoff.test.mjs  # harness scripts
```

A pipeline like `./gradlew … | tail` reports tail's exit code — check
gradle's. One gradle build at a time: sessions share the checkout and the Pi.
Building on ARM64 needs the qemu-wrapped aapt2 (`ARCHITECTURE.md`).

## Code rules

- New pure logic (parsers, matchers, calculators) goes in `helper/` as an
  Android-free object with unit tests next to the existing ones.
- Bottom sheets go through `ui/BottomSheetMenu`; app-launch/selection flows go
  through `MainViewModel.selectedApp(model, flag)`.
- All state is SharedPreferences file `"com.parem.launcher"`. New exported keys
  need type registration in `Prefs` (see LONG_PREF_KEYS / exportExcludeKeys).
- Async/posted callbacks in fragments must guard on `isAdded` / `_binding != null`.
- Strings: new user-facing text goes in `res/values/strings.xml`; mark it
  `translatable="false"` unless you also add translations.
- Behaviour changes add a line under `## [Unreleased]` in `CHANGELOG.md`.

## Release

Bump versionCode/versionName in `app/build.gradle`, add the fastlane changelog,
rename `[Unreleased]` in CHANGELOG.md, tag `v*`, push; CI signs and publishes.
Tagging is an owner task — agents prepare, Patric tags after the device pass
(`docs/RELEASE_CHECKLIST.md` once M1-WP1 lands). Never commit keystores.
