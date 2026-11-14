#!/usr/bin/env bash
# Explicitly disposable CI emulator only; never changes a physical device.
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true ]] || { echo 'CI only' >&2; exit 1; }
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'Emulator required' >&2; exit 1; }
fixture=com.parem.widgetstress
if adb shell pm path "$fixture" | grep -q '^package:'; then
  echo 'Refusing to replace an existing fixture' >&2
  exit 1
fi
mkdir -p app/build/widget-picker-profile
trap 'adb logcat -d -v threadtime > app/build/widget-picker-profile/logcat.txt || true; adb pull /data/local/tmp/parem-widget-picker-gfxinfo.txt app/build/widget-picker-profile/gfxinfo.txt >/dev/null 2>&1 || true; adb uninstall "$fixture" >/dev/null 2>&1 || true' EXIT
adb shell settings put global hide_error_dialogs 1
adb install app/build/widget-picker-profile-fixture/widget-stress.apk
adb shell am start -W -n "$fixture/.Publisher"
./gradlew connectedPlayDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetPickerProfileTest \
  -Pandroid.testInstrumentationRunnerArguments.widget_picker_profile=true
