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
        list.post {
            if (!inHeader || !list.isAttachedToWindow) return@post
            // Scroll to the header by position rather than selecting it: the header
            // is deliberately nonselectable and D-pad selection skips it.
            list.smoothScrollToPositionFromTop(0, 0, 200)
            if (restoreFocus) {
                val deadline = android.os.SystemClock.elapsedRealtime() + 3_000
                val retry = object : Runnable {
                    override fun run() {
                        if (!inHeader || !list.isAttachedToWindow) return
                        if (search.isAttachedToWindow) {
                            search.requestFocus()
                            search.requestRectangleOnScreen(android.graphics.Rect(0, 0, search.width, search.height), true)
                        } else if (android.os.SystemClock.elapsedRealtime() < deadline) {
                            list.postDelayed(this, 50)
                        }
                    }
                }
                list.post(retry)
            }
        }
    }
}
