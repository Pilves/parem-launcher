#!/usr/bin/env bash
set -uo pipefail
mkdir -p app/build/widget-picker-profile
./gradlew connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetPickerOverflowTest
result=$?
adb logcat -d -v threadtime -s WidgetOverflowDiag:I '*:S' > app/build/widget-picker-profile/overflow-log.txt || true
for file in widget-overflow-failure.xml widget-overflow-failure.png; do
  adb pull "/data/local/tmp/$file" "app/build/widget-picker-profile/$file" >/dev/null 2>&1 || true
done
exit "$result"
