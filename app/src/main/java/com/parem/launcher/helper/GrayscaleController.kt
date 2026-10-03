package com.parem.launcher.helper

import android.Manifest
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.parem.launcher.helper.GrayscalePolicy.Action
import com.parem.launcher.helper.GrayscalePolicy.State
import java.util.concurrent.TimeUnit

/**
 * System grayscale through the Secure-settings daltonizer. Needs
 * WRITE_SECURE_SETTINGS, which only adb can grant (GrayscaleGrant does it on
 * the phone itself; the WebUSB page and the adb command are the fallbacks).
 *
 * [reconcile] is the only writer and is idempotent: it runs at launcher start,
 * on every home resume, after each trigger change and from the focus-end
 * worker, so a crash or a missed callback heals on the next home press.
 */
object GrayscaleController {

    private const val TAG = "Grayscale"
    private const val PREFS_NAME = "com.parem.launcher"

    // Exported: the user's choices
    const val KEY_ON_FOCUS = "GRAYSCALE_ON_FOCUS"
    const val KEY_ON_LIMIT = "GRAYSCALE_ON_LIMIT"
    const val KEY_APPS = "GRAYSCALE_APPS"
    // Device-moment state, excluded from export
    const val KEY_MANUAL = "GRAYSCALE_MANUAL"
    const val KEY_APPLIED = "GRAYSCALE_APPLIED"
    const val KEY_SUPPRESSED = "GRAYSCALE_SUPPRESSED"
    const val KEY_LIMIT_OVERRIDE = "GRAYSCALE_LIMIT_OVERRIDE"
    const val KEY_LEFT_SINCE_OVERRIDE = "GRAYSCALE_LEFT_SINCE_OVERRIDE"
    const val KEY_PREV_ENABLED = "GRAYSCALE_PREV_ENABLED"
    const val KEY_PREV_MODE = "GRAYSCALE_PREV_MODE"
    const val KEY_APP_FG = "GRAYSCALE_APP_FG"

    // @hide constants in Settings.Secure
    private const val SECURE_ENABLED = "accessibility_display_daltonizer_enabled"
    private const val SECURE_MODE = "accessibility_display_daltonizer"
    // AccessibilityManager.DALTONIZER_CORRECT_DEUTERANOMALY, the system default
    // mode; restored when the mode had never been set
    private const val DEFAULT_MODE = 12
    private const val MODE_UNSET = -1

    private const val FOCUS_END_WORK = "grayscale_focus_end"
    private const val KEY_FOCUS_END_TIME = "FOCUS_MODE_END_TIME"

    private fun prefs(context: Context): SharedPreferences = context.getSharedPreferences(PREFS_NAME, 0)

    fun isGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun isManual(context: Context) = prefs(context).getBoolean(KEY_MANUAL, false)
    fun isOnFocus(context: Context) = prefs(context).getBoolean(KEY_ON_FOCUS, false)
    fun isOnLimit(context: Context) = prefs(context).getBoolean(KEY_ON_LIMIT, false)

    /** "Now" is an explicit choice inside Parem, so it also lifts a user override. */
    fun setManual(context: Context, on: Boolean) {
        prefs(context).edit(commit = true) {
            putBoolean(KEY_MANUAL, on)
            putBoolean(KEY_SUPPRESSED, false)
        }
        reconcile(context)
    }

    fun setOnFocus(context: Context, on: Boolean) {
        prefs(context).edit(commit = true) { putBoolean(KEY_ON_FOCUS, on) }
        reconcile(context)
    }

    fun setOnLimit(context: Context, on: Boolean) {
        prefs(context).edit(commit = true) { putBoolean(KEY_ON_LIMIT, on) }
        reconcile(context)
    }

