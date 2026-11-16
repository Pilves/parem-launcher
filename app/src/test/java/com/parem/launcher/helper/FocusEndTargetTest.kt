package com.parem.launcher.helper

import com.parem.launcher.helper.GrayscaleController.focusEndTarget
import com.parem.launcher.helper.GrayscaleController.sameFocusEndTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FocusEndTargetTest {

    private val wall = 1_700_000_000_000L
    private val elapsed = 50_000_000L
    private val end = wall + 25 * 60_000L
    private val scheduled = focusEndTarget(end, wall, elapsed)

    @Test
    fun firesOneSecondAfterTheEndAsBefore() {
        assertEquals(elapsed + 25 * 60_000L + 1_000, scheduled)
        // An end already passed still waits the one second
        assertEquals(elapsed + 1_000, focusEndTarget(wall - 5_000, wall, elapsed))
    }

    @Test
    fun laterReconcileOfTheSameSessionSkips() {
        assertTrue(sameFocusEndTarget(scheduled, focusEndTarget(end, wall + 90_000, elapsed + 90_000)))
        // Reading the two clocks a few ms apart
        assertTrue(sameFocusEndTarget(scheduled, focusEndTarget(end, wall + 90_000, elapsed + 90_004)))
    }

    @Test
    fun nothingPendingAlwaysEnqueues() {
        assertFalse(sameFocusEndTarget(null, scheduled))
    }

    @Test
    fun changedEndEnqueues() {
        assertFalse(sameFocusEndTarget(scheduled, focusEndTarget(end + 60_000, wall, elapsed)))
        assertFalse(sameFocusEndTarget(scheduled, focusEndTarget(end - 60_000, wall, elapsed)))
    }

    @Test
    fun changedWallClockEnqueuesEvenWithTheSameEnd() {
        // The pending job's relative delay no longer matches the end on the new clock
        assertFalse(sameFocusEndTarget(scheduled, focusEndTarget(end, wall + 60_000, elapsed)))
        assertFalse(sameFocusEndTarget(scheduled, focusEndTarget(end, wall - 2_000, elapsed)))
    }

    @Test
    fun slackBoundary() {
        assertTrue(sameFocusEndTarget(scheduled, scheduled + 99))
        assertFalse(sameFocusEndTarget(scheduled, scheduled + 100))
        assertFalse(sameFocusEndTarget(scheduled, scheduled - 100))
    }
}
