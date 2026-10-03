package com.parem.launcher.helper

import android.accessibilityservice.AccessibilityServiceInfo
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserHandle
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.accessibility.AccessibilityManager
import com.parem.launcher.R
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * System-facing intents and checks: notification drawer, stock-app launches
 * (dialer/camera/alarm/calendar), app info, uninstall, accessibility state.
 */
fun openAppInfo(context: Context, userHandle: UserHandle, packageName: String) {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    // Resolve via LauncherApps for the given profile: getLaunchIntentForPackage only
    // sees the main profile, so work-profile apps failed here (upstream fix #446)
    val component = launcher.getActivityList(packageName, userHandle).firstOrNull()?.componentName
    if (component != null)
        launcher.startAppDetailsActivity(component, userHandle, null, null)
    else
        context.showToast(context.getString(R.string.unable_to_open_app))
}

@SuppressLint("WrongConstant", "PrivateApi")
fun expandNotificationDrawer(context: Context) {
    // Source: https://stackoverflow.com/a/51132142
    try {
        val statusBarService = context.getSystemService("statusbar")
        val statusBarManager = Class.forName("android.app.StatusBarManager")
        val method = statusBarManager.getMethod("expandNotificationsPanel")
        method.invoke(statusBarService)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to expand notification drawer", e)
    }
}

fun openDialerApp(context: Context) {
    try {
        val sendIntent = Intent(Intent.ACTION_DIAL)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to open dialer app", e)
    }
}

fun openCameraApp(context: Context) {
    try {
        val sendIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        context.startActivity(sendIntent)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to open camera app", e)
    }
}

fun openAlarmApp(context: Context) {
    try {
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to open alarm app", e)
    }
}

fun openCalendar(context: Context) {
    try {
        val calendarUri = CalendarContract.CONTENT_URI
            .buildUpon()
            .appendPath("time")
            .build()
        context.startActivity(Intent(Intent.ACTION_VIEW, calendarUri))
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_APP_CALENDAR)
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("Utils", "Failed to open calendar", e)
        }
    }
}

/**
 * Starts an omnibox quick action. Alarm and timer go straight to the clock app
 * (SET_ALARM is normal-level, no prompt); an event opens the calendar's editor
 * prefilled and the user saves it there, so no calendar permission is needed.
 * False when no app handles it.
 */
fun fireQuickAction(context: Context, action: QuickAction): Boolean {
    val intent = when (action) {
        is QuickAction.Alarm -> Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, action.hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
        is QuickAction.Timer -> Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            .apply { action.label?.let { putExtra(AlarmClock.EXTRA_MESSAGE, it) } }
        is QuickAction.Event -> {
            val end = if (action.allDay) action.start.plusDays(1) else action.start.plusHours(1)
            Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI)
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, epochMillis(action.start))
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, epochMillis(end))
                .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, action.allDay)
                .putExtra(CalendarContract.Events.TITLE, action.title)
        }
    }
    return try {
        context.startActivity(intent)
        true
    } catch (e: Exception) {
        Log.e("Utils", "No app for quick action", e)
        false
    }
}

/**
 * The tip line for a quick action, in the system's 12/24 h setting and the
 * locale's own date order. The parser never guesses am/pm, so this preview is
 * where the user catches 07:30 vs 19:30.
 */
fun quickActionPreview(context: Context, action: QuickAction): String {
    val timeSkeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hma"
    return when (action) {
        is QuickAction.Alarm -> context.getString(
            R.string.alarm_hint,
            withLabel(formatSkeleton(LocalDate.now().atTime(action.hour, action.minute), timeSkeleton), action.label),
        )
        is QuickAction.Timer -> context.getString(
            R.string.timer_hint, withLabel(QuickActionParser.durationText(action.seconds), action.label),
        )
        is QuickAction.Event -> context.getString(
            R.string.event_hint,
            if (action.allDay) context.getString(R.string.event_all_day, formatSkeleton(action.start, "EEEdMMM"))
            else formatSkeleton(action.start, "EEEdMMM$timeSkeleton"),
            action.title,
        )
    }
}

private fun withLabel(text: String, label: String?) = if (label == null) text else "$text · $label"

private fun formatSkeleton(time: LocalDateTime, skeleton: String): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return android.icu.text.SimpleDateFormat(pattern, locale).format(Date(epochMillis(time)))
}

private fun epochMillis(time: LocalDateTime) = time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

fun isAccessServiceEnabled(context: Context): Boolean {
    val enabled = try {
        Settings.Secure.getInt(context.applicationContext.contentResolver, Settings.Secure.ACCESSIBILITY_ENABLED)
    } catch (e: Exception) {
        0
    }
    if (enabled == 1) {
        val enabledServicesString: String? = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServicesString?.contains(context.packageName + "/" + MyAccessibilityService::class.java.name) ?: false
    }
    return false
}

/**
 * True when the system has the service bound. The enabled-services setting can
 * still list a service Android has stopped (crash, restriction), and then the
 * lock click reaches nobody; this list only holds services that are running.
 * Fails open (true) so a query error keeps the old click-and-hope behaviour.
 */
fun isAccessServiceBound(context: Context): Boolean = try {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
        val service = it.resolveInfo?.serviceInfo
        service?.packageName == context.packageName && service.name == MyAccessibilityService::class.java.name
    }
} catch (e: Exception) {
    true
}

fun Context.uninstall(packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE)
    intent.data = Uri.parse("package:$packageName")
    startActivity(intent)
}
