package com.parem.launcher.helper

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.parem.launcher.R
import com.parem.launcher.helper.adb.WirelessAdb

/**
 * The in-app grant (Android 11+): Wireless debugging's pairing dialog closes
 * when Settings loses focus, so the code is typed into a notification reply
 * instead, and [Receiver] pairs with the phone's own adbd and runs `pm grant`.
 * Finishing the grant turns grayscale on: tapping "Grayscale" was the intent.
 */
object GrayscalePairing {

    private const val TAG = "GrayscalePairing"
    private const val CHANNEL_ID = "grayscale_pairing"
    private const val NOTIFICATION_ID = 0x9a1
    private const val KEY_CODE = "pairing_code"

    fun isSupported() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts the code-entry notification and opens Developer options (or About phone if they are off). */
    @RequiresApi(Build.VERSION_CODES.R)
    fun start(context: Context) {
        post(context, context.getString(R.string.grayscale_pair_notif_text), withReply = true)
        val devOn = Settings.Global.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
        val intent = if (devOn) {
            // Settings scrolls to and highlights this preference key
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).putExtra(":settings:fragment_args_key", "toggle_adb_wireless")
        } else {
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    private fun post(context: Context, text: String, withReply: Boolean, timeoutMs: Long = 0) {
        if (!canNotify(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && nm.getNotificationChannel(CHANNEL_ID) == null) {
            // Default importance, not high: a heads-up would cover the code it asks for
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.grayscale), NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_info)
            .setContentTitle(context.getString(R.string.grayscale_pair_notif_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOnlyAlertOnce(true)
            .setSilent(true)
        if (timeoutMs > 0) builder.setTimeoutAfter(timeoutMs)
        if (withReply) {
            val remoteInput = RemoteInput.Builder(KEY_CODE)
                .setLabel(context.getString(R.string.grayscale_pair_code_hint))
                .build()
            // Mutable: the system writes the typed code into this intent
            val pending = PendingIntent.getBroadcast(
                context, 0, Intent(context, Receiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, context.getString(R.string.grayscale_pair_enter_code), pending)
                    .addRemoteInput(remoteInput)
                    .build()
            )
        }
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "notification permission missing", e)
        }
    }

    private fun failureText(context: Context, failure: WirelessAdb.Failure?): String = context.getString(
        when (failure) {
            WirelessAdb.Failure.PAIRING_NOT_FOUND -> R.string.grayscale_pair_err_not_found
            WirelessAdb.Failure.WRONG_CODE -> R.string.grayscale_pair_err_code
            WirelessAdb.Failure.CONNECT_NOT_FOUND -> R.string.grayscale_pair_err_connect
            WirelessAdb.Failure.CONNECTION, null -> R.string.grayscale_pair_err_generic
        }
    )

    class Receiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
            val code = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_CODE)
                ?.filter { it.isDigit() }?.toString().orEmpty()
            val app = context.applicationContext
            if (code.length != 6) {
                post(app, app.getString(R.string.grayscale_pair_err_code), withReply = true)
                return
            }
            // Replace the reply spinner straight away
            post(app, app.getString(R.string.grayscale_pair_working), withReply = false)
            val pending = goAsync()
            Thread {
                try {
                    WirelessAdb.pairAndRun(app, code, "pm grant ${app.packageName} ${Manifest.permission.WRITE_SECURE_SETTINGS}")
                    if (GrayscaleController.isGranted(app)) {
                        GrayscaleController.setManual(app, true)
                        post(app, app.getString(R.string.grayscale_pair_done), withReply = false, timeoutMs = 15_000)
                    } else {
                        // pm refused (some OEMs need an extra developer toggle)
                        post(app, app.getString(R.string.grayscale_pair_err_refused), withReply = true)
                    }
                } catch (e: WirelessAdb.AdbException) {
                    Log.w(TAG, "pairing failed", e)
                    post(app, failureText(app, e.failure), withReply = true)
                } catch (e: Exception) {
                    Log.w(TAG, "pairing failed", e)
                    post(app, failureText(app, null), withReply = true)
                } finally {
                    pending.finish()
                }
            }.start()
        }
    }
}
