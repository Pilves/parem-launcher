package com.parem.launcher.ui

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView

/** Put a picker's headings, search and actions in its one scrolling list. */
internal fun LinearLayout.makePickerScroller() {
    val content = (0 until childCount).map(::getChildAt).filterIsInstance<ViewGroup>()
        .firstOrNull { group -> (0 until group.childCount).any { group.getChildAt(it) is RecyclerView } }
        ?: return
    val list = (0 until content.childCount).map(content::getChildAt).filterIsInstance<RecyclerView>().single()
    val choices = list.adapter ?: return
    val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val footer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val children = (0 until childCount).map(::getChildAt)
    var afterContent = false
    for (child in children) {
        removeView(child)
        if (child === content) {
            content.removeView(list)
            header.addView(child)
            afterContent = true
        } else if (afterContent) footer.addView(child) else header.addView(child)
    }
    list.adapter = ConcatAdapter(FixedPickerViewAdapter(header), choices, FixedPickerViewAdapter(footer))
    addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
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
