package com.parem.launcher.ui

import android.content.Context
import android.os.SystemClock
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.MindfulPause
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Bad-habit app dialogs shared by the home screen and the app drawer:
 * the launch gate (mindful pause / "limit reached — open anyway?" warning) and
 * the daily time-limit picker.
 */
object BadHabitDialogs {

    fun showLimitWarning(
        context: Context,
        appName: String,
        usageMinutes: Long,
        limitMinutes: Int,
        onOpenAnyway: () -> Unit,
    ) {
        BottomSheetMenu(context)
            .message(limitWarningText(context, appName, usageMinutes, limitMinutes))
            .option(context.getString(R.string.open_anyway)) { onOpenAnyway() }
            .option(context.getString(R.string.go_back), dimmed = true) {}
            .show()
    }

    private fun limitWarningText(context: Context, appName: String, usageMinutes: Long, limitMinutes: Int): String {
        val hours = usageMinutes / 60
        val mins = usageMinutes % 60
        val usageText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val limitText =
            if (limitMinutes >= 60) "${limitMinutes / 60}h ${limitMinutes % 60}m" else "${limitMinutes}m"
        return context.getString(R.string.app_limit_warning, appName, usageText, limitText)
    }

    /**
     * The gate every launch route runs for a limited app: focus-blocked apps skip
     * straight to [open] (which shows the focus toast), then the mindful pause or
     * the over-limit warning per [MindfulPause.decide]. [onCancel] runs when the
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
        val limitMinutes = AppLimitManager.getLimit(context, packageName)
        if (limitMinutes == null || !FocusModeManager.isAppAllowed(context, packageName)) {
            open()
            return
        }
        val pause = AppLimitManager.hasPause(context, packageName)
        scope.launch {
            val usageMs = withContext(Dispatchers.IO) {
                UsageStatsHelper.getUsageForApp(context, packageName)
            }
            if (!isActive()) return@launch
            val usageMinutes = usageMs / 60_000
            val overLimit = usageMinutes >= limitMinutes
            val label = appLabel(context, appName, packageName)
            when (MindfulPause.decide(pause, overLimit)) {
                MindfulPause.Gate.LAUNCH -> open()
                MindfulPause.Gate.LIMIT_WARNING ->
                    showLimitWarning(context, label, usageMinutes, limitMinutes, open)
                MindfulPause.Gate.PAUSE -> showMindfulPause(
                    context, label,
                    if (overLimit) limitWarningText(context, label, usageMinutes, limitMinutes) else null,
                    open, onCancel
                )
            }
        }
    }

    /** Gesture letters pass no name; show the app's label rather than its package. */
    private fun appLabel(context: Context, appName: String, packageName: String): String {
        if (appName.isNotEmpty()) return appName
        return try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun showMindfulPause(
        context: Context,
        appName: String,
        limitNote: String?,
        onOpen: () -> Unit,
        onCancel: () -> Unit,
    ) {
        var proceeded = false
        // A plain row instead of option(): option() dismisses on any tap, and a
        // tap during the countdown must do nothing
        val openRow = TextView(context).apply {
            textSize = 16f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setPadding(24.dpToPx(), 14.dpToPx(), 24.dpToPx(), 14.dpToPx())
        }
        val menu = BottomSheetMenu(context)
            .message(context.getString(R.string.mindful_pause_question, appName))
        if (limitNote != null) menu.message(limitNote)
        val dialog = menu
            .customView(openRow)
            .option(context.getString(R.string.go_back), dimmed = true) {}
            // Back, swipe-down and "Go back" all land here; the open row sets proceeded first
            .onDismiss { if (!proceeded) onCancel() }
            .show()

        val start = SystemClock.elapsedRealtime()
        val tick = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                val elapsed = SystemClock.elapsedRealtime() - start
                val left = MindfulPause.secondsRemaining(elapsed)
                if (left > 0) {
                    openRow.text = context.getString(R.string.mindful_pause_wait, appName, left)
                    openRow.postDelayed(this, MindfulPause.msUntilNextTick(elapsed))
                    return
                }
                openRow.text = context.getString(R.string.mindful_pause_open, appName)
                openRow.setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                openRow.setBackgroundResource(context.selectableBackgroundRes())
                openRow.setOnClickListener {
                    proceeded = true
                    dialog.dismiss()
                    onOpen()
                }
            }
        }
        tick.run()
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

    fun showTimeLimitPicker(context: Context, packageName: String, onSet: () -> Unit = {}) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_time_limit))
        val options = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 120 to "2 hours")
        for ((minutes, label) in options) {
            menu.option(label) {
                AppLimitManager.setLimit(context, packageName, minutes)
                onSet()
            }
        }
        menu.show()
    }
}
