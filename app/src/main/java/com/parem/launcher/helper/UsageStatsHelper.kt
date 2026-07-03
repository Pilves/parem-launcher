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
     * Returns today's total foreground usage for the given app in milliseconds.
     * Must be called off the main thread.
     */
    fun getUsageForApp(context: Context, packageName: String): Long {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return 0L
        if (!context.appUsagePermissionGranted()) return 0L

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
        return cachedUsage[packageName] ?: 0L
    }
}
