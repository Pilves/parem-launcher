package com.parem.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.parem.launcher.R
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr

/**
 * Custom View that draws 7 vertical bars representing daily screen time.
 * Each bar is labeled with a day abbreviation below and hours text above.
 *
 * Visual hierarchy: the LAST entry is treated as "today" and drawn at full
 * strength; earlier days are dimmed so the eye lands on the current day.
 * Callers must therefore supply data ordered oldest-first.
 */
class ScreenTimeGraphView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var data: List<Pair<String, Long>> = emptyList()
    private var precomputedLabels: List<String> = emptyList()

    private val primaryColor = context.getColorFromAttr(R.attr.primaryColor)

    // ~35% strength for past days; today gets the full-strength variants below
    private val dimAlpha = 90
    private val textDimAlpha = 130

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

    private val rect = RectF()
    private val topPadding = 24.dpToPx().toFloat()
    private val bottomPadding = 20.dpToPx().toFloat()
    private val sidePadding = 12.dpToPx().toFloat()
    private val intrinsicHeightPx = 200.dpToPx()
    private val barMinHeight = 2.dpToPx().toFloat()
    private val maxBarWidthPx = 32.dpToPx().toFloat()
    private val textAboveBarPadding = 6.dpToPx().toFloat()
    private val labelBelowBarPadding = 2.dpToPx().toFloat()

    /**
     * Set the data to display. Each pair is (dayLabel, milliseconds).
     * Expects up to 7 entries; oldest on the left, today last.
     */
    fun setData(data: List<Pair<String, Long>>) {
        this.data = data
        precomputedLabels = data.map { (_, millis) ->
            val hours = millis / 3_600_000.0
            if (hours >= 1.0) {
                String.format("%.1fh", hours)
            } else {
                val mins = millis / 60_000
                "${mins}m"
            }
        }
        invalidate()
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

        for (i in data.indices) {
            val (label, millis) = data[i]
            val isToday = i == data.lastIndex
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
        }
    }
}
