#!/usr/bin/env bash
# Reproducible-build check for F-Droid (docs/fdroid/README.md).
#
#   scripts/check-reproducible.sh [ref] [signed.apk]
#
# Builds the unsigned release APK of <ref> (default HEAD) twice, from two fresh
# clones at different paths, and fails if they differ. With a signed APK (e.g.
# the GitHub release asset for that tag) it also compares that APK's entries
# against the build, ignoring signature files: that is what F-Droid checks
# before it publishes our signed APK. Each clone runs a full release build, one
# after the other (one gradle build at a time on the Pi).
set -euo pipefail

repo=$(cd "$(dirname "$0")/.." && pwd)
ref=${1:-HEAD}
signed=${2:-}
[ -n "$signed" ] && signed=$(realpath "$signed")
sha=$(git -C "$repo" rev-parse --verify "$ref^{commit}")
work=$(mktemp -d)
apk_path=app/build/outputs/apk/release/app-release-unsigned.apk

# Never sign: release builds pick up KEYSTORE_* from the environment.
unset KEYSTORE_FILE KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD

build() {
    local dir=$1
    git clone --quiet --no-local "$repo" "$dir"
    git -C "$dir" checkout --quiet --detach "$sha"
    [ -f "$repo/local.properties" ] && cp "$repo/local.properties" "$dir/"
    (cd "$dir" && ./gradlew --no-daemon --quiet assembleRelease)
}

# Per-entry sha256 list of an APK, minus the v1 signature files.
entries() {
    unzip -Z1 "$1" | grep -Ev '^META-INF/[^/]+\.(SF|RSA|DSA|EC|MF)$' | sort | while IFS= read -r e; do
        printf '%s  %s\n' "$(unzip -p "$1" "$e" | sha256sum | cut -d' ' -f1)" "$e"
    done
}

echo "Building $sha in $work/a and $work/build-b"
build "$work/a"
build "$work/build-b"

a="$work/a/$apk_path"
b="$work/build-b/$apk_path"
status=0
if cmp -s "$a" "$b"; then
    echo "OK: both builds are byte-identical ($(sha256sum "$a" | cut -d' ' -f1))"
else
    echo "FAIL: the two builds differ. Differing entries:"
    diff <(entries "$a") <(entries "$b") || true
    status=1
fi

if [ -n "$signed" ]; then
    if diff <(entries "$signed") <(entries "$a") >"$work/signed.diff"; then
        echo "OK: $signed matches the build (signature files ignored)"
    else
        echo "FAIL: $signed differs from the build:"
        cat "$work/signed.diff"
        status=1
    fi
fi

if [ "$status" -eq 0 ]; then
    rm -rf "$work"
else
    echo "Builds kept in $work for diffoscope"
fi
exit "$status"
