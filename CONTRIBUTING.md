# Contributing to Parem Launcher

Parem is an Android launcher written in Kotlin with classic Views. The app has
`full` and `play` flavours. See [ARCHITECTURE.md](ARCHITECTURE.md) for the code
map, the differences between flavours, and the lock, widget and cache traps.
[ONBOARDING.md](ONBOARDING.md) traces common changes through the code.

## Build and check

Install JDK 17 and the Android SDK for API 36, then run:

```sh
./gradlew compileFullDebugKotlin compilePlayDebugKotlin
./gradlew testFullDebugUnitTest testPlayDebugUnitTest
./gradlew assembleFullDebug assemblePlayDebug
node --test scripts/build-handoff.test.mjs scripts/claim-wp.test.mjs
```

The APKs are under `app/build/outputs/apk/<flavour>/debug/`. Debug builds use a
separate `.debug` application ID. CI runs instrumented tests on emulators, but
UI, accessibility, device permissions and hardware-specific behaviour also
need the relevant device checks in [docs/RELEASE_CHECKLIST.md](docs/RELEASE_CHECKLIST.md).
For ARM64 Linux build setup, see the build section of ARCHITECTURE.md.

## Changes and pull requests

Check [docs/ROADMAP.md](docs/ROADMAP.md) and existing issues and pull requests
before starting. Keep changes focused; describe the problem, the fix, what you
ran and what remains unverified. Add user-visible behavior changes under
`Unreleased` in [CHANGELOG.md](CHANGELOG.md). Do not commit signing keys,
credentials or local configuration.

The repository's work-package and generated-handoff rules for agents are in
[AGENTS.md](AGENTS.md). Maintainers can help contributors choose an available
package and coordinate shared files. Translation changes should preserve format
arguments and be reviewed by a speaker of the target language.
