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
    private var longPressOn = false
    private var gestureDetector: GestureDetector?
    private val config = SwipeConfig.from(c)
    private var downX = 0f
    private var downY = 0f

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                view.isPressed = false
                longPressOn = false
            }
        }
        return gestureDetector?.onTouchEvent(motionEvent) ?: false
    }

    fun cleanup() {
        gestureDetector = null
    }

    private inner class GestureListener(private val view: View) : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            onClick(view)
            return super.onSingleTapUp(e)
        }

        override fun onLongPress(e: MotionEvent) {
            longPressOn = true
            onLongClick(view)
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
    open fun onLongClick(view: View) {}
    open fun onClick(view: View) {}

    init {
        gestureDetector = GestureDetector(c, GestureListener(v))
    }
}
