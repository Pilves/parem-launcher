package com.parem.launcher.helper

import android.content.Context
import android.icu.util.Calendar
import android.os.SystemClock
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

    // The guardian's lookback is the 24 h before midnight, so one wrapper (and the
    // guardian inside it) serves every scan of the day. These are touched only from
    // the lambdas cache.read runs under its lock.
    private class KeptGuardian(val wrapper: EventLogWrapper, val stamp: ScreenTimeFreshness.GuardianStamp)
    private var kept: KeptGuardian? = null
    private var lastReadAvailable = false

    /** Null means usage is unavailable, not zero. Must be called off the main thread. */
    internal fun readToday(context: Context): UsageReadCache.Reading = cache.read(
        System.currentTimeMillis(), LocalDate.now().toEpochDay(),
        // A read that finds no access sends the next scan in cold (M6-WP55)
        hasAccess = { context.appUsagePermissionGranted().also { if (!it) lastReadAvailable = false } },
        query = { scanToday(context) },
    )

    private fun scanToday(context: Context): Map<String, Long> {
        val now = System.currentTimeMillis()
        val elapsed = SystemClock.elapsedRealtime()
        val day = Calendar.getInstance()
        day.timeInMillis = now.convertEpochToMidnight()
        val dayStart = day.timeInMillis
        day.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = day.timeInMillis
        val zoneId = day.timeZone.id

        val reused = kept?.takeIf {
            ScreenTimeFreshness.guardianReusable(it.stamp, lastReadAvailable, dayStart, zoneId, now, elapsed)
        }
        val stamp = reused?.stamp?.copy(lastWallMs = now)
            ?: ScreenTimeFreshness.GuardianStamp(dayStart, zoneId, now, elapsed, now)
        lastReadAvailable = false
        try {
            val wrapper = reused?.wrapper ?: EventLogWrapper(context, requireEvents = true)
            val usage = wrapper.aggregateForegroundStats(wrapper.getForegroundStatsByTimestamps(dayStart, dayEnd))
            kept = KeptGuardian(wrapper, stamp)
            lastReadAvailable = true
            return usage
        } catch (e: Exception) {
            CrashReporter.reportNonFatal("UsageStatsHelper", e)
            throw e
        }
    }

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
