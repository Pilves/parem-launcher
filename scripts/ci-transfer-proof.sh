#!/usr/bin/env bash
# Destructive backup commands, restricted to the disposable GitHub Actions emulator.
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true ]] || { echo "CI only" >&2; exit 1; }
pkg=com.parem.launcher.debug
runner="$pkg.test/androidx.test.runner.AndroidJUnitRunner"
serial=$(adb devices | awk '$2 == "device" {print $1}')
[[ $serial =~ ^emulator-[0-9]+$ ]] || { echo "Expected one emulator, got: $serial" >&2; exit 1; }
adb_cmd() { timeout --kill-after=5s 90s adb -s "$serial" "$@"; }
[[ $(adb_cmd shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'Not an emulator' >&2; exit 1; }
[[ $(adb_cmd shell pm path "$pkg" | tr -d '\r') == package:* ]] || { echo 'Debug package absent' >&2; exit 1; }
old_params=$(adb_cmd shell settings get secure backup_local_transport_parameters | tr -d '\r')
old_transports=$(adb_cmd shell bmgr list transports | tr -d '\r')
old_transport=$(printf '%s\n' "$old_transports" | sed -n 's/^[[:space:]]*\*[[:space:]]*//p' | head -1)
old_enabled=$(adb_cmd shell bmgr enabled | tr -d '\r')
cleanup() {
  if [[ $old_params == null ]]; then adb_cmd shell settings delete secure backup_local_transport_parameters || true
  else adb_cmd shell settings put secure backup_local_transport_parameters "$old_params" || true; fi
  if [[ -n $old_transport ]]; then adb_cmd shell bmgr transport "$old_transport" || true; fi
  if [[ $old_enabled == *disabled* ]]; then adb_cmd shell bmgr enable false || true; fi
}
trap cleanup EXIT
printf '%s\n' "Initial backup state: $old_enabled" "$old_transports"
transport=$(printf '%s\n' "$old_transports" | sed 's/^[[:space:]]*\*[[:space:]]*//' | sed 's/^[[:space:]]*//' | grep -m1 'com.android.localtransport/' || true)
[[ -n $transport ]] || { echo 'LocalTransport unavailable' >&2; exit 1; }
adb_cmd shell settings put secure backup_local_transport_parameters is_device_transfer=true
adb_cmd shell bmgr enable true
adb_cmd shell bmgr transport "$transport"
adb_cmd shell am start -n "$pkg/com.parem.launcher.MainActivity" >/dev/null
seed() {
  local method=$1 output
  output=$(adb_cmd shell am instrument -w -e transfer_fixture true -e class "com.parem.launcher.TransferProofTest#$method" "$runner" | tr -d '\r')
  printf '%s\n' "$output"
  grep -q 'OK (1 test)' <<<"$output"
}
seed seedSource
adb_cmd shell bmgr backupnow @pm@
backup=$(adb_cmd shell bmgr backupnow "$pkg" | tr -d '\r')
printf '%s\n' "$backup"
grep -F "Package $pkg with result: Success" <<<"$backup"
sets=$(adb_cmd shell bmgr list sets | tr -d '\r')
printf '%s\n' "$sets"
token=$(printf '%s\n' "$sets" | sed -n 's/^[[:space:]]*\([0-9a-fA-F]*\)[[:space:]]*:.*/\1/p' | head -1)
[[ -n $token ]] || { echo 'No restore token found' >&2; exit 1; }
seed seedDestination
printf '%s\n' 'Destination before restore:'
adb_cmd shell run-as "$pkg" cat shared_prefs/com.parem.launcher.xml
adb_cmd shell run-as "$pkg" cat no_backup/transfer-proof-canary
restore=$(adb_cmd shell bmgr restore "$token" "$pkg" | tr -d '\r')
printf '%s\n' "$restore"
grep -Eq 'restoreFinished: 0|Restore finished with result: 0' <<<"$restore"
# Do not force-stop, clear, uninstall or start instrumentation after restore: launch
# the ordinary activity and check the production agent plus normal initialization.
adb_cmd shell am start -W -n "$pkg/com.parem.launcher.MainActivity"
restored=''
for attempt in {1..50}; do
  restored=$(adb_cmd shell run-as "$pkg" cat shared_prefs/com.parem.launcher.xml | tr -d '\r')
  grep -Fq 'name="TEXT_SIZE_SCALE"' <<<"$restored" && break
  sleep 0.2
done
canary=$(adb_cmd shell run-as "$pkg" cat no_backup/transfer-proof-canary | tr -d '\r')
printf '%s\n' 'Preferences after normal launch:' "$restored" "No-backup canary: $canary"
grep -Fq 'name="TRANSFER_PROOF_SENTINEL">source<' <<<"$restored"
grep -Eq 'name="TEXT_SIZE_SCALE" value="1(\.0+)?"' <<<"$restored"
! grep -Fq 'name="GRAYSCALE_SUPPRESSED"' <<<"$restored"
[[ $canary == destination-survived ]]
echo 'TRANSFER_PROOF_PASS: restored source, cleared device-only key, initialized Medium'
