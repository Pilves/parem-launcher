package com.parem.launcher.helper

import org.junit.Assert.*
import org.junit.Test

class DrawerPullTest {
    @Test fun thresholdRequiresReleaseAndCanBeCrossedAgain() {
        val pull = DrawerPull(64f)
        assertFalse(pull.pull(63f))
        assertTrue(pull.pull(1f))
        assertFalse(pull.pull(10f))
        assertFalse(pull.release(false))
        assertFalse(pull.release(true))
        assertTrue(pull.pull(70f))
        assertFalse(pull.pull(-10f))
        assertTrue(pull.pull(4f))
        assertTrue(pull.release(true))
        assertFalse(pull.release(true))
    }
}
