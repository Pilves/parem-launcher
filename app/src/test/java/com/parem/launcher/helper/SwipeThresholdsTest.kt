package com.parem.launcher.helper

import com.parem.launcher.helper.SwipeThresholds.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeThresholdsTest {

    // Android defaults: touch slop 8dp, minimum fling velocity 50dp/s
    private fun classify(dx: Float, dy: Float, vx: Float = 0f, vy: Float = 0f, density: Float = 1f) =
        SwipeThresholds.classify(dx, dy, vx, vy, 8f * density, 50f * density, density)

    @Test
    fun minDistance_isTheLargerOfThreeSlopsAnd48dp() {
        // 3 x 8dp = 24dp < 48dp: the dp floor wins at every density
        assertEquals(48f, SwipeThresholds.minDistancePx(8f, 1f), 0f)
        assertEquals(132f, SwipeThresholds.minDistancePx(22f, 2.75f), 0f)
        // A huge slop (some OEM tablets) lifts the floor with it
        assertEquals(90f, SwipeThresholds.minDistancePx(30f, 1f), 0f)
    }

    @Test
    fun minVelocity_isFourTimesMinimumFling() {
        assertEquals(200f, SwipeThresholds.minVelocityPx(50f), 0f)
    }

    @Test
    fun insideSlop_isATapAtEveryDensity() {
        // 5dp of drift during a long press, 1x / 2.75x / 3.5x
        for (density in listOf(1f, 2.75f, 3.5f)) {
            assertNull(classify(5f * density, 0f, density = density))
            assertNull(classify(0f, -5f * density, density = density))
            // even with a high release velocity: slop always wins
            assertNull(classify(5f * density, 0f, vx = 5000f, density = density))
        }
    }

    @Test
    fun exactlyAtSlop_isStillATap() {
        assertNull(classify(8f, 0f, vx = 5000f))
    }

    @Test
    fun longEnoughDistance_isASwipeAtEveryDensity() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            assertEquals(Direction.RIGHT, classify(48f * density, 0f, density = density))
            assertEquals(Direction.LEFT, classify(-48f * density, 0f, density = density))
            assertEquals(Direction.UP, classify(0f, -48f * density, density = density))
            assertEquals(Direction.DOWN, classify(0f, 48f * density, density = density))
        }
    }

    @Test
    fun justUnderDistance_withSlowVelocity_isNothing() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            assertNull(classify(47f * density, 0f, vx = 199f * density, density = density))
        }
    }

    @Test
    fun shortButFast_isASwipe() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            assertEquals(Direction.DOWN, classify(0f, 20f * density, vy = 200f * density, density = density))
        }
    }

    @Test
    fun diagonalJitter_isRejected() {
        // off-axis at exactly half the on-axis movement is already too much
        assertNull(classify(100f, 50f))
        assertNull(classify(100f, -50f))
        assertNull(classify(50f, 100f))
        // and a bit under half passes
        assertEquals(Direction.RIGHT, classify(100f, 49f))
        assertEquals(Direction.DOWN, classify(49f, 100f))
    }

    @Test
    fun velocityOnTheOtherAxis_doesNotCount() {
        assertNull(classify(20f, 0f, vy = 5000f))
    }
}
