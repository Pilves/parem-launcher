package com.parem.launcher.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.icu.text.MeasureFormat
import android.icu.util.Measure
import android.icu.util.MeasureUnit
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.isVisible
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.parem.launcher.MainViewModel
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.databinding.FragmentHomeBinding
import com.parem.launcher.helper.ScreenTimeSpeech
import com.parem.launcher.helper.WeatherManager
import com.parem.launcher.helper.WeatherStaleness
import com.parem.launcher.helper.appUsagePermissionGranted
import com.parem.launcher.helper.formattedTimeSpent
import com.parem.launcher.helper.openAlarmApp
import com.parem.launcher.helper.openCalendar
import com.parem.launcher.ui.HomeFragment
import com.parem.launcher.ui.ScreenTimeGraphDialog
import com.parem.launcher.ui.isAlive
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Owns the home screen's clock row: date/time rendering (including weather
 * staleness dimming), the clock/calendar tap targets, and the screen-time row.
 *
 * Extracted from HomeFragment; mirrors HomeWidgetController's shape. Created
 * in HomeFragment.onViewCreated. Its only listener is the date/battery
 * broadcast receiver, which it registers and unregisters itself by watching
 * the fragment's view lifecycle (resumed only), so it needs no cleanup call.
 */
class HomeClockController(
    private val fragment: HomeFragment,
    private val binding: FragmentHomeBinding,
    private val prefs: Prefs,
    private val viewModel: MainViewModel,
) {

    private val context get() = binding.root.context

    // Without this the date and battery % only refresh on resume, so a home
    // screen left visible across midnight (or a timezone change) goes stale.
    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (fragment.isAlive) populateDateTime()
        }
    }

    init {
        fragment.viewLifecycleOwner.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_DATE_CHANGED)
                    addAction(Intent.ACTION_TIME_TICK)
                    addAction(Intent.ACTION_TIMEZONE_CHANGED)
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                }
                ContextCompat.registerReceiver(context, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            }

            override fun onPause(owner: LifecycleOwner) {
                context.unregisterReceiver(refreshReceiver)
            }
        })
    }

    fun populateDateTime() {
        binding.dateTimeLayout.isVisible = prefs.dateTimeVisibility != Constants.DateTime.OFF
        binding.clock.isVisible = Constants.DateTime.isTimeVisible(prefs.dateTimeVisibility)
        binding.date.isVisible = Constants.DateTime.isDateVisible(prefs.dateTimeVisibility)

        val rawWeatherTemp = WeatherManager.getCachedTemp(context)
        val staleness = WeatherManager.getStaleness(context)
        // Older than 24h: hide entirely rather than show a silently stale reading.
        val weatherTemp = if (staleness == WeatherStaleness.Level.EXPIRED) "" else rawWeatherTemp

        var dateText = LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM"))
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

    fun populateScreenTime() {
        if (context.appUsagePermissionGranted().not()) return

        viewModel.getTodaysScreenTime()
        binding.tvScreenTime.visibility = View.VISIBLE
    }

    fun showScreenTime(timeSpentMs: Long) {
        binding.tvScreenTime.text = context.formattedTimeSpent(timeSpentMs)
        // "1m" is read as "one metre"; give TalkBack words
        binding.tvScreenTime.contentDescription = when (val spoken = ScreenTimeSpeech.of(timeSpentMs)) {
            ScreenTimeSpeech.UnderAMinute -> context.getString(R.string.a11y_screen_time_under_minute)
            is ScreenTimeSpeech.Duration -> {
                val measures = listOfNotNull(
                    spoken.hours?.let { Measure(it, MeasureUnit.HOUR) },
                    spoken.minutes?.let { Measure(it, MeasureUnit.MINUTE) },
                )
                val duration = MeasureFormat
                    .getInstance(context.resources.configuration.locales[0], MeasureFormat.FormatWidth.WIDE)
                    .formatMeasures(*measures.toTypedArray())
                context.getString(R.string.a11y_screen_time_today, duration)
            }
        }
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
            ScreenTimeGraphDialog(context, fragment.viewLifecycleOwner).show()
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
