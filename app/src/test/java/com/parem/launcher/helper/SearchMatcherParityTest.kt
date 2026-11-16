package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.Normalizer

/**
 * The prepared-query overload must answer exactly like the String overload,
 * and both exactly like the matcher before queries were prepared once per
 * keystroke (kept below, verbatim, as the oracle).
 */
class SearchMatcherParityTest {

    private val labels = listOf(
        "Google Maps", "Café Noir", "F-Droid", "YouTube", "Shaurmas Kitchen",
        "The Grand Exchange", "Alarmy", "Ilmateade", "Õhtuleht", "Здоровье", "微信",
        "Signal — Private Messenger", "1Password", "K-9 Mail", "Ärge (test)",
        "naïve café", "İstanbul", "Straße", "Ǆemal", "A.B.C", "Clock & Date", "", "   ",
    )

    private val queries = listOf(
        "", " ", "   ", "g", "G", "gm", "goo", "maps", "google m", "googlemaps", "GOOGLE MAPS",
        " google", "google ", "cafe", "CAFÉ", "café noir", "cn", "fd", "f-d", "f d", "you tube",
        "s ki", "sha kit", "ki s", "g ex", "the ex", "alarm ", "alarm 7", "ilmatéade", "õhtu",
        "ohtu", "здо", "ЗДОРОВЬЕ", "微", "微信", "pass", "1p", "k9", "k-9 m", "a.b", "abc", "..",
        "-", "(", "istanbul", "İst", "strasse", "straße", "ǆ", "dž", "x", "zz", ",", "e x",
        "clock date", "c&d", "pm", "private messenger", "\t", "naive",
    )

    @Test
    fun preparedAndStringOverloadsMatchTheOriginalMatcher() {
        var hits = 0
        var misses = 0
        for (label in labels) {
            val key = SearchMatcher.key(label)
            for (query in queries) {
                val expected = legacyMatches(label, key, query)
                assertEquals("'$label' / '$query' (String)", expected, SearchMatcher.matches(label, key, query))
                assertEquals("'$label' / '$query' (Query)", expected,
                    SearchMatcher.matches(label, key, SearchMatcher.Query(query)))
                if (expected) hits++ else misses++
            }
        }
        // Guards against a corpus that only exercises one answer
        assertTrue("hits=$hits misses=$misses", hits > 100 && misses > 100)
    }

    @Test
    fun onePreparedQueryServesManyLabels() {
        val query = SearchMatcher.Query("s ki")
        val matched = labels.filter { SearchMatcher.matches(it, SearchMatcher.key(it), query) }
        assertEquals(labels.filter { SearchMatcher.matches(it, SearchMatcher.key(it), "s ki") }, matched)
    }

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val separators = Regex("[-_+,.·:&()\\s]+")

    private fun stripDiacritics(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(diacritics, "")

    private fun legacyMatches(label: String, key: SearchMatcher.LabelKey, query: String): Boolean {
        if (label.contains(query, ignoreCase = true)) return true
        val flatQuery = stripDiacritics(query).lowercase()
        val q = flatQuery.replace(separators, "")
        if (q.isEmpty()) return true
        if (key.normalized.contains(q)) return true
        if (q.length >= 2 && key.initials.startsWith(q)) return true
        val tokens = flatQuery.split(separators).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            var wordIndex = 0
            for (token in tokens) {
                while (wordIndex < key.words.size && !key.words[wordIndex].startsWith(token)) wordIndex++
                if (wordIndex >= key.words.size) return false
                wordIndex++
            }
            return true
        }
        return false
    }
}
