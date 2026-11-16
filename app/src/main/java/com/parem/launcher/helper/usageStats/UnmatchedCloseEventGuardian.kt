package com.parem.launcher.helper.usageStats

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.util.Log
import com.parem.launcher.helper.UsageEventFilter

/**
 * "…a diminutive Guardian who traveled backward through time…"
 *
 * Guards [EventLogWrapper] against Faulty unmatched close events (per
 * [the documentation](https://codeberg.org/fynngodau/usageDirect/wiki/Event-log-wrapper-scenarios))
 * by seeking backwards through time and scanning for the open event.
 */
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager, private val requireEvents: Boolean = false) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 // 24 hours
    }

    // Cache parsed events for a given queryStart to avoid redundant 24h IPC queries.
    // Multiple test() calls within the same getForegroundStatsByTimestamps invocation
    // share the same queryStart, so this eliminates 3-10x redundant queries.
    private var cachedQueryStart: Long = -1L
    private var cachedLookback: GuardianLookback? = null

    /**
     * Read the lookback once for unmatched closes and event-free fallback checks.
     */
    private fun queryLookback(queryStart: Long): GuardianLookback? {
        val usageEvents = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart) ?: run {
            if (requireEvents) throw IllegalStateException("Usage lookback unavailable")
            return null
        }
        val lookback = GuardianLookback()
        val e = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(e)
            lookback.add(e.eventType, e.packageName, e.className, e.timeStamp)
        }

        return lookback
    }

    /**
     * @param event      Event to validate
     * @param queryStart Timestamp at which original query started
     * @return True if the event is valid, false otherwise
     */
    // Synchronized: the wrapper (and thus this cache) can be hit concurrently from
    // separate viewModelScope IO coroutines; the two cache fields must move together.
    @Synchronized
    fun test(event: UsageEvents.Event, queryStart: Long): Boolean {
        if (UsageEventFilter.isDream(event.className)) return false
        val open = lookbackBefore(queryStart)?.wasOpen(event.packageName, event.timeStamp) ?: return false

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")
        return open
    }

    /** Exclude an already-running dream from the event-free foreground fallback. */
    @Synchronized
    fun dreamActiveAt(packageName: String, queryStart: Long): Boolean =
        lookbackBefore(queryStart)?.dreamActive(packageName) ?: true

    private fun lookbackBefore(queryStart: Long): GuardianLookback? =
        if (queryStart == cachedQueryStart && cachedLookback != null) {
            cachedLookback!!
        } else {
            val queried = queryLookback(queryStart)
            cachedQueryStart = queryStart
            cachedLookback = queried
            queried
        }
}
