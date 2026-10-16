package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeCapacityTest {

    @Test
    fun fitting_dividesUsableHeightByRowHeight() {
        assertEquals(4, HomeCapacity.fitting(400, 100))
        assertEquals(3, HomeCapacity.fitting(399, 100))
        assertEquals(0, HomeCapacity.fitting(0, 100))
        assertEquals(0, HomeCapacity.fitting(400, 0))
        assertEquals(0, HomeCapacity.fitting(-50, 100))
    }

    @Test
    fun hiddenLabels_namesOccupiedSlotsBeyondCapacity() {
        val labels = listOf("Phone", "Mail", "", "Maps", "Notes", "", "", "")
        assertEquals(listOf("Maps", "Notes"), HomeCapacity.hiddenLabels(labels, shown = 5, capacity = 3))
        assertEquals(listOf("Mail", "Maps"), HomeCapacity.hiddenLabels(labels, shown = 4, capacity = 1))
        assertEquals(emptyList<String>(), HomeCapacity.hiddenLabels(labels, shown = 5, capacity = 5))
        assertEquals(emptyList<String>(), HomeCapacity.hiddenLabels(labels, shown = 5, capacity = 8))
        // Only empty slots drop off: nothing to name
        assertEquals(emptyList<String>(), HomeCapacity.hiddenLabels(labels, shown = 8, capacity = 5))
        // A zero capacity (measured nothing) names everything shown
        assertEquals(listOf("Phone", "Mail"), HomeCapacity.hiddenLabels(labels, shown = 2, capacity = 0))
    }

    @Test
    fun room_isWhatRemainsUnderTheConfiguredRows() {
        assertEquals(500, HomeCapacity.room(usableHeightPx = 1000, rowHeightPx = 100, rows = 5))
        assertEquals(0, HomeCapacity.room(usableHeightPx = 800, rowHeightPx = 100, rows = 8))
        assertEquals(-200, HomeCapacity.room(usableHeightPx = 600, rowHeightPx = 100, rows = 8))
    }
    @Test
    fun measuredOverflowNeverCountsAsUnknownSpace() {
        org.junit.Assert.assertTrue(HomeCapacity.canAddWidget(null, 200))
        org.junit.Assert.assertFalse(HomeCapacity.canAddWidget(-1, 200))
        org.junit.Assert.assertFalse(HomeCapacity.canAddWidget(-200, 200))
        org.junit.Assert.assertFalse(HomeCapacity.canAddWidget(199, 200))
        org.junit.Assert.assertTrue(HomeCapacity.canAddWidget(200, 200))
    }

}
