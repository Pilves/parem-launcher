package com.parem.launcher

import android.content.Context
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.FolderManager
import com.parem.launcher.helper.GestureLetterManager
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/** Real JSON import followed by initial home creation and recreation on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class ImportContentTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = Prefs(context)
    private val sp = context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)

    private fun imported(vararg pairs: Pair<String, Any>) {
        // This device-only key is preserved across imports; keep onboarding out of the way.
        prefs.onboardingVersionSeen = Constants.ONBOARDING_VERSION
        val json = JSONObject().put("__parem_export_version", 1).put("FIRST_OPEN", false)
        for ((key, value) in pairs) json.put(key, value)
        prefs.importFromJson(JSONObject(json.toString()))
    }

    private fun homeSurvivesRestart() {
        withExperimentalTaskCleanup { scenario ->
            scenario.onActivity { assertNotNull("home was not created", it.findViewById<View>(R.id.homeAppsLayout)) }
            scenario.recreate()
            scenario.onActivity { assertNotNull("home did not survive recreation", it.findViewById<View>(R.id.homeAppsLayout)) }
        }
    }

    @Test fun malformedFolderContentsDoNotCrashHome() {
        imported("FOLDER_DATA" to "not JSON")
        assertFalse(FolderManager(context).isFolderSlot(1))
        homeSurvivesRestart()

        imported("FOLDER_DATA" to """{"1":{"name":"Safe","apps":[null,42,{"appName":"One","packageName":"example.app","activityClassName":"","userString":""}]},"2":"wrong shape"}""")
        val folders = FolderManager(context)
        assertEquals("Safe", folders.getFolderGroup(1)?.name)
        assertEquals("One", folders.getFolderGroup(1)?.apps?.single()?.appName)
        assertEquals(null, folders.getFolderGroup(2))
        homeSurvivesRestart()
    }

    @Test fun malformedFocusScheduleDoesNotCrashHome() {
        sp.edit().putBoolean("FOCUS_MODE_ENABLED", false).putLong("FOCUS_SCHEDULE_SKIP_UNTIL", 0L).commit()
        imported("FOCUS_SCHEDULE_ENABLED" to true,
            "FOCUS_SCHEDULE" to "oops;127,999999999999999999,0;0,0,0;1,-1,1440;1,2")
        assertEquals(emptyList<Any>(), FocusModeManager.getSchedule(context))
        assertFalse(FocusModeManager.isActive(context))
        homeSurvivesRestart()
    }

    @Test fun malformedGestureMappingsDoNotCrashHome() {
        imported("GESTURE_LETTERS_ENABLED" to true,
            "GESTURE_LETTER_MAPPINGS" to ",:broken::,missing-fields,A:example.app:Main:UserHandle{0},B:too:many:parts:extra")
        val mappings = GestureLetterManager.getAllMappings(context)
        assertEquals(setOf('A'), mappings.keys)
        assertEquals("example.app", mappings['A']?.first)
        homeSurvivesRestart()
    }

    @Test fun malformedImportedWidgetStateCannotReplaceDeviceWidgets() {
        // Widgets are device-only; even correctly typed imported values must be ignored.
        sp.edit().putInt("WIDGET_ID", -1).putString("WIDGET_IDS", "")
            .putInt("WIDGET_PLACEMENT", Constants.WidgetPlacement.ABOVE)
            .putString("WIDGET_HEIGHTS", "9:120").putString("WIDGET_PROVIDERS", "9:example/.Widget").commit()
        imported("WIDGET_ID" to Int.MIN_VALUE, "WIDGET_IDS" to "oops,999999999999999999,-42",
            "WIDGET_PLACEMENT" to Int.MAX_VALUE, "WIDGET_HEIGHTS" to "x:y,9:2147483647",
            "WIDGET_PROVIDERS" to "9:not/a/valid/component")
        assertEquals(-1, prefs.widgetId)
        assertEquals(emptyList<Int>(), prefs.getWidgetIdList())
        assertEquals(Constants.WidgetPlacement.ABOVE, prefs.widgetPlacement)
        assertEquals(120, prefs.getWidgetHeight(9))
        assertEquals("example/.Widget", prefs.getAllWidgetProviders()[9])
        homeSurvivesRestart()
    }
}
