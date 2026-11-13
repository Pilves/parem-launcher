package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class UsageReadCacheTest {
    @Test fun emptySuccessAndFailureRemainDistinctInCache() {
        val cache = UsageReadCache()
        var calls = 0
        val success = cache.read(100, 2, { true }) { calls++; emptyMap() }
        assertEquals(UsageReadCache.Reading.Available(emptyMap()), success)
        assertEquals(success, cache.read(101, 2, { true }) { error("should use cache") })
        assertEquals(1, calls)
        val failed = cache.read(70_000, 2, { true }) { error("query unavailable") }
        assertEquals(UsageReadCache.Reading.Failed, failed)
        assertEquals(failed, cache.read(70_001, 2, { true }) { error("should retain failure") })
        assertEquals(UsageReadCache.Reading.Available(mapOf("app" to 12L)),
            cache.read(140_001, 2, { true }) { mapOf("app" to 12L) })
    }

    @Test fun revokedDuringQueryCannotBecomeZeroOrPopulateCache() {
        val cache = UsageReadCache()
        var granted = true
        assertEquals(UsageReadCache.Reading.NoAccess,
            cache.read(100, 2, { granted }) { granted = false; emptyMap() })
        granted = true
        assertEquals(UsageReadCache.Reading.Available(mapOf("app" to 5L)),
            cache.read(101, 2, { granted }) { mapOf("app" to 5L) })
        granted = false
        assertEquals(UsageReadCache.Reading.NoAccess, cache.read(102, 2, { granted }) { error("no access") })
    }
}
