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
  adb shell am instrument -w -e class 'com.parem.launcher.SmokeTest#swipeUpOpensDrawer,com.parem.launcher.SmokeTest#omniboxLaunchesApp,com.parem.launcher.SmokeTest#swipeDownRowStaysWhileItsPickerIsOpen' \
    com.parem.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner >"$out/$label.txt" 2>&1 || status=$?
  adb shell 'kill -2 $(pidof screenrecord)' || true
  wait "$recorder" || true
  adb pull "/sdcard/motion-$label.mp4" "$out/$label.mp4"
  cat "$out/$label.txt"
  [[ "$status" == 0 ]] && grep -Eq '^OK \(3 tests\)' "$out/$label.txt"
done
# Full core suites with Android's animations explicitly enabled, both flavours.
./gradlew connectedPlayDebugAndroidTest connectedFullDebugAndroidTest
