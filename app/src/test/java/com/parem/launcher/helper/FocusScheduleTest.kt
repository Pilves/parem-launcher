package com.parem.launcher.helper

import com.parem.launcher.helper.FocusSchedule.NO_END
import com.parem.launcher.helper.FocusSchedule.Window
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class FocusScheduleTest {

    // Days: 0 = Monday … 6 = Sunday
    private val mon = 0
    private val tue = 1
    private val sat = 5
    private val sun = 6
    private val monFri = 0b0011111
    private val every = FocusSchedule.ALL_DAYS
    private fun hm(h: Int, m: Int = 0) = h * 60 + m

    private val work = Window(monFri, hm(9), hm(17))
    private val sleep = Window(every, hm(22), hm(7))

    @Test
    fun parseSerialize_roundTrip() {
        val s = "31,540,1020;127,1320,420"
        assertEquals(listOf(work, sleep), FocusSchedule.parse(s))
        assertEquals(s, FocusSchedule.serialize(listOf(work, sleep)))
        assertEquals("", FocusSchedule.serialize(emptyList()))
    }

    @Test
    fun parse_dropsGarbage() {
        val s = "abc;31,540;0,1,2;128,1,2;31,1440,5;31,-1,5;31,1,2,3;; 31 , 540 , 1020 "
        assertEquals(listOf(work), FocusSchedule.parse(s))
        assertEquals(emptyList<Window>(), FocusSchedule.parse(""))
        assertEquals(emptyList<Window>(), FocusSchedule.parse(null))
    }

    @Test
    fun isActive_startInclusiveEndExclusive() {
        val w = listOf(work)
        assertFalse(FocusSchedule.isActive(w, mon, hm(8, 59)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(9)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(16, 59)))
        assertFalse(FocusSchedule.isActive(w, mon, hm(17)))
        assertFalse(FocusSchedule.isActive(w, sat, hm(12)))
    }

    @Test
    fun isActive_crossesMidnightOnTheStartDay() {
        val w = listOf(Window(1 shl mon, hm(22), hm(7)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(22)))
        assertTrue(FocusSchedule.isActive(w, tue, 0))
        assertTrue(FocusSchedule.isActive(w, tue, hm(6, 59)))
        assertFalse(FocusSchedule.isActive(w, tue, hm(7)))
        // Sunday isn't set, so Monday morning is free
        assertFalse(FocusSchedule.isActive(w, mon, hm(6)))
        assertFalse(FocusSchedule.isActive(w, tue, hm(22)))
    }

    @Test
    fun isActive_sundayIntoMonday() {
        val w = listOf(Window(1 shl sun, hm(22), hm(7)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(1)))
        assertFalse(FocusSchedule.isActive(w, sun, hm(1)))
        assertEquals(hm(7), FocusSchedule.activeUntil(w, mon, hm(1)))
        assertEquals(hm(24 + 7), FocusSchedule.activeUntil(w, sun, hm(23)))
    }

    @Test
    fun overlap_isUnionAndRunEndMerges() {
        val w = listOf(Window(monFri, hm(9), hm(12)), Window(monFri, hm(11, 40), hm(17)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(11, 50)))
        assertEquals(hm(17), FocusSchedule.activeUntil(w, mon, hm(10)))
    }

    @Test
    fun activeUntil_chainsTouchingWindowsAcrossMidnight() {
        val w = listOf(sleep, Window(1 shl tue, hm(7), hm(9)))
        assertEquals(1440 + hm(9), FocusSchedule.activeUntil(w, mon, hm(23)))
        // Wednesday has no morning window, so Tuesday night ends at 07:00
        assertEquals(1440 + hm(7), FocusSchedule.activeUntil(w, tue, hm(23)))
        assertNull(FocusSchedule.activeUntil(w, mon, hm(12)))
    }

    @Test
    fun startEqualsEnd_isAFullDay() {
        val w = listOf(Window(1 shl mon, hm(10), hm(10)))
        assertTrue(FocusSchedule.isActive(w, mon, hm(10)))
        assertTrue(FocusSchedule.isActive(w, tue, hm(9, 59)))
        assertFalse(FocusSchedule.isActive(w, tue, hm(10)))
        assertFalse(FocusSchedule.isActive(w, mon, hm(9, 59)))
        assertEquals(1440 + hm(10), FocusSchedule.activeUntil(w, mon, hm(10)))

        val monTue = listOf(Window((1 shl mon) or (1 shl tue), hm(10), hm(10)))
        assertTrue(FocusSchedule.isActive(monTue, tue, hm(10)))

        val saturday = listOf(Window(1 shl sat, 0, 0))
        assertTrue(FocusSchedule.isActive(saturday, sat, 0))
        assertTrue(FocusSchedule.isActive(saturday, sat, hm(23, 59)))
        assertFalse(FocusSchedule.isActive(saturday, sun, 0))
    }

    @Test
    fun alwaysOn_returnsNoEnd() {
        assertEquals(NO_END, FocusSchedule.activeUntil(listOf(Window(every, 0, 0)), mon, hm(12)))
        val dayAndNight = listOf(sleep, Window(every, hm(7), hm(22)))
        assertEquals(NO_END, FocusSchedule.activeUntil(dayAndNight, sat, hm(3)))
    }

    @Test
    fun horizon_sevenDaysIsNoEnd_oneMinuteShortIsNot() {
        // From Monday 12:00 straight through to next Monday 11:59
        val short = listOf(
            Window(1 shl mon, hm(12), 0),
            Window(every and (1 shl mon).inv(), 0, 0),
            Window(1 shl mon, 0, hm(11, 59)),
        )
        val horizon = hm(12) + 7 * 1440
        assertEquals(horizon - 1, FocusSchedule.activeUntil(short, mon, hm(12)))

        val full = short.dropLast(1) + Window(1 shl mon, 0, hm(12))
        assertEquals(NO_END, FocusSchedule.activeUntil(full, mon, hm(12)))
    }

    @Test
    fun currentWindowEnd_boundsTheAlwaysOnOverride() {
        assertEquals(1440, FocusSchedule.currentWindowEnd(listOf(Window(every, 0, 0)), mon, hm(12)))
        val dayAndNight = listOf(sleep, Window(every, hm(7), hm(22)))
        assertEquals(1440 + hm(7), FocusSchedule.currentWindowEnd(dayAndNight, mon, hm(23)))
        assertEquals(hm(7), FocusSchedule.currentWindowEnd(dayAndNight, mon, hm(3)))
        assertNull(FocusSchedule.currentWindowEnd(listOf(work), sat, hm(12)))
    }

    @Test
    fun emptyDayMask_isNeverActive() {
        val w = listOf(Window(0, hm(9), hm(17)))
        assertFalse(FocusSchedule.isActive(w, mon, hm(12)))
        assertNull(FocusSchedule.activeUntil(w, mon, hm(12)))
        assertEquals(emptyList<Window>(), FocusSchedule.parse("0,540,1020"))
    }

    private val tallinn: TimeZone = TimeZone.getTimeZone("Europe/Tallinn")

    private fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int): Calendar {
        val ms = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, mo - 1, d, h, mi)
        }.timeInMillis
        return Calendar.getInstance(tallinn).apply { timeInMillis = ms }
    }

    private fun activeAt(w: List<Window>, cal: Calendar): Boolean {
        val (day, minute) = FocusSchedule.dayAndMinute(cal)
        return FocusSchedule.isActive(w, day, minute)
    }

    @Test
    fun dayAndMinute_mapsMondayFirst() {
        // 2026-10-05 is a Monday; 09:30 UTC is 12:30 in Tallinn (EEST)
        assertEquals(mon to hm(12, 30), FocusSchedule.dayAndMinute(utc(2026, 10, 5, 9, 30)))
        assertEquals(sun to hm(12, 30), FocusSchedule.dayAndMinute(utc(2026, 10, 4, 9, 30)))
    }

    @Test
    fun dst_springForward_startInGapBeginsAtFirstExistingMinute() {
        // 2026-03-29 (Sunday): 03:00 EET jumps to 04:00 EEST at 01:00 UTC
        val w = listOf(Window(every, hm(3, 30), hm(5)))
        assertFalse(activeAt(w, utc(2026, 3, 29, 0, 59))) // 02:59 local
        assertTrue(activeAt(w, utc(2026, 3, 29, 1, 0)))   // 04:00 local
        assertFalse(activeAt(w, utc(2026, 3, 29, 2, 0)))  // 05:00 local
    }

    @Test
    fun dst_fallBack_windowEndingInRepeatedHourIsActiveAgain() {
        // 2026-10-25 (Sunday): 04:00 EEST falls back to 03:00 EET at 01:00 UTC
        val w = listOf(Window(every, hm(2), hm(3, 30)))
        assertTrue(activeAt(w, utc(2026, 10, 25, 0, 15)))  // 03:15 EEST
        assertFalse(activeAt(w, utc(2026, 10, 25, 0, 45))) // 03:45 EEST
        assertTrue(activeAt(w, utc(2026, 10, 25, 1, 15)))  // 03:15 EET, the repeat
        assertFalse(activeAt(w, utc(2026, 10, 25, 1, 30))) // 03:30 EET
    }

    @Test
    fun toEpochMs_isWallClockAcrossDst() {
        // Saturday 23:00 EET; tomorrow 07:00 is EEST after the spring-forward
        val now = utc(2026, 3, 28, 21, 0)
        assertEquals(utc(2026, 3, 29, 4, 0).timeInMillis, FocusSchedule.toEpochMs(now, 1440 + hm(7)))
        assertEquals(utc(2026, 3, 28, 22, 0).timeInMillis, FocusSchedule.toEpochMs(now, 1440))
    }
}
