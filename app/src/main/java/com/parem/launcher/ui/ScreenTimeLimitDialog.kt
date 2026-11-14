package com.parem.launcher.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.text.BidiFormatter
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
        val listed = appUsageMap.entries
            .sortedByDescending { it.value }
            .take(15)
            .map { it.key }
            // A limit outlives its app, and a bare package name helps nobody
            .plus(AppLimitManager.configuredApps(context).filter { context.appLabelOrNull(it) != null })
            .distinct()
            .toMutableList()

        fun addRow(packageName: String): TextView {
            val usageMs = appUsageMap[packageName] ?: 0L
            val appName = BidiFormatter.getInstance().unicodeWrap(context.appLabelOrNull(packageName) ?: packageName)

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

            // Limit selector (clickable text that opens the shared limit picker)
            val limitView = TextView(context).apply {
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
            fun refresh() {
                val label = BadHabitDialogs.limitLabel(context, packageName)
                val limitText = if (AppLimitManager.hasLimit(context, packageName)) context.getString(R.string.a11y_app_limit_value, label) else label
                limitView.text = label
                limitView.contentDescription = context.getString(R.string.a11y_app_limit_row, appName, usageView.text, limitText)
            }
            refresh()

            limitView.setOnClickListener {
                BadHabitDialogs.showTimeLimitPicker(context, packageName) { refresh() }
            }

            row.addView(limitView)
            listLayout.addView(row)
            return limitView
        }

        for (packageName in listed) addRow(packageName)

        if (listed.isEmpty()) rootLayout.addView(TextView(context).apply {
            text = context.getString(R.string.app_limits_empty)
            textSize = 14f
            setTextColor(secondaryTextColor)
            setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
        })
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
            adapter = AppPickerAdapter(context.getColorFromAttr(R.attr.textColor), maxSelected = 1, entries = entries, singleChoice = true) {
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
                .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true)
        }
    }

}
