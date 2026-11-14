package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenTimeFreshnessTest {
    @Test
    fun homeRefreshesForFutureOrAtLeastOneMinuteOldTimestamp() {
        val now = 100_000L
        assertTrue(ScreenTimeFreshness.homeRefreshDue(now, now + 1))
        assertTrue(ScreenTimeFreshness.homeRefreshDue(now, now + 86_400_000))
        assertFalse(ScreenTimeFreshness.homeRefreshDue(now, now))
        assertFalse(ScreenTimeFreshness.homeRefreshDue(now, now - 59_999))
        assertTrue(ScreenTimeFreshness.homeRefreshDue(now, now - 60_000))
        assertTrue(ScreenTimeFreshness.homeRefreshDue(now, now - 60_001))
    }

    @Test
    fun sharedUsageCacheRejectsSameDayRollbackAndRetainsExistingTtl() {
        val now = 100_000L
        assertTrue(ScreenTimeFreshness.usageCacheStale(now, now + 1, 10, 10))
        assertFalse(ScreenTimeFreshness.usageCacheStale(now, now, 10, 10))
        assertFalse(ScreenTimeFreshness.usageCacheStale(now, now - 60_000, 10, 10))
        assertTrue(ScreenTimeFreshness.usageCacheStale(now, now - 60_001, 10, 10))
        assertTrue(ScreenTimeFreshness.usageCacheStale(now, now - 1, 10, 9))
    }
}
