package com.parem.launcher.helper

import java.util.Calendar

/**
 * Weekly focus windows ("Mon–Fri 09:00–17:00", "every day 22:00–07:00"),
 * evaluated from the wall clock at check time. Nothing is scheduled: whether
 * focus is on comes from the clock at the moment of the check, so screen-off,
 * Doze and reboot cannot make it late (docs/design/M4-WP3.md).
 *
 * Days are 0 = Monday … 6 = Sunday; minutes are minutes of the local day.
 * A window whose end is <= its start crosses midnight and belongs to the day it
 * starts on; start == end is a full 24 h window.
 */
object FocusSchedule {

    data class Window(val days: Int, val startMin: Int, val endMin: Int) {
        /** Length in minutes: always 1..1440. */
        val length: Int get() = if (endMin > startMin) endMin - startMin else endMin - startMin + DAY
    }

    /** [activeUntil] result when the merged run reaches the 7-day horizon. */
    const val NO_END = -1

    const val ALL_DAYS = 0b1111111
    private const val DAY = 1440
    private const val HORIZON_DAYS = 7

    /** Format: "days,start,end;days,start,end". Malformed or out-of-range entries are dropped. */
    fun parse(s: String?): List<Window> =
        s.orEmpty().split(';').mapNotNull { entry ->
            val parts = entry.split(',').map { it.trim().toIntOrNull() }
            if (parts.size != 3 || parts.any { it == null }) return@mapNotNull null
            val (days, start, end) = parts.map { it!! }
            if (days !in 1..ALL_DAYS || start !in 0 until DAY || end !in 0 until DAY) null
            else Window(days, start, end)
        }

    fun serialize(windows: List<Window>): String =
        windows.joinToString(";") { "${it.days},${it.startMin},${it.endMin}" }

    fun isActive(windows: List<Window>, dayOfWeek: Int, minuteOfDay: Int): Boolean =
        intervals(windows, dayOfWeek).any { minuteOfDay in it }

    /**
     * End of the merged run containing now, in minutes from today's 00:00
     * (1860 = tomorrow 07:00); null when no window contains now; [NO_END] when
     * the run lasts at least 7 days from now.
     */
    fun activeUntil(windows: List<Window>, dayOfWeek: Int, minuteOfDay: Int): Int? {
        val sorted = intervals(windows, dayOfWeek).sortedBy { it.first }
        var runStart = Int.MIN_VALUE
        var runEnd = Int.MIN_VALUE // exclusive
        var found: Int? = null
        for (r in sorted) {
            val end = r.last + 1
            if (r.first <= runEnd) {
                runEnd = maxOf(runEnd, end)
            } else {
                if (minuteOfDay in runStart until runEnd) { found = runEnd; break }
                runStart = r.first
                runEnd = end
            }
        }
        if (found == null && minuteOfDay in runStart until runEnd) found = runEnd
        if (found == null) return null
        return if (found >= minuteOfDay + HORIZON_DAYS * DAY) NO_END else found
    }

    /** Latest end among the single windows that contain now, same units as [activeUntil]. */
    fun currentWindowEnd(windows: List<Window>, dayOfWeek: Int, minuteOfDay: Int): Int? =
        intervals(windows, dayOfWeek).filter { minuteOfDay in it }.maxOfOrNull { it.last + 1 }

    /** Calendar → (0 = Monday … 6 = Sunday, wall-clock minute of day). */
    fun dayAndMinute(cal: Calendar): Pair<Int, Int> =
        (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 to
            cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    /**
     * Epoch ms of the wall-clock time [minutesFromToday] after the start of
     * [now]'s day, set by fields so the result is a wall-clock time across DST.
     */
    fun toEpochMs(now: Calendar, minutesFromToday: Int): Long {
        val c = now.clone() as Calendar
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.add(Calendar.DAY_OF_MONTH, Math.floorDiv(minutesFromToday, DAY))
        val m = Math.floorMod(minutesFromToday, DAY)
        c.set(Calendar.HOUR_OF_DAY, m / 60)
        c.set(Calendar.MINUTE, m % 60)
        return c.timeInMillis
    }

    /**
     * Concrete intervals for day offsets -1 (yesterday's cross-midnight windows)
     * through [HORIZON_DAYS], relative to today's 00:00. A fixed day range, so
     * always-on schedules still terminate.
     */
    private fun intervals(windows: List<Window>, dayOfWeek: Int): List<IntRange> {
        val out = ArrayList<IntRange>()
        for (offset in -1..HORIZON_DAYS) {
            val day = Math.floorMod(dayOfWeek + offset, 7)
            for (w in windows) {
                if (w.days and (1 shl day) == 0) continue
                val start = offset * DAY + w.startMin
                out += start until start + w.length
            }
        }
        return out
    }
}
