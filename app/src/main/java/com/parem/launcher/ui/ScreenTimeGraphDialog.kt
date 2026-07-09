package com.parem.launcher.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.helper.AppIconCache
import com.parem.launcher.helper.UsageStatsHelper
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.usageStats.EventLogWrapper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Bottom sheet showing a 7-day screen time graph plus the week's most-used
 * apps (icon, name, weekly total). Usage data loads via EventLogWrapper on a
 * background thread after the sheet is visible.
 */
class ScreenTimeGraphDialog(private val context: Context) {

    companion object {
        // Completed days can never change, so each historical day's per-app
        // map is scanned once per process life and only today is re-scanned
        // on open. Per-app maps (not bare totals) so the weekly top-apps list
        // costs nothing extra: a day's total is just the map's value sum.
        // Keyed by the day's start-of-day millis, not its offset, so entries
        // stay correct across midnight. Deliberately not persisted: prefs
        // would leak into the settings export.
        private val completedDayStats = ConcurrentHashMap<Long, Map<String, Long>>()

        private const val TOP_APPS_COUNT = 7
    }

    private var loadJob: Job? = null

    fun show() {
        val graphView = ScreenTimeGraphView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200.dpToPx()
            ).apply {
                marginStart = 12.dpToPx()
                marginEnd = 12.dpToPx()
            }
        }
        val averageView = TextView(context).apply {
            textSize = 12f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            gravity = Gravity.END
            setPadding(24.dpToPx(), 2.dpToPx(), 24.dpToPx(), 4.dpToPx())
        }
        // Header + rows are added here only once data lands, so a missing
        // usage permission shows a plain graph sheet, not a dangling header
        val topAppsColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        BottomSheetMenu(context)
            .title(context.getString(R.string.screen_time_week_title))
            .customView(graphView)
            .customView(averageView)
            .customView(topAppsColumn)
            .onDismiss { loadJob?.cancel() }
            .show()

        loadData(graphView, averageView, topAppsColumn)
    }

    private fun loadData(
        graphView: ScreenTimeGraphView,
        averageView: TextView,
        topAppsColumn: LinearLayout,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.appUsagePermissionGranted()) return

        val appContext = context.applicationContext
        loadJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            data class WeekData(
                val days: List<Pair<String, Long>>,
                val topApps: List<Triple<String, String, Long>>, // pkg, label, ms
                val perDayTop: List<List<Triple<String, String, Long>>>,
                val weekTotalMs: Long,
            )

            val week = withContext(Dispatchers.IO) {
                val wrapper = EventLogWrapper(appContext)
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val calendar = Calendar.getInstance()
                val now = System.currentTimeMillis()

                fun scanDayPerApp(dayStart: Long, dayEnd: Long): Map<String, Long> =
                    wrapper.aggregateForegroundStats(
                        wrapper.getForegroundStatsByTimestamps(dayStart, dayEnd)
                    )
                        .groupBy { it.applicationId }
                        .mapValues { (_, stats) -> stats.sumOf { it.timeUsed } }

                val days = mutableListOf<Pair<String, Long>>()
                val dayMaps = mutableListOf<Map<String, Long>>()
                val weekly = mutableMapOf<String, Long>()

                for (offset in 6 downTo 0) {
                    calendar.timeInMillis = now
                    calendar.add(Calendar.DAY_OF_YEAR, -offset)
                    val dayLabel = dayFormat.format(calendar.time)

                    // Scan by explicit day bounds so the cache key and the
                    // scanned range can't disagree if midnight passes mid-loop
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    val dayStart = calendar.timeInMillis
                    calendar.add(Calendar.DAY_OF_YEAR, 1)
                    val dayEnd = calendar.timeInMillis

                    val perApp = if (offset == 0) {
                        // Today rides the shared 60s cache all other screen-time
                        // consumers use instead of running its own scan
                        UsageStatsHelper.getPerAppUsageToday(appContext)
                    } else {
                        completedDayStats.getOrPut(dayStart) { scanDayPerApp(dayStart, dayEnd) }
                    }

                    days.add(Pair(dayLabel, perApp.values.sum()))
                    dayMaps.add(perApp)
                    for ((pkg, ms) in perApp) {
                        weekly[pkg] = (weekly[pkg] ?: 0L) + ms
                    }
                }

                val pm = appContext.packageManager
                fun resolveTop(usage: Map<String, Long>): List<Triple<String, String, Long>> =
                    usage.entries
                        .sortedByDescending { it.value }
                        .asSequence()
                        .mapNotNull { (pkg, ms) ->
                            // Uninstalled apps drop out of the list rather than
                            // showing a bare package name with no icon
                            try {
                                val label = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                                Triple(pkg, label, ms)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        .take(TOP_APPS_COUNT)
                        .toList()

                // Per-day lists are resolved up front so a bar tap swaps the
                // list synchronously instead of doing PackageManager IPC on tap
                WeekData(days, resolveTop(weekly), dayMaps.map(::resolveTop), days.sumOf { it.second })
            }

            graphView.setData(week.days)
            averageView.text = context.getString(
                R.string.daily_average,
                context.formattedTimeSpent(week.weekTotalMs / 7)
            )
            populateTopApps(topAppsColumn, week.topApps, context.getString(R.string.top_apps_week))
            graphView.onDaySelected = { index ->
                if (index == null) {
                    populateTopApps(topAppsColumn, week.topApps, context.getString(R.string.top_apps_week))
                } else {
                    populateTopApps(
                        topAppsColumn,
                        week.perDayTop[index],
                        context.getString(R.string.top_apps_day, week.days[index].first)
                    )
                }
            }
        }
    }

    private fun populateTopApps(
        column: LinearLayout,
        topApps: List<Triple<String, String, Long>>,
        headerText: String,
    ) {
        column.removeAllViews()

        // Header stays even for an empty day so a bar tap visibly lands
        val header = TextView(context).apply {
            text = headerText
            textSize = 14f
            setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            setTypeface(null, Typeface.BOLD)
            setPadding(24.dpToPx(), 12.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        column.addView(header)

        val iconSize = 20.dpToPx()
        for ((pkg, label, ms) in topApps) {
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 8.dpToPx())
            }
            val nameView = TextView(context).apply {
                text = label
                textSize = 16f
                setTextColor(context.getColorFromAttr(R.attr.primaryColor))
                isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                AppIconCache.get(context, pkg)?.let { icon ->
                    icon.setBounds(0, 0, iconSize, iconSize)
                    setCompoundDrawablesRelative(icon, null, null, null)
                    compoundDrawablePadding = 12.dpToPx()
                }
            }
            row.addView(nameView)
            val timeView = TextView(context).apply {
                text = context.formattedTimeSpent(ms)
                textSize = 14f
                setTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
            }
            row.addView(timeView)
            column.addView(row)
        }
    }
}
