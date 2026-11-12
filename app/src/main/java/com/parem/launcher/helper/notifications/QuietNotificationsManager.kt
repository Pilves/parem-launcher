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
import androidx.core.content.edit
import com.parem.launcher.data.paremPrefs
import com.parem.launcher.helper.MindfulPause

/**
 * State and storage for "Hide from shade, keep for later" (filtered notifications).
 *
 * There is no enabled pref: the state is derived from the listener component and
 * the system's notification-access list, so a restored or transferred device can
 * never show the feature as on while the component is off.
 *
 * The allowlist is an exported CSV in the launcher prefs. The keys we snoozed, when
 * we snoozed them and the `when` of any the user opened or dismissed ([QuietKeys])
 * live in a second prefs file ([KEYS_PREFS_NAME]) that export never reads and backup
 * rules exclude, because an app-chosen notification tag can carry an identifier.
 * No notification content is stored anywhere.
 */
object QuietNotificationsManager {

    enum class State { OFF, NEEDS_ACCESS, ON }

    private const val KEY_ALLOWED = "QUIET_NOTIF_ALLOWED"

    // Excluded from backup in data_extraction_rules.xml / backup_rules.xml by file name
    private const val KEYS_PREFS_NAME = "com.parem.launcher.quiet_keys"

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
     * Turns the filter off. Our hidden notifications come back to the shade
     * first, while the listener can still reach them. False when not all of them
     * could be (not connected): the rest come back only when their snooze ends.
     */
    fun turnOff(context: Context): Boolean {
        val listener = QuietNotificationListener.instance
        val released = listener?.release() == true
        try {
            listener?.requestUnbind()
        } catch (e: Exception) {
            Log.e("QuietNotifications", "requestUnbind failed", e)
        }
        setEnabled(context, false)
        return released
    }

    fun getAllowed(context: Context): Set<String> {
        return MindfulPause.parse(context.paremPrefs().getString(KEY_ALLOWED, ""))
    }

    /**
     * Apps that just became allowed get their hidden notifications back in the
     * shade now: a snoozed key would swallow their new posts until it ends.
     */
    fun setAllowed(context: Context, packages: Set<String>) {
        val added = packages - getAllowed(context)
        context.paremPrefs()
            .edit { putString(KEY_ALLOWED, packages.joinToString(",")) }
        if (added.isNotEmpty()) QuietNotificationListener.instance?.release(added)
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

    /** Our keys and marks. The first read moves the old key-only set over. */
    fun tracked(context: Context): QuietKeys.Tracked {
        val prefs = keysPrefs(context)
        val all = prefs.all
        val tracked = QuietKeys.decode(all, System.currentTimeMillis())
        if (QuietKeys.LEGACY_NAME in all) prefs.edit {
            remove(QuietKeys.LEGACY_NAME)
            for ((key, at) in tracked.snoozedAt) putLong(QuietKeys.snoozedName(key), at)
        }
        return tracked
    }

    /** Called on every snooze, so a key the filter keeps hiding never ages out. */
    fun markSnoozed(context: Context, key: String) {
        keysPrefs(context).edit { putLong(QuietKeys.snoozedName(key), System.currentTimeMillis()) }
    }

    /** [marks]: key to the `when` of the item the user opened or dismissed. */
    fun markDismissed(context: Context, marks: Map<String, Long>) {
        if (marks.isEmpty()) return
        keysPrefs(context).edit { for ((key, w) in marks) putLong(QuietKeys.dismissedName(key), w) }
    }

    // Every post asks, few keys have a mark, and getAll copies the whole file: contains() first
    fun dismissedWhen(context: Context, key: String): Long? {
        val prefs = keysPrefs(context)
        val name = QuietKeys.dismissedName(key)
        if (!prefs.contains(name)) return null
        val all = prefs.all
        return QuietKeys.liveMark(
            all[name] as? Long, all[QuietKeys.snoozedName(key)] as? Long, System.currentTimeMillis()
        )
    }

    fun clearDismissed(context: Context, key: String) {
        val prefs = keysPrefs(context)
        val name = QuietKeys.dismissedName(key)
        // Called for every post that the filter decides, so no write without a mark
        if (prefs.contains(name)) prefs.edit { remove(name) }
    }

    fun forget(context: Context, keys: Collection<String>) {
        if (keys.isEmpty()) return
        keysPrefs(context).edit {
            for (key in keys) {
                remove(QuietKeys.snoozedName(key))
                remove(QuietKeys.dismissedName(key))
            }
        }
    }

    /** Forgets keys whose snooze has surely ended ([QuietKeys.prune]); returns what is left. */
    fun prune(context: Context, listed: Set<String>): QuietKeys.Tracked {
        val tracked = tracked(context)
        val kept = QuietKeys.prune(tracked, listed, System.currentTimeMillis())
        forget(context, (tracked.snoozedAt.keys + tracked.dismissed.keys) - kept.snoozedAt.keys)
        return kept
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
