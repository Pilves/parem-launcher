package com.parem.launcher.helper

import kotlin.math.abs

/** Wall-clock cache checks: a timestamp in the future is stale after a clock rollback. */
object ScreenTimeFreshness {
    private const val MINUTE_MS = 60_000L
    // Under the 2 s step at which Android's usage service rewrites stored event times
    private const val CLOCK_STEP_MS = 1_000L
    // An event stamped just before midnight may reach the log a moment later, so a
    // lookback read in the first minutes of the day is not kept
    private const val LOOKBACK_SETTLE_MS = 5 * MINUTE_MS

    fun homeRefreshDue(nowMs: Long, lastUpdatedMs: Long): Boolean =
        nowMs < lastUpdatedMs || nowMs - lastUpdatedMs >= MINUTE_MS

    fun usageCacheStale(nowMs: Long, cachedAtMs: Long, today: Long, cachedDay: Long): Boolean =
        nowMs < cachedAtMs || nowMs - cachedAtMs > MINUTE_MS || today != cachedDay

    /** The day, zone and clocks a kept screen-time guardian was made under, and its last scan. */
    data class GuardianStamp(
        val dayStart: Long,
        val zoneId: String,
        val createdWallMs: Long,
        val createdElapsedMs: Long,
        val lastWallMs: Long,
    )

    /**
     * Whether a guardian kept from an earlier scan may serve this one. Its lookback
     * holds only on the same day and zone, with no clock step since it was made, after
     * a successful read (M6-WP52, M6-WP55). Otherwise the scan starts cold, as all did.
     */
    fun guardianReusable(
        kept: GuardianStamp,
        lastReadAvailable: Boolean,
        dayStart: Long,
        zoneId: String,
        nowMs: Long,
        elapsedMs: Long,
    ): Boolean =
        lastReadAvailable &&
            dayStart == kept.dayStart && zoneId == kept.zoneId &&
            kept.createdWallMs - kept.dayStart >= LOOKBACK_SETTLE_MS &&
            nowMs >= kept.lastWallMs &&
            abs((nowMs - kept.createdWallMs) - (elapsedMs - kept.createdElapsedMs)) <= CLOCK_STEP_MS
}
