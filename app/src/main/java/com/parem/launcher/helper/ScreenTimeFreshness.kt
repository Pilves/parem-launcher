package com.parem.launcher.helper

/** Wall-clock cache checks: a timestamp in the future is stale after a clock rollback. */
object ScreenTimeFreshness {
    private const val MINUTE_MS = 60_000L

    fun homeRefreshDue(nowMs: Long, lastUpdatedMs: Long): Boolean =
        nowMs < lastUpdatedMs || nowMs - lastUpdatedMs >= MINUTE_MS

    fun usageCacheStale(nowMs: Long, cachedAtMs: Long, today: Long, cachedDay: Long): Boolean =
        nowMs < cachedAtMs || nowMs - cachedAtMs > MINUTE_MS || today != cachedDay
}
