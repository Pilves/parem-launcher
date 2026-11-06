#!/usr/bin/env bash
set -uo pipefail
flavour=$1
out=app/build/reports/minified-release
mkdir -p "$out"
adb logcat -c
status=0
adb install -r "app/build/outputs/apk/$flavour/release/app-$flavour-release.apk" || status=1
adb install -r "app/build/outputs/apk/androidTest/$flavour/release/app-$flavour-release-androidTest.apk" || status=1
if [ "$status" = 0 ]; then
  adb shell am instrument -w -e class com.parem.launcher.ReleaseSmokeTest com.parem.launcher.test/androidx.test.runner.AndroidJUnitRunner >"$out/instrumentation.txt" 2>&1 || status=1
  cat "$out/instrumentation.txt"
  grep -Eq '^OK \(1 test\)' "$out/instrumentation.txt" || status=1
fi
adb logcat -d -v threadtime >"$out/logcat.txt" || status=1
if grep -E 'FATAL EXCEPTION|Fatal signal|FATAL EXCEPTION IN SYSTEM PROCESS' "$out/logcat.txt"; then
  echo 'Fatal exception or native crash in emulator logcat' >&2
  status=1
fi
exit "$status"
