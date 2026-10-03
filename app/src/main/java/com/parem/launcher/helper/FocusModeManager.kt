package com.parem.launcher.helper

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

/**
 * Manages Focus Mode state: enabled flag, auto-expire timer, and app whitelist.
 * All state is persisted directly in SharedPreferences ("com.parem.launcher").
 *
 * Focus mode prevents launching non-whitelisted apps. The caller is responsible
 * for checking [isAppAllowed] and showing feedback to the user.
 */
object FocusModeManager {

    private const val PREFS_NAME = "com.parem.launcher"
    private const val KEY_ENABLED = "FOCUS_MODE_ENABLED"
    private const val KEY_END_TIME = "FOCUS_MODE_END_TIME"
    private const val KEY_WHITELIST = "FOCUS_MODE_WHITELIST"
    private const val KEY_SCHEDULE = "FOCUS_SCHEDULE"
    private const val KEY_SCHEDULE_ENABLED = "FOCUS_SCHEDULE_ENABLED"
    // Epoch ms; device-moment state, so Prefs excludes it from export
    private const val KEY_SKIP_UNTIL = "FOCUS_SCHEDULE_SKIP_UNTIL"

    private const val MAX_WHITELIST_SIZE = 5
    private const val END_TIME_UNLIMITED = -1L

    /**
     * Returns true if focus mode is currently active: a timed or unlimited
     * session is running, or a schedule window contains now and isn't paused.
     */
    fun isActive(context: Context): Boolean =
        activeNow(context.getSharedPreferences(PREFS_NAME, 0))

    private fun activeNow(p: SharedPreferences): Boolean {
        val now = Calendar.getInstance()
        return timedActive(p, now.timeInMillis) || scheduleActive(p, now)
    }

    private fun timedActive(p: SharedPreferences, nowMs: Long): Boolean {
        if (!p.getBoolean(KEY_ENABLED, false)) return false
        val endTime = p.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        return endTime == END_TIME_UNLIMITED || nowMs < endTime
    }

    private fun scheduleActive(p: SharedPreferences, now: Calendar): Boolean {
        if (!p.getBoolean(KEY_SCHEDULE_ENABLED, false)) return false
        if (now.timeInMillis < p.getLong(KEY_SKIP_UNTIL, 0L)) return false
        val (day, minute) = FocusSchedule.dayAndMinute(now)
        return FocusSchedule.isActive(schedule(p), day, minute)
    }

    private fun schedule(p: SharedPreferences) = FocusSchedule.parse(p.getString(KEY_SCHEDULE, ""))

    fun getSchedule(context: Context): List<FocusSchedule.Window> =
        schedule(context.getSharedPreferences(PREFS_NAME, 0))

