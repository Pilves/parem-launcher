package com.parem.launcher.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView

/** Keep picker search reachable without moving it into the widget list. */
internal class WidgetPickerChrome(context: Context, private val minListHeight: Int) : ScrollView(context) {
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (h < oldh) findFocus()?.let { focused ->
            focused.post { if (focused.isAttachedToWindow && focused.hasFocus()) focused.requestRectangleOnScreen(android.graphics.Rect(0, 0, focused.width, focused.height), true) }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = View.MeasureSpec.getMode(heightMeasureSpec)
        val height = View.MeasureSpec.getSize(heightMeasureSpec)
        val bounded = if (mode == View.MeasureSpec.UNSPECIFIED) heightMeasureSpec else {
            val limit = if (height >= 2 * minListHeight) height - minListHeight else height / 2
            View.MeasureSpec.makeMeasureSpec(limit.coerceAtLeast(if (height > 0) 1 else 0), View.MeasureSpec.AT_MOST)
        }
        super.onMeasure(widthMeasureSpec, bounded)
    }
}

internal fun installWidgetPickerChrome(container: LinearLayout, title: View, search: View, minListHeight: Int): WidgetPickerChrome {
    val index = container.indexOfChild(title)
    val hadFocus = search.hasFocus()
    val titleParams = LinearLayout.LayoutParams(title.layoutParams as LinearLayout.LayoutParams)
    val searchParams = LinearLayout.LayoutParams(search.layoutParams as LinearLayout.LayoutParams)
    container.removeView(title)
    container.removeView(search)
    val content = LinearLayout(container.context).apply { orientation = LinearLayout.VERTICAL }
    content.addView(title, titleParams)
    content.addView(search, searchParams)
    val chrome = WidgetPickerChrome(container.context, minListHeight).apply {
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    container.addView(chrome, index, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    if (hadFocus) search.post { if (search.isAttachedToWindow) search.requestFocus() }
    return chrome
}
