package com.parem.launcher.listener

import android.content.Context
import android.util.Log
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import com.parem.launcher.helper.SwipeThresholds
import kotlin.math.hypot

internal open class ViewSwipeTouchListener(c: Context?, v: View) : OnTouchListener {
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
                velocityX = 0f
                velocityY = 0f
                swipeActive = true
                downX = motionEvent.x
                downY = motionEvent.y
                view.isPressed = true
            }
            // Past slop this is a swipe, not a press: drop the dimmed colour now so a
            // label is never left dimmed after the finger leaves the view
            MotionEvent.ACTION_MOVE -> {
                if (view.isPressed && hypot(motionEvent.x - downX, motionEvent.y - downY) > config.touchSlop)
                    view.isPressed = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                swipeActive = false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> view.isPressed = false
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

    private inner class GestureListener(private val view: View) : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            swipeActive = false
            onClick(view)
            return super.onSingleTapUp(e)
        }

        override fun onLongPress(e: MotionEvent) {
            swipeActive = false
            onLongClick(view)
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            // GestureDetector supplies velocity only for flings. Slow releases keep
            // zero velocity and still qualify through distance in ACTION_UP.
            this@ViewSwipeTouchListener.velocityX = velocityX
            this@ViewSwipeTouchListener.velocityY = velocityY
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
            Log.e("ParemLauncher", "swipe error", exception)
        }
    }

    open fun onSwipeRight() {}
    open fun onSwipeLeft() {}
    open fun onSwipeUp() {}
    open fun onSwipeDown() {}
    open fun onLongClick(view: View) {}
    open fun onClick(view: View) {}

    init {
        gestureDetector = GestureDetector(c, GestureListener(v))
    }
}
