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
class UnmatchedCloseEventGuardian(private val usageStatsManager: UsageStatsManager) {

    companion object {
        private const val SCAN_INTERVAL = 1000L * 60 * 60 * 24 // 24 hours
    }

    // Cache parsed events for a given queryStart to avoid redundant 24h IPC queries.
    // Multiple test() calls within the same getForegroundStatsByTimestamps invocation
    // share the same queryStart, so this eliminates 3-10x redundant queries.
    private var cachedQueryStart: Long = -1L
    private data class Event(val type: Int, val packageName: String?, val className: String?, val timestamp: Long)
    private var cachedEvents: List<Event>? = null

    /**
     * Parse the lookback once for unmatched closes and event-free fallback checks.
     */
    private fun queryAndParseEvents(queryStart: Long): List<Event>? {
        val usageEvents = usageStatsManager.queryEvents(queryStart - SCAN_INTERVAL, queryStart) ?: return null
        val result = mutableListOf<Event>()
        val e = UsageEvents.Event()

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(e)
            result.add(Event(e.eventType, e.packageName, e.className, e.timeStamp))
        }

        return result
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
        val events = eventsBefore(queryStart) ?: return false

        // Track whether the package is currently in foreground or background
        var open = false // Not open until opened

        for ((eventType, packageName, className, timestamp) in events) {
            if (UsageEventFilter.isDream(className)) continue
            if (eventType == UsageEvents.Event.DEVICE_STARTUP) {
                // Consider all apps closed after startup according to docs
                open = false
            }

            // Only consider events concerning our package otherwise
            if (event.packageName == packageName) {
                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED, 4 -> open = true
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    3 -> if (timestamp != event.timeStamp) open = false
                }
            }
        }

        val result = if (open) "True" else "Faulty"
        Log.d("Guardian", "Scanned for package ${event.packageName} and determined event to be $result")
        return open
    }

    /** Exclude an already-running dream from the event-free foreground fallback. */
    @Synchronized
    fun dreamActiveAt(packageName: String, queryStart: Long): Boolean {
        var dream = false
        for ((type, pkg, className) in eventsBefore(queryStart) ?: return true) {
            if (type == UsageEvents.Event.DEVICE_STARTUP) dream = false
            if (pkg != packageName) continue
            when (type) {
                UsageEvents.Event.ACTIVITY_RESUMED, 4 -> dream = UsageEventFilter.dreamActiveAfter(dream, className, true)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 3 ->
                    dream = UsageEventFilter.dreamActiveAfter(dream, className, false)
            }
        }
        return dream
    }

    private fun eventsBefore(queryStart: Long): List<Event>? =
        if (queryStart == cachedQueryStart && cachedEvents != null) {
            cachedEvents!!
        } else {
            val queried = queryAndParseEvents(queryStart)
            cachedQueryStart = queryStart
            cachedEvents = queried
            queried
        }
}
