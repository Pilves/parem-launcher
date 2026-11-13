package com.parem.launcher.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.helper.dpToPx

/** Measures the picker list after the sheet's fixed content, so its footer remains reachable. */
internal class PickerSheetLayout(context: Context) : LinearLayout(context) {
    var fitPicker = false

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val list = if (fitPicker) findPicker(this) else null
        if (list == null || MeasureSpec.getMode(heightMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        // First measure title, search, actions and insets without the list. A second
        // pass allocates only the remaining dialog height to the scrolling list.
        val params = list.layoutParams
        params.height = 0
        forceRemeasure(list)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        var occupied = paddingTop + paddingBottom
        for (index in 0 until childCount) {
            val child = getChildAt(index)
            if (child.visibility == View.GONE) continue
            val margins = child.layoutParams as? MarginLayoutParams
            occupied += child.measuredHeight + (margins?.topMargin ?: 0) + (margins?.bottomMargin ?: 0)
        }
        val preferred = minOf(300.dpToPx(), resources.displayMetrics.heightPixels / 3)
        params.height = minOf(preferred, (MeasureSpec.getSize(heightMeasureSpec) - occupied).coerceAtLeast(0))
        forceRemeasure(list)
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }

    private fun forceRemeasure(list: View) {
        var view: View? = list
        while (view != null && view !== this) {
            view.forceLayout()
            view = view.parent as? View
        }
    }

    private fun findPicker(view: View): RecyclerView? {
        if (view is RecyclerView) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findPicker(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
