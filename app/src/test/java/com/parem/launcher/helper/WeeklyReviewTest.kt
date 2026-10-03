package com.parem.launcher.helper

import com.parem.launcher.helper.WeeklyReview.AppChange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyReviewTest {

    private val min = 60_000L
    private val hour = 60 * min

    private fun week(vararg day: Map<String, Long>) = day.toList()
    private fun same(day: Map<String, Long>) = List(7) { day }

    @Test
    fun compare_thisWeekAverageMatchesGraphTotalOverSeven() {
        // The sheet's "/day average" is weekTotal / 7 with integer division
        val thisWeek = week(
            mapOf("a" to 1 * hour), mapOf("a" to 2 * hour), mapOf("b" to 3 * hour),
            emptyMap(), mapOf("a" to 10 * min, "b" to 7L), mapOf("c" to 1L), mapOf("a" to 5 * min),
        )
        val graphTotal = thisWeek.sumOf { it.values.sum() }
        val result = WeeklyReview.compare(thisWeek, same(mapOf("a" to hour)))!!
        assertEquals(graphTotal / 7, result.thisWeekAvgMs)
    }

    @Test
    fun compare_fullWeekPercentAndAverages() {
        val result = WeeklyReview.compare(
            thisWeek = same(mapOf("a" to 90 * min)),
            lastWeek = same(mapOf("a" to 2 * hour)),
        )!!
        assertEquals(90 * min, result.thisWeekAvgMs)
        assertEquals(2 * hour, result.lastWeekAvgMs)
        assertEquals(7, result.lastWeekDays)
        assertEquals(-25, result.percentChange)
    }

    @Test
    fun compare_risesAreRoundedPercent() {
        val result = WeeklyReview.compare(
            thisWeek = same(mapOf("a" to 100 * min)),
            lastWeek = same(mapOf("a" to 3 * hour)),
        )!!
        // 100 / 180 - 1 = -44.4%
        assertEquals(-44, result.percentChange)
        val up = WeeklyReview.compare(same(mapOf("a" to 2 * hour)), same(mapOf("a" to 90 * min)))!!
        assertEquals(33, up.percentChange)
    }

    @Test
    fun compare_prunedDaysAreLeftOutOfLastWeeksAverage() {
        // Oldest four days pruned from the event log: average over the three on record
        val lastWeek = List(4) { emptyMap<String, Long>() } + List(3) { mapOf("a" to 2 * hour) }
        val result = WeeklyReview.compare(same(mapOf("a" to 2 * hour)), lastWeek)!!
        assertEquals(3, result.lastWeekDays)
        assertEquals(2 * hour, result.lastWeekAvgMs)
        assertEquals(0, result.percentChange)
        assertTrue(result.risers.isEmpty())
        assertTrue(result.fallers.isEmpty())
    }

    @Test
    fun compare_tooLittleHistoryReturnsNull() {
        val lastWeek = List(5) { emptyMap<String, Long>() } + List(2) { mapOf("a" to hour) }
        assertNull(WeeklyReview.compare(same(mapOf("a" to hour)), lastWeek))
        assertNull(WeeklyReview.compare(same(mapOf("a" to hour)), List(7) { emptyMap() }))
    }

    @Test
    fun recordedDays_stopsAtTheNewestGap() {
        val a = mapOf("a" to hour)
        val lastWeek = week(a, a, a, emptyMap(), a, a, a)
        assertEquals(3, WeeklyReview.recordedDays(lastWeek).size)
        assertEquals(0, WeeklyReview.recordedDays(week(a, a, a, a, a, a, mapOf("a" to 0L))).size)
    }

    @Test
    fun compare_risersAndFallersRankedByDailyChange() {
        val result = WeeklyReview.compare(
            thisWeek = same(mapOf("social" to 2 * hour, "video" to 30 * min, "maps" to 20 * min, "new" to 10 * min)),
            lastWeek = same(mapOf("social" to 1 * hour, "video" to 2 * hour, "maps" to 20 * min, "gone" to 15 * min)),
        )!!
        assertEquals(listOf(AppChange("social", hour), AppChange("new", 10 * min)), result.risers)
        assertEquals(listOf(AppChange("video", -90 * min), AppChange("gone", -15 * min)), result.fallers)
    }

    @Test
    fun compare_partialHistoryComparesAppsPerRecordedDay() {
        // 3 recorded days at 1h each = 1h/day, the same as 1h/day across this week
        val lastWeek = List(4) { emptyMap<String, Long>() } + List(3) { mapOf("a" to hour, "b" to 30 * min) }
        val result = WeeklyReview.compare(same(mapOf("a" to hour)), lastWeek)!!
        assertTrue(result.risers.isEmpty())
        assertEquals(listOf(AppChange("b", -30 * min)), result.fallers)
    }

    @Test
    fun compare_subMinuteChangesAreNotRisersOrFallers() {
        val result = WeeklyReview.compare(
            thisWeek = same(mapOf("a" to hour + 59_000L, "b" to hour)),
            lastWeek = same(mapOf("a" to hour, "b" to hour + 59_000L)),
        )!!
        assertTrue(result.risers.isEmpty())
        assertTrue(result.fallers.isEmpty())
    }

    @Test
    fun compare_zeroLengthDaysAreNotOnRecord() {
        val lastWeek = same(mapOf("a" to 0L))
        assertNull(WeeklyReview.compare(same(mapOf("a" to hour)), lastWeek))
    }

    @Test
    fun compare_tiesBreakByPackageName() {
        val result = WeeklyReview.compare(
            thisWeek = same(mapOf("b" to hour, "a" to hour)),
            lastWeek = same(mapOf("z" to min)),
        )!!
        assertEquals(listOf("a", "b"), result.risers.map { it.pkg })
    }
}
