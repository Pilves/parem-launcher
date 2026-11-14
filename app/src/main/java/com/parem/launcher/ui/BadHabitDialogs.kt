package com.parem.launcher.ui

import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.SystemClock
import android.widget.TextView
import androidx.core.text.BidiFormatter
import androidx.core.view.ViewCompat
import androidx.core.view.doOnPreDraw
import com.parem.launcher.R
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.MindfulPause
import com.parem.launcher.helper.LaunchGate
import com.parem.launcher.helper.appLabelOrNull
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.UsageReadCache
import com.parem.launcher.helper.WebLaunchTarget
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.showToast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bad-habit app dialogs shared by the home screen and the app drawer:
 * the launch gate (mindful pause / "limit reached — open anyway?" warning) and
 * the daily time-limit picker.
 */
object BadHabitDialogs {

    private fun showLimitWarning(context: Context, warning: String, onOpenAnyway: () -> Unit) {
        BottomSheetMenu(context)
            .message(warning)
            .option(context.getString(R.string.go_back)) {}
            .option(context.getString(R.string.open_anyway), dimmed = true) { onOpenAnyway() }
            .show()
    }

    private fun limitWarningText(context: Context, appName: String, usageMinutes: Long, limitMinutes: Int): String {
        val usageText = context.formattedTimeSpent(usageMinutes * 60_000L)
        val limitText = context.formattedTimeSpent(limitMinutes * 60_000L)
        return context.getString(R.string.app_limit_warning, appName, usageText, limitText)
    }

    /**
     * The gate for app launches: Focus blocks before the mindful pause or limit
     * warning. [onCancel] runs when the
     * pause is backed out of (button, back, swipe-down).
     */
    fun gateLaunch(
        context: Context,
        scope: CoroutineScope,
        isActive: () -> Boolean,
        appName: String,
        packageName: String,
        open: () -> Unit,
        onCancel: () -> Unit = {},
    ) {
        val allowed = FocusModeManager.isAppAllowed(context, packageName)
        val gated = AppLimitManager.isGated(context, packageName)
        when (LaunchGate.decide(allowed, gated, false, false)) {
            LaunchGate.Decision.BLOCK -> { FocusModeManager.showBlocked(context); return }
            LaunchGate.Decision.OPEN -> if (!gated) { open(); return }
            else -> Unit
        }
        val limitMinutes = AppLimitManager.getLimit(context, packageName)
        val pause = AppLimitManager.hasPause(context, packageName)
        scope.launch {
            // A pause-only app has no limit to measure against.
            val reading = if (limitMinutes == null) null else withContext(Dispatchers.IO) {
                UsageStatsHelper.readToday(context)
            }
            if (!isActive()) return@launch
            val checkedReading = if (reading != null && !context.appUsagePermissionGranted()) UsageReadCache.Reading.NoAccess else reading
            if (checkedReading != null && checkedReading !is UsageReadCache.Reading.Available) {
                var continued = false
                lateinit var dialog: BottomSheetDialog
                dialog = BottomSheetMenu(context)
                    .message(context.getString(if (checkedReading is UsageReadCache.Reading.NoAccess)
                        R.string.limit_usage_access_missing else R.string.limit_usage_unavailable))
                    .option(context.getString(R.string.go_back)) {}
                    .option(context.getString(R.string.usage_access_open_settings)) {
                        if (!isActive()) return@option
                        try { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                        catch (_: android.content.ActivityNotFoundException) { context.showToast(R.string.limit_settings_unavailable) }
                    }
                    .option(context.getString(R.string.open_anyway), dimmed = true, dismissOnClick = false) {
                        continued = true
                        dialog.dismiss()
                        if (!isActive()) { onCancel(); return@option }
                        if (!FocusModeManager.isAppAllowed(context, packageName)) {
                            FocusModeManager.showBlocked(context)
                            return@option
                        }
                        if (pause) {
                            val label = BidiFormatter.getInstance().unicodeWrap(appLabel(context, appName, packageName))
                            showPause(context,
                                question = context.getString(R.string.mindful_pause_question, label), note = null,
                                waiting = { left -> context.getString(R.string.mindful_pause_wait, label, left) },
                                ready = context.getString(R.string.mindful_pause_open, label),
                                onProceed = {
                                    if (!isActive()) onCancel()
                                    else if (!FocusModeManager.isAppAllowed(context, packageName)) FocusModeManager.showBlocked(context)
                                    else open()
                                }, onCancel = onCancel)
                                .dismissWithScope(scope)
                        } else open()
                    }
                    .onDismiss { if (!continued) onCancel() }
                    .show()
                dialog.dismissWithScope(scope)
                return@launch
            }
            val usageMs = (checkedReading as? UsageReadCache.Reading.Available)?.usage?.get(packageName) ?: 0L
            val usageMinutes = usageMs / 60_000
            val overLimit = MindfulPause.isOverLimit(limitMinutes, usageMinutes)
            val label = BidiFormatter.getInstance().unicodeWrap(appLabel(context, appName, packageName))
            val warning = limitMinutes?.takeIf { overLimit }
                ?.let { limitWarningText(context, label, usageMinutes, it) }
            // Opening past the limit is the "after a limit" grayscale trigger
            val openOverLimit = {
                if (isActive()) {
                    if (!FocusModeManager.isAppAllowed(context, packageName)) FocusModeManager.showBlocked(context)
                    else {
                        if (overLimit) GrayscaleController.onLimitOverride(context)
                        open()
                    }
                }
            }
            when (LaunchGate.decide(FocusModeManager.isAppAllowed(context, packageName), true, pause, overLimit)) {
                LaunchGate.Decision.BLOCK -> FocusModeManager.showBlocked(context)
                LaunchGate.Decision.OPEN -> open()
                LaunchGate.Decision.LIMIT_WARNING ->
                    showLimitWarning(context, warning.orEmpty(), openOverLimit)
                LaunchGate.Decision.PAUSE -> showPause(
                    context,
                    question = context.getString(R.string.mindful_pause_question, label),
                    note = warning,
                    waiting = { left -> context.getString(R.string.mindful_pause_wait, label, left) },
                    ready = context.getString(R.string.mindful_pause_open, label),
                    onProceed = openOverLimit,
                    onCancel = onCancel,
                )
            }
        }
    }

    /**
     * The gate for a resolved intent (including web opens): the intent is gated on the app that will
     * receive [intent] per [WebLaunchTarget.target] — focus first, then that
     * app's limit and pause through [gateLaunch]. [open] gets that package and must
     * pin the intent to it; "" means no single app (the chooser, or nothing) — the
     * receiver is unknown, so nothing is gated and the intent goes out unpinned;
     * this is permitted only when focus is inactive.
     */
    fun gateWebOpen(
        context: Context,
        scope: CoroutineScope,
        isActive: () -> Boolean,
        intent: Intent,
        open: (packageName: String) -> Unit,
    ) {
        val pm = context.packageManager
        val handler = LaunchGate.knownTarget(intent.component?.packageName, intent.`package`) ?: try {
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        } catch (_: Exception) {
            null
        }
        // Candidates matter only under focus, where the chooser must not offer a blocked browser
        val focusActive = FocusModeManager.isActive(context)
        val candidates = if (!focusActive || (!handler.isNullOrEmpty() && handler != WebLaunchTarget.CHOOSER)) emptyList() else try {
            pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).map { it.activityInfo.packageName }
        } catch (_: Exception) {
            emptyList()
        }
        val allowed: ((String) -> Boolean)? =
            if (focusActive) { pkg -> FocusModeManager.isAppAllowed(context, pkg) } else null
        val packageName = WebLaunchTarget.target(handler, candidates, allowed)
        if (packageName == null) {
            FocusModeManager.showBlocked(context)
            return
        }
        gateLaunch(context, scope, isActive, "", packageName, open = { open(packageName) })
    }

