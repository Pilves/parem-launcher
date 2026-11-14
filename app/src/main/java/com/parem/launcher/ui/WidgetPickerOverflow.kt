package com.parem.launcher.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ListView

/** Keeps fixed widget-picker chrome reachable when the remaining window is very short. */
internal class WidgetPickerOverflow(
    private val container: LinearLayout,
    private val title: View,
    private val search: View,
    private val list: ListView,
    private val header: LinearLayout,
    private val minListHeight: Int,
) {
    private val titleIndex = container.indexOfChild(title)
    private val searchIndex = container.indexOfChild(search)
    private val titleParams = LinearLayout.LayoutParams(title.layoutParams as LinearLayout.LayoutParams)
    private val searchParams = LinearLayout.LayoutParams(search.layoutParams as LinearLayout.LayoutParams)
    private var inHeader = false

    init {
        container.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            container.post { if (container.isAttachedToWindow) update() }
        }
    }

    internal fun update() {
        if (container.height <= 0 || title.measuredHeight <= 0 || search.measuredHeight <= 0) return
        var fixed = container.paddingTop + container.paddingBottom
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === list || child === title || child === search || child.visibility == View.GONE) continue
            val lp = child.layoutParams as? ViewGroup.MarginLayoutParams
            fixed += child.measuredHeight + (lp?.topMargin ?: 0) + (lp?.bottomMargin ?: 0)
        }
        val chrome = title.measuredHeight + titleParams.topMargin + titleParams.bottomMargin +
            search.measuredHeight + searchParams.topMargin + searchParams.bottomMargin
        val shouldScrollChrome = container.height - fixed < chrome + minListHeight
        if (shouldScrollChrome == inHeader) return
        val hadFocus = search.hasFocus()
        android.util.Log.i("WidgetOverflowDiag", "update should=$shouldScrollChrome hadFocus=$hadFocus container=${container.height} first=${list.firstVisiblePosition}")
        if (shouldScrollChrome) {
            container.removeView(title)
            container.removeView(search)
            header.addView(title, LinearLayout.LayoutParams(titleParams))
            header.addView(search, LinearLayout.LayoutParams(searchParams))
        } else {
            header.removeView(title)
            header.removeView(search)
            container.addView(title, titleIndex, LinearLayout.LayoutParams(titleParams))
            container.addView(search, searchIndex, LinearLayout.LayoutParams(searchParams))
        }
        inHeader = shouldScrollChrome
        if (shouldScrollChrome) revealHeader(hadFocus)
        else if (hadFocus) search.post { if (search.isAttachedToWindow) search.requestFocus() }
    }

    private fun revealHeader(restoreFocus: Boolean) {
        // Scroll without selecting: the header deliberately cannot be selected.
        // A tiny viewport may need many list pages to reach it from a distant row.
        var remaining = 240
        val reveal = object : Runnable {
            override fun run() {
                if (!inHeader || !list.isAttachedToWindow || remaining-- <= 0) { android.util.Log.i("WidgetOverflowDiag", "stop inHeader=$inHeader attached=${list.isAttachedToWindow} remaining=$remaining"); return }
                if (remaining % 30 == 0 || remaining > 237) android.util.Log.i("WidgetOverflowDiag", "step first=${list.firstVisiblePosition} top=${list.getChildAt(0)?.top} height=${list.height} attached=${search.isAttachedToWindow} focused=${search.hasFocus()}")
                val first = list.getChildAt(0)
                if (list.firstVisiblePosition == 0 && first != null && first.top >= list.paddingTop) {
                    if (restoreFocus && search.isAttachedToWindow) {
                        android.util.Log.i("WidgetOverflowDiag", "requestFocus returned=${search.requestFocus()} focused=${search.hasFocus()}")
                        search.requestRectangleOnScreen(android.graphics.Rect(0, 0, search.width, search.height), true)
                    }
                    return
                }
                list.scrollListBy(-list.height.coerceAtLeast(1))
                list.postOnAnimation(this)
            }
        }
        list.post(reveal)
    }
}
