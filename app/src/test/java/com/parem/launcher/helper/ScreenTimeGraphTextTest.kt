package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScreenTimeGraphTextTest {

    @Test
    fun barLabelUsesHoursFromOneHourUp() {
        assertEquals("0m", ScreenTimeGraphText.barLabel(0))
        assertEquals("35m", ScreenTimeGraphText.barLabel(35 * 60_000L))
        assertEquals("1.0h", ScreenTimeGraphText.barLabel(3_600_000L))
        assertEquals("2.5h", ScreenTimeGraphText.barLabel(9_000_000L))
    }

    @Test
    fun spokenDurationSplitsHoursAndMinutes() {
        assertEquals(Pair(0L, 0L), ScreenTimeGraphText.spokenDuration(30_000L))
        assertEquals(Pair(0L, 35L), ScreenTimeGraphText.spokenDuration(35 * 60_000L))
        assertEquals(Pair(2L, 18L), ScreenTimeGraphText.spokenDuration(2 * 3_600_000L + 18 * 60_000L))
    }

    @Test
    fun columnIndexCoversWholeColumnAndRejectsPadding() {
        // width 100, padding 10 -> 7 slots of ~11.43 between x=10 and x=90
        assertNull(ScreenTimeGraphText.columnIndex(5f, 100, 10f, 7))
        assertEquals(0, ScreenTimeGraphText.columnIndex(10f, 100, 10f, 7))
        assertEquals(0, ScreenTimeGraphText.columnIndex(21f, 100, 10f, 7))
        assertEquals(1, ScreenTimeGraphText.columnIndex(22f, 100, 10f, 7))
        assertEquals(6, ScreenTimeGraphText.columnIndex(89f, 100, 10f, 7))
        assertNull(ScreenTimeGraphText.columnIndex(95f, 100, 10f, 7))
    }

    @Test
    fun columnIndexHandlesDegenerateSizes() {
        assertNull(ScreenTimeGraphText.columnIndex(5f, 100, 10f, 0))
        assertNull(ScreenTimeGraphText.columnIndex(5f, 10, 10f, 7))
    }
}
