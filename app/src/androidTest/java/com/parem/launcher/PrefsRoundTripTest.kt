package com.parem.launcher

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Prefs
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PrefsRoundTripTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val sp = context.getSharedPreferences(Prefs.PREFS_NAME, Context.MODE_PRIVATE)

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
            "FOCUS_MODE_WHITELIST" to setOf("com.example.a", "com.example.b"),
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
