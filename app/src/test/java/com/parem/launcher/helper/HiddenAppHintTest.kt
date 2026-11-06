package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppHintTest {

    private val hidden = listOf("Spotify", "Tinder")

    @Test
    fun searchWaitsForHiddenLabelsAndBlocksMatchesBeforeHintIsUpdated() {
        assertFalse(HiddenAppHint.canSearchWeb("Tinder", null))
        assertFalse(HiddenAppHint.canSearchWeb("tind", hidden))
        assertTrue(HiddenAppHint.canSearchWeb("weather forecast", hidden))
        assertTrue(HiddenAppHint.canSearchWeb("anything", emptyList()))
    }

    @Test
    fun oldAndFailedLoadsCannotAuthorizeAWebSearch() {
        val load = HiddenLabelLoad()
        val older = load.begin(setOf("old"))
        val newer = load.begin(setOf("old", "new"))
        assertFalse(load.isCurrent(older))
        assertTrue(load.isCurrent(newer))
        load.complete(older, setOf("old", "new"), listOf("Old"))
        assertFalse(HiddenAppHint.canSearchWeb("New", load.labels))
        load.complete(newer, setOf("old", "new"), null) // Failed or partial query.
        assertFalse(HiddenAppHint.canSearchWeb("New", load.labels))
        val retry = load.begin(setOf("old", "new"))
        load.complete(newer, setOf("old", "new"), listOf("Old"))
        assertFalse(HiddenAppHint.canSearchWeb("New", load.labels))
        load.complete(retry, setOf("old", "new"), listOf("Old", "New"))
        assertFalse(HiddenAppHint.canSearchWeb("New", load.labels))
        assertTrue(HiddenAppHint.canSearchWeb("Weather", load.labels))
        val changed = load.begin(setOf("old", "new"))
        load.complete(changed, setOf("old", "new", "other"), listOf("Old", "New"))
        assertFalse(HiddenAppHint.canSearchWeb("Other", load.labels))
    }

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
