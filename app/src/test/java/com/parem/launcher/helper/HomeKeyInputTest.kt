package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeKeyInputTest {

    @Test
    fun menuKey_opensDrawerWithoutQuery() {
        assertEquals("", HomeKeyInput.drawerQuery(isMenuKey = true, unicodeChar = 0, hasShortcutModifier = false))
    }

    @Test
    fun letter_seedsQuery() {
        assertEquals("a", HomeKeyInput.drawerQuery(false, 'a'.code, false))
    }

    @Test
    fun shiftedLetter_keepsCase() {
        assertEquals("A", HomeKeyInput.drawerQuery(false, 'A'.code, false))
    }

    @Test
    fun digit_seedsQuery() {
        assertEquals("7", HomeKeyInput.drawerQuery(false, '7'.code, false))
    }

    @Test
    fun nonAsciiLetter_seedsQuery() {
        assertEquals("õ", HomeKeyInput.drawerQuery(false, 'õ'.code, false))
    }

    @Test
    fun supplementaryLetter_seedsWholeCodePoint() {
        val cp = 0x20000 // CJK Extension B, a letter outside the BMP
        assertEquals(String(Character.toChars(cp)), HomeKeyInput.drawerQuery(false, cp, false))
    }

    @Test
    fun noCharacter_isIgnored() {
        assertNull(HomeKeyInput.drawerQuery(false, 0, false))
    }

    @Test
    fun spaceAndSymbols_areIgnored() {
        assertNull(HomeKeyInput.drawerQuery(false, ' '.code, false))
        assertNull(HomeKeyInput.drawerQuery(false, '!'.code, false))
        assertNull(HomeKeyInput.drawerQuery(false, '+'.code, false))
    }

    @Test
    fun shortcutModifier_isIgnored() {
        assertNull(HomeKeyInput.drawerQuery(false, 'a'.code, true))
        assertNull(HomeKeyInput.drawerQuery(true, 0, true))
    }

    @Test
    fun deadKey_isIgnored() {
        // KeyEvent.unicodeChar sets the high bit (COMBINING_ACCENT) for dead keys
        assertNull(HomeKeyInput.drawerQuery(false, Int.MIN_VALUE or '`'.code, false))
    }
}
