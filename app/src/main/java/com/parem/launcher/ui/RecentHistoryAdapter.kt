package com.parem.launcher.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.data.AppModel
import com.parem.launcher.helper.dpToPx

/**
 * Omnibox history rows (M4-WP16) above the app list while the search is
 * empty: a "Recent" label, then recent launches and queries, newest first.
 */
class RecentHistoryAdapter(
    private val labelGravity: Int,
    private val onApp: (AppModel, View) -> Unit,
    private val onQuery: (String) -> Unit,
) : RecyclerView.Adapter<RecentHistoryAdapter.ViewHolder>() {

    sealed interface Row {
        data class App(val app: AppModel) : Row
        data class Query(val text: String) : Row
    }

    private var rows: List<Row> = emptyList()

    // Inside the drawer's ConcatAdapter a full change also rebinds every app
    // row, so the first keystroke (history to none) removes just these rows
    @SuppressLint("NotifyDataSetChanged")
    fun submit(newRows: List<Row>) {
        if (newRows == rows) return
        val oldCount = itemCount
        rows = newRows
        val newCount = itemCount
        when {
            // Inserted rows would land above the first visible row, which the
            // list stays anchored on, so the history would open scrolled out
            // of view; a full change keeps the old layout from the top
            oldCount == 0 -> notifyDataSetChanged()
            newCount == 0 -> notifyItemRangeRemoved(0, oldCount)
            else -> {
                // The header (position 0) stays; the rows under it change, then the tail grows or shrinks
                notifyItemRangeChanged(1, minOf(oldCount, newCount) - 1)
                if (newCount > oldCount) notifyItemRangeInserted(oldCount, newCount - oldCount)
                else if (newCount < oldCount) notifyItemRangeRemoved(newCount, oldCount - newCount)
            }
        }
    }

    override fun getItemCount(): Int = if (rows.isEmpty()) 0 else rows.size + 1

    override fun getItemViewType(position: Int): Int = if (position == 0) 0 else 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = if (viewType == 0) TextView(parent.context, null, 0, R.style.TextSmallLight).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPaddingRelative(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 4.dpToPx())
            text = context.getString(R.string.search_history_recent)
        } else LayoutInflater.from(parent.context).inflate(R.layout.adapter_recent_history, parent, false)
        (view as TextView).gravity = labelGravity
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == 0) return
        val title = holder.itemView as TextView
        when (val row = rows[position - 1]) {
            is Row.App -> {
                title.text = row.app.appLabel
                title.setOnClickListener { onApp(row.app, title) }
            }
            is Row.Query -> {
                title.text = title.context.getString(R.string.search_history_query, row.text.trim())
                title.setOnClickListener { onQuery(row.text) }
            }
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view)
}
