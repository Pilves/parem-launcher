#!/usr/bin/env bash
# Disposable emulator: compare identical core flows at normal system speed.
set -euo pipefail
out=app/build/motion-review
mkdir -p "$out"
adb shell settings put global window_animation_scale 1
adb shell settings put global transition_animation_scale 1
adb shell settings put global animator_duration_scale 1
for label in before after; do
  if [[ "$label" == before ]]; then root=../motion-before; else root=.; fi
  adb install -r "$root/app/build/outputs/apk/play/debug/app-play-debug.apk"
  adb install -r "$root/app/build/outputs/apk/androidTest/play/debug/app-play-debug-androidTest.apk"
  adb shell pm clear com.parem.launcher.debug >/dev/null
  adb shell screenrecord --time-limit 150 "/sdcard/motion-$label.mp4" &
  recorder=$!
  sleep 1
  status=0
  : >"$out/$label.txt"
  for test in swipeUpOpensDrawer omniboxLaunchesApp swipeDownRowStaysWhileItsPickerIsOpen; do
    # Isolate each test, as Android Test Orchestrator does in the ordinary suite.
    adb shell am force-stop com.android.settings
    adb shell pm clear com.parem.launcher.debug >/dev/null
    adb shell cmd package set-home-activity com.parem.launcher.debug/com.parem.launcher.MainActivity
    adb shell am start -n com.parem.launcher.debug/com.parem.launcher.MainActivity >/dev/null
    adb shell am instrument -w -e class "com.parem.launcher.SmokeTest#$test" \
      com.parem.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner >"$out/$label-$test.txt" 2>&1 || status=1
    cat "$out/$label-$test.txt" | tee -a "$out/$label.txt"
    grep -Eq '^OK \(1 test\)' "$out/$label-$test.txt" || status=1
  done
  adb shell 'kill -2 $(pidof screenrecord)' || true
  wait "$recorder" || true
  adb pull "/sdcard/motion-$label.mp4" "$out/$label.mp4"
  [[ "$status" == 0 ]]
done
# Full core suites with Android's animations explicitly enabled, both flavours.
./gradlew connectedPlayDebugAndroidTest connectedFullDebugAndroidTest
