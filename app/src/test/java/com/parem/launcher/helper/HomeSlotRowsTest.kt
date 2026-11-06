package com.parem.launcher.helper

import com.parem.launcher.helper.HomeSlotRows.Row.*
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSlotRowsTest {
    @Test fun firstEmptyIsTheOnlyHintAndLockedHidesEmptyRows() {
        assertEquals(listOf(HINT, HIDDEN, HIDDEN, HIDDEN), HomeSlotRows.render(listOf(false, false, false, false), false))
        assertEquals(listOf(FILLED, HINT, FILLED, HIDDEN), HomeSlotRows.render(listOf(true, false, true, false), false))
        assertEquals(listOf(FILLED, HIDDEN, FILLED, HIDDEN), HomeSlotRows.render(listOf(true, false, true, false), true))
        assertEquals(listOf(HINT), HomeSlotRows.render(listOf(false), false))
    }

    @Test fun fittingPrefersFilledRowsWithoutMovingTheirSlots() {
        val rows = listOf(FILLED, HINT, HIDDEN, FILLED)
        assertEquals(listOf(FILLED, HIDDEN, HIDDEN, FILLED), HomeSlotRows.fit(rows, 2))
        assertEquals(listOf(FILLED, HINT, HIDDEN, FILLED), HomeSlotRows.fit(rows, 3))
        assertEquals(listOf(FILLED, HIDDEN, HIDDEN, HIDDEN), HomeSlotRows.fit(rows, 1))
    }
}
