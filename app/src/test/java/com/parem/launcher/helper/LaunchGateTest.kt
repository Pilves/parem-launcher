package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class LaunchGateTest {
    @Test fun explicitComponentDoesNotDependOnPackageVisibility() {
        assertEquals("example.wellbeing", LaunchGate.knownTarget("example.wellbeing", null))
        assertEquals("example.wellbeing", LaunchGate.knownTarget("example.wellbeing", "wrong.package"))
        assertEquals("example.package", LaunchGate.knownTarget(null, "example.package"))
        assertEquals(null, LaunchGate.knownTarget(null, null))
    }

    @Test fun focusBlocksDisallowedAndAllowsAllowedPackages() {
        assertEquals(LaunchGate.Decision.BLOCK, LaunchGate.decide(false, false, false, false))
        assertEquals(LaunchGate.Decision.OPEN, LaunchGate.decide(true, false, false, false))
    }

    @Test fun denialAlwaysPrecedesPauseAndLimit() {
        for (gated in listOf(false, true)) for (pause in listOf(false, true)) for (over in listOf(false, true)) {
            assertEquals(LaunchGate.Decision.BLOCK, LaunchGate.decide(false, gated, pause, over))
        }
        assertEquals(LaunchGate.Decision.BLOCK, LaunchGate.decide(false, true, true, true))
        assertEquals(LaunchGate.Decision.PAUSE, LaunchGate.decide(true, true, true, true))
        assertEquals(LaunchGate.Decision.LIMIT_WARNING, LaunchGate.decide(true, true, false, true))
        assertEquals(LaunchGate.Decision.OPEN, LaunchGate.decide(true, false, false, false))
    }
}
