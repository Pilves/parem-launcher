package com.parem.launcher.helper

import org.junit.Assert.*
import org.junit.Test

class SearchMatcherTest {

    @Test
    fun matches_googleMaps_withInitials() {
        val label = "Google Maps"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "gm"))
    }

    @Test
    fun matches_googleMaps_withPrefix() {
        val label = "Google Maps"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "goo"))
        assertTrue(SearchMatcher.matches(label, key, "maps"))
    }

    @Test
    fun matches_googleMaps_withNormalized() {
        val label = "Google Maps"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "googlemaps"))
        assertTrue(SearchMatcher.matches(label, key, "google m"))
    }

    @Test
    fun matches_googleMaps_notMatching() {
        val label = "Google Maps"
        val key = SearchMatcher.key(label)
        assertFalse(SearchMatcher.matches(label, key, "gz"))
        assertFalse(SearchMatcher.matches(label, key, "mg"))
    }

    @Test
    fun matches_cafeNoir_withDiacritics() {
        val label = "Café Noir"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "cafe"))
        assertTrue(SearchMatcher.matches(label, key, "cn"))
    }

    @Test
    fun matches_fDroid_withInitials() {
        val label = "F-Droid"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "fd"))
        assertTrue(SearchMatcher.matches(label, key, "fdroid"))
    }

    @Test
    fun matches_youtube_withPrefixes() {
        val label = "YouTube"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "you"))
        assertTrue(SearchMatcher.matches(label, key, "tube"))
    }

    @Test
    fun matches_singleCharContains() {
        val label = "YouTube"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "y"))
    }

    @Test
    fun matches_singleCharNotContained() {
        val label = "YouTube"
        val key = SearchMatcher.key(label)
        assertFalse(SearchMatcher.matches(label, key, "z"))
    }

    @Test
    fun matches_emptyQuery_matchesAnything() {
        val label = "Any App"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, ""))
    }

    @Test
    fun key_extractsInitials() {
        val key = SearchMatcher.key("Google Maps")
        assertEquals("gm", key.initials)
    }

    @Test
    fun key_normalizesWithoutDiacritics() {
        val key = SearchMatcher.key("Café Noir")
        assertTrue(key.normalized.contains("cafe"))
    }

    @Test
    fun matches_wordPrefixTokens_geStyle() {
        val label = "Shaurmas Kitchen"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "s ki"))
        assertTrue(SearchMatcher.matches(label, key, "sha kit"))
        assertFalse(SearchMatcher.matches(label, key, "s x"))
        assertFalse(SearchMatcher.matches(label, key, "ki s")) // wrong order
    }

    @Test
    fun matches_wordPrefixTokens_skipsWords() {
        val label = "The Grand Exchange"
        val key = SearchMatcher.key(label)
        assertTrue(SearchMatcher.matches(label, key, "g ex"))
        assertTrue(SearchMatcher.matches(label, key, "the ex"))
        assertFalse(SearchMatcher.matches(label, key, "g zz"))
    }
}
