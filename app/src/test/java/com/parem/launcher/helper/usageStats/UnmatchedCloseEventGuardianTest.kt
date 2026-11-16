package com.parem.launcher.helper.usageStats

import android.app.usage.UsageEvents
import com.parem.launcher.helper.UsageEventFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

private const val RESUMED = UsageEvents.Event.ACTIVITY_RESUMED
private const val PAUSED = UsageEvents.Event.ACTIVITY_PAUSED
private const val STOPPED = UsageEvents.Event.ACTIVITY_STOPPED
private const val END_OF_DAY = 3
private const val CONTINUE_PREVIOUS_DAY = 4
private const val STARTUP = UsageEvents.Event.DEVICE_STARTUP
private const val SHUTDOWN = UsageEvents.Event.DEVICE_SHUTDOWN
private const val CONFIG = UsageEvents.Event.CONFIGURATION_CHANGE
private const val DREAM = "android.service.dreams.DreamActivity"

private data class Ev(val type: Int, val packageName: String?, val className: String?, val timestamp: Long)

private fun ev(type: Int, pkg: String?, ts: Long, cls: String? = "$pkg.Main") = Ev(type, pkg, cls, ts)
private fun boot(ts: Long, cls: String? = null) = Ev(STARTUP, "android", cls, ts)

/**
 * UnmatchedCloseEventGuardian.test and dreamActiveAt as of ddaa007, copied with a
 * plain list in place of the queried lookback: the real ones need UsageStatsManager.
 */
private object LegacyGuardianScan {
    fun test(events: List<Ev>, eventPackageName: String?, eventClassName: String?, eventTimeStamp: Long): Boolean {
        if (UsageEventFilter.isDream(eventClassName)) return false

        // Track whether the package is currently in foreground or background
        var open = false // Not open until opened

        for ((eventType, packageName, className, timestamp) in events) {
            if (UsageEventFilter.isDream(className)) continue
            if (eventType == UsageEvents.Event.DEVICE_STARTUP) {
                // Consider all apps closed after startup according to docs
                open = false
            }

            // Only consider events concerning our package otherwise
            if (eventPackageName == packageName) {
                when (eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED, 4 -> open = true
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED,
                    3 -> if (timestamp != eventTimeStamp) open = false
                }
            }
        }
        return open
    }

