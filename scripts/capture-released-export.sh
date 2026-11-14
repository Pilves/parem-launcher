#!/usr/bin/env bash
set -euo pipefail
out=capture
adb install "$out/release.apk"
adb install release-smoke-driver/build/outputs/apk/debug/release-smoke-driver-debug.apk
adb install release-smoke-driver/build/outputs/apk/androidTest/debug/release-smoke-driver-debug-androidTest.apk
adb shell rm -f /sdcard/Download/parem_settings.json
adb shell am instrument -w -e class com.parem.smokedriver.ReleasedExportCaptureTest com.parem.smokedriver.test/androidx.test.runner.AndroidJUnitRunner | tee "$out/instrumentation.txt"
grep -Eq '^OK \(1 test\)' "$out/instrumentation.txt"
adb pull /sdcard/Download/parem_settings.json "$out/parem_settings.json"
sha256sum "$out/parem_settings.json" | tee "$out/fixture-sha256.txt"
