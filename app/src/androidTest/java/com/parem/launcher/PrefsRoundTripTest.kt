package com.parem.launcher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.FocusSchedule
import com.parem.launcher.helper.ThemeScheduleManager
import com.parem.launcher.helper.ThemeScheduleWorker
import com.parem.launcher.data.Constants
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkInfo
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sp = context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    private lateinit var saved: Map<String, *>

    @Before
    fun preservePreferences() { saved = sp.all.toMap() }

    @After
    fun restorePreferences() {
        val editor = sp.edit().clear()
        for ((key, value) in saved) if (value != null) editor.put(key, value)
        check(editor.commit())
    }

    @Test
    fun focusExpiryAndSchedulePauseSurvivePreferenceReads() {
        sp.edit().remove("FOCUS_SCHEDULE_SKIP_UNTIL").commit()
        FocusModeManager.setScheduleEnabled(context, false)
        FocusModeManager.setWhitelist(context, setOf("example.allowed"))
        FocusModeManager.enable(context, 25)
        assertTrue(FocusModeManager.isAppAllowed(context, "example.allowed"))
        assertFalse(FocusModeManager.isAppAllowed(context, "example.blocked"))
        sp.edit().putLong("FOCUS_MODE_END_TIME", System.currentTimeMillis() - 1).commit()
        assertFalse(FocusModeManager.isActive(context))
        FocusModeManager.checkAndExpire(context)
        assertFalse(sp.getBoolean("FOCUS_MODE_ENABLED", true))
        FocusModeManager.setSchedule(context, listOf(FocusSchedule.Window(FocusSchedule.ALL_DAYS, 0, 0)))
        FocusModeManager.setScheduleEnabled(context, true)
        assertTrue(FocusModeManager.isActive(context))
        FocusModeManager.pauseSchedule(context)
        assertFalse(FocusModeManager.isActive(context))
        assertTrue(sp.getLong("FOCUS_SCHEDULE_SKIP_UNTIL", 0) > System.currentTimeMillis())
    }

    @Test
    fun workManagerInitializesOnDemandAndRunsThemeWorker() {
        ThemeScheduleManager.setMode(context, Constants.ThemeScheduleMode.MANUAL)
        val manager = WorkManager.getInstance(context)
        val request = OneTimeWorkRequestBuilder<ThemeScheduleWorker>().build()
        try {
            manager.enqueue(request).result.get(10, TimeUnit.SECONDS)
            val deadline = System.currentTimeMillis() + 20_000
            var state: WorkInfo.State? = null
            while (System.currentTimeMillis() < deadline) {
                state = manager.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)?.state
                if (state?.isFinished == true) break
                android.os.SystemClock.sleep(100)
            }
            assertEquals(WorkInfo.State.SUCCEEDED, state)
        } finally {
            manager.cancelWorkById(request.id).result.get(10, TimeUnit.SECONDS)
        }
    }

    @Test
    fun exportImportKeepsValuesAndTypes() {
        // Pinned values: 1000L and 2.0f come back from org.json as Integer, so only
        // the LONG_PREF_KEYS / FLOAT_PREF_KEYS guards keep their types
        val seeded = mapOf<String, Any>(
            "FIRST_OPEN_TIME" to 1000L,
            "TEXT_SIZE_SCALE" to 2.0f,
            "HOME_APPS_NUM" to 6,
            "RENAME_com.example.app" to "Renamed",
            "FIRST_OPEN" to false,
            "DIM_WALLPAPER" to true,
            "BAD_HABIT_APPS" to "com.example.app=15",
            "MINDFUL_PAUSE_APPS" to "com.example.app",
            "FOCUS_SCHEDULE" to "127,1320,420",
            "FOCUS_MODE_WHITELIST" to "com.example.a,com.example.b",
            "HIDDEN_APPS" to setOf("com.example.a|UserHandle{0}", "com.example.b|UserHandle{0}"),
        )
        val editor = sp.edit().clear()
        for ((key, value) in seeded) editor.put(key, value)
        editor.putLong(EXCLUDED_KEY, 5000L).commit()

        val prefs = Prefs(context)
        // Serialize and reparse like the real export file; an in-memory JSONObject
        // still holds Long/Double and would never exercise the type guards
        val json = JSONObject(prefs.exportToJson().toString())
        json.put(EXCLUDED_KEY, 9999L)

        sp.edit().clear().putLong(EXCLUDED_KEY, 7000L).commit()
        prefs.importFromJson(json)

        val all = sp.all
        for ((key, expected) in seeded) {
            val actual = all[key]
            assertEquals("type of $key", kind(expected), kind(actual))
            assertEquals("value of $key", expected, actual)
        }
        assertEquals("excluded key keeps its local value", 7000L, all[EXCLUDED_KEY])
    }

    // Set implementations differ (LinkedHashSet seed vs HashSet from prefs)
    private fun kind(value: Any?) = if (value is Set<*>) Set::class else value?.let { it::class }

    @Suppress("UNCHECKED_CAST")
    private fun android.content.SharedPreferences.Editor.put(key: String, value: Any) {
        when (value) {
            is Long -> putLong(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is String -> putString(key, value)
            is Boolean -> putBoolean(key, value)
            is Set<*> -> putStringSet(key, value as Set<String>)
        }
    }

    private companion object {
        const val EXCLUDED_KEY = "FOCUS_MODE_END_TIME"
    }
}
