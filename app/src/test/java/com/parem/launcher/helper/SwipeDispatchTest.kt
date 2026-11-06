package com.parem.launcher.helper

import com.parem.launcher.helper.SwipeThresholds.Direction
import org.junit.Assert.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDispatchTest {

    @Test
    fun aTouchThatMovedIsNotHeld() {
        val swipe = SwipeDispatch(touchSlop = 8f, minFlingVelocity = 50f, density = 1f)
        swipe.down(100f, 500f)
        assertTrue(swipe.heldStill())
        swipe.move(100f, 504f)
        assertTrue("inside the slop is still a hold", swipe.heldStill())
        swipe.move(100f, 400f)
        assertFalse(swipe.heldStill())
        swipe.move(100f, 500f)
        assertFalse("coming back does not undo a move", swipe.heldStill())
    }

    @Test
    fun aReleasedTouchIsNotHeld() {
        val swipe = SwipeDispatch(touchSlop = 8f, minFlingVelocity = 50f, density = 1f)
        swipe.down(100f, 500f)
        swipe.release(100f, 500f)
        assertFalse(swipe.heldStill())
    }

    @Test
    fun aNewTouchStartsHeldAgain() {
        val swipe = SwipeDispatch(touchSlop = 8f, minFlingVelocity = 50f, density = 1f)
        swipe.down(100f, 500f)
        swipe.move(100f, 100f)
        swipe.down(100f, 500f)
        assertTrue(swipe.heldStill())
    }

    @Test
    fun remainingHoldCountsFromWhenTheAppSawTheTouch() {
        val swipe = SwipeDispatch(touchSlop = 8f, minFlingVelocity = 50f, density = 1f)
        assertEquals("a touch delivered late starts from zero", 400L, swipe.remainingHold(0L, 400L))
        assertEquals(150L, swipe.remainingHold(250L, 400L))
        assertEquals(0L, swipe.remainingHold(400L, 400L))
        assertEquals(0L, swipe.remainingHold(900L, 400L))
    }
    private val swipe = SwipeDispatch(8f, 50f, 1f)

    @Test fun slowReleasesDispatchEachDirectionOnceFromTheDownPosition() {
        for ((offset, direction) in listOf(
            (48f to 0f) to Direction.RIGHT, (-48f to 0f) to Direction.LEFT,
            (0f to -48f) to Direction.UP, (0f to 48f) to Direction.DOWN,
        )) {
            swipe.down(20f, 30f)
            assertEquals(direction, swipe.release(20f + offset.first, 30f + offset.second))
            assertNull(swipe.release(200f, 30f))
        }
    }

    @Test fun tapLongPressPointerAndCancelSuppressionCannotBeUndoneByFling() {
        swipe.down(0f, 0f)
        swipe.cancel()
        swipe.fling(1000f, 0f)
        assertNull(swipe.release(100f, 0f))
        swipe.down(0f, 0f)
        assertEquals(Direction.RIGHT, swipe.release(48f, 0f))
    }

    @Test fun doubleTapSuppressesBackgroundSwipeButLeavesSlotDefaultAlone() {
        swipe.down(0f, 0f)
        assertTrue(swipe.doubleTap(true))
        assertNull(swipe.release(100f, 0f))
        swipe.down(0f, 0f)
        assertFalse(swipe.doubleTap(false))
        assertEquals(Direction.RIGHT, swipe.release(100f, 0f))
    }

    @Test fun velocityQualifiesShortSwipeButNeverLeaksIntoNextTouch() {
        swipe.down(0f, 0f)
        swipe.fling(200f, 0f)
        assertEquals(Direction.RIGHT, swipe.release(20f, 0f))
        swipe.down(0f, 0f)
        assertNull(swipe.release(20f, 0f))
    }

    @Test fun pressedFeedbackEndsStrictlyPastRadialSlopEvenWithoutQualifiedSwipe() {
        swipe.down(10f, 10f)
        assertFalse(swipe.movedPastSlop(18f, 10f))
        assertTrue(swipe.movedPastSlop(16f, 16f))
        assertNull(swipe.release(16f, 16f))
    }
}
