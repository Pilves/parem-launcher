package com.parem.launcher.helper

import com.parem.launcher.helper.GrayscalePolicy.Action
import com.parem.launcher.helper.GrayscalePolicy.GREY
import com.parem.launcher.helper.GrayscalePolicy.State
import com.parem.launcher.helper.GrayscalePolicy.desired
import com.parem.launcher.helper.GrayscalePolicy.isMarked
import com.parem.launcher.helper.GrayscalePolicy.plan
import com.parem.launcher.helper.GrayscalePolicy.toggle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GrayscalePolicyTest {

    private val off = State(enabled = false, mode = -1)
    private val deuteranomaly = State(enabled = true, mode = 12)
    private val offButMonochromacyMode = State(enabled = false, mode = 0)

    @Test
    fun desiredTruthTable() {
        assertFalse(desired(manual = false, onFocus = false, focusActive = true, onLimit = false, limitOverride = true, appForeground = false))
        assertTrue(desired(manual = true, onFocus = false, focusActive = false, onLimit = false, limitOverride = false, appForeground = false))
        assertTrue(desired(manual = false, onFocus = true, focusActive = true, onLimit = false, limitOverride = false, appForeground = false))
        assertFalse(desired(manual = false, onFocus = true, focusActive = false, onLimit = false, limitOverride = false, appForeground = false))
        assertTrue(desired(manual = false, onFocus = false, focusActive = false, onLimit = true, limitOverride = true, appForeground = false))
        assertFalse(desired(manual = false, onFocus = false, focusActive = false, onLimit = true, limitOverride = false, appForeground = false))
    }

    @Test
    fun neverWritesWhenNothingWantsGreyAndNothingWasApplied() {
        assertEquals(Action.NONE, plan(desired = false, applied = false, suppressed = false, current = off))
        assertEquals(Action.NONE, plan(desired = false, applied = false, suppressed = false, current = deuteranomaly))
        // The user's own grayscale is theirs to keep
        assertEquals(Action.NONE, plan(desired = false, applied = false, suppressed = false, current = GREY))
    }

    @Test
    fun appliesAndSavesFromColour() {
        assertEquals(Action.APPLY_AND_SAVE, plan(desired = true, applied = false, suppressed = false, current = off))
        assertEquals(Action.APPLY_AND_SAVE, plan(desired = true, applied = false, suppressed = false, current = deuteranomaly))
        assertEquals(Action.APPLY_AND_SAVE, plan(desired = true, applied = false, suppressed = false, current = offButMonochromacyMode))
    }

    @Test
    fun keepsTheUsersExistingMonochromacy() {
        assertEquals(Action.NONE, plan(desired = true, applied = false, suppressed = false, current = GREY))
    }

    @Test
    fun restoresOnlyWhatItApplied() {
        assertEquals(Action.RESTORE, plan(desired = false, applied = true, suppressed = false, current = GREY))
        assertEquals(Action.NONE, plan(desired = true, applied = true, suppressed = false, current = GREY))
    }

    @Test
    fun userChangeWhileAppliedIsAnOverride() {
        assertEquals(Action.USER_OVERRIDE, plan(desired = true, applied = true, suppressed = false, current = off))
        // Saved state is dropped, not restored over what the user just chose
        assertEquals(Action.USER_OVERRIDE, plan(desired = true, applied = true, suppressed = false, current = deuteranomaly))
        assertEquals(Action.USER_OVERRIDE, plan(desired = false, applied = true, suppressed = false, current = off))
    }

    @Test
    fun suppressionHoldsUntilTheTriggerEnds() {
        // Focus still active after the user turned grey off by hand
        assertEquals(Action.NONE, plan(desired = true, applied = false, suppressed = true, current = off))
        assertEquals(Action.CLEAR_SUPPRESSION, plan(desired = false, applied = false, suppressed = true, current = off))
        // The next trigger then applies again
        assertEquals(Action.APPLY_AND_SAVE, plan(desired = true, applied = false, suppressed = false, current = off))
    }

    @Test
    fun aMarkedAppInFrontWantsGreyOnItsOwn() {
        assertTrue(desired(manual = false, onFocus = false, focusActive = false, onLimit = false, limitOverride = false, appForeground = true))
        // Leaving the app never cancels another trigger
        assertTrue(desired(manual = true, onFocus = false, focusActive = false, onLimit = false, limitOverride = false, appForeground = false))
    }

    @Test
    fun colourCorrectionTurnedOffInsideAMarkedAppHoldsUntilItIsLeft() {
        val inApp = desired(manual = false, onFocus = false, focusActive = false, onLimit = false, limitOverride = false, appForeground = true)
        assertEquals(Action.USER_OVERRIDE, plan(inApp, applied = true, suppressed = false, current = off))
        assertEquals(Action.NONE, plan(inApp, applied = false, suppressed = true, current = off))
        val home = desired(manual = false, onFocus = false, focusActive = false, onLimit = false, limitOverride = false, appForeground = false)
        assertEquals(Action.CLEAR_SUPPRESSION, plan(home, applied = false, suppressed = true, current = off))
    }

    @Test
    fun marksToggleByPackage() {
        assertFalse(isMarked("", "a.b"))
        assertFalse(isMarked(null, "a.b"))
        assertEquals("a.b", toggle("", "a.b"))
        assertEquals("a.b,c.d", toggle("c.d", "a.b"))
        assertEquals("c.d", toggle("a.b,c.d", "a.b"))
        assertTrue(isMarked("a.b,c.d", "c.d"))
        // No prefix matches
        assertFalse(isMarked("a.bc", "a.b"))
        assertEquals("", toggle("a.b", "a.b"))
    }
}
