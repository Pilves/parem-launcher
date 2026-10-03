package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutMatcherTest {

    private val user = "UserHandle{0}"
    private fun raw(pkg: String, id: String, label: String, rank: Int = 0, userKey: String = user) =
        ShortcutMatcher.Raw(pkg, userKey, id, label, rank)

    private val liked = raw("com.spotify", "liked", "Liked Songs")
    private val spotifySearch = raw("com.spotify", "search", "Search", rank = 1)
    private val mapsHome = raw("com.maps", "home", "Home")
    private val mapsWork = raw("com.maps", "work", "Work", rank = 1)
    private val incognito = raw("com.chrome", "incog", "New Incognito tab")

    private val labels = mapOf(
        "com.spotify|$user" to "Spotify",
        "com.maps|$user" to "Maps",
        "com.chrome|$user" to "Chrome",
    )
    private val all = ShortcutMatcher.entries(listOf(liked, spotifySearch, mapsHome, mapsWork, incognito), labels)

    private fun ids(query: String, entries: List<ShortcutMatcher.Entry> = all, limit: Int = 5) =
        ShortcutMatcher.filter(entries, query, limit).map { it.raw.id }

    @Test
    fun matchesOwnLabel() {
        assertEquals(listOf("liked"), ids("liked"))
        assertEquals(listOf("incog"), ids("incog"))
    }

    @Test
    fun appLabelQueryListsNoShortcuts() {
        assertEquals(emptyList<String>(), ids("maps"))
        assertEquals(emptyList<String>(), ids("spotify"))
    }

    @Test
    fun multiTokenMatchesAppPlusShortcut() {
        assertEquals(listOf("home"), ids("maps home"))
        assertEquals(listOf("liked"), ids("spotify liked"))
    }

    @Test
    fun singleTokenNeverCrossesIntoAppLabel() {
        assertEquals(emptyList<String>(), ids("sho"))
        assertEquals(emptyList<String>(), ids("apsho"))
    }

    @Test
    fun ignoresDiacritics() {
        val cafe = ShortcutMatcher.entries(listOf(raw("com.x", "c", "Café menu")), mapOf("com.x|$user" to "X"))
        assertEquals(listOf("c"), ids("cafe", cafe))
        val plain = ShortcutMatcher.entries(listOf(raw("com.x", "c", "Cafe menu")), mapOf("com.x|$user" to "X"))
        assertEquals(listOf("c"), ids("café", plain))
    }

    @Test
    fun underThreeCharsMatchesNothing() {
        assertEquals(emptyList<String>(), ids("li"))
        assertEquals(emptyList<String>(), ids(" l i "))
    }

    @Test
    fun dropsShortcutsOfUnlistedApps() {
        val other = raw("com.spotify", "liked", "Liked Songs", userKey = "UserHandle{10}")
        val hidden = raw("com.hidden", "x", "Liked things")
        assertTrue(ShortcutMatcher.entries(listOf(other, hidden), labels).isEmpty())
    }

    @Test
    fun renamedAppMatchesNewNameOnly() {
        val renamed = ShortcutMatcher.entries(listOf(mapsHome), mapOf("com.maps|$user" to "Navi"))
        assertEquals(listOf("home"), ids("navi home", renamed))
        assertEquals(emptyList<String>(), ids("maps home", renamed))
        assertEquals("Navi", renamed.single().appLabel)
    }

    @Test
    fun ordersByAppLabelThenRankAndCaps() {
        val entries = ShortcutMatcher.entries(
            listOf(
                raw("com.b", "b1", "Open later", rank = 1),
                raw("com.b", "b0", "Open now", rank = 0),
                raw("com.a", "a2", "Open file", rank = 2),
                raw("com.a", "a0", "Open new", rank = 0),
                raw("com.c", "c0", "Open it"),
                raw("com.d", "d0", "Open that"),
            ),
            mapOf("com.a|$user" to "alpha", "com.b|$user" to "Beta", "com.c|$user" to "Gamma", "com.d|$user" to "Delta"),
        )
        assertEquals(listOf("a0", "a2", "b0", "b1", "d0"), ids("open", entries))
        assertEquals(listOf("a0", "a2"), ids("open", entries, limit = 2))
    }
}
