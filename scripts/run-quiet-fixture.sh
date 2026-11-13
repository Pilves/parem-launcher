#!/usr/bin/env bash
# Run only on a fresh disposable CI emulator; fixture opts in to listener access.
set -euo pipefail
mkdir -p app/build
trap 'adb logcat -d -v threadtime > app/build/quiet-fixture-logcat.txt || true' EXIT
./gradlew connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.QuietNotificationFixtureTest -Pandroid.testInstrumentationRunnerArguments.quiet_fixture=true
./gradlew connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.QuietNotificationFixtureTest -Pandroid.testInstrumentationRunnerArguments.quiet_fixture=true
