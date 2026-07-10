package com.parem.launcher.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.getColorFromAttr
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * A BottomSheetDialog that shows the most-used apps with preset limit options.
 * Users can assign per-app daily time limits with dialog-based warnings.
 *
 * @param context The activity context
 * @param appUsageMap Map of packageName to today's usage in milliseconds, from the ViewModel
 */
class ScreenTimeLimitDialog(
    context: Context,
    private val appUsageMap: Map<String, Long>
) : BottomSheetDialog(context) {

    private data class LimitOption(val label: String, val minutes: Int)

    private val limitOptions = listOf(
        LimitOption("15m", 15),
        LimitOption("30m", 30),
        LimitOption("1h", 60),
        LimitOption("2h", 120),
        LimitOption("Unlimited", -1)
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val textColor = context.getColorFromAttr(R.attr.primaryColor)
        val secondaryTextColor = context.getColorFromAttr(R.attr.primaryColorTrans50)

        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setBackgroundResource(R.drawable.bg_bottom_sheet)
        }

        rootLayout.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(40.dpToPx(), 4.dpToPx()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = 16.dpToPx()
            }
            setBackgroundColor(secondaryTextColor)
        })

        // Title
        val titleView = TextView(context).apply {
            text = context.getString(R.string.app_limits)
            textSize = 14f
            setTextColor(secondaryTextColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 8.dpToPx())
        }
        rootLayout.addView(titleView)

        val listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Sort apps by usage descending, take top 15
        val currentLimits = AppLimitManager.getAllLimits(context)
        val sortedApps = appUsageMap.entries
            .sortedByDescending { it.value }
            .take(15)

        val pm = context.packageManager

        for ((packageName, usageMs) in sortedApps) {
            val appName = try {
                val appInfo = pm.getApplicationInfo(packageName, 0)
                pm.getApplicationLabel(appInfo).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 8.dpToPx(), 0, 8.dpToPx())
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
                textSize = 13f
                setTextColor(secondaryTextColor)
                setPadding(8.dpToPx(), 0, 8.dpToPx(), 0)
            }
            row.addView(usageView)

            // Limit selector (clickable text that opens a popup menu)
            val currentLimit = currentLimits[packageName]
            val limitLabel = when {
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
                setBackgroundResource(context.selectableBackgroundRes())
            }

            limitView.setOnClickListener {
                showLimitPicker(packageName, limitView)
            }

            row.addView(limitView)
            listLayout.addView(row)
        }

        rootLayout.addView(listLayout)

        // Whole sheet scrolls (the old inner wrap-content ScrollView never
        // actually scrolled); expanded so landscape's short peek doesn't
        // open it half-hidden
        setContentView(ScrollView(context).apply { addView(rootLayout) })
        transparentSheetFrame()
        behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        behavior.skipCollapsed = true
    }

    // A themed sheet instead of the system PopupMenu, whose Material styling
    // clashed with the launcher's mono look
    private fun showLimitPicker(packageName: String, limitView: TextView) {
        val menu = BottomSheetMenu(context)
            .title(context.getString(R.string.select_time_limit))
        for (option in limitOptions) {
            menu.option(option.label, dimmed = option.minutes < 0) {
                if (option.minutes < 0) {
                    // "Unlimited" removes the limit; the row shows "No limit",
                    // matching what a reopened dialog would display
                    AppLimitManager.removeLimit(context, packageName)
                    limitView.text = "No limit"
                } else {
                    AppLimitManager.setLimit(context, packageName, option.minutes)
                    limitView.text = option.label
                }
            }
        }
        menu.show()
    }
}
