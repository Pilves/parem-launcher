package com.parem.launcher.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

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

internal fun installWidgetPickerChrome(container: LinearLayout, title: View, search: View, minListHeight: Int, bottomContentPadding: Int): WidgetPickerChrome {
    val titleIndex = container.indexOfChild(title)
    val handle = container.getChildAt(titleIndex - 1)
    val hadFocus = search.hasFocus()
    val handleParams = LinearLayout.LayoutParams(handle.layoutParams as LinearLayout.LayoutParams)
    val titleParams = LinearLayout.LayoutParams(title.layoutParams as LinearLayout.LayoutParams)
    val searchParams = LinearLayout.LayoutParams(search.layoutParams as LinearLayout.LayoutParams)
    val topPadding = container.paddingTop
    val navPadding = (container.paddingBottom - bottomContentPadding).coerceAtLeast(0)
    container.removeView(handle)
    container.removeView(title)
    container.removeView(search)
    container.setPadding(container.paddingLeft, 0, container.paddingRight, navPadding)
    ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        view.setPadding(view.paddingLeft, 0, view.paddingRight, bottom)
        insets
    }
    val content = LinearLayout(container.context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, topPadding, 0, 0)
    }
    content.addView(handle, handleParams)
    content.addView(title, titleParams)
    content.addView(search, searchParams)
    val chrome = WidgetPickerChrome(container.context, minListHeight).apply {
        addView(content, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    }
    container.addView(chrome, titleIndex - 1, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
    ViewCompat.requestApplyInsets(container)
    if (hadFocus) search.post { if (search.isAttachedToWindow) search.requestFocus() }
    return chrome
}
