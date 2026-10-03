package com.parem.launcher.helper

import com.parem.launcher.helper.LockServiceCheck.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LockServiceCheckTest {

    @Test
    fun runningServiceLocks() {
        assertEquals(Outcome.LOCK, LockServiceCheck.decide(enabled = true, bound = true, connectedBefore = true, offExplained = false))
    }

    @Test
    fun runningServiceLocksEvenIfAnOldExplanationFlagLingers() {
        assertEquals(Outcome.LOCK, LockServiceCheck.decide(enabled = true, bound = true, connectedBefore = true, offExplained = true))
    }

    @Test
    fun neverConnectedAsksForConsent() {
        assertEquals(Outcome.CONSENT, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = false, offExplained = false))
    }

    @Test
    fun listedButUnboundNeverConnectedAsksForConsent() {
        assertEquals(Outcome.CONSENT, LockServiceCheck.decide(enabled = true, bound = false, connectedBefore = false, offExplained = false))
    }

    @Test
    fun turnedOffAfterWorkingExplains() {
        assertEquals(Outcome.EXPLAIN_OFF, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = false))
    }

    @Test
    fun listedButNotRunningAfterWorkingExplains() {
        // The silent case: the click is emitted but no service receives it.
        assertEquals(Outcome.EXPLAIN_OFF, LockServiceCheck.decide(enabled = true, bound = false, connectedBefore = true, offExplained = false))
    }

    @Test
    fun secondTapAfterExplanationDoesNotRepeatIt() {
        assertEquals(Outcome.OFF_EXPLAINED, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = true))
    }
}
