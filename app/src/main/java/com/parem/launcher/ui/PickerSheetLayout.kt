package com.parem.launcher.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView

/** Scroll headings and search with choices, keeping action rows below the list. */
internal fun LinearLayout.makePickerScroller() {
    val content = (0 until childCount).map(::getChildAt).filterIsInstance<ViewGroup>()
        .firstOrNull { group -> (0 until group.childCount).any { group.getChildAt(it) is RecyclerView } }
        ?: return
    val list = (0 until content.childCount).map(content::getChildAt).filterIsInstance<RecyclerView>().single()
    val choices = list.adapter ?: return
    val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val children = (0 until childCount).map(::getChildAt)
    var afterContent = false
    for (child in children) {
        if (child === content) {
            removeView(child)
            content.removeView(list)
            header.addView(child)
            afterContent = true
        } else if (!afterContent) {
            removeView(child)
            header.addView(child)
        }
    }
    list.adapter = ConcatAdapter(FixedPickerViewAdapter(header), choices)
    addView(list, 0, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
}

/** The fixed views retain their state when their single item is detached. */
private class FixedPickerViewAdapter(private val view: View) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = 1
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        view.layoutParams = RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return object : RecyclerView.ViewHolder(view) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) = Unit
}
