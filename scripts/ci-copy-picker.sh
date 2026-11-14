#!/usr/bin/env bash
set -euo pipefail
for flavor in Play Full; do
  ./gradlew "connected${flavor}DebugAndroidTest" -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.PickerSelectionTest
done
