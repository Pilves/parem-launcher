package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageStatsHelperTest {

    @Test
    fun isCacheStale_atMidnightEvenInsideTheTtl() {
        // A49: filled at 23:59:40, read at 00:00:20 — 40 s old but a different day
        val cachedAt = 1_000_000L
        assertTrue(UsageStatsHelper.isCacheStale(cachedAt + 40_000L, cachedAt, today = 101, cachedDay = 100))
        assertFalse(UsageStatsHelper.isCacheStale(cachedAt + 40_000L, cachedAt, today = 100, cachedDay = 100))
        assertTrue(UsageStatsHelper.isCacheStale(cachedAt + 61_000L, cachedAt, today = 100, cachedDay = 100))
    }
}