    /** Preserve an external action while applying the same receiver gate as web opens. */
    fun gateIntent(context: Context, scope: CoroutineScope, isActive: () -> Boolean, intent: Intent, onFailure: () -> Unit = {}) {
        gateWebOpen(context, scope, isActive, intent) { packageName ->
            if (!isActive()) return@gateWebOpen
            if (!FocusModeManager.isAppAllowed(context, packageName)) {
                FocusModeManager.showBlocked(context)
                return@gateWebOpen
            }
            try {
                context.startActivity(Intent(intent).apply { if (packageName.isNotEmpty()) setPackage(packageName) })
            } catch (_: Exception) {
                onFailure()
            }
        }
    }

    /** Gesture letters pass no name; show the app's label rather than its package. */
    private fun appLabel(context: Context, appName: String, packageName: String): String {
        if (appName.isNotEmpty()) return appName
        return context.appLabelOrNull(packageName) ?: packageName
    }

    /**
     * The speed bump behind every "are you sure" in Parem: a countdown, then a
     * dimmed way through, with the calm choice first. A pause, never a block.
     * [onCancel] runs on "Go back", back and swipe-down.
     */
    /** Dismiss a launch-owned sheet when the originating screen's scope ends. */
    private fun BottomSheetDialog.dismissWithScope(scope: CoroutineScope) {
        var detached = false
        val watcher = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally {
                withContext(NonCancellable + Dispatchers.Main.immediate) { if (!detached && isShowing) dismiss() }
            }
        }
        // Do not replace the caller's onDismiss listener (which delivers cancellation).
        window?.decorView?.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit
            override fun onViewDetachedFromWindow(v: android.view.View) {
                detached = true
                watcher.cancel()
                v.removeOnAttachStateChangeListener(this)
            }
        })
    }

    fun showPause(
        context: Context,
        question: String,
        note: String?,
        waiting: (secondsLeft: Int) -> String,
        ready: String,
        onProceed: () -> Unit,
        onCancel: () -> Unit,
    ): BottomSheetDialog {
        var proceeded = false
        // A plain row instead of option(): option() dismisses on any tap, and a
        // tap during the countdown must do nothing
        val openRow = TextView(context).apply {
            textSize = 16f
            text = waiting(MindfulPause.DEFAULT_DELAY_SECONDS)
            setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        }
        val menu = BottomSheetMenu(context).message(question)
        if (note != null) menu.message(note)
        val dialog = menu
            .option(context.getString(R.string.go_back)) {}
            .customView(openRow)
            // Back, swipe-down and "Go back" all land here; the open row sets proceeded first
            .onDismiss { if (!proceeded) onCancel() }
            .show()

        // The timer starts after the first draw is submitted, so a busy main
        // thread cannot consume the pause before its first visible frame.
        openRow.doOnPreDraw {
            openRow.post {
                val start = SystemClock.elapsedRealtime()
                val tick = object : Runnable {
                    override fun run() {
                        if (!dialog.isShowing) return
                        val elapsed = SystemClock.elapsedRealtime() - start
                        val left = MindfulPause.secondsRemaining(elapsed)
                        if (left > 0) {
                            openRow.text = waiting(left)
                            openRow.postDelayed(this, MindfulPause.msUntilNextTick(elapsed))
                            return
                        }
                        // Live only for this one change: the countdown ticks above stay silent
                        ViewCompat.setAccessibilityLiveRegion(openRow, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE)
                        openRow.text = ready
                        openRow.setBackgroundResource(context.selectableBackgroundRes())
                        openRow.setOnClickListener {
                            proceeded = true
                            dialog.dismiss()
                            onProceed()
                        }
                    }
                }
                tick.run()
            }
        }
        return dialog
    }

    /** Toggle row for sheets that edit an app's limit; shown only while the app has one. */
    fun addMindfulPauseToggle(menu: BottomSheetMenu, context: Context, packageName: String) {
        if (!AppLimitManager.hasLimit(context, packageName)) return
        val on = AppLimitManager.hasPause(context, packageName)
        menu.option(
            context.getString(if (on) R.string.mindful_pause_turn_off else R.string.mindful_pause_turn_on)
        ) {
            AppLimitManager.setPause(context, packageName, !on)
        }
    }

    /** The name of what the app has, for a row or a summary. */
    fun limitLabel(context: Context, packageName: String): String {
        val limit = AppLimitManager.getLimit(context, packageName)
        return when {
            limit != null -> context.formattedTimeSpent(limit * 60_000L)
            AppLimitManager.hasPause(context, packageName) -> context.getString(R.string.pause_only)
            else -> context.getString(R.string.app_menu_no_limit)
        }
    }

    /**
     * The usage-access sheet, named as the permission it is. Without the grant it
     * says what the limit needs; with it, what revoking in Android switches off.
     */
    fun showUsageAccessSheet(context: Context): BottomSheetDialog {
        val granted = context.appUsagePermissionGranted()
        return BottomSheetMenu(context)
            .title(context.getString(R.string.usage_access))
            .message(context.getString(if (granted) R.string.usage_access_granted_body else R.string.usage_access_limits_body))
            .option(context.getString(R.string.usage_access_open_settings), primaryAction = true) {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
            .option(context.getString(R.string.not_now), dimmed = true) {}
            .show()
    }

    /** False means the sheet asking for usage access is showing instead; a limit saved without it never fires. */
    fun ensureUsageAccess(context: Context): Boolean {
        if (context.appUsagePermissionGranted()) return true
        showUsageAccessSheet(context)
        return false
    }

    fun showTimeLimitPicker(context: Context, packageName: String, onSet: () -> Unit = {}): BottomSheetDialog? {
        if (!ensureUsageAccess(context)) return null
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_time_limit))
        val current = AppLimitManager.getLimit(context, packageName)
        for (minutes in listOf(15, 30, 60, 120)) {
            menu.option(context.formattedTimeSpent(minutes * 60_000L), checked = current == minutes) {
                AppLimitManager.setLimit(context, packageName, minutes)
                onSet()
            }
        }
        val pauseOnly = current == null && AppLimitManager.hasPause(context, packageName)
        menu.gap().option(context.getString(R.string.pause_only), checked = pauseOnly) {
            AppLimitManager.setPauseOnly(context, packageName)
            onSet()
        }
        menu.option(context.getString(R.string.app_menu_no_limit), checked = current == null && !pauseOnly) {
            AppLimitManager.removeLimit(context, packageName)
            onSet()
        }
        addMindfulPauseToggle(menu, context, packageName)
        return menu.show()
    }
}
