package com.parem.launcher.ui

import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.helper.dpToPx

/** Keep title, search and actions visible around the one scrolling app list. */
internal fun LinearLayout.makePickerScroller() {
    val content = (0 until childCount).map(::getChildAt).filterIsInstance<ViewGroup>()
        .firstOrNull { group -> (0 until group.childCount).any { group.getChildAt(it) is RecyclerView } }
        ?: return
    val list = (0 until content.childCount).map(content::getChildAt).filterIsInstance<RecyclerView>().single()
    content.removeView(list)
    addView(list, indexOfChild(content) + 1,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 48.dpToPx(), 1f))
}
