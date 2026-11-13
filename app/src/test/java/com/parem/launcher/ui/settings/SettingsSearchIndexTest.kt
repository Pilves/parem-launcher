package com.parem.launcher.ui.settings

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards "every settings row is findable by its title": a row title (an
 * id-less TextView with a string, the convention in fragment_settings.xml)
 * missing from SettingsSearchIndex, or an index anchor without a layout or virtual ID,
 * fails here instead of silently dropping out of the omnibox.
 */
class SettingsSearchIndexTest {

    // Non-interactive captions are spoken as explanatory text, not searchable rows.
    private val nonRowText = setOf("home_screen", "appearance", "settings_calm_section", "settings_search_section", "keyboard_caption")

    private fun mainFile(path: String): File =
        listOf(File("src/main/$path"), File("app/src/main/$path")).firstOrNull { it.isFile }
            ?: error("$path not found from ${File(".").absolutePath}")

    private val layout = mainFile("res/layout/fragment_settings.xml").readText()
    private val index = mainFile("java/com/parem/launcher/ui/settings/SettingsSearchIndex.kt").readText()
    private val entries = Regex("""R\.string\.(\w+) to R\.id\.(\w+)""").findAll(index)
        .map { it.groupValues[1] to it.groupValues[2] }.toList()

    @Test
    fun everyRowTitleIsIndexed() {
        val titles = Regex("""<TextView\b(.*?)/?>""", RegexOption.DOT_MATCHES_ALL).findAll(layout)
            .map { it.groupValues[1] }
            .filter { "android:id=" !in it }
            .mapNotNull { Regex("""android:text="@string/(\w+)"""").find(it)?.groupValues?.get(1) }
            .toSet() - nonRowText
        assertTrue("no row titles parsed", titles.size > 20)
        val missing = titles - entries.map { it.first }.toSet()
        assertTrue("rows missing from SettingsSearchIndex: $missing", missing.isEmpty())
    }

    @Test
    fun everyAnchorExistsInTheLayoutOrVirtualIds() {
        assertTrue("no index entries parsed", entries.isNotEmpty())
        val ids = mainFile("res/values/ids.xml").readText()
        val virtual = setOf("quietNotifAllowed", "quietNotifSilent")
        assertTrue("missing virtual quiet search ID", virtual.all { "name=\"$it\"" in ids })
        val settings = mainFile("java/com/parem/launcher/ui/SettingsFragment.kt").readText()
        assertTrue("virtual quiet IDs must route through the quiet sheet", virtual.all { "anchorId == R.id.$it" in settings })
        val missing = entries.map { it.second }.filter { "android:id=\"@+id/$it\"" !in layout && it !in virtual }
        assertTrue("anchors not in fragment_settings.xml or virtual quiet IDs: $missing", missing.isEmpty())
    }
}
