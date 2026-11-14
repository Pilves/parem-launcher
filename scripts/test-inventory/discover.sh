#!/usr/bin/env bash
# Called only inside the CI disposable-emulator incident wrapper.
set -euo pipefail
[[ ${PAREM_INCIDENT_MONITORED:-} == 1 ]] || { echo 'inventory discovery requires the CI emulator wrapper' >&2; exit 1; }
flavor=${1:?expected Play or Full}
[[ $flavor == Play || $flavor == Full ]] || { echo 'invalid flavor' >&2; exit 1; }
out="app/build/smoke-diagnostics/inventory-${flavor,,}"
mkdir -p "$out"
# Install exactly the variant whose runner inventory will be used by the suite.
./gradlew "install${flavor}Debug" "install${flavor}DebugAndroidTest"
runner=com.parem.launcher.debug.test/androidx.test.runner.AndroidJUnitRunner
annotation=com.parem.launcher.SingleDevice
for group in all single device; do
  args=()
  [[ $group != single ]] || args=(-e annotation "$annotation")
  [[ $group != device ]] || args=(-e notAnnotation "$annotation")
  timeout --kill-after=5s 3m adb shell am instrument -w -r -e log true "${args[@]}" "$runner" >"$out/$group.txt"
done
node scripts/test-inventory/check.mjs "$out/all.txt" "$out/single.txt" "$out/device.txt" scripts/test-inventory/single-device.txt "$out/inventory.json"
