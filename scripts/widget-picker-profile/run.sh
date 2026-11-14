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
trap 'adb logcat -d -v threadtime > app/build/widget-picker-profile/logcat.txt || true; for image in cold-open cold-filtered cold-scrolled warm-open warm-filtered warm-scrolled; do adb pull "/data/local/tmp/parem-widget-picker-$image.png" "app/build/widget-picker-profile/$image.png" >/dev/null 2>&1 || true; done; adb uninstall "$fixture" >/dev/null 2>&1 || true' EXIT
adb shell settings put global hide_error_dialogs 1
adb logcat -c
adb install app/build/widget-picker-profile-fixture/widget-stress.apk
adb shell am start -W -n "$fixture/.Publisher"
ready=false
for attempt in $(seq 1 60); do
  if adb logcat -d -s ParemWidgetStress:I '*:S' | grep -q 'generated_previews=120'; then ready=true; break; fi
  sleep 1
done
[[ $ready == true ]] || { echo 'Synthetic preview publisher did not become ready' >&2; exit 1; }
./gradlew connectedPlayDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetPickerProfileTest \
  -Pandroid.testInstrumentationRunnerArguments.widget_picker_profile=true
