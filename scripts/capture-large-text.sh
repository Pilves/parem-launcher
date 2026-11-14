#!/usr/bin/env bash
set -euo pipefail

adb shell settings put global hide_error_dialogs 1
./gradlew installPlayDebug installPlayDebugAndroidTest
adb shell wm size 320x640
adb shell wm density 160
adb shell pm clear com.parem.launcher.debug
# Settle boot, broadcasts and the stock HOME activity before launching Parem.
timeout 60s bash -c 'until [ "$(adb shell getprop sys.boot_completed | tr -d "\r")" = 1 ]; do sleep 1; done'
timeout 45s adb shell cmd activity wait-for-broadcast-idle
launcher=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME | tr -d '\r' | tail -n 1)
test -n "$launcher"
adb shell input keyevent KEYCODE_HOME
timeout 45s bash -c 'until adb shell dumpsys activity activities | grep -E "mResumedActivity|topResumedActivity" | grep -Fq "$1"; do sleep 1; done' _ "$launcher"
timeout 45s adb shell cmd activity wait-for-broadcast-idle

# Retain diagnostics on a test failure while preserving both command and test verdicts.
set +e
adb shell am instrument -w -e smallPhoneCapture true -e class com.parem.launcher.LargeTextSmallPhoneCaptureTest com.parem.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner | tee large-text-instrumentation.txt
instrument_status=${PIPESTATUS[0]}
adb pull /data/local/tmp/parem-large ./large-text-capture
pull_status=$?
timeout --kill-after=2s 8s adb logcat -d -v threadtime > large-text-logcat.txt || true
set -e
[ "$instrument_status" -eq 0 ] && [ "$pull_status" -eq 0 ] && grep -q 'OK (1 test)' large-text-instrumentation.txt
