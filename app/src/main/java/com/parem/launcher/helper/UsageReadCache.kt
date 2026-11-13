package com.parem.launcher.helper

/** An empty successful reading is different from a failed or unavailable reading. */
internal class UsageReadCache {
    sealed class Reading {
        data class Available(val usage: Map<String, Long>) : Reading()
        data object NoAccess : Reading()
        data object Failed : Reading()
    }

    private var cachedAt = 0L
    private var cachedDay = Long.MIN_VALUE
    private var cached: Reading? = null

    @Synchronized
    fun read(now: Long, day: Long, hasAccess: () -> Boolean, query: () -> Map<String, Long>): Reading {
        if (!hasAccess()) return Reading.NoAccess
        if (cached == null || ScreenTimeFreshness.usageCacheStale(now, cachedAt, day, cachedDay)) {
            val result = try { Reading.Available(query()) } catch (_: Exception) { Reading.Failed }
            if (!hasAccess()) return Reading.NoAccess
            cached = result
            cachedAt = now
            cachedDay = day
        }
        return if (hasAccess()) checkNotNull(cached) else Reading.NoAccess
    }
}
