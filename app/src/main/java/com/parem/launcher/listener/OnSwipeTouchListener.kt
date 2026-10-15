package com.parem.launcher.listener

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import com.parem.launcher.helper.SwipeThresholds

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?) : OnTouchListener {
    private var longPressOn = false
    private var gestureDetector: GestureDetector?
    private val config = SwipeConfig.from(c)

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.action) {
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressOn = false
            }
        }
        return gestureDetector?.onTouchEvent(motionEvent) ?: false
    }

    fun cleanup() {
        gestureDetector = null
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleClick()
            return super.onDoubleTap(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            onLongClick()
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            try {
                if (event1 == null) return false
                when (config.classify(event2.x - event1.x, event2.y - event1.y, velocityX, velocityY)) {
                    SwipeThresholds.Direction.RIGHT -> onSwipeRight()
                    SwipeThresholds.Direction.LEFT -> onSwipeLeft()
                    SwipeThresholds.Direction.UP -> onSwipeUp()
                    SwipeThresholds.Direction.DOWN -> onSwipeDown()
                    null -> {}
                }
            } catch (exception: Exception) {
                Log.e("ParemLauncher", "onFling error", exception)
            }
            return false
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
