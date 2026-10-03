package com.parem.launcher.helper.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Moves notifications from apps the user did not allow out of the shade by
 * snoozing them for [QuietFilter.SNOOZE_MS]. The system keeps the snoozed
 * notification; we keep only its key (see [QuietNotificationsManager]), so the
 * quiet list survives our process dying and no content is stored.
 *
 * Declared android:enabled="false": a disabled component is never bound, so
 * nothing runs here until the user turns the filter on.
 */
class QuietNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "QuietNotifications"

        /** Non-null only while the system has us bound and connected. */
        @Volatile
        var instance: QuietNotificationListener? = null
            private set
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) snoozedKeys()?.let {
            QuietNotificationsManager.pruneKeys(this, it)
        }
    }

    override fun onListenerDisconnected() {
        instance = null
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification, rankingMap: RankingMap?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        // API 31+ filters silent notifications out through the manifest's
        // default_filter_types; older versions deliver them, so drop them here
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && rankingMap != null) {
            val ranking = Ranking()
            if (rankingMap.getRanking(sbn.key, ranking) &&
                ranking.importance < NotificationManager.IMPORTANCE_DEFAULT
            ) return
        }
        try {
            if (!shouldSilence(sbn)) return
            snoozeNotification(sbn.key, QuietFilter.SNOOZE_MS)
            QuietNotificationsManager.addKey(this, sbn.key)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide notification", e)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        onNotificationPosted(sbn, null)
    }

    private fun shouldSilence(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        val pkg = sbn.packageName
        return QuietFilter.shouldSilence(
            pkg = pkg,
            category = n.category,
            isOngoing = sbn.isOngoing || n.flags and Notification.FLAG_FOREGROUND_SERVICE != 0,
            isClearable = sbn.isClearable,
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isMedia = n.extras?.containsKey(Notification.EXTRA_MEDIA_SESSION) == true,
            hasLauncherActivity = hasLauncherActivity(pkg, sbn),
            allowed = QuietNotificationsManager.getAllowed(this),
            alwaysAllowed = QuietFilter.alwaysAllowed(
                packageName,
                QuietNotificationsManager.defaultDialer(this),
                QuietNotificationsManager.defaultSms(this),
            ),
        )
    }

    private fun hasLauncherActivity(pkg: String, sbn: StatusBarNotification): Boolean = try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getActivityList(pkg, sbn.user).isNotEmpty()
    } catch (e: Exception) {
        // Unknown means untouched: a wrong "no" only leaves a notification in the shade
        false
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun snoozed(): List<StatusBarNotification>? = try {
        snoozedNotifications?.toList()
    } catch (e: Exception) {
        // Thrown when the system has just unbound us
        Log.e(TAG, "getSnoozedNotifications failed", e)
        null
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun snoozedKeys(): Set<String>? = snoozed()?.mapTo(HashSet()) { it.key }

    /**
     * Our hidden notifications, newest first. Notifications the user snoozed
     * from the shade are never included: only keys we snoozed count.
     */
    fun waiting(): List<StatusBarNotification> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return emptyList()
        val snoozed = snoozed() ?: return emptyList()
        val ours = QuietNotificationsManager.pruneKeys(this, snoozed.mapTo(HashSet()) { it.key })
        return snoozed.filter { it.key in ours }.sortedByDescending { it.postTime }
    }

    /**
     * Opens a hidden notification as tapping it in the shade would. [context]
     * should be the visible activity: the sheet is on screen when the user taps.
     */
    fun open(context: Context, key: String) {
        val sbn = waiting().firstOrNull { it.key == key }
        QuietNotificationsManager.removeKey(this, key)
        if (sbn == null) return
        val n = sbn.notification
        try {
            n.contentIntent?.send(context, 0, null, null, null, null, backgroundStartOptions())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification", e)
        }
        if (n.flags and Notification.FLAG_AUTO_CANCEL != 0) {
            try {
                cancelNotification(key)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to cancel opened notification", e)
            }
        }
    }

    /** Cancels only the notifications we hid; the user's own shade snoozes stay. */
    fun dismissAll() {
        val keys = QuietNotificationsManager.getOurKeys(this)
        if (keys.isNotEmpty()) {
            try {
                cancelNotifications(keys.toTypedArray())
            } catch (e: Exception) {
                Log.e(TAG, "Failed to dismiss hidden notifications", e)
            }
        }
        QuietNotificationsManager.setOurKeys(this, emptySet())
    }

    /**
     * API 34+ needs an explicit opt-in for a PendingIntent sent by us to start
     * an activity. ALLOWED is deprecated at 36 in favour of ALLOW_IF_VISIBLE,
     * which fits: the quiet-list sheet is visible when the user taps.
     */
    @Suppress("DEPRECATION")
    private fun backgroundStartOptions(): Bundle? = when {
        Build.VERSION.SDK_INT >= 36 -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOW_IF_VISIBLE
            ).toBundle()
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(
                ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
            ).toBundle()
        else -> null
    }
}
