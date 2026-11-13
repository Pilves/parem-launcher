#!/usr/bin/env bash
# Only a fresh disposable CI emulator may receive synthetic notifications/access.
set -euo pipefail
mkdir -p app/build
trap 'adb logcat -d -v threadtime > app/build/quiet-positive-logcat.txt || true' EXIT
if [[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" != 1 ]]; then
  echo 'Refusing to install notification fixtures outside an emulator' >&2
  exit 1
fi
adb install ci/quiet-fixture-app/build/outputs/apk/debug/*.apk
adb shell pm grant com.parem.quietfixture android.permission.POST_NOTIFICATIONS
./gradlew connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.QuietPositiveFixtureTest -Pandroid.testInstrumentationRunnerArguments.quiet_positive_fixture=true
./gradlew connectedFullDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.QuietPositiveFixtureTest -Pandroid.testInstrumentationRunnerArguments.quiet_positive_fixture=true