    fun reconcile(context: Context) {
        val p = prefs(context)
        val cr = context.contentResolver
        val current = read(cr)
        val focusActive = FocusModeManager.isActive(context)
        val onFocus = p.getBoolean(KEY_ON_FOCUS, false)
        val desired = GrayscalePolicy.desired(
            manual = p.getBoolean(KEY_MANUAL, false),
            onFocus = onFocus,
            focusActive = focusActive,
            onLimit = p.getBoolean(KEY_ON_LIMIT, false),
            limitOverride = p.getBoolean(KEY_LIMIT_OVERRIDE, false),
            appForeground = p.getBoolean(KEY_APP_FG, false),
        )
        val applied = p.getBoolean(KEY_APPLIED, false)
        val action = GrayscalePolicy.plan(desired, applied, p.getBoolean(KEY_SUPPRESSED, false), current)
        try {
            when (action) {
                Action.APPLY_AND_SAVE -> {
                    if (!isGranted(context)) return
                    // APPLIED before the write: a crash in between then reads as a
                    // user override (colour stays), never as grey nobody owns
                    p.edit(commit = true) {
                        putBoolean(KEY_PREV_ENABLED, current.enabled)
                        putInt(KEY_PREV_MODE, current.mode)
                        putBoolean(KEY_APPLIED, true)
                    }
                    write(cr, GrayscalePolicy.GREY)
                }
                Action.RESTORE -> {
                    val prevMode = p.getInt(KEY_PREV_MODE, MODE_UNSET)
                    write(cr, State(p.getBoolean(KEY_PREV_ENABLED, false), if (prevMode == MODE_UNSET) DEFAULT_MODE else prevMode))
                    p.edit(commit = true) { clearApplied() }
                }
                Action.USER_OVERRIDE -> p.edit(commit = true) {
                    clearApplied()
                    putBoolean(KEY_MANUAL, false)
                    putBoolean(KEY_SUPPRESSED, true)
                }
                Action.CLEAR_SUPPRESSION -> p.edit(commit = true) { putBoolean(KEY_SUPPRESSED, false) }
                Action.NONE -> Unit
            }
        } catch (e: SecurityException) {
            // Grant revoked: nothing Parem can write any more
            Log.w(TAG, "WRITE_SECURE_SETTINGS missing", e)
            p.edit(commit = true) { clearApplied() }
            return
        }
        if (p.getBoolean(KEY_APPLIED, false) && onFocus && focusActive) scheduleFocusEnd(context)
    }

    private fun SharedPreferences.Editor.clearApplied() {
        putBoolean(KEY_APPLIED, false)
        remove(KEY_PREV_ENABLED)
        remove(KEY_PREV_MODE)
    }

    private fun read(cr: ContentResolver) = State(
        enabled = Settings.Secure.getInt(cr, SECURE_ENABLED, 0) == 1,
        mode = Settings.Secure.getInt(cr, SECURE_MODE, MODE_UNSET),
    )

    private fun write(cr: ContentResolver, state: State) {
        if (state.enabled) {
            // Mode first, so turning on never flashes the old correction
            Settings.Secure.putInt(cr, SECURE_MODE, state.mode)
            Settings.Secure.putInt(cr, SECURE_ENABLED, 1)
        } else {
            Settings.Secure.putInt(cr, SECURE_ENABLED, 0)
            Settings.Secure.putInt(cr, SECURE_MODE, state.mode)
        }
    }

    // ---- After a limit: from "Open anyway" until Parem is left and returned to ----

    fun onLimitOverride(context: Context) {
        if (!isOnLimit(context)) return
        prefs(context).edit(commit = true) {
            putBoolean(KEY_LIMIT_OVERRIDE, true)
            putBoolean(KEY_LEFT_SINCE_OVERRIDE, false)
        }
        reconcile(context)
    }

    /** MainActivity.onStop. In prefs, not a field, so it survives process death. */
    fun onLauncherStopped(context: Context) {
        val p = prefs(context)
        if (p.getBoolean(KEY_LIMIT_OVERRIDE, false)) p.edit { putBoolean(KEY_LEFT_SINCE_OVERRIDE, true) }
    }

    /** MainActivity.onStart. Not HomeFragment.onResume: the drawer's pop back home resumes it mid-launch. */
    fun onLauncherStarted(context: Context) {
        val p = prefs(context)
        if (!p.getBoolean(KEY_LIMIT_OVERRIDE, false) || !p.getBoolean(KEY_LEFT_SINCE_OVERRIDE, false)) return
        p.edit(commit = true) {
            putBoolean(KEY_LIMIT_OVERRIDE, false)
            putBoolean(KEY_LEFT_SINCE_OVERRIDE, false)
        }
        reconcile(context)
    }

    // ---- Per-app grayscale (M4-WP21): grey while a marked app is in front ----

    private const val TICK_MS = 1_000L
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    // Main thread only
    private var watch: Runnable? = null

