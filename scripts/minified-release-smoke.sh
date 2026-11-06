#!/usr/bin/env bash
set -uo pipefail
flavour=$1
out=app/build/reports/minified-release
mkdir -p "$out"
# This Google emulator process is not needed for the test and can SIGILL on
# the hosted x86 emulator. Still fail on every crash after the test starts.
adb shell pm disable-user --user 0 com.google.android.googlequicksearchbox || exit 1
adb shell am force-stop com.google.android.googlequicksearchbox || exit 1
adb logcat -c
status=0
adb install -r "app/build/outputs/apk/$flavour/release/app-$flavour-release.apk" || status=1
adb install -r "release-smoke-driver/build/outputs/apk/debug/release-smoke-driver-debug.apk" || status=1
adb install -r "release-smoke-driver/build/outputs/apk/androidTest/debug/release-smoke-driver-debug-androidTest.apk" || status=1
if [ "$status" = 0 ]; then
  adb shell am instrument -w -e class com.parem.smokedriver.ReleaseSmokeTest com.parem.smokedriver.test/androidx.test.runner.AndroidJUnitRunner >"$out/instrumentation.txt" 2>&1 || status=1
  cat "$out/instrumentation.txt"
  grep -Eq '^OK \(1 test\)' "$out/instrumentation.txt" || status=1
fi
adb logcat -d -v threadtime >"$out/logcat.txt" || status=1
if grep -E 'FATAL EXCEPTION|Fatal signal|FATAL EXCEPTION IN SYSTEM PROCESS' "$out/logcat.txt"; then
  echo 'Fatal exception or native crash in emulator logcat' >&2
  status=1
fi
exit "$status"
