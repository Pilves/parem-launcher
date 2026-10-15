package com.parem.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Bundle
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.customview.widget.ExploreByTouchHelper
import kotlin.math.abs
import com.parem.launcher.R
import com.parem.launcher.helper.ScreenTimeGraphText
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr

/**
 * Custom View that draws 7 vertical bars representing daily screen time.
 * Each bar is labeled with a day abbreviation below and hours text above.
 *
 * Visual hierarchy: the LAST entry is treated as "today" and drawn at full
 * strength; earlier days are dimmed so the eye lands on the current day.
 * Callers must therefore supply data ordered oldest-first.
 *
 * Each day column is a virtual accessibility node (ExploreByTouchHelper), so
 * TalkBack reads "Mon, 2 hours 18 minutes" per column and a keyboard can
 * D-pad between columns and press Enter to select one.
 */
class ScreenTimeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Long>> = emptyList()
    private var precomputedLabels: List<String> = emptyList()

    /**
     * Tapping a day's column selects it (tapping it again deselects). Null
     * means no selection, in which case today carries the visual emphasis.
     */
    var onDaySelected: ((Int?) -> Unit)? = null
    private var selectedIndex: Int? = null

    private var downX = 0f
    private var downY = 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private val primaryColor = context.getColorFromAttr(R.attr.primaryColor)

    // Past days are dimmed, today gets the full-strength variants below.
    // 60% text on the sheet background is 5.7:1 in the light theme (WCAG
    // 1.4.3 needs 4.5:1); 45% bars are 3.4:1 (1.4.11 needs 3:1 for graphics).
    private val dimAlpha = 115
    private val textDimAlpha = 153

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        alpha = dimAlpha
        style = Paint.Style.FILL
    }

    private val todayBarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        alpha = textDimAlpha
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val todayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        textSize = 10f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        alpha = textDimAlpha
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val todayLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }

    private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        alpha = 50
        style = Paint.Style.STROKE
        strokeWidth = 1.dpToPx().toFloat()
    }

    private val focusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryColor
        style = Paint.Style.STROKE
        strokeWidth = 2.dpToPx().toFloat()
    }

    private val rect = RectF()
    private val focusRect = RectF()
    private val topPadding = 24.dpToPx().toFloat()
    private val bottomPadding = 20.dpToPx().toFloat()
    private val sidePadding = 12.dpToPx().toFloat()
    private val intrinsicHeightPx = 200.dpToPx()
    private val barMinHeight = 2.dpToPx().toFloat()
    private val maxBarWidthPx = 32.dpToPx().toFloat()
    private val textAboveBarPadding = 6.dpToPx().toFloat()
    private val labelBelowBarPadding = 2.dpToPx().toFloat()
    private val focusCornerRadius = 8.dpToPx().toFloat()

    private val touchHelper = DayColumnTouchHelper()

    init {
        isFocusable = true
        ViewCompat.setAccessibilityDelegate(this, touchHelper)
    }

    /**
     * Set the data to display. Each pair is (dayLabel, milliseconds).
     * Expects up to 7 entries; oldest on the left, today last.
     */
    fun setData(data: List<Pair<String, Long>>) {
        this.data = data
        selectedIndex = null
        precomputedLabels = data.map { (_, millis) -> ScreenTimeGraphText.barLabel(millis) }
        touchHelper.invalidateRoot()
        invalidate()
    }

    private fun toggleSelection(index: Int) {
        selectedIndex = if (selectedIndex == index) null else index
        onDaySelected?.invoke(selectedIndex)
        touchHelper.invalidateRoot()
        invalidate()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (data.isEmpty()) return super.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (abs(event.x - downX) <= touchSlop && abs(event.y - downY) <= touchSlop) {
                    tappedIndex(event.x)?.let { index ->
                        toggleSelection(index)
                        performClick()
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun dispatchHoverEvent(event: MotionEvent): Boolean =
        touchHelper.dispatchHoverEvent(event) || super.dispatchHoverEvent(event)

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val handled = touchHelper.dispatchKeyEvent(event) || super.dispatchKeyEvent(event)
        // Keyboard focus may have moved between columns; the ring is drawn in onDraw
        if (handled) invalidate()
        return handled
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        touchHelper.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        invalidate()
    }

    /** Hit-tests the whole day column, not just the bar — bars are thin. */
    private fun tappedIndex(x: Float): Int? =
        ScreenTimeGraphText.columnIndex(x, width, sidePadding, data.size)

    private fun columnBounds(index: Int, out: Rect) {
        val slotWidth = (width - sidePadding * 2) / data.size
        val left = sidePadding + slotWidth * index
        out.set(left.toInt(), 0, (left + slotWidth).toInt(), height)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val desiredHeight = when (heightMode) {
            MeasureSpec.EXACTLY -> heightSize
            MeasureSpec.AT_MOST -> intrinsicHeightPx.coerceAtMost(heightSize)
            else -> intrinsicHeightPx
        }
        setMeasuredDimension(width, desiredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val barCount = data.size
        val availableWidth = width.toFloat() - sidePadding * 2
        val availableHeight = height.toFloat() - topPadding - bottomPadding

        val slotWidth = availableWidth / barCount
        val barWidth = (slotWidth * 0.5f).coerceAtMost(maxBarWidthPx)
        val barCornerRadius = barWidth / 2f

        val maxValue = data.maxOf { it.second }.coerceAtLeast(1L)
        val baselineY = topPadding + availableHeight

        canvas.drawLine(sidePadding, baselineY, width - sidePadding, baselineY, baselinePaint)

        // Emphasis follows the selection; with nothing selected it sits on today
        val emphasizedIndex = selectedIndex ?: data.lastIndex
        val focusedIndex =
            if (isFocused) touchHelper.keyboardFocusedVirtualViewId else ExploreByTouchHelper.INVALID_ID

        for (i in data.indices) {
            val (label, millis) = data[i]
            val isToday = i == emphasizedIndex
            val centerX = sidePadding + slotWidth * i + slotWidth / 2f

            // Minimum stub height so zero-usage days stay visible on the axis
            val barHeight = ((millis.toFloat() / maxValue.toFloat()) * availableHeight)
                .coerceAtLeast(barMinHeight)

            val barTop = baselineY - barHeight
            rect.set(centerX - barWidth / 2f, barTop, centerX + barWidth / 2f, baselineY)
            canvas.drawRoundRect(
                rect, barCornerRadius, barCornerRadius,
                if (isToday) todayBarPaint else barPaint
            )

            canvas.drawText(
                precomputedLabels.getOrElse(i) { "" },
                centerX,
                barTop - textAboveBarPadding,
                if (isToday) todayTextPaint else textPaint
            )

            canvas.drawText(
                label,
                centerX,
                height.toFloat() - labelBelowBarPadding,
                if (isToday) todayLabelPaint else labelPaint
            )

            if (i == focusedIndex) {
                val inset = focusPaint.strokeWidth
                focusRect.set(
                    sidePadding + slotWidth * i + inset, inset,
                    sidePadding + slotWidth * (i + 1) - inset, height - inset
                )
                canvas.drawRoundRect(focusRect, focusCornerRadius, focusCornerRadius, focusPaint)
            }
        }
    }

    private inner class DayColumnTouchHelper : ExploreByTouchHelper(this) {

        override fun getVirtualViewAt(x: Float, y: Float): Int =
            tappedIndex(x) ?: INVALID_ID

        override fun getVisibleVirtualViews(virtualViewIds: MutableList<Int>) {
            for (i in data.indices) virtualViewIds.add(i)
        }

        override fun onPopulateNodeForVirtualView(virtualViewId: Int, node: AccessibilityNodeInfoCompat) {
            val (label, millis) = data.getOrNull(virtualViewId) ?: run {
                // Data changed under a cached id; the helper throws on a node
                // with empty bounds or no text, so give it a placeholder
                node.contentDescription = ""
                node.setBoundsInParent(Rect(0, 0, 1, 1))
                return
            }
            val (hours, minutes) = ScreenTimeGraphText.spokenDuration(millis)
            val duration = when {
                hours > 0 && minutes > 0 ->
                    context.getString(R.string.screen_time_a11y_hours_minutes, hours, minutes)
                hours > 0 -> context.getString(R.string.screen_time_a11y_hours, hours)
                else -> context.getString(R.string.screen_time_a11y_minutes, minutes)
            }
            node.contentDescription = if (virtualViewId == data.lastIndex) {
                context.getString(R.string.screen_time_a11y_day_today, label, duration)
            } else {
                context.getString(R.string.screen_time_a11y_day, label, duration)
            }
            node.isSelected = virtualViewId == selectedIndex
            node.isClickable = true
            node.addAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            val bounds = Rect()
            columnBounds(virtualViewId, bounds)
            node.setBoundsInParent(bounds)
        }

        override fun onPerformActionForVirtualView(
            virtualViewId: Int,
            action: Int,
            arguments: Bundle?
        ): Boolean {
            if (action != AccessibilityNodeInfoCompat.ACTION_CLICK) return false
            if (virtualViewId !in data.indices) return false
            toggleSelection(virtualViewId)
            return true
        }
    }
}
