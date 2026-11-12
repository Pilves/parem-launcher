package com.parem.launcher.helper.notifications

import android.app.ActivityOptions
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.parem.launcher.helper.AppOpenCounter
import java.util.concurrent.ConcurrentHashMap

/**
 * Moves notifications from apps the user did not allow out of the shade by
 * snoozing them for [QuietFilter.SNOOZE_MS]. The system keeps the snoozed
 * notification; we keep only its key, when we snoozed it and, once the user opened
 * or dismissed it, its `when` (see [QuietNotificationsManager]), so the quiet list
 * survives our process dying and no content is stored.
 *
 * A snoozed key swallows every later post under it, and a listener cannot
 * cancel it ([QuietKeys]). So nothing here cancels a snoozed item: allowing an
 * app or turning the filter off releases its keys at once, and opening or
 * dismissing one only marks it.
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

    /** Keys we released, to the elapsed-realtime deadline until which their repost is left alone. */
    private val passThrough = ConcurrentHashMap<String, Long>()

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        snoozedKeys()?.let {
            QuietNotificationsManager.prune(this, it)
        }
        // An app allowed while we were unbound could not be released then
        release(QuietNotificationsManager.getAllowed(this))
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
        val key = sbn.key
        val mark = QuietNotificationsManager.dismissedWhen(this, key)
        when (QuietKeys.postedAction(passThrough.remove(key), SystemClock.elapsedRealtime(), mark, sbn.notification.`when`)) {
            QuietKeys.Posted.PASS_THROUGH -> return
            QuietKeys.Posted.CANCEL -> {
                // Back from its snooze, so it is posted now and cancel works
                try {
                    cancelNotification(key)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to cancel dismissed notification", e)
                }
                QuietNotificationsManager.forget(this, listOf(key))
                return
            }
            // A newer post under that key, or a stale mark: either way the mark is spent
            QuietKeys.Posted.FILTER -> QuietNotificationsManager.clearDismissed(this, key)
        }
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
            snoozeNotification(key, QuietFilter.SNOOZE_MS)
            QuietNotificationsManager.markSnoozed(this, key)
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
            hasLauncherActivity = { hasLauncherActivity(pkg, sbn) },
            allowed = { QuietNotificationsManager.getAllowed(this) },
            alwaysAllowed = {
                QuietFilter.alwaysAllowed(
                    packageName,
                    QuietNotificationsManager.defaultDialer(this),
                    QuietNotificationsManager.defaultSms(this),
                )
            },
        )
    }

    private fun hasLauncherActivity(pkg: String, sbn: StatusBarNotification): Boolean = try {
        val launcherApps = getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        launcherApps.getActivityList(pkg, sbn.user).isNotEmpty()
    } catch (e: Exception) {
        // Unknown means untouched: a wrong "no" only leaves a notification in the shade
        false
    }

    private fun snoozed(): List<StatusBarNotification>? = try {
        snoozedNotifications?.toList()
    } catch (e: Exception) {
        // Thrown when the system has just unbound us
        Log.e(TAG, "getSnoozedNotifications failed", e)
        null
    }

    private fun snoozedKeys(): Set<String>? = snoozed()?.mapTo(HashSet()) { it.key }

    /**
     * Our hidden notifications, newest first, without the ones the user opened
     * or dismissed. Notifications the user snoozed from the shade are never
     * included: only keys we snoozed count.
     */
    fun waiting(): List<StatusBarNotification> {
        val snoozed = snoozed() ?: return emptyList()
        val ours = QuietNotificationsManager.prune(this, snoozed.mapTo(HashSet()) { it.key })
        return snoozed
            .filter { it.key in ours.snoozedAt && QuietKeys.isVisible(it.key, it.notification.`when`, ours.dismissed) }
            .sortedByDescending { it.postTime }
    }

    /**
     * Opens a hidden notification as tapping it in the shade would. [context]
     * should be the visible activity: the sheet is on screen when the user taps.
     * It stays snoozed, so it is only marked: the list hides it until the app
     * posts a newer version, and at expiry the unchanged item is cancelled.
     */
    fun open(context: Context, key: String) {
        val sbn = waiting().firstOrNull { it.key == key } ?: return
        val n = sbn.notification
        try {
            n.contentIntent?.send(context, 0, null, null, null, null, backgroundStartOptions())
            // Open counts are exported, so only the personal profile's package name lands there
            if (sbn.user == android.os.Process.myUserHandle()) AppOpenCounter.increment(this, sbn.packageName)
            // Only after the send: an item that failed to open stays in the list
            QuietNotificationsManager.markDismissed(this, mapOf(key to n.`when`))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open notification", e)
        }
    }

    /**
     * Marks everything in the quiet list as seen, as [open] does. Only our keys
     * count, so the user's own shade snoozes stay.
     */
    fun dismissAll() {
        QuietNotificationsManager.markDismissed(this, waiting().associate { it.key to it.notification.`when` })
    }

    /**
     * Brings our hidden notifications back to the shade now, alerting like new
     * arrivals: snoozing a snoozed key again reschedules its repost. [packages]
     * limits this to those apps; null releases all. Items the user opened or
     * dismissed come back too: left snoozed, their keys would swallow the app's
     * new posts. A key that could not be released stays tracked.
     */
    fun release(packages: Set<String>? = null) {
        val snoozed = snoozed() ?: return
        val keys = QuietKeys.releasable(
            snoozed.associate { it.key to it.packageName },
            QuietNotificationsManager.tracked(this).snoozedAt.keys,
            packages,
        )
        val until = SystemClock.elapsedRealtime() + QuietKeys.PASS_THROUGH_MS
        val released = keys.filter { key ->
            // In the map before the snooze, so the repost finds it even while we stay bound
            passThrough[key] = until
            try {
                // The shortest snooze the system accepts
                snoozeNotification(key, 1)
                true
            } catch (e: Exception) {
                passThrough.remove(key)
                Log.e(TAG, "Failed to release hidden notification", e)
                false
            }
        }
        QuietNotificationsManager.forget(this, released)
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
