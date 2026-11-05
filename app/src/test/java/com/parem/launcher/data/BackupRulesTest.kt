package com.parem.launcher.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * In-app and privacy-policy copy says these files stay out of Android cloud
 * backup. Android has no test hook for that, so this pins the rules that make
 * it true, including the include that keeps every other domain out.
 */
class BackupRulesTest {

    private val allPrefs = setOf(MAIN, "$MAIN.quiet_keys", "$MAIN.omnibox_history").map { "$it.xml" }.toSet()

    private fun rules(name: String): Element {
        val file = listOf(File("src/main/res/xml/$name"), File("app/src/main/res/xml/$name")).first { it.isFile }
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement
    }

    private fun Element.paths(tag: String): Set<String> {
        val nodes = getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
            .onEach { assertEquals("sharedpref", it.getAttribute("domain")) }
            .map { it.getAttribute("path") }.toSet()
    }

    private fun Element.section(tag: String) = getElementsByTagName(tag).item(0) as Element

    @Test
    fun legacyRulesKeepAllPrefsOutOfBackup() {
        val rules = rules("backup_rules.xml")
        assertEquals(setOf("."), rules.paths("include"))
        assertEquals(allPrefs, rules.paths("exclude"))
    }

    @Test
    fun cloudBackupKeepsAllPrefsOut() {
        val cloud = rules("data_extraction_rules.xml").section("cloud-backup")
        assertEquals(setOf("."), cloud.paths("include"))
        assertEquals(allPrefs, cloud.paths("exclude"))
    }

    @Test
    fun deviceTransferCarriesMainPrefsOnly() {
        val transfer = rules("data_extraction_rules.xml").section("device-transfer")
        assertEquals(setOf("."), transfer.paths("include"))
        assertEquals(allPrefs - "$MAIN.xml", transfer.paths("exclude"))
    }

    private companion object {
        const val MAIN = "com.parem.launcher"
    }
}
