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
}
