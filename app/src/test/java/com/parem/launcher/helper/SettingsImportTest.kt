package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class SettingsImportTest {
    private fun validate(values: Map<String, Any>) = SettingsImport.validate(
        values, setOf("FIRST_OPEN_TIME", "SCREEN_TIME_LAST_UPDATED", "LAUNCHER_RECREATE_TIMESTAMP"), setOf("TEXT_SIZE_SCALE")
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

    @Test fun checksImportedNumericDomainsWithoutRejectingLegacyValues() {
        val valid = mapOf("HOME_APPS_NUM" to 0, "HOME_ALIGNMENT" to 0x800003,
            "APP_LABEL_ALIGNMENT" to 17, "DATE_TIME_VISIBILITY" to 2,
            "APP_THEME" to -1, "SWIPE_DOWN_ACTION" to 1, "SWIPE_LEFT_ACTION" to -1,
            "SWIPE_RIGHT_ACTION" to 9, "DOUBLE_TAP_ACTION" to 9,
            "THEME_SCHEDULE_MODE" to 2, "TEXT_SIZE_SCALE" to 0.6,
            "FIRST_OPEN_TIME" to 0, "SCREEN_TIME_LAST_UPDATED" to 0, "LAUNCHER_RECREATE_TIMESTAMP" to 0)
        assertEquals(0.6f, validate(valid)["TEXT_SIZE_SCALE"])
        for ((key, value) in listOf("HOME_APPS_NUM" to -1, "HOME_APPS_NUM" to 9,
            "HOME_ALIGNMENT" to -999, "DATE_TIME_VISIBILITY" to 99, "APP_THEME" to 42,
            "SWIPE_DOWN_ACTION" to 777, "SWIPE_LEFT_ACTION" to -2, "SWIPE_RIGHT_ACTION" to 10,
            "DOUBLE_TAP_ACTION" to 1234, "THEME_SCHEDULE_MODE" to 3,
            "TEXT_SIZE_SCALE" to 0.59, "TEXT_SIZE_SCALE" to 2.01, "FIRST_OPEN_TIME" to -1,
            "SCREEN_TIME_LAST_UPDATED" to -1, "LAUNCHER_RECREATE_TIMESTAMP" to -1)) {
            assertThrows("$key=$value", IllegalArgumentException::class.java) { validate(mapOf(key to value)) }
        }
        // Excluded widget state may occur in old exports; Prefs ignores it.
        assertEquals(Int.MAX_VALUE, validate(mapOf("WIDGET_PLACEMENT" to Int.MAX_VALUE))["WIDGET_PLACEMENT"])
    }

    @Test fun rejectsDeepTruncatedAndOversizedFilesBeforeParsing() {
        fun read(text: String) = SettingsImport.readBounded(ByteArrayInputStream(text.toByteArray(Charsets.UTF_8)))
        val deep = "{\"__parem_export_version\":1,\"x\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}"
        assertThrows(IllegalArgumentException::class.java) { read(deep) }
        assertThrows(IllegalArgumentException::class.java) { read("{\"__parem_export_version\":1") }
        val escaped = "{\"label\":\"escaped \\\" [ { \\\\ ] }\",\"set\":[\"one\"]}"
        assertEquals(escaped, read(escaped))
        val bomExport = "\uFEFF{\"__parem_export_version\":1,\"APP_THEME\":2}"
        assertEquals(bomExport, read(bomExport))
        assertThrows(IllegalArgumentException::class.java) { read("\uFEFF$bomExport") }
        assertThrows(IllegalArgumentException::class.java) { read("{\"x\":/*\"*/" + "[".repeat(100) + "]".repeat(100) + "}") }
        assertThrows(IllegalArgumentException::class.java) { read("{\"x\":'" + "[".repeat(100) + "'}") }
        val prefix = "{\"x\":\""
        val suffix = "\"}"
        val atLimit = prefix + "a".repeat(SettingsImport.MAX_IMPORT_BYTES - prefix.length - suffix.length) + suffix
        assertEquals(atLimit, read(atLimit))
        assertThrows(IllegalArgumentException::class.java) { read(atLimit.dropLast(2) + "é\"}") }
        val bomAtLimit = "\uFEFF" + prefix + "a".repeat(SettingsImport.MAX_IMPORT_BYTES - 3 - prefix.length - suffix.length) + suffix
        assertEquals(bomAtLimit, read(bomAtLimit))
        assertThrows(IllegalArgumentException::class.java) { read("\uFEFF$atLimit") }
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            SettingsImport.readBounded(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }

    @Test fun rejectsNestedFolderJsonBeforeItCanCrashHome() {
        val deepFolder = "{\"1\":" + "[".repeat(100_000) + "]".repeat(100_000) + "}"
        assertThrows(IllegalArgumentException::class.java) { validate(mapOf("FOLDER_DATA" to deepFolder)) }
        assertEquals("not JSON", validate(mapOf("FOLDER_DATA" to "not JSON"))["FOLDER_DATA"])
        val folder = "{\"1\":{\"name\":\"[safe]\",\"apps\":[]}}"
        assertEquals(folder, validate(mapOf("FOLDER_DATA" to folder))["FOLDER_DATA"])
    }
}
