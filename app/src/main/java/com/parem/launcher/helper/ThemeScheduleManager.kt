package com.parem.launcher.helper

import android.content.Context
import androidx.core.content.edit
import com.parem.launcher.data.Constants
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.parem.launcher.data.paremPrefs
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

object ThemeScheduleManager {

    private const val THEME_SCHEDULE_MODE = "THEME_SCHEDULE_MODE"
    private const val THEME_LIGHT_TIME = "THEME_LIGHT_TIME"
    private const val THEME_DARK_TIME = "THEME_DARK_TIME"

    private const val WORKER_TAG = "THEME_SCHEDULE_WORKER"

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun getMode(context: Context): Int =
        context.paremPrefs().getInt(THEME_SCHEDULE_MODE, Constants.ThemeScheduleMode.MANUAL)

    fun setMode(context: Context, mode: Int) {
        context.paremPrefs().edit { putInt(THEME_SCHEDULE_MODE, mode) }
    }

    fun getLightTime(context: Context): String =
        context.paremPrefs().getString(THEME_LIGHT_TIME, "07:00") ?: "07:00"

    fun setLightTime(context: Context, time: String) {
        context.paremPrefs().edit { putString(THEME_LIGHT_TIME, time) }
    }

    fun getDarkTime(context: Context): String =
        context.paremPrefs().getString(THEME_DARK_TIME, "19:00") ?: "19:00"

    fun setDarkTime(context: Context, time: String) {
        context.paremPrefs().edit { putString(THEME_DARK_TIME, time) }
    }

    fun startWorker(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<ThemeScheduleWorker>(
            3, TimeUnit.HOURS  // reduced from 60 min - theme offset by up to 3h is acceptable
        ).setConstraints(constraints)
         .addTag(WORKER_TAG)
         .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORKER_TAG,
            ExistingPeriodicWorkPolicy.REPLACE,
            workRequest
        )
    }

    fun cancelWorker(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORKER_TAG)
    }

    fun shouldBeDark(context: Context): Boolean {
        val mode = getMode(context)
        val now = LocalTime.now()

        return when (mode) {
            Constants.ThemeScheduleMode.SCHEDULED -> {
                val (lightTime, darkTime) = scheduledTimes(context)
                isInDarkPeriod(now, lightTime, darkTime)
            }

            Constants.ThemeScheduleMode.SUNRISE_SUNSET -> {
                val (latStr, lngStr) = WeatherManager.getLocation(context)
                if (latStr.isBlank() || lngStr.isBlank()) {
                    val (lightTime, darkTime) = scheduledTimes(context)
                    isInDarkPeriod(now, lightTime, darkTime)
                } else {
                    val lat = latStr.toDoubleOrNull()
                    val lng = lngStr.toDoubleOrNull()
                    if (lat == null || lng == null) {
                        // Fall back to scheduled times if lat/lng parsing fails
                        val (lightTime, darkTime) = scheduledTimes(context)
                        return isInDarkPeriod(now, lightTime, darkTime)
                    }
                    val (sunrise, sunset) = SunriseSunsetCalculator.calculate(lat, lng, LocalDate.now())
                    now.isBefore(sunrise) || now.isAfter(sunset)
                }
            }

            else -> false // MANUAL, no automatic determination
        }
    }

    private fun scheduledTimes(context: Context): Pair<LocalTime, LocalTime> {
        val lightTime = try {
            LocalTime.parse(getLightTime(context), timeFormatter)
        } catch (_: Exception) { LocalTime.of(7, 0) }
        val darkTime = try {
            LocalTime.parse(getDarkTime(context), timeFormatter)
        } catch (_: Exception) { LocalTime.of(19, 0) }
        return lightTime to darkTime
    }

    /**
     * Determines if the current time falls in the "dark" period.
     * Dark period is from darkTime to lightTime (wrapping around midnight if needed).
     */
    private fun isInDarkPeriod(now: LocalTime, lightTime: LocalTime, darkTime: LocalTime): Boolean {
        return if (darkTime.isAfter(lightTime)) {
            // Normal case: light 07:00, dark 19:00
            // Dark period: 19:00 -> midnight -> 07:00
            now.isAfter(darkTime) || now.isBefore(lightTime)
        } else {
            // Inverted case: light 19:00, dark 07:00
            // Dark period: 07:00 -> 19:00
            now.isAfter(darkTime) && now.isBefore(lightTime)
        }
    }
}
