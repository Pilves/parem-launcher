package com.parem.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.parem.launcher.R
import com.parem.launcher.helper.GestureLetterRecognizer
import com.parem.launcher.helper.GestureLetterRecognizer.Point
import com.parem.launcher.helper.Haptics
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.skipAnimations
import kotlin.math.hypot

/**
 * Transparent overlay view for detecting letter-shaped gestures on the home screen.
 *
 * When [isGestureLettersEnabled] is true, this view intercepts touch events to collect
 * path points. On ACTION_UP the collected path is analyzed to detect a letter shape.
 * A faint trail is drawn during the gesture.
 *
 * When disabled or when no significant drag is detected, touch events pass through
 * to views below.
 */
class GestureLetterOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    /**
     * Invoked when a letter is recognized. Returns true when the letter was
     * acted on; false (no app mapped) rejects it like an unrecognized stroke.
     */
    var onLetterDetected: ((Char) -> Boolean)? = null

    /** When false, all touch events pass through to views below. */
    var isGestureLettersEnabled: Boolean = false

    /**
     * True while a letter stroke is actively being captured (drag threshold
     * exceeded). Swipe listeners must not process events while this holds,
     * otherwise a letter draw also fires a swipe action.
     */
    val isCapturing: Boolean
        get() = isTracking && isLetterCandidate

    private val points = mutableListOf<Point>()
    private val drawPath = Path()
    private var isTracking = false
    private var hasExceededThreshold = false
    // Letters always contain a direction change; straight strokes are swipes and
    // must stay visible to the swipe detector. Only capture once the path turns.
    private var isLetterCandidate = false
    private var anchorX = 0f
    private var anchorY = 0f
    private var startX = 0f
    private var startY = 0f
    // Keeps the rejected trail drawable while it fades out after the finger lifts
    private var trailVisible = false

    private val dragThreshold = 30.dpToPx().toFloat()
    private val rejectFadeMs = 150L

    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = context.getColorFromAttr(R.attr.primaryColor)
        alpha = 102 // ~40% of 255
        style = Paint.Style.STROKE
        strokeWidth = 3.dpToPx().toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /**
     * Called externally (e.g. from the swipe listener) to forward touch events
     * for gesture tracking without consuming them in the view hierarchy.
     */
    fun forwardTouchEvent(event: MotionEvent) {
        if (!isGestureLettersEnabled) return
        onTouchEvent(event)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isGestureLettersEnabled) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animate().cancel()
                alpha = 1f
                startX = event.x
                startY = event.y
                points.clear()
                points.add(Point(event.x.toDouble(), event.y.toDouble()))
                drawPath.reset()
                drawPath.moveTo(event.x, event.y)
                isTracking = true
                hasExceededThreshold = false
                isLetterCandidate = false
                trailVisible = false
                // Do not consume ACTION_DOWN yet; let parent also see it.
                // We will consume subsequent events once the drag threshold is met.
                return false
            }

            MotionEvent.ACTION_MOVE -> {
                if (!isTracking) return false

                // Android batches move samples; keep their curves instead of just the last point.
                for (i in 0 until event.historySize) recordPoint(event.getHistoricalX(i), event.getHistoricalY(i))
                recordPoint(event.x, event.y)

                if (!hasExceededThreshold) {
                    val dx = event.x - startX
                    val dy = event.y - startY
                    if (hypot(dx, dy) >= dragThreshold) {
                        hasExceededThreshold = true
                        trailVisible = true
                        anchorX = event.x
                        anchorY = event.y
                        // Request that parent not intercept further events
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                } else if (!isLetterCandidate) {
                    val ax = anchorX - startX
                    val ay = anchorY - startY
                    val bx = event.x - anchorX
                    val by = event.y - anchorY
                    val lenB = hypot(bx, by)
                    if (lenB >= dragThreshold * 0.7f) {
                        val lenA = hypot(ax, ay)
                        val cos = (ax * bx + ay * by) / (lenA * lenB)
                        if (cos < 0.707f) {
                            isLetterCandidate = true  // path turned >45° — this is a letter
                        } else {
                            anchorX = event.x  // still straight; slide the anchor forward
                            anchorY = event.y
                        }
                    }
                }

                if (hasExceededThreshold) {
                    invalidate()
                    return true
                }
                return false
            }

            MotionEvent.ACTION_UP -> {
                if (!isTracking) return false
                isTracking = false
                val wasExceeded = hasExceededThreshold
                // A straight stroke is a swipe for the listeners, not a failed letter
                val wasLetterAttempt = isLetterCandidate

                var accepted = false
                if (wasExceeded) {
                    points.add(Point(event.x.toDouble(), event.y.toDouble()))
                    val letter = GestureLetterRecognizer.recognize(points)
                    accepted = letter != null && onLetterDetected?.invoke(letter) == true
                }

                points.clear()
                hasExceededThreshold = false
                isLetterCandidate = false
                if (wasLetterAttempt && !accepted) rejectTrail() else clearTrail()
                return wasExceeded
            }

            MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_CANCEL -> {
                isTracking = false
                hasExceededThreshold = false
                isLetterCandidate = false
                points.clear()
                clearTrail()
                return false
            }
        }
        return false
    }

    private fun recordPoint(x: Float, y: Float) {
        // Bound storage without freezing recognition at the beginning of a long stroke.
        if (points.size >= 512) {
            val reduced = points.filterIndexed { index, _ -> index % 2 == 0 }
            points.clear()
            points.addAll(reduced)
        }
        points.add(Point(x.toDouble(), y.toDouble()))
        drawPath.lineTo(x, y)
    }

    private fun clearTrail() {
        trailVisible = false
        drawPath.reset()
        invalidate()
    }

    /** DESIGN 8: a rejected letter buzzes and its trail fades over 150 ms (instantly when animations are off). */
    private fun rejectTrail() {
        Haptics.reject(this)
        if (context.skipAnimations()) {
            clearTrail()
            return
        }
        animate().alpha(0f).setDuration(rejectFadeMs).withEndAction {
            clearTrail()
            alpha = 1f
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (trailVisible && !drawPath.isEmpty) {
            canvas.drawPath(drawPath, trailPaint)
        }
    }

}
