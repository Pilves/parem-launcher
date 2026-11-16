package com.parem.launcher.helper

import com.parem.launcher.helper.SettingsSearch.Row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsSearchTest {

    private val rows = listOf(
        Row("Show date & time", 1),
        Row("Show icons", 2),
        Row("Auto-show keyboard", 3),
        Row("Theme mode", 4),
        Row("Daily wallpaper update", 5),
        Row("Ilmateade", 6),
    )

    private val prefixKeys = SettingsSearch.prefixKeys(rows + Row("Sort apps by usage", 7, listOf("Rank apps")))

    @Test
    fun guardIncludesShortPrefixesAliasesAndWordMatches() {
        listOf("s", "so", "sh", "wall", "ra", "rank a", "ILMATÉ", "key").forEach {
            assertEquals("prefix '$it'", true, SettingsSearch.isPrefix(it, prefixKeys))
        }
        listOf("", "  ", " so", "zz", "not a setting").forEach {
            assertEquals("not a prefix '$it'", false, SettingsSearch.isPrefix(it, prefixKeys))
        }
        assertNull(match("so")) // The guard does not make short suggestions visible.
    }

    @Test
    fun settingsPrefixYieldsOnlyToTheMatchingAppLabelOrWordPrefix() {
        val keys = SettingsSearch.prefixKeys(listOf(
            Row("Clock and date", 1), Row("Weather", 2), Row("Export settings", 3),
            Row("Sort apps by usage", 4), Row("Café mode", 5),
        ))
        listOf("clo" to "Clock", "wea" to "Weather", "set" to "Settings",
            "clo" to "Desk Clock", "cafe" to "Café Tools").forEach { (q, label) ->
            assertEquals("$q should yield to $label", false, SettingsSearch.holdsAutoLaunch(q, label, keys))
        }
        listOf("so" to "Resource", "sort" to "Resort", "set" to "Asset",
            "cafe" to "Decafeteria").forEach { (q, label) ->
            assertEquals("$q should hold $label", true, SettingsSearch.holdsAutoLaunch(q, label, keys))
        }
        assertEquals(false, SettingsSearch.holdsAutoLaunch("zzz", "Zzz", keys))
    }

    private fun match(q: String) = SettingsSearch.match(q, rows)?.anchor

    @Test
    fun fullTitle_findsEveryRow() {
        rows.forEach { assertEquals(it.title, it.anchor, match(it.title)) }
    }

    @Test
    fun titlePrefix() {
        assertEquals(4, match("the"))
        assertEquals(4, match("THEME"))
    }

    @Test
    fun shortQuery_isIgnored() {
        assertNull(match("th"))
        assertNull(match(""))
        assertNull(match("  "))
    }

    @Test
    fun wordPrefix_inOrder() {
        assertEquals(3, match("keyboard"))
        assertEquals(5, match("wallpaper"))
        assertEquals(2, match("show ic"))
        assertNull(match("icons show"))
    }

    @Test
    fun titlePrefix_beatsWordPrefix_thenListOrder() {
        // "show" starts rows 1 and 2 and is a word of row 3; the first title prefix wins
        assertEquals(1, match("show"))
    }

    @Test
    fun separatorsAndDiacritics() {
        assertEquals(3, match("autoshow"))
        assertEquals(1, match("date time"))
        assertEquals(6, match("ilmatéade"))
    }

    @Test
    fun noMatch() {
        assertNull(match("spotify"))
        assertNull(match("eme"))
    }

    @Test
    fun precomputedKeysPickTheSameRowAsKeyingOnEveryCall() {
        // A duplicate title and overlapping aliases make the tie order observable
        val all = rows + Row("Sort apps by usage", 7, listOf("Rank apps", "Usage order")) +
            Row("Show icons", 8) + Row("Apps on home", 9, listOf("Sort home"))
        val queries = listOf("", "  ", "th", "the", "THEME", "show", "show ic", "icons show", "keyboard",
            "autoshow", "date time", "ilmatéade", "rank", "usage", "order", "sort", "sort a", "apps",
            "home", "s i", "wall", "spotify", "eme", " show", "Show icons")
        repeat(2) { // the second pass reads keys built by the first
            queries.forEach { q -> assertEquals("'$q'", uncachedMatch(q, all), SettingsSearch.match(q, all)) }
        }
        assertEquals(2, SettingsSearch.match("show icons", all)?.anchor)
    }

    // The ranking as it was before row keys were cached
    private fun uncachedMatch(query: String, rows: List<Row>): Row? {
        val tokens = SearchMatcher.key(query).words
        val flat = tokens.joinToString("")
        if (flat.length < SettingsSearch.MIN_QUERY_LENGTH) return null
        var best: Row? = null
        var bestScore = Int.MAX_VALUE
        for (row in rows) {
            val score = (listOf(row.title) + row.aliases).mapNotNull { text ->
                val key = SearchMatcher.key(text)
                when {
                    key.normalized.startsWith(flat) -> 0
                    wordsInOrder(tokens, key.words) -> 1
                    else -> null
                }
            }.minOrNull() ?: continue
            if (score < bestScore) {
                best = row
                bestScore = score
            }
        }
        return best
    }

    private fun wordsInOrder(tokens: List<String>, words: List<String>): Boolean {
        var i = 0
        for (token in tokens) {
            while (i < words.size && !words[i].startsWith(token)) i++
            if (i == words.size) return false
            i++
        }
        return true
    }
}
