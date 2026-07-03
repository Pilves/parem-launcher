package com.parem.launcher.ui

import android.content.Context
import com.parem.launcher.R
import com.parem.launcher.helper.AppLimitManager

/**
 * Bad-habit app dialogs shared by the home screen and the app drawer:
 * the "limit reached — open anyway?" warning and the daily time-limit picker.
 */
object BadHabitDialogs {

    fun showLimitWarning(
        context: Context,
        appName: String,
        usageMinutes: Long,
        limitMinutes: Int,
        onOpenAnyway: () -> Unit,
    ) {
        val hours = usageMinutes / 60
        val mins = usageMinutes % 60
        val usageText = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
        val limitText =
            if (limitMinutes >= 60) "${limitMinutes / 60}h ${limitMinutes % 60}m" else "${limitMinutes}m"

        BottomSheetMenu(context)
            .message(context.getString(R.string.app_limit_warning, appName, usageText, limitText))
            .option(context.getString(R.string.open_anyway)) { onOpenAnyway() }
            .option(context.getString(R.string.go_back), dimmed = true) {}
            .show()
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
