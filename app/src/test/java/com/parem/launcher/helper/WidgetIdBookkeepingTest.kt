package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class WidgetIdBookkeepingTest {

    @Test
    fun unavailableAndThrowingQueriesStayUnknown() {
        var failures = 0
        assertNull(WidgetIdBookkeeping.queryOrUnknown<String>({ null }) { failures++ })
        assertEquals(0, failures)
        assertNull(WidgetIdBookkeeping.queryOrUnknown<String>({ throw IllegalStateException("unavailable") }) { failures++ })
        assertEquals(1, failures)
        assertEquals("available", WidgetIdBookkeeping.queryOrUnknown({ "available" }) { failures++ })
    }

    @Test
    fun unavailableSavedIdsStillOccupyCapacityUntilRemoved() {
        val saved = listOf(1, 2, 3, 4, 5, 6)
        assertFalse(WidgetIdBookkeeping.canAdd(saved))
        assertTrue(WidgetIdBookkeeping.canAdd(saved.dropLast(1)))
    }

    @Test
    fun anotherWidgetOperationWaitsForPendingOperationAndResult() {
        assertTrue(WidgetIdBookkeeping.canStartOperation(false, false))
        assertFalse(WidgetIdBookkeeping.canStartOperation(true, false))
        assertFalse(WidgetIdBookkeeping.canStartOperation(false, true))
        assertFalse(WidgetIdBookkeeping.canStartOperation(true, true))
    }

    @Test
    fun pendingReleaseNeverDeletesASavedWidget() {
        assertFalse(WidgetIdBookkeeping.canReleasePending(listOf(1, 2), 2))
        assertTrue(WidgetIdBookkeeping.canReleasePending(listOf(1, 2), 3))
    }

    @Test
    fun replaceId_keepsPosition() {
        assertEquals(listOf(1, 20, 3), WidgetIdBookkeeping.replaceId(listOf(1, 2, 3), oldId = 2, newId = 20))
    }

    @Test
    fun replaceId_neverResurrectsRemovedTarget() {
        assertNull(WidgetIdBookkeeping.replaceId(listOf(1, 3), oldId = 2, newId = 20))
    }

    @Test
    fun replaceId_tracksTargetAfterReorder() {
        assertEquals(listOf(3, 20, 1), WidgetIdBookkeeping.replaceId(listOf(3, 2, 1), oldId = 2, newId = 20))
    }

    @Test
    fun legacySwapIndexIsNeverUsedAsAnId() {
        assertEquals(-2, WidgetIdBookkeeping.pendingTarget(null, legacyIndex = 1))
        assertEquals(-1, WidgetIdBookkeeping.pendingTarget(null, legacyIndex = -1))
        assertEquals(42, WidgetIdBookkeeping.pendingTarget(42, legacyIndex = 1))
    }

    @Test
    fun orphanIds_excludesHomeAndPending() {
        val orphans = WidgetIdBookkeeping.orphanIds(
            hostIds = intArrayOf(1, 2, 3, 4),
            ids = listOf(1, 3),
            pendingId = 4,
        )
        assertEquals(listOf(2), orphans)
    }

    @Test
    fun orphanIds_noPending() {
        assertEquals(listOf(2), WidgetIdBookkeeping.orphanIds(intArrayOf(1, 2), listOf(1), pendingId = -1))
    }
}