    fun isAppMarked(context: Context, pkg: String) = GrayscalePolicy.isMarked(prefs(context).getString(KEY_APPS, ""), pkg)

    fun hasMarkedApps(context: Context) = !prefs(context).getString(KEY_APPS, "").isNullOrBlank()

    fun toggleAppMark(context: Context, pkg: String) {
        val p = prefs(context)
        p.edit { putString(KEY_APPS, GrayscalePolicy.toggle(p.getString(KEY_APPS, ""), pkg)) }
    }

    /**
     * Everything per-app grayscale needs to bring colour back: the grant, usage
     * access to see app switches, and Parem as home so pressing Home resets it.
     * Nothing sets [KEY_APP_FG] while this is false, so there is nothing to undo.
     */
    fun appModeReady(context: Context) =
        isGranted(context) && context.appUsagePermissionGranted() && isParemDefault(context)

    fun setAppForeground(context: Context, on: Boolean) {
        val p = prefs(context)
        if (p.getBoolean(KEY_APP_FG, false) != on) p.edit(commit = true) { putBoolean(KEY_APP_FG, on) }
        reconcile(context)
    }

    /**
     * Follows app switches that skip Parem (recents, notifications) from
     * [sinceMs], the launcher's last pause: the next app resumes before Parem
     * stops, so starting later would miss it. Runs in the home process, which
     * Android keeps out of the cached (freezable) range while Parem is home.
     */
    fun startAppWatch(context: Context, sinceMs: Long) {
        stopAppWatch()
        val app = context.applicationContext
        val usage = app.getSystemService(UsageStatsManager::class.java)
        val power = app.getSystemService(PowerManager::class.java)
        val launchable = HashMap<String, Boolean>()
        var since = sinceMs
        val tick = object : Runnable {
            override fun run() {
                if (watch !== this) return
                // Screen off: no query and no wakelock, the next tick catches up
                if (power.isInteractive) {
                    if (!app.appUsagePermissionGranted()) {
                        // Lost access: fall back to colour, never stay grey blind
                        watch = null
                        setAppForeground(app, false)
                        return
                    }
                    val events = usage.queryEvents(since, System.currentTimeMillis())
                    val event = UsageEvents.Event()
                    var front: String? = null
                    while (events != null && events.hasNextEvent()) {
                        events.getNextEvent(event)
                        since = event.timeStamp
                        val pkg = event.packageName
                        // Parem's own resume would read as an unmarked app, and apps
                        // without a launcher entry (permission dialogs, recents,
                        // SystemUI) would flash colour mid-app
                        if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED && pkg != app.packageName &&
                            launchable.getOrPut(pkg) { app.packageManager.getLaunchIntentForPackage(pkg) != null }
                        ) front = pkg
                    }
                    if (front != null) {
                        val marked = isAppMarked(app, front)
                        if (marked != prefs(app).getBoolean(KEY_APP_FG, false)) setAppForeground(app, marked)
                    }
                }
                handler.postDelayed(this, TICK_MS)
            }
        }
        watch = tick
        handler.post(tick)
    }

    fun stopAppWatch() {
        watch?.let { handler.removeCallbacks(it) }
        watch = null
    }

    // ---- Focus end while the user is in another app ----

    private fun focusEndMs(context: Context): Long? = when (val label = FocusModeManager.getActiveLabel(context)) {
        is FocusModeManager.ActiveLabel.Timed -> prefs(context).getLong(KEY_FOCUS_END_TIME, -1L).takeIf { it > 0 }
        is FocusModeManager.ActiveLabel.ScheduledUntil -> label.epochMs
        else -> null
    }

    /** Replaces any pending job, so re-enabling focus never leaves two. */
    private fun scheduleFocusEnd(context: Context) {
        val end = focusEndMs(context) ?: return
        val delay = (end - System.currentTimeMillis()).coerceAtLeast(0) + 1_000
        WorkManager.getInstance(context).enqueueUniqueWork(
            FOCUS_END_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<FocusEndWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).build()
        )
    }

    fun cancelFocusEnd(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(FOCUS_END_WORK)
    }

    /** Only reconciles; FocusModeManager.isActive is already false past the end time. */
    class FocusEndWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
        override fun doWork(): Result {
            reconcile(applicationContext)
            return Result.success()
        }
    }
}