    fun setSchedule(context: Context, windows: List<FocusSchedule.Window>) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_SCHEDULE, FocusSchedule.serialize(windows))
            .apply()
    }

    fun isScheduleEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, 0).getBoolean(KEY_SCHEDULE_ENABLED, false)

    fun setScheduleEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_SCHEDULE_ENABLED, enabled)
            .apply()
    }

    /**
     * The user's override: pauses the schedule until the current run ends, or
     * for an always-on run until the current single window ends (at most 24 h).
     * No-op when no window is active. Only the sheet's Disable button calls this;
     * [disable] must stay free of it or an expiring timed session
     * ([checkAndExpire]) would silently pause the schedule.
     */
    fun pauseSchedule(context: Context) {
        val p = context.getSharedPreferences(PREFS_NAME, 0)
        val now = Calendar.getInstance()
        if (!scheduleActive(p, now)) return
        val windows = schedule(p)
        val (day, minute) = FocusSchedule.dayAndMinute(now)
        val runEnd = FocusSchedule.activeUntil(windows, day, minute) ?: return
        val end = if (runEnd == FocusSchedule.NO_END) {
            FocusSchedule.currentWindowEnd(windows, day, minute) ?: return
        } else runEnd
        p.edit().putLong(KEY_SKIP_UNTIL, FocusSchedule.toEpochMs(now, end)).apply()
    }

    /** What the active sheet and the block toast say about the current session. */
    sealed class ActiveLabel {
        data class Timed(val remaining: String) : ActiveLabel()
        data class ScheduledUntil(val epochMs: Long) : ActiveLabel()
        object ScheduledNoEnd : ActiveLabel()
        object Unlimited : ActiveLabel()
    }

    /**
     * Wall-clock end for "Scheduled until %s": time only (12/24 h per system
     * setting) within 24 h, otherwise short weekday + time ("Tue 07:00").
     */
    fun formatScheduledEnd(context: Context, epochMs: Long): String {
        val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(epochMs))
        if (epochMs - System.currentTimeMillis() < 24 * 60 * 60_000L) return time
        val weekday = java.text.SimpleDateFormat("EEE", java.util.Locale.getDefault()).format(java.util.Date(epochMs))
        return "$weekday $time"
    }

    /**
     * Null when focus is off. An unlimited manual session wins (it has no end);
     * otherwise whichever of the timed session and the schedule run ends later.
     */
    fun getActiveLabel(context: Context): ActiveLabel? {
        val p = context.getSharedPreferences(PREFS_NAME, 0)
        val now = Calendar.getInstance()
        val timed = timedActive(p, now.timeInMillis)
        val timedEnd = p.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (timed && timedEnd == END_TIME_UNLIMITED) return ActiveLabel.Unlimited
        val timedLabel = getRemainingTimeFormatted(context)?.let { ActiveLabel.Timed(it) }
        if (!scheduleActive(p, now)) return if (timed) timedLabel else null
        val (day, minute) = FocusSchedule.dayAndMinute(now)
        val runEnd = FocusSchedule.activeUntil(schedule(p), day, minute) ?: return timedLabel
        if (runEnd == FocusSchedule.NO_END) return ActiveLabel.ScheduledNoEnd
        val scheduledEnd = FocusSchedule.toEpochMs(now, runEnd)
        return if (timed && timedEnd > scheduledEnd && timedLabel != null) timedLabel
        else ActiveLabel.ScheduledUntil(scheduledEnd)
    }

    /**
     * Enables focus mode.
     * @param durationMinutes Duration in minutes. Pass -1 for unlimited (until manually disabled).
     */
    fun enable(context: Context, durationMinutes: Int) {
        val endTime = if (durationMinutes == -1) {
            END_TIME_UNLIMITED
        } else {
            System.currentTimeMillis() + durationMinutes * 60_000L
        }
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_ENABLED, true)
            .putLong(KEY_END_TIME, endTime)
            .apply()
    }

    /**
     * Disables focus mode and resets the end time.
     */
    fun disable(context: Context) {
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putBoolean(KEY_ENABLED, false)
            .putLong(KEY_END_TIME, END_TIME_UNLIMITED)
            .apply()
    }

    /**
     * Returns true if the given app is allowed to launch.
     * An app is allowed if focus mode is not active OR the app is in the whitelist.
     * Reads SharedPreferences once to avoid redundant lookups from isActive() + getWhitelist().
     */
    fun isAppAllowed(context: Context, packageName: String): Boolean {
        val p = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!activeNow(p)) return true
        // Never block the phone app, whitelisted or not: being unable to place a call
        // is worse than a broken focus session
        try {
            val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
            if (telecom?.defaultDialerPackage == packageName) return true
        } catch (_: Exception) {}
        val csv = p.getString(KEY_WHITELIST, "") ?: ""
        if (csv.isBlank()) return false
        val whitelist = csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
        return packageName in whitelist
    }

    /**
     * Returns the current whitelist as a set of package names.
     */
    fun getWhitelist(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val csv = prefs.getString(KEY_WHITELIST, "") ?: ""
        if (csv.isBlank()) return emptySet()
        return csv.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    /**
     * Sets the whitelist. At most [MAX_WHITELIST_SIZE] apps are stored.
     */
    fun setWhitelist(context: Context, packages: Set<String>) {
        val limited = packages.take(MAX_WHITELIST_SIZE)
        val csv = limited.joinToString(",")
        context.getSharedPreferences(PREFS_NAME, 0)
            .edit()
            .putString(KEY_WHITELIST, csv)
            .apply()
    }

    /**
     * Returns a human-readable remaining time string ("Xm" or "Xh Ym"),
     * or null if focus mode is inactive or set to unlimited.
     */
    fun getRemainingTimeFormatted(context: Context): String? {
        if (!isActive(context)) return null
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        val endTime = prefs.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (endTime == END_TIME_UNLIMITED) return null

        val remainingMs = endTime - System.currentTimeMillis()
        if (remainingMs <= 0) return null

        val totalMinutes = (remainingMs / 60_000).toInt()
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60

        return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
    }

    /**
     * If focus mode is active with a timed end and the time has passed, auto-disables it.
     * Call this from onResume to ensure expired sessions are cleaned up.
     */
    fun checkAndExpire(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, 0)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val endTime = prefs.getLong(KEY_END_TIME, END_TIME_UNLIMITED)
        if (endTime == END_TIME_UNLIMITED) return
        if (System.currentTimeMillis() > endTime) {
            disable(context)
        }
    }
}
