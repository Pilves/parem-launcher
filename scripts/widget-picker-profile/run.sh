#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
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
trap 'adb logcat -d -v threadtime > app/build/widget-picker-profile/logcat.txt || true; for image in cold-open cold-filtered cold-scrolled warm-open warm-filtered warm-scrolled font1.0-imeoff font1.0-imeon font2.0-imeoff font2.0-imeon; do adb pull "/data/local/tmp/parem-widget-picker-$image.png" "app/build/widget-picker-profile/$image.png" >/dev/null 2>&1 || true; done; adb shell settings delete system font_scale >/dev/null 2>&1 || true; adb uninstall "$fixture" >/dev/null 2>&1 || true' EXIT
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

if [[ ${WIDGET_PICKER_CAPTURE:-false} == true ]]; then
  # AVD has a hardware keyboard; allow a user-equivalent tap to raise the IME.
  adb shell settings put secure show_ime_with_hard_keyboard 1
  for font in 1.0 2.0; do
    adb shell settings put system font_scale "$font"
    for ime in off on; do
      ./gradlew connectedPlayDebugAndroidTest \
        -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetPickerCaptureTest \
        -Pandroid.testInstrumentationRunnerArguments.widget_picker_capture=true \
        -Pandroid.testInstrumentationRunnerArguments.widget_picker_font="$font" \
        -Pandroid.testInstrumentationRunnerArguments.widget_picker_ime="$ime"
    done
  done
fi