    fun dreamActiveAt(events: List<Ev>, packageName: String): Boolean {
        var dream = false
        for ((type, pkg, className) in events) {
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
}

/** A recorded lookback and the guardian's answer for one close of [pkg] stamped [ts]. */
private class Golden(
    val name: String, val lookback: List<Ev>, val pkg: String, val ts: Long, val expected: Boolean,
    val cls: String? = "$pkg.Main",
)

/** Expected values are worked out by hand from the event order, then pinned against today's scan. */
private val GOLDENS = listOf(
    Golden("true unmatched close: opened before queryStart, never closed",
        listOf(ev(RESUMED, "a", 100)), "a", 1_000, true),
    Golden("faulty unmatched close: closed before queryStart",
        listOf(ev(RESUMED, "a", 100), ev(PAUSED, "a", 200)), "a", 1_000, false),
    Golden("faulty: paused then stopped",
        listOf(ev(RESUMED, "a", 100), ev(PAUSED, "a", 200), ev(STOPPED, "a", 210)), "a", 1_000, false),
    Golden("duplicate open",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "a", 150)), "a", 1_000, true),
    Golden("reopened after a close",
        listOf(ev(RESUMED, "a", 100), ev(PAUSED, "a", 200), ev(RESUMED, "a", 300)), "a", 1_000, true),
    Golden("end of day closes",
        listOf(ev(RESUMED, "a", 100), ev(END_OF_DAY, "a", 999)), "a", 1_000, false),
    Golden("continue previous day opens",
        listOf(ev(CONTINUE_PREVIOUS_DAY, "a", 50)), "a", 1_000, true),
    Golden("a startup closes every app",
        listOf(ev(RESUMED, "a", 100), boot(200)), "a", 1_000, false),
    Golden("opened again after the startup",
        listOf(ev(RESUMED, "a", 100), boot(200), ev(RESUMED, "a", 300)), "a", 1_000, true),
    Golden("a shutdown alone does not close",
        listOf(ev(RESUMED, "a", 100), ev(SHUTDOWN, "android", 200, null)), "a", 1_000, true),
    // A reboot today moved queryStart to 1_000: the lookback is the day before it
    Golden("reboot moved queryStart: open at the shutdown",
        listOf(boot(10), ev(RESUMED, "a", 100), ev(RESUMED, "b", 200), ev(PAUSED, "b", 300),
            ev(SHUTDOWN, "android", 990, null)), "a", 1_005, true),
    Golden("reboot moved queryStart: closed before the shutdown",
        listOf(boot(10), ev(RESUMED, "a", 100), ev(RESUMED, "b", 200), ev(PAUSED, "b", 300),
            ev(SHUTDOWN, "android", 990, null)), "b", 1_005, false),
    // The swapped case: a's close at 990 came after the startup at 1_000 in today's
    // log, so the guardian tests that same close and finds it in its own lookback
    Golden("a close at exactly the tested timestamp is ignored",
        listOf(ev(RESUMED, "a", 500), ev(PAUSED, "a", 990)), "a", 990, true),
    Golden("the same lookback, tested one ms later",
        listOf(ev(RESUMED, "a", 500), ev(PAUSED, "a", 990)), "a", 991, false),
    Golden("an earlier close still counts when the last one is skipped",
        listOf(ev(RESUMED, "a", 100), ev(PAUSED, "a", 200), ev(PAUSED, "a", 990)), "a", 990, false),
    Golden("interleaved: a closed",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "b", 110), ev(PAUSED, "a", 120), ev(RESUMED, "c", 130),
            ev(PAUSED, "b", 140), ev(STOPPED, "a", 150), ev(RESUMED, "b", 160)), "a", 1_000, false),
    Golden("interleaved: b reopened",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "b", 110), ev(PAUSED, "a", 120), ev(RESUMED, "c", 130),
            ev(PAUSED, "b", 140), ev(STOPPED, "a", 150), ev(RESUMED, "b", 160)), "b", 1_000, true),
    Golden("interleaved: c open",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "b", 110), ev(PAUSED, "a", 120), ev(RESUMED, "c", 130),
            ev(PAUSED, "b", 140), ev(STOPPED, "a", 150), ev(RESUMED, "b", 160)), "c", 1_000, true),
    Golden("interleaved across a startup: a",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "b", 110), boot(115), ev(RESUMED, "c", 130), ev(PAUSED, "b", 140)),
        "a", 1_000, false),
    Golden("interleaved across a startup: c",
        listOf(ev(RESUMED, "a", 100), ev(RESUMED, "b", 110), boot(115), ev(RESUMED, "c", 130), ev(PAUSED, "b", 140)),
        "c", 1_000, true),
    Golden("empty lookback", emptyList(), "a", 1_000, false),
    Golden("other packages and event types are ignored",
        listOf(ev(RESUMED, "a", 100), ev(CONFIG, "a", 200), ev(RESUMED, "b", 300), ev(PAUSED, "b", 400)),
        "a", 1_000, true),
    Golden("a dream-class open is skipped",
        listOf(ev(RESUMED, "a", 100, DREAM)), "a", 1_000, false),
    Golden("a dream-class close is skipped",
        listOf(ev(RESUMED, "a", 100), ev(PAUSED, "a", 200, DREAM)), "a", 1_000, true),
    Golden("a dream-class tested close is never true",
        listOf(ev(RESUMED, "a", 100)), "a", 1_000, false, cls = DREAM),
    Golden("log order wins over timestamp order: open last",
        listOf(ev(PAUSED, "a", 300), ev(RESUMED, "a", 200)), "a", 1_000, true),
    Golden("log order wins over timestamp order: close last",
        listOf(ev(RESUMED, "a", 300), ev(PAUSED, "a", 200)), "a", 1_000, false),
)

