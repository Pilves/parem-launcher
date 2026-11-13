package com.parem.quietfixture;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;

/** Disposable CI-only launcher app producing synthetic notification fixtures. */
public final class FixtureActivity extends Activity {
    private static final String CHANNEL = "quiet-fixture";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        NotificationManager manager = getSystemService(NotificationManager.class);
        String mode = getIntent().getStringExtra("mode");
        if ("cancel".equals(mode)) {
            manager.cancelAll();
        } else if ("alert1".equals(mode) || "alert2".equals(mode) || "call".equals(mode)) {
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Quiet test", NotificationManager.IMPORTANCE_DEFAULT));
            Notification.Builder builder = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Synthetic quiet test")
                .setContentText(mode)
                .setAutoCancel(false);
            if ("call".equals(mode)) builder.setCategory(Notification.CATEGORY_CALL);
            manager.notify("parem-quiet-" + mode, 1, builder.build());
        }
        finish();
    }
}
