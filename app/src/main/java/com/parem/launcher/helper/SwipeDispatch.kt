package com.parem.launcher.helper

import kotlin.math.hypot

/** Touch-stream state shared by background and slot swipe dispatch. Thresholds stay in [SwipeThresholds]. */
internal class SwipeDispatch(
    private val touchSlop: Float,
    private val minFlingVelocity: Float,
    private val density: Float,
) {
    private var active = false
    private var moved = false
    private var downX = 0f
    private var downY = 0f
    private var velocityX = 0f
    private var velocityY = 0f

    fun down(x: Float, y: Float) {
        downX = x
        downY = y
        velocityX = 0f
        velocityY = 0f
        active = true
        moved = false
    }

    fun move(x: Float, y: Float) {
        if (movedPastSlop(x, y)) moved = true
    }

    /** True while this touch could still become a long press: down, tracked, and never moved. */
    fun heldStill(): Boolean = active && !moved

    /** Tap, long press, extra pointer and cancellation all suppress the release swipe. */
    fun cancel() { active = false }

    /** Only the background handles double taps; slot detection keeps its original default callback. */
    fun doubleTap(enabled: Boolean): Boolean {
        if (enabled) cancel()
        return enabled
    }

    fun fling(x: Float, y: Float) {
        velocityX = x
        velocityY = y
    }

    fun movedPastSlop(x: Float, y: Float): Boolean = hypot(x - downX, y - downY) > touchSlop

    /** How much longer a touch must stay down to count as held, given how long the app has seen it. */
    fun remainingHold(seenForMs: Long, timeoutMs: Long): Long = (timeoutMs - seenForMs).coerceAtLeast(0L)

    fun release(x: Float, y: Float): SwipeThresholds.Direction? {
        val direction = if (active) SwipeThresholds.classify(
            x - downX, y - downY, velocityX, velocityY, touchSlop, minFlingVelocity, density,
        ) else null
        active = false
        return direction
    }
}
