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

    private val zone = "Europe/Tallinn"
    private val dayStart = 1_763_244_000_000L
    private val hour = 3_600_000L
    // Made at 01:00, an hour after boot
    private val kept = ScreenTimeFreshness.GuardianStamp(dayStart, zone, dayStart + hour, hour, dayStart + hour)

    private fun reusable(
        nowMs: Long,
        elapsedMs: Long,
        stamp: ScreenTimeFreshness.GuardianStamp = kept,
        today: Long = dayStart,
        zoneId: String = zone,
        lastReadAvailable: Boolean = true,
    ) = ScreenTimeFreshness.guardianReusable(stamp, lastReadAvailable, today, zoneId, nowMs, elapsedMs)

    @Test
    fun keptGuardian_servesLaterScansOfTheSameDayZoneAndClock() {
        assertTrue(reusable(dayStart + hour + 60_000, hour + 60_000))
        assertTrue(reusable(dayStart + 23 * hour, 23 * hour))
    }

    @Test
    fun keptGuardian_resetsOnNewDayOrTimezone() {
        assertFalse(reusable(dayStart + 25 * hour, 25 * hour, today = dayStart + 24 * hour))
        // Helsinki shares Tallinn's midnight, but a zone change resets anyway
        assertFalse(reusable(dayStart + 2 * hour, 2 * hour, zoneId = "Europe/Helsinki"))
        // Moving west: the same date with a later midnight
        assertFalse(reusable(dayStart + 2 * hour, 2 * hour, today = dayStart + 2 * hour, zoneId = "Europe/London"))
    }

    @Test
    fun keptGuardian_resetsOnClockStepsEitherWay() {
        val now = dayStart + 2 * hour
        assertTrue(reusable(now + 1_000, 2 * hour))
        assertTrue(reusable(now - 1_000, 2 * hour))
        assertFalse(reusable(now + 1_001, 2 * hour))
        assertFalse(reusable(now - 1_001, 2 * hour))
        // Set back an hour after an hour of use: the wall clock reads its old time
        assertFalse(reusable(dayStart + hour, 2 * hour))
        assertFalse(reusable(now + 3 * hour, 2 * hour))
    }

    @Test
    fun keptGuardian_resetsWhenNowIsBeforeItsLastScan() {
        val servedAt3 = kept.copy(lastWallMs = dayStart + 3 * hour)
        assertTrue(reusable(dayStart + 3 * hour, 3 * hour, stamp = servedAt3))
        // Half a second back is under the step threshold but still before the last scan
        assertFalse(reusable(dayStart + 3 * hour - 500, 3 * hour, stamp = servedAt3))
    }

    @Test
    fun keptGuardian_resetsAfterAFailedOrUnavailableRead() {
        assertTrue(reusable(dayStart + 2 * hour, 2 * hour, lastReadAvailable = true))
        assertFalse(reusable(dayStart + 2 * hour, 2 * hour, lastReadAvailable = false))
    }

    @Test
    fun keptGuardian_madeInTheDaysFirstMinutesIsNotKept() {
        val minute = 60_000L
        val early = ScreenTimeFreshness.GuardianStamp(dayStart, zone, dayStart + minute, minute, dayStart + minute)
        assertFalse(reusable(dayStart + 2 * minute, 2 * minute, stamp = early))
        val settled = ScreenTimeFreshness.GuardianStamp(dayStart, zone, dayStart + 5 * minute, 5 * minute, dayStart + 5 * minute)
        assertTrue(reusable(dayStart + 6 * minute, 6 * minute, stamp = settled))
    }
}
