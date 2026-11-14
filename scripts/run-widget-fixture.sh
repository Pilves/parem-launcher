#!/usr/bin/env bash
if [[ ${PAREM_INCIDENT_MONITORED:-} != 1 ]]; then exec bash scripts/ci-emulator-run.sh com.parem.launcher.debug bash "$0" "$@"; fi
# Synthetic companion installs/uninstalls are permitted only on a disposable CI emulator.
set -euo pipefail
[[ ${GITHUB_ACTIONS:-} == true ]] || { echo 'CI only' >&2; exit 1; }
[[ $(adb shell getprop ro.kernel.qemu | tr -d '\r') == 1 ]] || { echo 'Emulator required' >&2; exit 1; }
fixture=com.parem.widgetfixture
if adb shell pm path "$fixture" | grep -q '^package:'; then
    echo 'Refusing to replace an existing fixture package' >&2
    exit 1
fi
mkdir -p app/build
trap 'adb logcat -d -v threadtime > app/build/widget-fixture-logcat.txt || true; adb pull /sdcard/Android/data/com.parem.launcher.debug/files/widget-fixture-failure.png app/build/widget-fixture-failure.png >/dev/null 2>&1 || true; adb pull /data/local/tmp/parem-diag/. app/build/ >/dev/null 2>&1 || true; adb uninstall "$fixture" >/dev/null 2>&1 || true' EXIT
adb push app/build/widget-fixture/widget-fixture.apk /data/local/tmp/parem-widget-fixture.apk
for flavor in Play Full; do
    adb install -r app/build/widget-fixture/widget-fixture.apk
    ./gradlew "connected${flavor}DebugAndroidTest" \
        -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetProviderFixtureTest \
        -Pandroid.testInstrumentationRunnerArguments.widget_fixture=true
    adb uninstall "$fixture" >/dev/null 2>&1 || true
done
