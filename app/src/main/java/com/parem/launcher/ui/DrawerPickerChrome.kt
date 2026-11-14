package com.parem.launcher.ui

import android.content.Context
import android.view.View
import android.widget.ScrollView
import com.parem.launcher.R

/** Scroll picker chrome and share space with the list in short windows. */
internal class DrawerPickerChrome(context: Context) : ScrollView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val boundedHeight = if (mode == View.MeasureSpec.UNSPECIFIED) heightMeasureSpec else {
            val row = resources.getDimensionPixelSize(R.dimen.touch_target_min)
            val gap = (8 * resources.displayMetrics.density).toInt()
            val usable = (height - gap).coerceAtLeast(0)
            // A full row fits only when the header can keep at least that much space.
            // Below that size, share the visible area rather than hiding the header.
            val headerLimit = if (usable >= 2 * row) usable - row else (usable / 2).coerceAtLeast(if (height > 0) 1 else 0)
            View.MeasureSpec.makeMeasureSpec(headerLimit, View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, boundedHeight)
    }
}
