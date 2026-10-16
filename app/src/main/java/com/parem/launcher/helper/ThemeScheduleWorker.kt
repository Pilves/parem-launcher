package com.parem.launcher.helper

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.data.paremPrefs

class ThemeScheduleWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val mode = ThemeScheduleManager.getMode(applicationContext)

        if (mode == Constants.ThemeScheduleMode.MANUAL) {
            return Result.success()
        }

        val shouldBeDark = try {
            ThemeScheduleManager.shouldBeDark(applicationContext)
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("ThemeScheduleWorker: Failed to determine theme schedule", e)
            return Result.success()
        }

        val prefs = applicationContext.paremPrefs()
        val currentTheme = prefs.getInt(Prefs.KEY_APP_THEME, AppCompatDelegate.MODE_NIGHT_YES)

        val isDark = currentTheme == AppCompatDelegate.MODE_NIGHT_YES
        val isLight = currentTheme == AppCompatDelegate.MODE_NIGHT_NO

        val needsChange = (shouldBeDark && isLight) || (!shouldBeDark && isDark)

        if (needsChange) {
            val newTheme = if (shouldBeDark) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }

            prefs.edit { putInt(Prefs.KEY_APP_THEME, newTheme) }
        }

        return Result.success()
    }
}
