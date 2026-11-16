package com.parem.launcher.helper.usageStats

import android.app.usage.UsageEvents
import com.parem.launcher.helper.UsageEventFilter

/**
 * The guardian's lookback, grouped by package as it is read, so a test reads only
 * that package's events instead of the whole 24 h.
 *
 * Gives the same answers as replaying the whole log in order: a DEVICE_STARTUP
 * closes every package, so only a package's events after the last startup count.
 * It keeps the events rather than a set of apps open at the end because a test
 * ignores closes stamped with the tested event's own time.
 */
internal class GuardianLookback {
    private class Event(val index: Int, val type: Int, val className: String?, val timestamp: Long)

    private val byPackage = HashMap<String?, MutableList<Event>>()
    private var size = 0
    private var lastStartup = -1
    // A test skips dream-class events before it checks for a startup
    private var lastNonDreamStartup = -1

    fun add(type: Int, packageName: String?, className: String?, timestamp: Long) {
        val index = size++
        when (type) {
            UsageEvents.Event.DEVICE_STARTUP -> {
                lastStartup = index
                if (!UsageEventFilter.isDream(className)) lastNonDreamStartup = index
            }
            UsageEvents.Event.ACTIVITY_RESUMED, 4,
            UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 3 ->
                byPackage.getOrPut(packageName) { mutableListOf() }.add(Event(index, type, className, timestamp))
        }
    }

    /** Whether [packageName] was left open, not counting closes stamped [timestamp]. */
    fun wasOpen(packageName: String?, timestamp: Long): Boolean {
        var open = false
        for (e in byPackage[packageName] ?: return false) {
            if (e.index < lastNonDreamStartup || UsageEventFilter.isDream(e.className)) continue
            when (e.type) {
                UsageEvents.Event.ACTIVITY_RESUMED, 4 -> open = true
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                3 -> if (e.timestamp != timestamp) open = false
            }
        }
        return open
    }

    /** Whether a dream of [packageName] was still showing at the end. */
    fun dreamActive(packageName: String): Boolean {
        var dream = false
        for (e in byPackage[packageName] ?: return false) {
            if (e.index < lastStartup) continue
            when (e.type) {
                UsageEvents.Event.ACTIVITY_RESUMED, 4 -> dream = UsageEventFilter.dreamActiveAfter(dream, e.className, true)
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED, 3 ->
                    dream = UsageEventFilter.dreamActiveAfter(dream, e.className, false)
            }
        }
        return dream
    }
}
