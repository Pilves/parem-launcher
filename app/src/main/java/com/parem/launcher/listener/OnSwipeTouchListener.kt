package com.parem.launcher.listener

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.helper.SwipeThresholds

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : OnTouchListener {
    private var gestureDetector: GestureDetector?
    private val config = SwipeConfig.from(c)
    private var velocityX = 0f
    private var velocityY = 0f
    private var swipeActive = false

    private var downX = 0f
    private var downY = 0f

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = motionEvent.x
                downY = motionEvent.y
                velocityX = 0f
                velocityY = 0f
                swipeActive = true
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> swipeActive = false
        }
        val detector = gestureDetector ?: return false
        val handled = detector.onTouchEvent(motionEvent)
        if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
            val direction = if (swipeActive)
                config.classify(motionEvent.x - downX, motionEvent.y - downY, velocityX, velocityY)
            else null
            swipeActive = false
            dispatchSwipe(direction)
            return handled || direction != null
        }
        return handled
    }

    fun cleanup() {
        gestureDetector = null
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            swipeActive = false
            onClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            swipeActive = false
            onDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            swipeActive = false
            onLongClick()
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            // GestureDetector supplies velocity only for flings. Slow releases keep
            // zero velocity and still qualify through distance in ACTION_UP.
            this@OnSwipeTouchListener.velocityX = velocityX
            this@OnSwipeTouchListener.velocityY = velocityY
            return false
        }
    }

    private fun dispatchSwipe(direction: SwipeThresholds.Direction?) {
        try {
            when (direction) {
                SwipeThresholds.Direction.RIGHT -> onSwipeRight()
                SwipeThresholds.Direction.LEFT -> onSwipeLeft()
                SwipeThresholds.Direction.UP -> onSwipeUp()
                SwipeThresholds.Direction.DOWN -> onSwipeDown()
                null -> {}
            }
        } catch (exception: Exception) {
            CrashReporter.reportNonFatal("ParemLauncher: swipe error", exception)
        }
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}
    open fun onLongClick() {}
    open fun onDoubleClick() {}
    open fun onClick() {}

    init {
        gestureDetector = GestureDetector(c, GestureListener())
    }
}

/** The device's ViewConfiguration numbers both swipe listeners feed to [SwipeThresholds]. */
internal class SwipeConfig(val touchSlop: Float, private val minFlingVelocity: Float, private val density: Float) {

    fun classify(dx: Float, dy: Float, vx: Float, vy: Float): SwipeThresholds.Direction? =
        SwipeThresholds.classify(dx, dy, vx, vy, touchSlop, minFlingVelocity, density)

    companion object {
        fun from(context: Context?): SwipeConfig {
            // No context only happens in tests; fall back to Android's stock dp values at 1x
            val vc = context?.let { ViewConfiguration.get(it) }
            return SwipeConfig(
                touchSlop = vc?.scaledTouchSlop?.toFloat() ?: 8f,
                minFlingVelocity = vc?.scaledMinimumFlingVelocity?.toFloat() ?: 50f,
                density = context?.resources?.displayMetrics?.density ?: 1f,
            )
        }
    }
}
