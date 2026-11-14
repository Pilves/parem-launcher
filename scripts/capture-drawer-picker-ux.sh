#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
# Disposable emulator only: drawer assignment and hidden management, 1x/2x and IME up/down.
set -euo pipefail
out=app/build/drawer-picker-ux
[[ "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]] || { echo 'Disposable emulator required' >&2; exit 1; }
mkdir -p "$out"
trap 'adb shell settings delete system font_scale >/dev/null 2>&1 || true; adb pull /data/local/tmp/parem-drawer-picker-captures/. "$out/" >/dev/null 2>&1 || true; adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1 || true' EXIT
adb shell settings put global hide_error_dialogs 1
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell rm -rf /data/local/tmp/parem-drawer-picker-captures
for font in 1.0 2.0; do
  adb shell settings put system font_scale "$font"
  for ime in off on; do
    ./gradlew connectedPlayDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.DrawerPickerCaptureTest \
      -Pandroid.testInstrumentationRunnerArguments.drawer_picker_capture=true \
      -Pandroid.testInstrumentationRunnerArguments.drawer_picker_font="$font" \
      -Pandroid.testInstrumentationRunnerArguments.drawer_picker_ime="$ime"
  done
done
adb pull /data/local/tmp/parem-drawer-picker-captures/. "$out/"
for font in 1.0 2.0; do
  for ime in off on; do
    test -s "$out/ux-pick2-hidden-menu-font$font-ime$ime.png"
  done
  test -s "$out/ux-pick2-ordinary-font$font-imeon.png"
done
for route in assignment hidden; do
  for font in 1.0 2.0; do
    for ime in off on; do
      test -s "$out/ux-pick2-$route-font$font-ime$ime.png"
    done
  done
done
