#!/usr/bin/env bash
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
trap 'adb logcat -d -v threadtime > app/build/widget-fixture-logcat.txt || true; adb uninstall "$fixture" >/dev/null 2>&1 || true' EXIT
adb push app/build/widget-fixture/widget-fixture.apk /data/local/tmp/parem-widget-fixture.apk
for flavor in Play Full; do
    adb install -r app/build/widget-fixture/widget-fixture.apk
    ./gradlew "connected${flavor}DebugAndroidTest" \
        -Pandroid.testInstrumentationRunnerArguments.class=com.parem.launcher.WidgetProviderFixtureTest \
        -Pandroid.testInstrumentationRunnerArguments.widget_fixture=true
    adb uninstall "$fixture" >/dev/null 2>&1 || true
done
