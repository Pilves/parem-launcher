package com.parem.launcher.helper

import com.parem.launcher.helper.MindfulPause.Gate
import org.junit.Assert.assertEquals
import org.junit.Test

class MindfulPauseTest {

    @Test
    fun decide_noPauseUnderLimitLaunches() {
        assertEquals(Gate.LAUNCH, MindfulPause.decide(pauseEnabled = false, overLimit = false))
    }

    @Test
    fun decide_noPauseOverLimitWarns() {
        assertEquals(Gate.LIMIT_WARNING, MindfulPause.decide(pauseEnabled = false, overLimit = true))
    }

    @Test
    fun decide_pauseWinsOverLimitWarning() {
        assertEquals(Gate.PAUSE, MindfulPause.decide(pauseEnabled = true, overLimit = true))
        assertEquals(Gate.PAUSE, MindfulPause.decide(pauseEnabled = true, overLimit = false))
    }

    @Test
    fun secondsRemaining_startsAtFullDelay() {
        assertEquals(5, MindfulPause.secondsRemaining(0L))
    }

    @Test
    fun secondsRemaining_roundsUp() {
        assertEquals(5, MindfulPause.secondsRemaining(1L))
        assertEquals(1, MindfulPause.secondsRemaining(4_999L))
    }

    @Test
    fun secondsRemaining_zeroAtAndAfterDelay() {
        assertEquals(0, MindfulPause.secondsRemaining(5_000L))
        assertEquals(0, MindfulPause.secondsRemaining(60_000L))
    }

    @Test
    fun secondsRemaining_negativeElapsedTreatedAsStart() {
        assertEquals(5, MindfulPause.secondsRemaining(-500L))
    }

    @Test
    fun secondsRemaining_customDelay() {
        assertEquals(10, MindfulPause.secondsRemaining(0L, delaySeconds = 10))
    }

    @Test
    fun msUntilNextTick_fullSecondAtBoundary() {
        assertEquals(1000L, MindfulPause.msUntilNextTick(0L))
        assertEquals(1000L, MindfulPause.msUntilNextTick(2_000L))
    }

    @Test
    fun msUntilNextTick_landsOnNextChange() {
        val elapsed = 1_250L
        val next = MindfulPause.msUntilNextTick(elapsed)
        assertEquals(750L, next)
        assertEquals(MindfulPause.secondsRemaining(elapsed) - 1, MindfulPause.secondsRemaining(elapsed + next))
    }

    @Test
    fun msUntilNextTick_zeroWhenDone() {
        assertEquals(0L, MindfulPause.msUntilNextTick(5_000L))
    }

    @Test
    fun parse_blankIsEmpty() {
        assertEquals(emptySet<String>(), MindfulPause.parse(null))
        assertEquals(emptySet<String>(), MindfulPause.parse(""))
        assertEquals(emptySet<String>(), MindfulPause.parse(" , "))
    }

    @Test
    fun parse_trimsAndDedupes() {
        assertEquals(setOf("a.b", "c.d"), MindfulPause.parse(" a.b,c.d,a.b "))
    }

    @Test
    fun serialize_roundTripsSorted() {
        val csv = MindfulPause.serialize(setOf("z.app", "a.app"))
        assertEquals("a.app,z.app", csv)
        assertEquals(setOf("a.app", "z.app"), MindfulPause.parse(csv))
    }
}
