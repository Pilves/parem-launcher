package com.parem.launcher.helper

import com.parem.launcher.helper.LockServiceCheck.Outcome
import org.junit.Assert.assertEquals
import org.junit.Test

class LockServiceCheckTest {

    @Test
    fun runningServiceLocks() {
        assertEquals(Outcome.LOCK, LockServiceCheck.decide(enabled = true, bound = true, connectedBefore = true, offExplained = false, adminActive = false))
    }

    @Test
    fun runningServiceLocksEvenIfAnOldExplanationFlagLingers() {
        assertEquals(Outcome.LOCK, LockServiceCheck.decide(enabled = true, bound = true, connectedBefore = true, offExplained = true, adminActive = false))
    }

    @Test
    fun neverConnectedAsksForConsent() {
        assertEquals(Outcome.CONSENT, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = false, offExplained = false, adminActive = false))
    }

    @Test
    fun listedButUnboundNeverConnectedAsksForConsent() {
        assertEquals(Outcome.CONSENT, LockServiceCheck.decide(enabled = true, bound = false, connectedBefore = false, offExplained = false, adminActive = false))
    }

    @Test
    fun turnedOffAfterWorkingExplains() {
        assertEquals(Outcome.EXPLAIN_OFF, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = false, adminActive = false))
    }

    @Test
    fun listedButNotRunningAfterWorkingExplains() {
        // The silent case: the click is emitted but no service receives it.
        assertEquals(Outcome.EXPLAIN_OFF, LockServiceCheck.decide(enabled = true, bound = false, connectedBefore = true, offExplained = false, adminActive = false))
    }

    @Test
    fun secondTapAfterExplanationDoesNotRepeatIt() {
        assertEquals(Outcome.OFF_EXPLAINED, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = true, adminActive = false))
    }

    @Test
    fun runningServiceWinsOverActiveAdmin() {
        // Accessibility keeps biometric unlock; lockNow() forces the PIN
        assertEquals(Outcome.LOCK, LockServiceCheck.decide(enabled = true, bound = true, connectedBefore = true, offExplained = false, adminActive = true))
    }

    @Test
    fun listedButUnboundWithAdminLocksViaAdmin() {
        // The silent-swallow case must not reach performClick when the admin can lock
        assertEquals(Outcome.LOCK_ADMIN, LockServiceCheck.decide(enabled = true, bound = false, connectedBefore = true, offExplained = false, adminActive = true))
    }

    @Test
    fun neverConnectedWithAdminLocksViaAdmin() {
        assertEquals(Outcome.LOCK_ADMIN, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = false, offExplained = false, adminActive = true))
    }

    @Test
    fun turnedOffWithAdminLocksViaAdminInsteadOfExplaining() {
        assertEquals(Outcome.LOCK_ADMIN, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = false, adminActive = true))
    }

    @Test
    fun explainedOffWithAdminLocksViaAdminInsteadOfToasting() {
        assertEquals(Outcome.LOCK_ADMIN, LockServiceCheck.decide(enabled = false, bound = false, connectedBefore = true, offExplained = true, adminActive = true))
    }
}
