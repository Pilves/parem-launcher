#!/usr/bin/env bash
set -uo pipefail
mkdir -p app/build/widget-picker-profile
./gradlew connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetPickerOverflowTest
result=$?
for file in widget-overflow-failure.xml widget-overflow-failure.png; do
  adb pull "/sdcard/Android/data/com.parem.launcher.debug/files/$file" "app/build/widget-picker-profile/$file" >/dev/null 2>&1 || true
done
exit "$result"
