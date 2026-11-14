#!/usr/bin/env bash
# Build an isolated companion solely for the disposable-emulator widget fixture.
set -euo pipefail
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is required}}
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
src=scripts/widget-fixture
out=app/build/widget-fixture
mkdir -p "$out/classes" "$out/dex"
"$tools/aapt2" compile --dir "$src/res" -o "$out/resources.zip"
"$tools/aapt2" link -o "$out/unsigned.apk" -I "$platform" --manifest "$src/AndroidManifest.xml" -R "$out/resources.zip"
javac -source 8 -target 8 -bootclasspath "$platform" -d "$out/classes" "$src"/src/com/parem/widgetfixture/*.java
"$tools/d8" --lib "$platform" --min-api 29 --output "$out/dex" "$out"/classes/com/parem/widgetfixture/*.class
(cd "$out/dex" && zip -q ../unsigned.apk classes.dex)
"$tools/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
keytool -genkeypair -keystore "$out/fixture.keystore" -storepass android -keypass android -alias fixture \
    -dname 'CN=Parem CI widget fixture' -keyalg RSA -validity 2 -noprompt >/dev/null 2>&1
"$tools/apksigner" sign --ks "$out/fixture.keystore" --ks-key-alias fixture --ks-pass pass:android --key-pass pass:android \
    --out "$out/widget-fixture.apk" "$out/aligned.apk"
