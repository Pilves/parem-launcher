package com.parem.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 * importFromJson guesses a value's type from JSON, so a Long or Float key that
 * is neither registered in LONG_PREF_KEYS/FLOAT_PREF_KEYS nor excluded from
 * export comes back as an Int/Float and the next getLong/getFloat throws
 * ClassCastException. Every main source file uses the launcher prefs file, so
 * this scans all getLong/getFloat reads in app/src/main.
 */
class PrefsRegistrationTest {

    private class Source(val name: String, val text: String)

    private val constRegex = Regex("""\bval\s+(\w+)\s*(?::\s*String\s*)?=\s*"([^"$]*)"""")
    // Settings.Global/Secure/System reads are system settings, not launcher prefs.
    private val readRegex = Regex("""(?<!Settings\.Global|Settings\.Secure|Settings\.System)\.get(Long|Float)\(\s*([^,)]+?)\s*,""")

    private fun mainDir(): File =
        listOf(File("src/main"), File("app/src/main")).firstOrNull { it.isDirectory }
            ?: error("app/src/main not found from ${File(".").absolutePath}")

    private fun loadMainSources(): List<Source> =
        mainDir().walkTopDown()
            .filter { it.isFile && (it.extension == "kt" || it.extension == "java") }
            .map { Source(it.path, it.readText()) }
            .toList()

    private fun constants(text: String): Map<String, String> =
        constRegex.findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

    /** A literal, a constant from the same file, or a unique constant elsewhere; null if unresolvable. */
    private fun resolve(token: String, local: Map<String, String>, global: Map<String, Set<String>>): String? {
        val t = token.trim()
        if (t.startsWith("\"")) return if (t.endsWith("\"") && '$' !in t) t.trim('"') else null
        val name = t.substringAfterLast('.')
        local[name]?.let { return it }
        return global[name]?.singleOrNull()
    }

    private fun setContents(prefsText: String, setName: String): String =
        Regex("""\b$setName\s*=\s*setOf\(([^)]*)\)""").find(prefsText)?.groupValues?.get(1)
            ?: error("$setName = setOf(...) not found in Prefs.kt")

    /** Returns "file: getLong(KEY)" for every read whose key is not registered or excluded. */
    private fun unregisteredReads(sources: List<Source>, prefsText: String): List<String> {
        val global = mutableMapOf<String, MutableSet<String>>()
        for (s in sources) for ((k, v) in constants(s.text)) global.getOrPut(k) { mutableSetOf() }.add(v)

        val prefsConsts = constants(prefsText)
        fun keys(setName: String): Set<String> =
            setContents(prefsText, setName).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                .map { resolve(it, prefsConsts, global) ?: error("Cannot resolve '$it' in Prefs.$setName") }
                .toSet()

        val longKeys = keys("LONG_PREF_KEYS")
        val floatKeys = keys("FLOAT_PREF_KEYS")
        val excluded = keys("exportExcludeKeys")

        val problems = mutableListOf<String>()
        for (s in sources) {
            val local = constants(s.text)
            for (m in readRegex.findAll(s.text)) {
                val type = m.groupValues[1]
                val token = m.groupValues[2]
                val key = resolve(token, local, global)
                if (key == null) {
                    problems += "${s.name}: get$type($token) — key not resolvable to a string literal"
                    continue
                }
                val registered = if (type == "Long") key in longKeys else key in floatKeys
                if (!registered && key !in excluded) problems += "${s.name}: get$type($key)"
            }
        }
        return problems
    }

    private fun prefsSource(sources: List<Source>): String =
        sources.single { it.name.endsWith("data/Prefs.kt") }.text

    @Test
    fun everyLongAndFloatReadIsRegisteredOrExcluded() {
        val sources = loadMainSources()
        assertTrue(
            "Scanner found no getLong/getFloat reads; the regex or source path is broken",
            sources.any { readRegex.containsMatchIn(it.text) }
        )
        val problems = unregisteredReads(sources, prefsSource(sources))
        if (problems.isNotEmpty()) {
            fail(
                "Keys read as Long/Float must be in Prefs.LONG_PREF_KEYS/FLOAT_PREF_KEYS " +
                    "(or exportExcludeKeys if device-specific), or settings import breaks them:\n" +
                    problems.joinToString("\n")
            )
        }
    }

    @Test
    fun unregisteredKeyIsReported() {
        val prefs = Source(
            "data/Prefs.kt",
            """
            private val LONG_PREF_KEYS = setOf("REGISTERED_MS")
            private val FLOAT_PREF_KEYS = setOf("SCALE")
            private val exportExcludeKeys = setOf(DEVICE_KEY)
            private val DEVICE_KEY = "DEVICE_MS"
            """.trimIndent()
        )
        val helper = Source(
            "helper/Fake.kt",
            """
            private const val KEY_NEW = "NEW_MS"
            fun a() = p.getLong("REGISTERED_MS", 0L) + p.getLong(DEVICE_KEY, 0L)
            fun b() = p.getLong(KEY_NEW, 0L) + p.getFloat("SCALE", 1f) + p.getFloat("OTHER", 1f)
            """.trimIndent()
        )
        assertEquals(
            listOf("helper/Fake.kt: getLong(NEW_MS)", "helper/Fake.kt: getFloat(OTHER)"),
            unregisteredReads(listOf(prefs, helper), prefs.text)
        )
    }
}
