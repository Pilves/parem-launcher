#!/usr/bin/env bash
# Fails if an unminified play APK still holds in-app adb pairing. Point it at a
# debug APK: R8 renames classes in a release build, so a clean result there
# would prove nothing (docs/design/DECISIONS.md).
#
#   scripts/check-play-build.sh app-play-debug.apk [aapt2]
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
apk=$1
aapt2=${2:-$(find "${ANDROID_HOME:-$HOME/Android/Sdk}/build-tools" -name aapt2 | sort | tail -1)}
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$work"

# Captured first: under pipefail, grep -q closing the pipe early reads as "no match"
permissions=$("$aapt2" dump permissions "$apk")
manifest=$("$aapt2" dump xmltree --file AndroidManifest.xml "$apk")
strings=$("$aapt2" dump strings "$apk")

status=0
if grep -alE 'com/parem/launcher/helper/(GrayscalePairing|adb/)' "$work"/classes*.dex; then
    echo "FAIL: pairing classes in $apk"
    status=1
fi
# Not the dex: androidx.core names this permission itself, whether or not the app holds it.
if grep -rl POST_NOTIFICATIONS "$repo/app/src/main" "$repo/app/src/play" || grep -q POST_NOTIFICATIONS <<<"$permissions"; then
    echo "FAIL: POST_NOTIFICATIONS in $apk"
    status=1
fi
if grep -q GrayscalePairing <<<"$manifest"; then
    echo "FAIL: pairing receiver in the manifest of $apk"
    status=1
fi
if grep -q 'Wireless debugging' <<<"$strings"; then
    echo "FAIL: pairing text in $apk"
    status=1
fi
[ "$status" -eq 0 ] && echo "OK: no pairing code, permission, receiver or text in $apk"
exit "$status"
