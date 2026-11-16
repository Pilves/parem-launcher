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

    // Lookbacks by queryStart, so a guardian kept for the day (UsageStatsHelper) reads
    // each once. A scan needs midnight's, plus one per reboot today; two also bounds
    // what the graph's guardian holds across its 13 days.
    private val lookbacks = object : LinkedHashMap<Long, GuardianLookback>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, GuardianLookback>?) = size > 2
    }

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
    // separate viewModelScope IO coroutines, and the lookback map is not thread-safe.
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
        lookbacks[queryStart] ?: queryLookback(queryStart)?.also { lookbacks[queryStart] = it }
}
