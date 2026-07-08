package com.parem.launcher.ui

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.parem.launcher.R
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.usageStats.EventLogWrapper
import com.google.android.material.bottomsheet.BottomSheetDialog
import android.content.DialogInterface
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
 * A BottomSheetDialog that displays a 7-day screen time graph.
 * Fetches usage data via EventLogWrapper on a background thread.
 */
class ScreenTimeGraphDialog(context: Context) : BottomSheetDialog(context) {

    companion object {
        // Completed days' totals can never change, so each historical day is
        // scanned once per process life and only today is re-scanned on open
        // (previously every open ran seven full-day scans). Keyed by the day's
        // start-of-day millis, not its offset, so entries stay correct across
        // midnight. Deliberately not persisted: prefs would leak into the
        // settings export.
        private val completedDayTotals = ConcurrentHashMap<Long, Long>()
    }

    private var loadJob: Job? = null
    private lateinit var graphView: ScreenTimeGraphView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bgColor = context.getColorFromAttr(R.attr.primaryInverseColor)
        val textColor = context.getColorFromAttr(R.attr.primaryColor)

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            setBackgroundColor(bgColor)
        }

        val titleView = TextView(context).apply {
            text = "Screen Time - Last 7 Days"
            textSize = 18f
            setTextColor(textColor)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.START
            setPadding(0, 0, 0, 16.dpToPx())
        }
        container.addView(titleView)

        graphView = ScreenTimeGraphView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200.dpToPx()
            )
        }
        container.addView(graphView)

        setContentView(container)

        loadData()
    }

    private fun loadData() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        if (!context.appUsagePermissionGranted()) return

        val appContext = context.applicationContext
        loadJob = CoroutineScope(Dispatchers.Main + Job()).launch {
            val data = withContext(Dispatchers.IO) {
                val wrapper = EventLogWrapper(appContext)
                val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
                val calendar = Calendar.getInstance()
                val now = System.currentTimeMillis()

                fun scanDay(dayStart: Long, dayEnd: Long): Long {
                    val foregroundStats = wrapper.getForegroundStatsByTimestamps(dayStart, dayEnd)
                    val aggregated = wrapper.aggregateForegroundStats(foregroundStats)
                    return wrapper.aggregateSimpleUsageStats(aggregated)
                }

                val result = mutableListOf<Pair<String, Long>>()

                for (offset in 6 downTo 0) {
                    // Get the day abbreviation
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

                    val totalMs = if (offset == 0) {
                        scanDay(dayStart, dayEnd)
                    } else {
                        completedDayTotals.getOrPut(dayStart) { scanDay(dayStart, dayEnd) }
                    }

                    result.add(Pair(dayLabel, totalMs))
                }

                result
            }

            graphView.setData(data)
        }
    }

    override fun onStop() {
        loadJob?.cancel()
        super.onStop()
    }

    override fun dismiss() {
        loadJob?.cancel()
        super.dismiss()
    }
}
