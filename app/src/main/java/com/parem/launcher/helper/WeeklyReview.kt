package com.parem.launcher.helper

import kotlin.math.roundToInt

/**
 * Android-free comparison behind the screen-time sheet's weekly review: this
 * week (the 7 days the graph shows) against the 7 days before it.
 *
 * Everything is compared as a daily average. Android prunes its usage event
 * log after roughly ten days, so last week is often only partly on record;
 * averaging over the days that are on record keeps the comparison honest
 * instead of reporting a fake drop. This week's average is the graph's
 * total / 7, so it matches the sheet's "/day average" line exactly.
 */
object WeeklyReview {

    const val DAYS = 7

    /** Fewer recorded days than this and last week isn't worth comparing against. */
    const val MIN_HISTORY_DAYS = 3

    /** Per-app swings smaller than a minute a day are noise, not a riser or faller. */
    const val MIN_APP_CHANGE_MS_PER_DAY = 60_000L

    data class AppChange(val pkg: String, val deltaMsPerDay: Long)

    data class Result(
        val thisWeekAvgMs: Long,
        val lastWeekAvgMs: Long,
        /** Days of last week actually on record, 1..7. */
        val lastWeekDays: Int,
        /** Rounded percent change of the daily average. */
        val percentChange: Int,
        /** Apps used more per day this week, biggest rise first. */
        val risers: List<AppChange>,
        /** Apps used less per day this week, biggest drop first. */
        val fallers: List<AppChange>,
    )

    /**
     * The days of [lastWeek] (ordered oldest to newest) that are on record: the
     * unbroken run of non-empty days ending at the newest. A day with no usage
     * at all reads as "not on record" — the event log can't tell a phone left
     * off all day from a pruned day, and treating both the same never inflates
     * the comparison with an invented zero.
     */
    fun recordedDays(lastWeek: List<Map<String, Long>>): List<Map<String, Long>> =
        lastWeek.takeLastWhile { day -> day.values.any { it > 0L } }

    /**
     * Compares [thisWeek] against [lastWeek], both lists of per-day
     * package -> foreground-ms maps ordered oldest to newest. Returns null when
     * fewer than [MIN_HISTORY_DAYS] days of last week are on record (or they
     * average to zero, which leaves no base for a percentage).
     */
    fun compare(thisWeek: List<Map<String, Long>>, lastWeek: List<Map<String, Long>>): Result? {
        val recorded = recordedDays(lastWeek)
        if (recorded.size < MIN_HISTORY_DAYS) return null

        val thisTotals = perAppTotals(thisWeek)
        val lastTotals = perAppTotals(recorded)
        val thisAvg = thisTotals.values.sum() / DAYS
        val lastAvg = lastTotals.values.sum() / recorded.size
        if (lastAvg <= 0L) return null

        val changes = (thisTotals.keys + lastTotals.keys).map { pkg ->
            AppChange(pkg, (thisTotals[pkg] ?: 0L) / DAYS - (lastTotals[pkg] ?: 0L) / recorded.size)
        }

        return Result(
            thisWeekAvgMs = thisAvg,
            lastWeekAvgMs = lastAvg,
            lastWeekDays = recorded.size,
            percentChange = ((thisAvg - lastAvg) * 100.0 / lastAvg).roundToInt(),
            risers = changes
                .filter { it.deltaMsPerDay >= MIN_APP_CHANGE_MS_PER_DAY }
                .sortedWith(compareByDescending<AppChange> { it.deltaMsPerDay }.thenBy { it.pkg }),
            fallers = changes
                .filter { it.deltaMsPerDay <= -MIN_APP_CHANGE_MS_PER_DAY }
                .sortedWith(compareBy<AppChange> { it.deltaMsPerDay }.thenBy { it.pkg }),
        )
    }

    private fun perAppTotals(days: List<Map<String, Long>>): Map<String, Long> {
        val totals = mutableMapOf<String, Long>()
        for (day in days) {
            for ((pkg, ms) in day) totals[pkg] = (totals[pkg] ?: 0L) + ms
        }
        return totals
    }
}
