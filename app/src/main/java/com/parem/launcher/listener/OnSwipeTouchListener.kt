package com.parem.launcher.listener

import android.content.Context
import android.view.GestureDetector
import android.view.GestureDetector.SimpleOnGestureListener
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.view.ViewConfiguration
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import com.parem.launcher.BuildConfig
import com.parem.launcher.helper.CrashReporter
import com.parem.launcher.helper.SwipeThresholds
import com.parem.launcher.helper.SwipeDispatch

/*
Swipe, double tap and long press touch listener for a view
Source: https://www.tutorialspoint.com/how-to-handle-swipe-gestures-in-kotlin
*/

internal open class OnSwipeTouchListener(c: Context?, private val target: View? = null) : OnTouchListener {
    private var gestureDetector: GestureDetector?
    private val config = SwipeConfig.from(c)
    private val swipe = SwipeDispatch(config.touchSlop, config.minFlingVelocity, config.density)
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var touchSeenAt = 0L
    private var longPressPending = false

    // A frame callback runs after that frame's input, so a swipe's batched moves arrive first
    private val confirmLongPress = Choreographer.FrameCallback {
        val held = longPressPending && swipe.heldStill()
        longPressPending = false
        trace("long press ${if (held) "confirmed" else "dropped"}")
        if (held) {
            swipe.cancel()
            onLongClick()
        }
    }
    private val awaitNextFrame = Runnable { Choreographer.getInstance().postFrameCallback(confirmLongPress) }

    // Debug builds only: SmokeTest prints these when the drawer fails to open
    private fun trace(message: String) {
        if (BuildConfig.DEBUG) Log.d("ParemGesture", message)
    }

    override fun onTouch(view: View, motionEvent: MotionEvent): Boolean {
        when (motionEvent.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchSeenAt = SystemClock.uptimeMillis()
                trace("down y=${motionEvent.y} on ${view.javaClass.simpleName} ${touchSeenAt - motionEvent.downTime}ms late")
                dropPendingLongPress()
                swipe.down(motionEvent.x, motionEvent.y)
                if (target != null) view.isPressed = true
            }
            MotionEvent.ACTION_MOVE -> {
                swipe.move(motionEvent.x, motionEvent.y)
                if (target != null && view.isPressed && swipe.movedPastSlop(motionEvent.x, motionEvent.y))
                    view.isPressed = false
            }
            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                trace("cancelled by action ${motionEvent.actionMasked}")
                if (target != null) view.isPressed = false
                swipe.cancel()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> if (target != null) view.isPressed = false
        }
        val detector = gestureDetector ?: return false
        val handled = detector.onTouchEvent(motionEvent)
        if (motionEvent.actionMasked == MotionEvent.ACTION_UP) {
            // GestureDetector never reports a tap once its long press fired, even an unconfirmed one
            val tapped = longPressPending && swipe.heldStill()
            val direction = swipe.release(motionEvent.x, motionEvent.y)
            trace("up y=${motionEvent.y} after ${motionEvent.eventTime - motionEvent.downTime}ms -> $direction")
            dropPendingLongPress()
            dispatchSwipe(direction)
            if (tapped) onClick()
            return handled || direction != null
        }
        return handled
    }

    private fun dropPendingLongPress() {
        if (!longPressPending) return
        longPressPending = false
        handler.removeCallbacks(awaitNextFrame)
        Choreographer.getInstance().removeFrameCallback(confirmLongPress)
    }

    fun cleanup() {
        dropPendingLongPress()
        gestureDetector = null
    }

    private inner class GestureListener : SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            trace("single tap")
            swipe.cancel()
            onClick()
            return super.onSingleTapUp(e)
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (swipe.doubleTap(target == null)) onDoubleClick()
            return super.onDoubleTap(e)
        }

        /**
         * GestureDetector counts from the hardware down time, so a main-thread
         * stall makes this fire as soon as the touch is delivered, ahead of the
         * moves of what may be a swipe. A hold is judged by what the app has seen.
         */
        override fun onLongPress(e: MotionEvent) {
            val seenFor = SystemClock.uptimeMillis() - touchSeenAt
            trace("long press timer after ${seenFor}ms seen")
            longPressPending = true
            handler.postDelayed(awaitNextFrame, swipe.remainingHold(seenFor, ViewConfiguration.getLongPressTimeout().toLong()))
        }

        override fun onFling(
            event1: MotionEvent?,
            event2: MotionEvent,
            velocityX: Float,
            velocityY: Float,
        ): Boolean {
            // GestureDetector supplies velocity only for flings. Slow releases keep
            // zero velocity and still qualify through distance in ACTION_UP.
            swipe.fling(velocityX, velocityY)
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

/** The device's ViewConfiguration numbers the swipe listener feeds to [SwipeThresholds]. */
internal class SwipeConfig(val touchSlop: Float, val minFlingVelocity: Float, val density: Float) {

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
