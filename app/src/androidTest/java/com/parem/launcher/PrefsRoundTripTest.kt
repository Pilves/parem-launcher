package com.parem.launcher

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.test.core.app.ActivityScenario
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
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.SettingsImport
import com.parem.launcher.helper.TextScale
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.helper.DoubleTapActionManager
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
    private var savedHistory = ""

    @Before
    fun preservePreferences() {
        saved = sp.all.toMap()
        savedHistory = Prefs(context).omniboxHistory
    }

    @After
    fun restorePreferences() {
        val editor = sp.edit().clear()
        for ((key, value) in saved) if (value != null) editor.put(key, value)
        check(editor.commit())
        Prefs(context).omniboxHistory = savedHistory
    }

    @Test
    fun textSizeIsInitializedBeforeFirstActivityConfiguration() {
        check(sp.edit().clear().commit())
        val expected = TextScale.effective(context.resources.configuration.fontScale, TextScale.LARGE)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(TextScale.LARGE, Prefs(activity).textSizeScale, 0.001f)
                assertEquals(expected, activity.resources.configuration.fontScale, 0.001f)
            }
        }
    }

    @Test
    fun textSizeKeepsExistingAndPartialRestoredStores() {
        val prefs = Prefs(context)
        for (seed in listOf("FIRST_OPEN", "unrelated_restored_setting")) {
            check(sp.edit().clear().putBoolean(seed, false).commit())
            prefs.initializeTextSize()
            assertEquals(TextScale.DEFAULT, prefs.textSizeScale, 0.001f)
        }
        for (step in TextScale.STEPS) {
            check(sp.edit().clear().putBoolean("FIRST_OPEN", false).putFloat("TEXT_SIZE_SCALE", step).commit())
            prefs.initializeTextSize()
            assertEquals(step, prefs.textSizeScale, 0.001f)
        }
    }

    @SingleDevice
    @Test
    fun textSizeSurvivesImportAndExportIncludingOldFiles() {
        val prefs = Prefs(context)
        for (step in TextScale.STEPS) {
            prefs.textSizeScale = step
            val exported = JSONObject(prefs.exportToJson().toString())
            prefs.textSizeScale = TextScale.DEFAULT
            prefs.importFromJson(exported)
            assertEquals(step, prefs.textSizeScale, 0.001f)
            prefs.importFromJson(JSONObject().put("__parem_export_version", 1))
            assertEquals(step, prefs.textSizeScale, 0.001f)
        }
        check(sp.edit().clear().putBoolean("FIRST_OPEN", false).commit())
        prefs.importFromJson(JSONObject().put("__parem_export_version", 1))
        prefs.initializeTextSize()
        assertEquals(TextScale.DEFAULT, prefs.textSizeScale, 0.001f)
    }

    @SingleDevice
    @Test
    fun guidanceIsLocalAndImportCannotRestoreIt() {
        sp.edit().clear().commit()
        val prefs = Prefs(context)
        prefs.initializeHomeGuidance()
        assertTrue(prefs.homeGuidanceSwipe)
        assertTrue(prefs.homeGuidanceLongPress)
        prefs.homeGuidanceSwipe = false
        prefs.homeGuidanceLongPress = false
        prefs.searchHintOpens = 8
        val exported = prefs.exportToJson()
        assertFalse(exported.has("HOME_GUIDANCE_SWIPE"))
        assertFalse(exported.has("SEARCH_HINT_OPENS"))
        exported.put("HOME_GUIDANCE_SWIPE", true)
        exported.put("HOME_GUIDANCE_LONG_PRESS", true)
        exported.put("SEARCH_HINT_OPENS", 0)
        exported.put("FIRST_OPEN", true)
        prefs.importFromJson(exported)
        prefs.initializeHomeGuidance()
        assertFalse(prefs.homeGuidanceSwipe)
        assertFalse(prefs.homeGuidanceLongPress)
        assertEquals(8, prefs.searchHintOpens)
    }

    @SingleDevice
    @Test
    fun existingInstallDoesNotAcquireHomeGuidance() {
        sp.edit().clear().putBoolean("FIRST_OPEN", false).commit()
        val prefs = Prefs(context)
        prefs.initializeHomeGuidance()
        assertFalse(prefs.homeGuidanceNewInstall)
        assertFalse(prefs.homeGuidanceSwipe)
        assertFalse(prefs.homeGuidanceLongPress)
    }

    @SingleDevice
    @Test
    fun pauseOnlyAppSurvivesExportAndImport() {
        val prefs = Prefs(context)
        AppLimitManager.setLimit(context, "example.pause", 30)
        AppLimitManager.setPauseOnly(context, "example.pause")
        val exported = prefs.exportToJson()
        AppLimitManager.removeLimit(context, "example.pause")
        prefs.importFromJson(exported)
        AppLimitManager.clearCache()
        assertTrue(AppLimitManager.hasPause(context, "example.pause"))
        assertFalse(AppLimitManager.hasLimit(context, "example.pause"))
        assertTrue(AppLimitManager.isGated(context, "example.pause"))
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

    @SingleDevice
    @Test
    fun calmGestureAssignmentsRoundTripAndOldExportsKeepDefaults() {
        val prefs = Prefs(context)
        prefs.swipeLeftAction = Constants.GestureAction.FOCUS_25_MIN
        prefs.swipeRightAction = Constants.GestureAction.TOGGLE_GRAYSCALE
        DoubleTapActionManager.setAction(context, Constants.GestureAction.TOGGLE_GRAYSCALE)
        val exported = JSONObject(prefs.exportToJson().toString())
        prefs.swipeLeftAction = Constants.GestureAction.NONE
        prefs.swipeRightAction = Constants.GestureAction.NONE
        DoubleTapActionManager.setAction(context, Constants.GestureAction.NONE)
        prefs.importFromJson(exported)
        assertEquals(Constants.GestureAction.FOCUS_25_MIN, prefs.getEffectiveSwipeLeftAction())
        assertEquals(Constants.GestureAction.TOGGLE_GRAYSCALE, prefs.getEffectiveSwipeRightAction())
        assertEquals(Constants.GestureAction.TOGGLE_GRAYSCALE, DoubleTapActionManager.getAction(context))

        prefs.importFromJson(JSONObject().put("__parem_export_version", 1))
        assertEquals(Constants.GestureAction.OPEN_APP, prefs.getEffectiveSwipeLeftAction())
        assertEquals(Constants.GestureAction.OPEN_APP, prefs.getEffectiveSwipeRightAction())
        assertEquals(Constants.GestureAction.LOCK_SCREEN, DoubleTapActionManager.getAction(context))
    }

    @Test
    fun quickGrayscaleKeepsManualChoiceWhenAccessIsLost() {
        sp.edit().putBoolean(GrayscaleController.KEY_MANUAL, false)
            .putBoolean(GrayscaleController.KEY_SUPPRESSED, true)
            .putBoolean(GrayscaleController.KEY_ON_FOCUS, false).commit()
        fun contextWithGrantCheck(firstGranted: Boolean) = object : ContextWrapper(context) {
            var checks = 0
            override fun checkSelfPermission(permission: String): Int =
                if (checks++ == 0 && firstGranted) PackageManager.PERMISSION_GRANTED
                else PackageManager.PERMISSION_DENIED
        }
        assertFalse(GrayscaleController.toggleManualIfGranted(contextWithGrantCheck(false)))
        assertFalse(GrayscaleController.toggleManualIfGranted(contextWithGrantCheck(true)))
        assertFalse(sp.getBoolean(GrayscaleController.KEY_MANUAL, true))
        assertTrue(sp.getBoolean(GrayscaleController.KEY_SUPPRESSED, false))
        assertFalse(sp.getBoolean(GrayscaleController.KEY_ON_FOCUS, true))
    }

    @Test
    fun quickGrayscaleOffRetainsSavedCorrectionWhenRestoreLosesAccess() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        fun shell(command: String): String = ParcelFileDescriptor.AutoCloseInputStream(
            automation.executeShellCommand(command)
        ).bufferedReader().use { it.readText().trim() }
        assumeTrue("requires a disposable Android emulator", shell("getprop ro.kernel.qemu") == "1")
        val enabledKey = "accessibility_display_daltonizer_enabled"
        val modeKey = "accessibility_display_daltonizer"
        val previousEnabled = shell("settings get secure $enabledKey")
        val previousMode = shell("settings get secure $modeKey")
        fun restore(key: String, value: String) {
            if (value == "null") shell("settings delete secure $key")
            else shell("settings put secure $key $value")
        }
        try {
            assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS))
            shell("settings put secure $modeKey 0")
            shell("settings put secure $enabledKey 1")
            sp.edit().putBoolean(GrayscaleController.KEY_MANUAL, true)
                .putBoolean(GrayscaleController.KEY_SUPPRESSED, false)
                .putBoolean(GrayscaleController.KEY_APPLIED, true)
                .putBoolean(GrayscaleController.KEY_PREV_ENABLED, true)
                .putInt(GrayscaleController.KEY_PREV_MODE, 12)
                .putBoolean(GrayscaleController.KEY_ON_FOCUS, false)
                .putBoolean(GrayscaleController.KEY_ON_LIMIT, false)
                .putBoolean(GrayscaleController.KEY_APP_FG, false).commit()
            val revoked = object : ContextWrapper(context) {
                var checks = 0
                override fun checkSelfPermission(permission: String): Int =
                    if (checks++ == 0) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
            }
            assertFalse(GrayscaleController.toggleManualIfGranted(revoked))
            assertTrue(sp.getBoolean(GrayscaleController.KEY_MANUAL, false))
            assertTrue(sp.getBoolean(GrayscaleController.KEY_APPLIED, false))
            assertTrue(sp.getBoolean(GrayscaleController.KEY_PREV_ENABLED, false))
            assertEquals(12, sp.getInt(GrayscaleController.KEY_PREV_MODE, -1))
            assertEquals("1", shell("settings get secure $enabledKey"))
            assertEquals("0", shell("settings get secure $modeKey"))
        } finally {
            restore(modeKey, previousMode)
            restore(enabledKey, previousEnabled)
        }
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

    @SingleDevice
    @Test
    fun exportImportKeepsValuesAndTypes() {
        // Pinned values: 1000L and 2.0f come back from org.json as Integer, so only
        // the LONG_PREF_KEYS / FLOAT_PREF_KEYS guards keep their types
        val seeded = mapOf<String, Any>(
            "FIRST_OPEN_TIME" to 1000L,
            "LAUNCHER_RECREATE_TIMESTAMP" to 1000L,
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
        val exported = prefs.exportToJson().toString(2).byteInputStream()
        val json = JSONObject(SettingsImport.readBounded(exported))
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

    @SingleDevice
    @Test
    fun invalidImportLeavesPreferencesAndHistoryUntouched() {
        val prefs = Prefs(context)
        prefs.appTheme = 2
        prefs.homeAppsNum = 4
        prefs.omniboxHistoryEnabled = true
        prefs.omniboxHistory = "saved history"
        val invalid = JSONObject().put("__parem_export_version", 1)
            .put("OMNIBOX_HISTORY_ENABLED", false).put("APP_THEME", "x")
        assertThrows(IllegalArgumentException::class.java) { prefs.importFromJson(invalid) }
        assertEquals(2, prefs.appTheme)
        assertTrue(prefs.omniboxHistoryEnabled)
        assertEquals("saved history", prefs.omniboxHistory)

        val outOfRange = JSONObject().put("__parem_export_version", 1)
            .put("HOME_APPS_NUM", Int.MAX_VALUE).put("APP_THEME", 1)
        assertThrows(IllegalArgumentException::class.java) { prefs.importFromJson(outOfRange) }
        assertEquals(2, prefs.appTheme)
        assertEquals(4, prefs.homeAppsNum)
        val deepFolder = "{\"1\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}"
        val hostileFolder = JSONObject().put("__parem_export_version", 1).put("FOLDER_DATA", deepFolder)
        assertThrows(IllegalArgumentException::class.java) { prefs.importFromJson(hostileFolder) }
        assertEquals(2, prefs.appTheme)
        assertEquals(4, prefs.homeAppsNum)
    }

    @SingleDevice
    @Test
    fun importWithHistoryOffOrAbsentErasesHistory() {
        val prefs = Prefs(context)
        for (includeOff in listOf(true, false)) {
            prefs.omniboxHistoryEnabled = true
            prefs.omniboxHistory = "saved history"
            val json = JSONObject().put("__parem_export_version", 1)
            if (includeOff) json.put("OMNIBOX_HISTORY_ENABLED", false)
            prefs.importFromJson(json)
            assertFalse(prefs.omniboxHistoryEnabled)
            assertEquals("", prefs.omniboxHistory)
        }
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