private class DreamGolden(val name: String, val lookback: List<Ev>, val expected: Boolean)

private const val CLOCK = "com.android.deskclock"

private val DREAM_GOLDENS = listOf(
    DreamGolden("dream started before the query", listOf(ev(RESUMED, CLOCK, 100, DREAM)), true),
    DreamGolden("the clock app resumed after the dream",
        listOf(ev(RESUMED, CLOCK, 100, DREAM), ev(RESUMED, CLOCK, 200, "$CLOCK.DeskClock")), false),
    DreamGolden("a normal close leaves the dream showing",
        listOf(ev(RESUMED, CLOCK, 100, DREAM), ev(PAUSED, CLOCK, 150, "$CLOCK.DeskClock")), true),
    DreamGolden("the dream closed", listOf(ev(RESUMED, CLOCK, 100, DREAM), ev(PAUSED, CLOCK, 150, DREAM)), false),
    DreamGolden("a startup ends the dream", listOf(ev(RESUMED, CLOCK, 100, DREAM), boot(200)), false),
    DreamGolden("another package's dream", listOf(ev(RESUMED, "other", 100, DREAM)), false),
    DreamGolden("empty lookback", emptyList(), false),
)

private fun grouped(events: List<Ev>) =
    GuardianLookback().apply { events.forEach { add(it.type, it.packageName, it.className, it.timestamp) } }

/** The guardian's test(): its own dream check on the tested close, then the grouped lookback. */
private fun GuardianLookback.test(pkg: String?, cls: String?, ts: Long) =
    !UsageEventFilter.isDream(cls) && wasOpen(pkg, ts)

class UnmatchedCloseEventGuardianTest {

    @Test
    fun goldens_pinTodaysUnmatchedCloseAnswers() {
        for (g in GOLDENS) {
            assertEquals(g.name, g.expected, LegacyGuardianScan.test(g.lookback, g.pkg, g.cls, g.ts))
            assertEquals(g.name, g.expected, grouped(g.lookback).test(g.pkg, g.cls, g.ts))
        }
    }

    @Test
    fun goldens_pinTodaysDreamAnswers() {
        for (g in DREAM_GOLDENS) {
            assertEquals(g.name, g.expected, LegacyGuardianScan.dreamActiveAt(g.lookback, CLOCK))
            assertEquals(g.name, g.expected, grouped(g.lookback).dreamActive(CLOCK))
        }
    }

    @Test
    fun groupedLookback_matchesTheLinearScanOnRandomLogs() {
        val random = Random(20261116)
        val packages = listOf("a", "b", "c", null)
        val classes = listOf("x.Main", "x.Other", DREAM, null)
        val types = listOf(RESUMED, PAUSED, STOPPED, END_OF_DAY, CONTINUE_PREVIOUS_DAY, STARTUP, SHUTDOWN, CONFIG)
        repeat(10_000) { run ->
            // Eight timestamps: duplicates, ties with the tested close and out-of-order logs are common
            val events = List(random.nextInt(30)) {
                Ev(types.random(random), packages.random(random), classes.random(random), random.nextLong(8))
            }
            val lookback = grouped(events)
            for (pkg in packages) {
                for (ts in 0L until 8L) for (cls in listOf("x.Main", DREAM)) {
                    if (LegacyGuardianScan.test(events, pkg, cls, ts) != lookback.test(pkg, cls, ts)) {
                        fail("run $run: test($pkg, $cls, $ts) on $events")
                    }
                }
                if (pkg != null && LegacyGuardianScan.dreamActiveAt(events, pkg) != lookback.dreamActive(pkg)) {
                    fail("run $run: dreamActiveAt($pkg) on $events")
                }
            }
        }
    }
}
