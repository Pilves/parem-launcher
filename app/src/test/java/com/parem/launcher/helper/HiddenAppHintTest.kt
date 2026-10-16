package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppHintTest {

    private val hidden = listOf("Spotify", "Tinder")

    @Test
    fun hiddenMatchWithNoVisibleRows_shows() {
        assertTrue(HiddenAppHint.show("spot", 0, privateSpaceLocked = false, hiddenLabels = hidden))
        assertTrue(HiddenAppHint.show("Tinder", 0, privateSpaceLocked = false, hiddenLabels = hidden))
    }

    @Test
    fun visibleRows_hide() {
        assertFalse(HiddenAppHint.show("spot", 1, privateSpaceLocked = false, hiddenLabels = hidden))
    }

    @Test
    fun privateSpaceLocked_hides() {
        assertFalse(HiddenAppHint.show("spot", 0, privateSpaceLocked = true, hiddenLabels = hidden))
    }

    @Test
    fun noHiddenMatch_hides() {
        assertFalse(HiddenAppHint.show("zzz", 0, privateSpaceLocked = false, hiddenLabels = hidden))
        assertFalse(HiddenAppHint.show("spot", 0, privateSpaceLocked = false, hiddenLabels = emptyList()))
    }

    @Test
    fun blankQuery_hides() {
        assertFalse(HiddenAppHint.show("  ", 0, privateSpaceLocked = false, hiddenLabels = hidden))
    }
}
