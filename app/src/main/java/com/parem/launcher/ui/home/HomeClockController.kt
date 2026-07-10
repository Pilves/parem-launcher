package com.parem.launcher.ui.home

import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.WeatherStaleness
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.openAlarmApp
import com.parem.launcher.helper.openCalendar
import com.parem.launcher.ui.HomeFragment
import com.parem.launcher.ui.ScreenTimeGraphDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Owns the home screen's clock row: date/time rendering (including weather
 * staleness dimming), the clock/calendar tap targets, and the screen-time row.
 *
 * Extracted from HomeFragment; mirrors HomeWidgetController's shape. Created
 * in HomeFragment.onViewCreated; holds no listeners or async state of its own,
 * so it needs no cleanup.
 */
class HomeClockController(
    private val fragment: HomeFragment,
    private val binding: FragmentHomeBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) {

    private val context get() = binding.root.context

    companion object {
        private var cachedLocale: Locale? = null
        private var dateFormatter: DateTimeFormatter? = null
        fun getDateFormatter(): DateTimeFormatter {
            val current = Locale.getDefault()
            if (current != cachedLocale || dateFormatter == null) {
                cachedLocale = current
                dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", current)
            }
            return dateFormatter!!
        }
    }

    fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val rawWeatherTemp = WeatherManager.getDisplayString(context)
        val staleness = WeatherManager.getStaleness(context)
        // Older than 24h: hide entirely rather than show a silently stale reading.
        val weatherTemp = if (staleness == WeatherStaleness.Level.EXPIRED) "" else rawWeatherTemp

        var dateText = LocalDate.now().format(getDateFormatter())
        if (weatherTemp.isNotEmpty()) dateText = "$weatherTemp  $dateText"

        if (!prefs.showStatusBar) {
            val ctx = context
            val batteryIntent = ctx.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val battery = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            if (battery > 0)
                dateText = context.getString(R.string.day_battery, dateText, battery)
        }
        val displayDate = dateText.replace(".,", ",")

        // 3h-24h old: dim just the temperature portion (~50% alpha) so it
        // reads as visibly untrusted without a toast or layout change.
        if (weatherTemp.isNotEmpty() && staleness == WeatherStaleness.Level.STALE) {
            val dimmedColor = ColorUtils.setAlphaComponent(binding.date.currentTextColor, 128)
            val spannable = SpannableString(displayDate)
            spannable.setSpan(
                ForegroundColorSpan(dimmedColor),
                0,
                weatherTemp.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            binding.date.text = spannable
        } else {
            binding.date.text = displayDate
        }
        binding.date.contentDescription = displayDate
        binding.clock.contentDescription = binding.clock.text
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    fun populateScreenTime() {
        if (context.appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE
    }

    fun openClockApp() {
        if (prefs.clockAppPackage.isBlank())
            openAlarmApp(context)
        else
            fragment.slotsController?.launchApp(
                "Clock",
                prefs.clockAppPackage,
                prefs.clockAppClassName,
                prefs.clockAppUser
            )
    }

    fun openCalendarApp() {
        if (prefs.calendarAppPackage.isBlank())
            openCalendar(context)
        else
            fragment.slotsController?.launchApp(
                "Calendar",
                prefs.calendarAppPackage,
                prefs.calendarAppClassName,
                prefs.calendarAppUser
            )
    }

    fun openScreenTimeDigitalWellbeing() {
        try {
            ScreenTimeGraphDialog(context).show()
        } catch (e: Exception) {
            Log.e("HomeFragment", "Failed to show screen time graph", e)
            // Fallback to Digital Wellbeing
            val intent = Intent()
            try {
                intent.setClassName(
                    Constants.DIGITAL_WELLBEING_PACKAGE_NAME,
                    Constants.DIGITAL_WELLBEING_ACTIVITY
                )
                fragment.startActivity(intent)
            } catch (e2: Exception) {
                Log.e("HomeFragment", "Failed to open Digital Wellbeing", e2)
            }
        }
    }
}
