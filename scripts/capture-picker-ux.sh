#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
# Disposable CI emulator only: capture real picker routes at both font scales and IME states.
set -euo pipefail
out=app/build/picker-ux
mkdir -p "$out"
trap 'adb shell settings delete system font_scale >/dev/null 2>&1 || true; adb pull /data/local/tmp/parem-picker-captures/. "$out/" >/dev/null 2>&1 || true; adb logcat -d -v threadtime > "$out/logcat.txt" 2>&1 || true' EXIT
# This emulator is disposable; suppress stale system ANR dialogs from its boot.
# Tests still fail on missing routes or Parem crashes, and logcat is retained.
adb shell settings put global hide_error_dialogs 1
# The CI AVD reports a visible hardware keyboard. Allow an explicit tap to
# open the software IME for the requested keyboard-up screenshots.
adb shell settings put secure show_ime_with_hard_keyboard 1
adb shell rm -rf /data/local/tmp/parem-picker-captures
for font in 1.0 2.0; do
  adb shell settings put system font_scale "$font"
  for ime in off on; do
    ./gradlew connectedPlayDebugAndroidTest \
      -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.PickerCaptureTest \
      -Pandroid.testInstrumentationRunnerArguments.picker_capture=true \
      -Pandroid.testInstrumentationRunnerArguments.picker_font="$font" \
      -Pandroid.testInstrumentationRunnerArguments.picker_ime="$ime"
  done
done
