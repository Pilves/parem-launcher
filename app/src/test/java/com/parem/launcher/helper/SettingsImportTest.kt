package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SettingsImportTest {
    private fun validate(values: Map<String, Any>) = SettingsImport.validate(
        values, setOf("FIRST_OPEN_TIME"), setOf("TEXT_SIZE_SCALE")
    )
    @Test fun validatesKnownTypesAndNormalizesNumbers() {
        assertEquals(mapOf("APP_THEME" to 2, "FIRST_OPEN_TIME" to 1000L,
            "TEXT_SIZE_SCALE" to 2f, "HIDDEN_APPS" to setOf("app")),
            validate(mapOf("APP_THEME" to 2L, "FIRST_OPEN_TIME" to 1000,
                "TEXT_SIZE_SCALE" to 2, "HIDDEN_APPS" to listOf("app"))))
        for ((key, value) in listOf("APP_THEME" to "x", "WEATHER_ENABLED" to 1,
            "APP_URL_1" to false, "HIDDEN_APPS" to listOf("app", 3),
            "FIRST_OPEN_TIME" to 1.5, "APP_THEME" to Long.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { validate(mapOf(key to value)) }
        }
    }
}
