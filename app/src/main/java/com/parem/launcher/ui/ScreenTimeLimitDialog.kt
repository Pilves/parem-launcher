package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.appLabelOrNull
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * A BottomSheetDialog that shows the most-used apps with preset limit options.
 * Users can assign per-app daily time limits with dialog-based warnings.
 *
 * @param context The activity context
 * @param appUsageMap Map of packageName to today's usage in milliseconds, from the ViewModel
 * @param scope Loads the installed apps for "Add another app…"
 */
class ScreenTimeLimitDialog(
    private val context: Context,
    private val appUsageMap: Map<String, Long>,
    private val scope: CoroutineScope,
) {

    private data class LimitOption(val label: String, val minutes: Int)

    private val limitOptions = listOf(
        LimitOption("15m", 15),
        LimitOption("30m", 30),
        LimitOption("1h", 60),
        LimitOption("2h", 120),
        LimitOption("Unlimited", -1)
    )

    fun show(): BottomSheetDialog {
        lateinit var dialog: BottomSheetDialog
        val menu = BottomSheetMenu(context).title(context.getString(R.string.app_limits))

        val textColor = context.getColorFromAttr(R.attr.textColor)
        val secondaryTextColor = context.getColorFromAttr(R.attr.textSecondaryColor)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
        }

        // Access can be revoked while the dialog's usage map is still cached:
        // say so in the sheet instead of listing limits that can never fire
        if (!context.appUsagePermissionGranted()) {
            rootLayout.addView(TextView(context).apply {
                text = context.getString(R.string.usage_access_limits_body)
                textSize = 14f
                setTextColor(secondaryTextColor)
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                setBackgroundResource(context.selectableBackgroundRes())
                setOnClickListener {
                    dialog.dismiss()
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            })
        }

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Top 15 by usage, plus anything already set up: an app added here has
        // no usage today and would otherwise drop off the list when reopened
        val currentLimits = AppLimitManager.getAllLimits(context)
        val listed = appUsageMap.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }
            .plus(AppLimitManager.configuredApps(context))
            .distinct()
            .toMutableList()

        fun addRow(packageName: String): TextView {
            val usageMs = appUsageMap[packageName] ?: 0L
            val appName = context.appLabelOrNull(packageName) ?: packageName

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
                minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            // App name
            val nameView = TextView(context).apply {
                text = appName
                textSize = 15f
                setTextColor(textColor)
                isSingleLine = true
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                )
            }
            row.addView(nameView)

            // Current usage
            val usageView = TextView(context).apply {
                text = context.formattedTimeSpent(usageMs)
                textSize = 14f
                setTextColor(secondaryTextColor)
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(usageView)

            // Limit selector (clickable text that opens a popup menu)
            val currentLimit = currentLimits[packageName]
            val limitLabel = when {
                currentLimit == null && AppLimitManager.hasPause(context, packageName) -> context.getString(R.string.pause_only)
                currentLimit == null -> "No limit"
                currentLimit < 0 -> "Unlimited"
                currentLimit >= 60 -> "${currentLimit / 60}h"
                else -> "${currentLimit}m"
            }

            val limitView = TextView(context).apply {
                text = limitLabel
                textSize = 14f
                setTextColor(textColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                gravity = Gravity.CENTER
                minWidth = 72.dpToPx()
                minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                setBackgroundResource(context.selectableBackgroundRes())
            }
            // The only tappable node in the row speaks the whole row: app, usage today, limit
            fun describe(limit: String) {
                val limitText = if (limit == "No limit" || limit == "Unlimited" || limit == context.getString(R.string.pause_only)) limit
                    else context.getString(R.string.a11y_app_limit_value, limit)
                limitView.contentDescription = context.getString(R.string.a11y_app_limit_row, appName, usageView.text, limitText)
            }
            describe(limitLabel)

            limitView.setOnClickListener {
                showLimitPicker(packageName, limitView) { describe(it) }
            }

            row.addView(limitView)
            listLayout.addView(row)
            return limitView
        }

        for (packageName in listed) addRow(packageName)

        rootLayout.addView(listLayout)
        rootLayout.addView(TextView(context).apply {
            text = context.getString(R.string.add_another_app)
            textSize = 15f
            setTextColor(textColor)
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            setBackgroundResource(context.selectableBackgroundRes())
            setOnClickListener {
                showAddApp(listed) { packageName ->
                    listed += packageName
                    addRow(packageName).performClick()
                }
            }
        })

        dialog = menu.customView(rootLayout).show()
        dialog.window?.setLayout(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        )
        return dialog
    }

    /** Single select: picking an app closes the sheet and goes straight to its limit choices. */
    private fun showAddApp(listed: List<String>, onPicked: (String) -> Unit) {
        scope.launch {
            val apps = getAppsList(context, Prefs(context), includeRegularApps = true, includeHiddenApps = true)
            val entries = apps
                .filter { it.appPackage.isNotEmpty() && it.appPackage !in listed }
                .distinctBy { it.appPackage }
                .map { AppPickerAdapter.Entry(it.appPackage, it.appLabel) }
            lateinit var picker: BottomSheetDialog
            lateinit var adapter: AppPickerAdapter
            adapter = AppPickerAdapter(context.getColorFromAttr(R.attr.textColor), maxSelected = 1, entries = entries) {
                adapter.selectedIds.firstOrNull()?.let { packageName ->
                    picker.dismiss()
                    onPicked(packageName)
                }
            }
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(16.dpToPx(), 0, 24.dpToPx(), 0)
                addAppPicker(adapter)
            }
            picker = BottomSheetMenu(context)
                .title(context.getString(R.string.add_another_app))
                .customView(content)
                .show(resizeForKeyboard = true)
        }
    }

    // A themed sheet instead of the system PopupMenu, whose Material styling
    // clashed with the launcher's mono look
    private fun showLimitPicker(packageName: String, limitView: TextView, onChanged: (String) -> Unit) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_time_limit))
        for (option in limitOptions) {
            if (option.minutes < 0) menu.option(context.getString(R.string.pause_only)) {
                AppLimitManager.setPauseOnly(context, packageName)
                limitView.text = context.getString(R.string.pause_only)
                onChanged(limitView.text.toString())
            }
            menu.option(option.label, dimmed = option.minutes < 0) {
                if (option.minutes < 0) {
                    // "Unlimited" removes the limit; the row shows "No limit",
                    // matching what a reopened dialog would display
                    AppLimitManager.removeLimit(context, packageName)
                    limitView.text = "No limit"
                    onChanged("No limit")
                } else {
                    AppLimitManager.setLimit(context, packageName, option.minutes)
                    limitView.text = option.label
                    onChanged(option.label)
                }
            }
        }
        BadHabitDialogs.addMindfulPauseToggle(menu, context, packageName)
        menu.show()
    }
}
