package com.parem.launcher.helper

import android.content.Context
import com.parem.launcher.helper.usageStats.EventLogWrapper
import java.time.LocalDate

/**
 * Utility for querying per-app usage stats.
 */
object UsageStatsHelper {

    // Aggregating today's UsageEvents is a full event-log scan (hundreds of ms),
    // and limit checks run on every tap of a limited app — so the aggregated map
    // is cached briefly. 60s matches the enforcement granularity (limits are in
    // minutes).
    private val cache = UsageReadCache()

    /** Null means usage is unavailable, not zero. Must be called off the main thread. */
    internal fun readToday(context: Context): UsageReadCache.Reading = cache.read(
        System.currentTimeMillis(), LocalDate.now().toEpochDay(),
        hasAccess = { context.appUsagePermissionGranted() },
        query = {
            try {
                val wrapper = EventLogWrapper(context, requireEvents = true)
                wrapper.aggregateForegroundStats(wrapper.getForegroundStatsByRelativeDay(0))
            } catch (e: Exception) {
                CrashReporter.reportNonFatal("UsageStatsHelper", e)
                throw e
            }
        },
    )

    /** Legacy display callers retain their empty fallback; launch gates use readToday. */
    fun getPerAppUsageToday(context: Context): Map<String, Long> =
        (readToday(context) as? UsageReadCache.Reading.Available)?.usage ?: emptyMap()

    /** Stale after 60 seconds, after a clock rollback, and at midnight: yesterday's total must never gate today's first launch. */
    fun isCacheStale(nowMs: Long, cachedAtMs: Long, today: Long, cachedDay: Long): Boolean =
        ScreenTimeFreshness.usageCacheStale(nowMs, cachedAtMs, today, cachedDay)

    /**
     * Returns today's total foreground usage for the given app in milliseconds.
     * Must be called off the main thread.
     */
    fun getUsageForApp(context: Context, packageName: String): Long {
        return getPerAppUsageToday(context)[packageName] ?: 0L
    }
}
