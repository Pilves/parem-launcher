#!/usr/bin/env bash
# Run only on a disposable emulator: the test changes HOME and binds a widget.
set -Eeuo pipefail
trap 'adb shell uiautomator dump /sdcard/upgrade-window.xml >/dev/null 2>&1 && adb shell cat /sdcard/upgrade-window.xml || true' ERR
package=com.parem.launcher.debug
runner="$package.test/androidx.test.runner.AndroidJUnitRunner"
old_apk=../parem-551/app/build/outputs/apk/debug/app-debug.apk
new_apk=app/build/outputs/apk/full/debug/app-full-debug.apk
test_apk=app/build/outputs/apk/androidTest/full/debug/app-full-debug-androidTest.apk
adb install "$old_apk"
adb install "$test_apk"
adb shell appwidget grantbind --package "$package" --user 0
run_test() {
  local result
  result=$(timeout 180s adb shell am instrument -w -e class "com.parem.launcher.Upgrade551Test#$1" "$runner" 2>&1) || {
    printf '%s\n' "$result"
    adb logcat -d -t 300
    return 1
  }
  printf '%s\n' "$result"
  grep -q 'OK (1 test)' <<<"$result"
}
run_test seed551
adb install -r "$new_apk"
run_test verify600
