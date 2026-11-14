#!/usr/bin/env bash
# Build 120 synthetic widget providers for disposable CI-emulator profiling only.
set -euo pipefail
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:?Android SDK is required}}
tools="$sdk/build-tools/36.0.0"
platform="$sdk/platforms/android-36/android.jar"
out=app/build/widget-picker-profile-fixture
src="$out/src/com/parem/widgetstress"
res="$out/res"
mkdir -p "$src" "$res/layout" "$res/xml" "$res/drawable" "$out/classes" "$out/dex"
cat > "$res/layout/widget.xml" <<'XML'
<TextView xmlns:android="http://schemas.android.com/apk/res/android" android:id="@android:id/text1" android:layout_width="match_parent" android:layout_height="match_parent" android:text="Synthetic preview" android:gravity="center" android:background="#325a66" android:textColor="#ffffff" />
XML
cat > "$res/xml/provider.xml" <<'XML'
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android" android:minWidth="120dp" android:minHeight="100dp" android:initialLayout="@layout/widget" android:previewLayout="@layout/widget" android:previewImage="@drawable/preview" android:resizeMode="horizontal|vertical" android:widgetCategory="home_screen" />
XML
cat > "$res/drawable/preview.xml" <<'XML'
<shape xmlns:android="http://schemas.android.com/apk/res/android" android:shape="rectangle"><solid android:color="#325a66" /><size android:width="96dp" android:height="96dp" /></shape>
XML
manifest="$out/AndroidManifest.xml"
cat > "$manifest" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.parem.widgetstress"><uses-sdk android:minSdkVersion="29" android:targetSdkVersion="35" /><application android:label="Parem stress widgets" android:theme="@android:style/Theme.Material.Light.NoActionBar"><activity android:name=".Publisher" android:exported="true" />
XML
for i in $(seq -w 0 119); do
  cat >> "$manifest" <<XML
<receiver android:name=".Provider$i" android:exported="true" android:label="Stress widget $i"><intent-filter><action android:name="android.appwidget.action.APPWIDGET_UPDATE" /></intent-filter><meta-data android:name="android.appwidget.provider" android:resource="@xml/provider" /></receiver>
XML
  printf 'package com.parem.widgetstress; public final class Provider%s extends android.appwidget.AppWidgetProvider {}\n' "$i" > "$src/Provider$i.java"
done
printf '</application></manifest>\n' >> "$manifest"
cat > "$src/Publisher.java" <<'JAVA'
package com.parem.widgetstress;
import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.os.Bundle;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.TextView;
public final class Publisher extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int layout = getResources().getIdentifier("widget", "layout", getPackageName());
        AppWidgetManager manager = AppWidgetManager.getInstance(this);
        TextView result = new TextView(this); result.setText("Waiting for synthetic providers"); setContentView(result);
        new Thread(new Runnable() { @Override public void run() {
            int ok = 0;
            if (android.os.Build.VERSION.SDK_INT >= 35) {
                for (int i = 0; i < 120; i++) {
                    String suffix = String.format(java.util.Locale.ROOT, "%03d", i);
                    RemoteViews views = new RemoteViews(getPackageName(), layout);
                    views.setTextViewText(android.R.id.text1, "Synthetic preview " + suffix);
                    boolean published = false;
                    long deadline = android.os.SystemClock.elapsedRealtime() + 30000;
                    while (!published && android.os.SystemClock.elapsedRealtime() < deadline) {
                        try {
                            published = manager.setWidgetPreview(new ComponentName(getPackageName(), getPackageName() + ".Provider" + suffix), AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN, views);
                        } catch (IllegalArgumentException notRegisteredYet) {
                            try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                        }
                        if (!published) {
                            try { Thread.sleep(250); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
                        }
                    }
                    if (!published) break;
                    ok++;
                }
            }
            final int publishedCount = ok;
            Log.i("ParemWidgetStress", "generated_previews=" + publishedCount);
            runOnUiThread(new Runnable() { @Override public void run() { result.setText("Published " + publishedCount + " generated previews"); } });
        } }).start();
    }
}
JAVA
"$tools/aapt2" compile --dir "$res" -o "$out/resources.zip"
"$tools/aapt2" link -o "$out/unsigned.apk" -I "$platform" --manifest "$manifest" -R "$out/resources.zip"
javac -source 8 -target 8 -bootclasspath "$platform" -d "$out/classes" "$src"/*.java
"$tools/d8" --lib "$platform" --min-api 29 --output "$out/dex" "$out"/classes/com/parem/widgetstress/*.class
(cd "$out/dex" && zip -q ../unsigned.apk classes.dex)
"$tools/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
keytool -genkeypair -keystore "$out/stress.keystore" -storepass android -keypass android -alias stress -dname 'CN=Parem CI widget stress fixture' -keyalg RSA -validity 2 -noprompt >/dev/null 2>&1
"$tools/apksigner" sign --ks "$out/stress.keystore" --ks-key-alias stress --ks-pass pass:android --key-pass pass:android --out "$out/widget-stress.apk" "$out/aligned.apk"
