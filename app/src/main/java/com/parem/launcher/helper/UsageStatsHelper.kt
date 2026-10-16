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
    private const val CACHE_TTL_MS = 60_000L

    @Volatile
    private var cachedAt = 0L

    // Local day (epoch day) the cached map belongs to: a map filled at 23:59:40
    // is "today's" usage for 60 s only on the day it was scanned.
    @Volatile
    private var cachedDay = Long.MIN_VALUE

    @Volatile
    private var cachedUsage: Map<String, Long> = emptyMap()

    /**
     * Returns today's foreground usage per package in milliseconds. Single
     * cached access point for today's scan — the home-screen total, the
     * drawer's per-app times, and limit checks all share this one map.
     * Must be called off the main thread.
     */
    fun getPerAppUsageToday(context: Context): Map<String, Long> {
        if (!context.appUsagePermissionGranted()) return emptyMap()

        val now = System.currentTimeMillis()
        val today = LocalDate.now().toEpochDay()
        if (isCacheStale(now, cachedAt, today, cachedDay)) {
            cachedUsage = try {
                val wrapper = EventLogWrapper(context)
                val foregroundStats = wrapper.getForegroundStatsByRelativeDay(0)
                wrapper.aggregateForegroundStats(foregroundStats)
                    .groupBy { it.applicationId }
                    .mapValues { (_, stats) -> stats.sumOf { it.timeUsed } }
            } catch (e: Exception) {
                emptyMap()
            }
            cachedAt = now
            cachedDay = today
        }
        return cachedUsage
    }

    /** Stale after [CACHE_TTL_MS], and at midnight: yesterday's total must never gate today's first launch. */
    fun isCacheStale(nowMs: Long, cachedAtMs: Long, today: Long, cachedDay: Long): Boolean =
        nowMs - cachedAtMs > CACHE_TTL_MS || today != cachedDay

    /**
     * Returns today's total foreground usage for the given app in milliseconds.
     * Must be called off the main thread.
     */
    fun getUsageForApp(context: Context, packageName: String): Long {
        return getPerAppUsageToday(context)[packageName] ?: 0L
    }
}
