package com.parem.launcher.helper

import org.junit.Assert.assertEquals
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
    fun lockedPrivateSpaceUsesNeutralTipForBlockedHiddenMatch() {
        assertEquals(HiddenAppHint.EmptyTip.NO_APPS,
            HiddenAppHint.emptyTip("Tinder", privateSpaceLocked = true, hiddenLabels = hidden))
        assertFalse(HiddenAppHint.canSearchWeb("Tinder", hidden))
        assertEquals(HiddenAppHint.EmptyTip.HIDDEN_APPS,
            HiddenAppHint.emptyTip("Tinder", privateSpaceLocked = false, hiddenLabels = hidden))
        assertEquals(HiddenAppHint.EmptyTip.SEARCH,
            HiddenAppHint.emptyTip("weather", privateSpaceLocked = true, hiddenLabels = hidden))
    }

    @Test
    fun emptyHiddenKeysStaySearchableAcrossPackageChanges() {
        val load = HiddenLabelLoad()
        load.begin(emptySet(), 10)
        assertEquals(emptyList<String>(), load.labelsFor(emptySet(), 11))
        assertTrue(HiddenAppHint.canSearchWeb("weather", load.labelsFor(emptySet(), 11)))
        load.begin(setOf("hidden.package"), 11)
        assertEquals(emptyList<String>(), load.labelsFor(emptySet(), 12))
    }

    @Test
    fun packageChangeInvalidatesLabelsUntilANewCompleteLoad() {
        val load = HiddenLabelLoad()
        val keys = setOf("hidden.package")
        val initial = load.begin(keys, 10)
        load.complete(initial, keys, listOf("Old label"), 10)
        assertTrue(HiddenAppHint.canSearchWeb("New label", load.labelsFor(keys, 10)))
        assertFalse(HiddenAppHint.canSearchWeb("New label", load.labelsFor(keys, 11)))
        val retry = load.begin(keys, 11)
        load.complete(initial, keys, listOf("Old label"), 11)
        assertFalse(HiddenAppHint.canSearchWeb("New label", load.labelsFor(keys, 11)))
        load.complete(retry, keys, listOf("New label"), 12)
        assertFalse(HiddenAppHint.canSearchWeb("New label", load.labelsFor(keys, 12)))
        load.complete(retry, keys, listOf("New label"), 11)
        assertFalse(HiddenAppHint.canSearchWeb("New label", load.labelsFor(keys, 11)))
        assertTrue(HiddenAppHint.canSearchWeb("Weather", load.labelsFor(keys, 11)))
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
