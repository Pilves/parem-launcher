#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
set -euo pipefail
# This script runs only on the disposable CI emulator.
./gradlew assemblePlayDebugAndroidTest
adb shell screenrecord --time-limit 180 /sdcard/parem-pause.mp4 &
recorder=$!
status=0
./gradlew connectedPlayDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.PauseFilmCapture -Pandroid.testInstrumentationRunnerArguments.filmCapture=true || status=$?
pid=$(adb shell pidof screenrecord | tr -d '\r' || true)
if [[ "$pid" =~ ^[0-9]+$ ]]; then adb shell kill -2 "$pid" || true; fi
wait "$recorder" || true
mkdir -p capture
adb logcat -d -s 'PauseFilmTiming:I' '*:S' > capture/pause-timing.txt
adb pull /sdcard/parem-pause.mp4 capture/pause-play-unreviewed.mp4 || status=1
git rev-parse HEAD > capture/source-sha.txt
exit "$status"
