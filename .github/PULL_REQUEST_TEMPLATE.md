## Concern

One sentence: what this PR does and which roadmap work package it closes
(`docs/ROADMAP.md`, e.g. `M2-WP1`).

## Files owned

List the directories or files this PR was allowed to touch. If you changed a
hot file (`app/build.gradle`, `CHANGELOG.md`, `strings.xml`, `Prefs.kt`,
`AndroidManifest.xml`), say why.

## Checks

Paste the final lines. Exit 0 or it is not ready.

```
./gradlew compileDebugKotlin  →
./gradlew testDebugUnitTest   →
./gradlew assembleDebug       →
node --test scripts/build-handoff.test.mjs  →
```

## Verified

- What was exercised beyond the suite (device, emulator, orientation, tablet).
- What was **not** verified. Be explicit; the next person plans around it.

## Checklist

- [ ] One concern only; diff touches only the files above
- [ ] Rebased on current `master`, no conflicts
- [ ] Traps in `ARCHITECTURE.md` checked (lock view, widget IDs, package-change stamp)
- [ ] New exported prefs registered in `Prefs`; export/import still round-trips
- [ ] New strings in `strings.xml` (`translatable="false"` unless translated)
- [ ] Behaviour change → line under `## [Unreleased]` in `CHANGELOG.md`
- [ ] No keystores, secrets or `local.properties` values in the diff
- [ ] Closes a work package → `docs/handoff/<WP-id>.md` fragment added (`docs/handoff/README.md`); `node scripts/build-handoff.mjs` output committed without manual edits
