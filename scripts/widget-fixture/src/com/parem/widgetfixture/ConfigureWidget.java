package com.parem.widgetfixture;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

/** Disposable CI-only provider: a broadcast completes the real ActivityResult after HOME. */
public class ConfigureWidget extends Activity {
    private final BroadcastReceiver complete = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            int id = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
            Log.i("WidgetFixtureLifecycle", "broadcast complete: RESULT_OK");
            ConfigureWidget.this.setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id));
            finish();
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        Log.i("WidgetFixtureLifecycle", "created, default RESULT_CANCELED");
        TextView label = new TextView(this);
        label.setText("Parem fixture configuration");
        setContentView(label);
        registerReceiver(complete, new IntentFilter("com.parem.widgetfixture.COMPLETE"));
    }

    @Override protected void onStop() {
        Log.i("WidgetFixtureLifecycle", "stopped, finishing=" + isFinishing());
        super.onStop();
    }

    @Override protected void onDestroy() {
        Log.i("WidgetFixtureLifecycle", "destroyed, finishing=" + isFinishing());
        unregisterReceiver(complete);
        super.onDestroy();
    }
}
