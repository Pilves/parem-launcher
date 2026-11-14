#!/usr/bin/env bash
# CI-only wrapper: system ANR dialogs are hidden only on a disposable emulator.
set -uo pipefail
pkg=${1:-}
shift || exit 1
[[ ${GITHUB_ACTIONS:-} == true && $pkg =~ ^com\.parem\.launcher(\.debug)?$ && $# -gt 0 ]] || { echo 'Expected CI, an explicit Parem package and a command' >&2; exit 1; }
serials=$(timeout --kill-after=2s 10s adb devices | awk '$2 == "device" { print $1 }') || exit 1
[[ $serials =~ ^emulator-[0-9]+$ ]] || { echo 'Expected exactly one disposable emulator' >&2; exit 1; }
adb_ci() { timeout --kill-after=2s 10s adb -s "$serials" "$@"; }
[[ $(adb_ci shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'Not an emulator' >&2; exit 1; }
adb_ci shell settings put global hide_error_dialogs 1 || exit 1
[[ $(adb_ci shell settings get global hide_error_dialogs | tr -d '\r') == 1 ]] || { echo 'Could not verify dialog setting' >&2; exit 1; }
mkdir -p app/build/ci-incidents || exit 1
# Clear only once, before the workload. A continuous stream limits ring-buffer loss.
adb_ci logcat -b events -c || exit 1
adb_ci logcat -b events -d -v threadtime -s am_crash:I am_anr:I '*:S' > app/build/ci-incidents/probe.txt || exit 1
adb -s "$serials" logcat -b main -b events -v threadtime -s ParemCiIncidentMonitor:I am_crash:I am_anr:I '*:S' > app/build/ci-incidents/stream.txt 2> app/build/ci-incidents/collector-error.txt &
collector=$!
collector_live=1
stop_collector() {
  [ "$collector_live" -eq 1 ] || return 0
  collector_live=0
  kill "$collector" 2>/dev/null || true
  for _ in {1..20}; do
    kill -0 "$collector" 2>/dev/null || break
    sleep 0.1
  done
  kill -KILL "$collector" 2>/dev/null || true
  wait "$collector" 2>/dev/null || true
}
trap stop_collector EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
marker() {
  local token=$1
  adb_ci shell log -p i -t ParemCiIncidentMonitor "$token" || return 1
  for _ in {1..50}; do
    grep -F 'ParemCiIncidentMonitor' app/build/ci-incidents/stream.txt | grep -Fq "$token" && return 0
    kill -0 "$collector" 2>/dev/null || return 1
    sleep 0.1
  done
  return 1
}
nonce="$$-$RANDOM-$RANDOM"
if ! marker "start-$nonce"; then
  stop_collector
  echo 'Incident collector did not acknowledge startup' >&2
  exit 1
fi
PAREM_INCIDENT_MONITORED=1 "$@"
workload_status=$?
collector_status=0
marker "end-$nonce" || collector_status=1
adb_ci logcat -b events -d -v threadtime -s am_crash:I am_anr:I '*:S' > app/build/ci-incidents/final.txt || collector_status=1
stop_collector
if ! awk -v pkg="$pkg" '
  /am_(crash|anr)[[:space:]]*:/ {
    match($0, /am_(crash|anr)[[:space:]]*:[[:space:]]*/)
    line=substr($0, RSTART + RLENGTH)
    if (line !~ /^\[/ || split(line, fields, ",") < 4) { print "Malformed incident event: " $0 > "/dev/stderr"; bad=1; next }
    process=fields[3]; gsub(/^[[:space:]]+|[[:space:]]+$/, "", process)
    if (process == pkg || index(process, pkg ":") == 1) { print "Parem crash/ANR: " $0 > "/dev/stderr"; bad=1 }
  }
  END { exit bad ? 1 : 0 }
' app/build/ci-incidents/stream.txt app/build/ci-incidents/final.txt; then collector_status=1; fi
if [ "$collector_status" -ne 0 ]; then echo 'Incident detected or collector unavailable' >&2; exit 1; fi
exit "$workload_status"
