package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchGateTest {
    @Test fun focusBlocksDisallowedAndAllowsAllowedPackages() {
        assertEquals(LaunchGate.Decision.BLOCK, LaunchGate.decide(true, false, false, false, false))
        assertEquals(LaunchGate.Decision.OPEN, LaunchGate.decide(true, true, false, false, false))
    }

    @Test fun focusPrecedesPauseAndLimitAndInactiveFocusDoesNotBlock() {
        assertEquals(LaunchGate.Decision.BLOCK, LaunchGate.decide(true, false, true, true, true))
        assertEquals(LaunchGate.Decision.PAUSE, LaunchGate.decide(false, false, true, true, true))
        assertEquals(LaunchGate.Decision.LIMIT_WARNING, LaunchGate.decide(false, false, true, false, true))
        assertEquals(LaunchGate.Decision.OPEN, LaunchGate.decide(false, false, false, false, false))
    }
}
