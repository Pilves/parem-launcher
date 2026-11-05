#!/usr/bin/env bash
# Disposable CI emulator only. With a run count, repeats the drawer tests in
# fresh processes to measure a failure rate; without one, runs both suites.
set -uo pipefail

runs=${1:-0}
pkg=com.parem.launcher.debug
tests=(swipeUpOpensDrawer omniboxLaunchesApp)
diag_out=app/build/reports/androidTests/parem-diag

collect_diagnostics() {
  mkdir -p "$diag_out"
  adb pull /data/local/tmp/parem-diag/. "$diag_out" >/dev/null 2>&1 || true
}

if [ "${runs:-0}" -le 0 ]; then
  status=0
  ./gradlew connectedPlayDebugAndroidTest || status=1
  ./gradlew connectedFullDebugAndroidTest || status=1
  collect_diagnostics
  exit "$status"
fi

./gradlew installPlayDebug installPlayDebugAndroidTest || exit 1
mkdir -p "$diag_out"
failed=0
for ((i = 1; i <= runs; i++)); do
  test=${tests[$((i % ${#tests[@]}))]}
  adb shell pm clear "$pkg" >/dev/null
  output=$(adb shell am instrument -w -e class "com.parem.launcher.SmokeTest#$test" \
    "$pkg.test/androidx.test.runner.AndroidJUnitRunner" 2>&1)
  if grep -q 'OK (1 test)' <<<"$output"; then
    echo "stress $i $test pass"
  else
    failed=$((failed + 1))
    echo "stress $i $test FAIL"
    printf '%s\n' "$output" >"$diag_out/run-$i-$test.txt"
    grep -m1 -E 'AssertionError|Exception|INSTRUMENTATION_' <<<"$output" | cut -c1-200
  fi
done
collect_diagnostics
echo "DRAWER_STRESS failed=$failed of=$runs"
[ "$failed" -eq 0 ]
