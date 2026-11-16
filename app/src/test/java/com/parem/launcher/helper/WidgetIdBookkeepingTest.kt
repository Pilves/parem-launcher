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

    private fun cacheWith(vararg entries: Pair<Int, String>) = WidgetViewCache<String>().apply {
        retain(entries.map { it.first }, stamp = 1, config = "day")
        entries.forEach { (id, signature) -> put(id, signature, "view$id") }
    }

    @Test
    fun viewCache_reusesOnlyForTheSameProviderAndProfile() {
        val cache = cacheWith(1 to "clock@user0")
        assertEquals("view1", cache.reusable(1, "clock@user0"))
        assertEquals("view1", cache.reusable(1, "clock@user0"))
        assertNull(cache.reusable(1, "clock@user10"))
        // The mismatched entry is gone, so it cannot come back for its old signature either.
        assertNull(cache.reusable(1, "clock@user0"))
        assertNull(cache.reusable(2, "clock@user0"))
    }

    @Test
    fun viewCache_dropsIdsNoLongerOnHome() {
        val cache = cacheWith(1 to "a", 2 to "b", 3 to "c")
        cache.retain(listOf(3, 1), stamp = 1, config = "day")
        assertEquals("view1", cache.reusable(1, "a"))
        assertNull(cache.reusable(2, "b"))
        assertEquals("view3", cache.reusable(3, "c"))
    }

    @Test
    fun viewCache_dropsEverythingAfterAPackageOrConfigurationChange() {
        val afterPackageChange = cacheWith(1 to "a")
        afterPackageChange.retain(listOf(1), stamp = 2, config = "day")
        assertNull(afterPackageChange.reusable(1, "a"))

        val afterNightModeFlip = cacheWith(1 to "a")
        afterNightModeFlip.retain(listOf(1), stamp = 1, config = "night")
        assertNull(afterNightModeFlip.reusable(1, "a"))

        // Views made after the change are reused while nothing changes again.
        afterNightModeFlip.put(1, "a", "fresh")
        afterNightModeFlip.retain(listOf(1), stamp = 1, config = "night")
        assertEquals("fresh", afterNightModeFlip.reusable(1, "a"))
    }

    @Test
    fun viewCache_evictAndClear() {
        val cache = cacheWith(1 to "a", 2 to "b")
        cache.evict(1)
        assertNull(cache.reusable(1, "a"))
        assertEquals("view2", cache.reusable(2, "b"))
        cache.clear()
        assertNull(cache.reusable(2, "b"))
    }

    @Test
    fun viewCache_reportsEachSizeOnceUntilTheViewIsReplaced() {
        val cache = cacheWith(1 to "a")
        assertTrue(cache.sizeChanged(1, 360, 200))
        assertFalse(cache.sizeChanged(1, 360, 200))
        assertTrue(cache.sizeChanged(1, 360, 173))
        assertFalse(cache.sizeChanged(1, 360, 173))
        // A fresh view for the same ID reports again, as a newly created view always did.
        cache.put(1, "a", "fresh")
        assertTrue(cache.sizeChanged(1, 360, 173))
        // An ID the cache does not hold always reports.
        assertTrue(cache.sizeChanged(2, 360, 173))
        assertTrue(cache.sizeChanged(2, 360, 173))
    }
}
