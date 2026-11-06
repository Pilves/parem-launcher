package com.parem.launcher.helper

import kotlin.math.abs
import kotlin.math.max

/**
 * Decides whether a finished touch was a swipe, and in which direction, from
 * numbers the listeners read off ViewConfiguration (DESIGN.md section 9).
 *
 * A swipe needs on-axis distance >= max(3 x touch slop, 48dp) or on-axis
 * velocity >= 4 x minimum fling velocity, with off-axis movement under half
 * of the on-axis movement. Anything that never left the slop is a tap,
 * however long it lasted; diagonal jitter is nothing at all.
 */
object SwipeThresholds {

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    const val MIN_DISTANCE_DP = 48f
    const val SLOP_MULTIPLIER = 3f
    const val VELOCITY_MULTIPLIER = 4f

    fun minDistancePx(touchSlopPx: Float, density: Float): Float =
        max(SLOP_MULTIPLIER * touchSlopPx, MIN_DISTANCE_DP * density)

    fun minVelocityPx(minFlingVelocityPx: Float): Float = VELOCITY_MULTIPLIER * minFlingVelocityPx

    /**
     * @param dx,dy movement from touch-down to release, in px (screen y grows downward)
     * @param vx,vy release velocity in px/s
     * @return the swipe direction, or null when the touch is a tap or jitter
     */
    fun classify(
        dx: Float,
        dy: Float,
        vx: Float,
        vy: Float,
        touchSlopPx: Float,
        minFlingVelocityPx: Float,
        density: Float,
    ): Direction? {
        val horizontal = abs(dx) > abs(dy)
        val onAxis = if (horizontal) abs(dx) else abs(dy)
        val offAxis = if (horizontal) abs(dy) else abs(dx)
        if (onAxis <= touchSlopPx) return null
        if (offAxis * 2 >= onAxis) return null
        val velocity = if (horizontal) abs(vx) else abs(vy)
        val isSwipe = onAxis >= minDistancePx(touchSlopPx, density) ||
            velocity >= minVelocityPx(minFlingVelocityPx)
        if (!isSwipe) return null
        return when {
            horizontal && dx > 0 -> Direction.RIGHT
            horizontal -> Direction.LEFT
            dy < 0 -> Direction.UP
            else -> Direction.DOWN
        }
    }
}
