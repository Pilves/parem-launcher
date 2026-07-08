package com.parem.launcher.helper

import android.content.Context
import android.os.Build
import com.parem.launcher.helper.usageStats.EventLogWrapper

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

    @Volatile
    private var cachedUsage: Map<String, Long> = emptyMap()

    /**
     * Returns today's foreground usage per package in milliseconds. Single
     * cached access point for today's scan — the home-screen total, the
     * drawer's per-app times, and limit checks all share this one map.
     * Must be called off the main thread.
     */
    fun getPerAppUsageToday(context: Context): Map<String, Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyMap()
        if (!context.appUsagePermissionGranted()) return emptyMap()

        val now = System.currentTimeMillis()
        if (now - cachedAt > CACHE_TTL_MS) {
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
        }
        return cachedUsage
    }

    /**
     * Returns today's total foreground usage for the given app in milliseconds.
     * Must be called off the main thread.
     */
    fun getUsageForApp(context: Context, packageName: String): Long {
        return getPerAppUsageToday(context)[packageName] ?: 0L
    }
}
