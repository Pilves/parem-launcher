package com.parem.launcher.helper.notifications

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat

/**
 * State and storage for "Hide from shade, keep for later" (filtered notifications).
 *
 * There is no enabled pref: the state is derived from the listener component and
 * the system's notification-access list, so a restored or transferred device can
 * never show the feature as on while the component is off.
 *
 * The allowlist is an exported CSV in the launcher prefs. The keys we snoozed live
 * in a second prefs file ([KEYS_PREFS_NAME]) that export never reads and backup
 * rules exclude, because an app-chosen notification tag can carry an identifier.
 * No notification content is stored anywhere.
 */
object QuietNotificationsManager {

    enum class State { OFF, NEEDS_ACCESS, ON }

    private const val PREFS_NAME = "com.parem.launcher"
    private const val KEY_ALLOWED = "QUIET_NOTIF_ALLOWED"

    // Excluded from backup in data_extraction_rules.xml / backup_rules.xml by file name
    private const val KEYS_PREFS_NAME = "com.parem.launcher.quiet_keys"
    private const val KEY_SNOOZED = "SNOOZED_KEYS"

    private fun component(context: Context) =
        ComponentName(context, QuietNotificationListener::class.java)

    fun isEnabled(context: Context): Boolean =
        context.packageManager.getComponentEnabledSetting(component(context)) ==
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun hasAccess(context: Context): Boolean =
        context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)

    fun state(context: Context): State = when {
        !isEnabled(context) -> State.OFF
        !hasAccess(context) -> State.NEEDS_ACCESS
        else -> State.ON
    }

    /** DONT_KILL_APP: with flags = 0 the system kills the launcher process. */
    fun setEnabled(context: Context, on: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            component(context),
            if (on) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    /**
     * Turns the filter off. With [dismissWaiting] our hidden notifications are
     * cancelled first; otherwise they come back when their snooze ends.
     */
    fun turnOff(context: Context, dismissWaiting: Boolean) {
        val listener = QuietNotificationListener.instance
        if (dismissWaiting) listener?.dismissAll()
        try {
            listener?.requestUnbind()
        } catch (e: Exception) {
            Log.e("QuietNotifications", "requestUnbind failed", e)
        }
        setEnabled(context, false)
    }

    fun getAllowed(context: Context): Set<String> {
        val csv = context.getSharedPreferences(PREFS_NAME, 0).getString(KEY_ALLOWED, "") ?: ""
        return csv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    fun setAllowed(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_ALLOWED, packages.joinToString(","))
            .apply()
    }

    fun defaultDialer(context: Context): String? = try {
        (context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager)?.defaultDialerPackage
    } catch (_: Exception) {
        null
    }

    fun defaultSms(context: Context): String? = try {
        Telephony.Sms.getDefaultSmsPackage(context)
    } catch (_: Exception) {
        null
    }

    private fun keysPrefs(context: Context) = context.getSharedPreferences(KEYS_PREFS_NAME, 0)

    fun getOurKeys(context: Context): Set<String> =
        keysPrefs(context).getStringSet(KEY_SNOOZED, null)?.toSet() ?: emptySet()

    // The set returned by getStringSet must not be mutated, so writes always copy
    fun setOurKeys(context: Context, keys: Set<String>) {
        keysPrefs(context).edit().putStringSet(KEY_SNOOZED, HashSet(keys)).apply()
    }

    fun addKey(context: Context, key: String) {
        val keys = getOurKeys(context)
        if (key !in keys) setOurKeys(context, keys + key)
    }

    fun removeKey(context: Context, key: String) {
        val keys = getOurKeys(context)
        if (key in keys) setOurKeys(context, keys - key)
    }

    /** Drops keys the system no longer holds as snoozed; returns what is left. */
    fun pruneKeys(context: Context, stillSnoozed: Set<String>): Set<String> {
        val keys = getOurKeys(context)
        val pruned = keys intersect stillSnoozed
        if (pruned.size != keys.size) setOurKeys(context, pruned)
        return pruned
    }

    /** The system's notification-access screen for our listener (detail page on API 30+). */
    fun openAccessSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val detail = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component(context).flattenToString()
                )
            try {
                context.startActivity(detail)
                return
            } catch (_: ActivityNotFoundException) {
                // Some OEM builds lack the detail page; the list below always exists
            }
        }
        try {
            context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.e("QuietNotifications", "No notification access settings", e)
        }
    }

    /** Per-app notification settings, where the user can make an app silent. */
    fun openAppNotificationSettings(context: Context, pkg: String) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: ActivityNotFoundException) {
            Log.e("QuietNotifications", "No app notification settings", e)
        }
    }
}
