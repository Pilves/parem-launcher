package com.parem.launcher.helper

import com.parem.launcher.data.Prefs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import java.security.MessageDigest
import org.junit.Test

class ReleasedExportTest {
    @Suppress("UNCHECKED_CAST")
    private fun registeredKeys(name: String): Set<String> =
        (Prefs::class.java.getDeclaredField(name).apply { isAccessible = true }.get(null) as Set<String>)

    @Test fun releasedExportPassesBoundedReadAndRegisteredValidation() {
        val stream = requireNotNull(javaClass.getResourceAsStream("/fixtures/v6.0.2-settings.json"))
        val bytes = stream.use { it.readBytes() }
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        assertEquals("15eac9639d5150a5d2be3186429f346218a3cf33917c49f9862d5b02fe7a21bd", digest)
        val text = SettingsImport.readBounded(bytes.inputStream())
        val json = JSONObject(text)
        assertEquals(1, json.get("__parem_export_version"))
        assertEquals(true, json.get("DAILY_WALLPAPER"))
        assertTrue("fixture must contain exported settings", json.length() > 1)
        val values = json.keys().asSequence().filter { it != "__parem_export_version" }.associateWith { key ->
            val value = json.get(key)
            if (value is JSONArray) (0 until value.length()).map(value::get) else value
        }
        val validated = SettingsImport.validate(values, registeredKeys("LONG_PREF_KEYS"), registeredKeys("FLOAT_PREF_KEYS"))
        assertEquals(values.keys, validated.keys)
        assertTrue(validated.getValue("FIRST_OPEN_TIME") is Long)
        assertEquals(emptySet<String>(), validated["HIDDEN_APPS"])
        assertEquals(true, validated["DAILY_WALLPAPER"])
    }
}
