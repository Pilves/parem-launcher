package com.parem.launcher.helper

import com.parem.launcher.helper.ScreenTimeSpeech.Duration
import com.parem.launcher.helper.ScreenTimeSpeech.UnderAMinute
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenTimeSpeechTest {

    @Test
    fun nothingUsedIsZeroMinutes() {
        assertEquals(Duration(null, 0), ScreenTimeSpeech.of(0))
    }

    @Test
    fun secondsAreUnderAMinute() {
        assertEquals(UnderAMinute, ScreenTimeSpeech.of(59_999))
    }

    @Test
    fun minutesOnly() {
        assertEquals(Duration(null, 1), ScreenTimeSpeech.of(60_000))
    }

    @Test
    fun hoursAndMinutes() {
        assertEquals(Duration(2, 11), ScreenTimeSpeech.of((2 * 60 + 11) * 60_000L + 30_000))
    }

    @Test
    fun wholeHoursDropTheMinutes() {
        assertEquals(Duration(3, null), ScreenTimeSpeech.of(3 * 3_600_000L))
    }
}
