#!/usr/bin/env bash
# Disposable CI emulator only. With a run count, repeats the drawer tests in
# fresh processes to measure a failure rate; without one, runs both suites.
set -uo pipefail

runs=${1:-0}
pkg=com.parem.launcher.debug
tests=(swipeUpOpensDrawer omniboxLaunchesApp)
diag_out=app/build/smoke-diagnostics
mkdir -p "$diag_out"
adb logcat -v threadtime >"$diag_out/continuous-logcat.txt" 2>&1 &
logcat_pid=$!

capture_failure() {
  local label=$1
  adb logcat -d -v threadtime >"$diag_out/$label-logcat.txt" 2>&1 || true
  # DropBoxManagerService ANDs search arguments, so query tags separately.
  for tag in data_app_crash data_app_anr data_app_native_crash system_app_crash; do
    adb shell dumpsys dropbox --print "$tag" >"$diag_out/$label-dropbox-$tag.txt" 2>&1 || true
  done
  adb shell ls -la /data/tombstones >"$diag_out/$label-tombstones.txt" 2>&1 || true
}

finish() {
  local result=$?
  if [ "$result" -ne 0 ]; then capture_failure exit; fi
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
}
trap finish EXIT

collect_diagnostics() {
  mkdir -p "$diag_out"
  adb pull /data/local/tmp/parem-diag/. "$diag_out" >/dev/null 2>&1 || true
}

if [ "${runs:-0}" -le 0 ]; then
  status=0
  if ! ./gradlew connectedPlayDebugAndroidTest; then
    status=1
    capture_failure play
    cp -r app/build/reports/androidTests/connected "$diag_out/play-failure-report" 2>/dev/null || true
    cp -r app/build/outputs/androidTest-results/connected "$diag_out/play-failure-results" 2>/dev/null || true
  fi
  if ! ./gradlew connectedFullDebugAndroidTest; then
    status=1
    capture_failure full
  fi
  if [ "${2:-false}" = true ]; then
    if ! bash scripts/perf-compare.sh; then
      status=1
      capture_failure perf
    fi
  fi
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
