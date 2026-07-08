package com.parem.launcher.ui

import android.view.ViewGroup
import android.widget.CheckBox
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.helper.SearchMatcher
import com.parem.launcher.helper.dpToPx

/**
 * Checkbox rows for the app pickers (folder creation, focus-mode whitelist),
 * backed by a RecyclerView so rows are recycled and filtering swaps the list
 * instead of toggling hundreds of inflated views.
 *
 * Selection lives in [selectedIds] — never in the CheckBox views — so it
 * survives recycling and filtering; its iteration order is click order.
 * At most [maxSelected] entries can be checked; an excess check is rejected
 * by unchecking, matching the old inline pickers.
 */
class AppPickerAdapter(
    private val textColor: Int,
    private val maxSelected: Int,
    entries: List<Entry>,
    initiallySelected: Collection<String> = emptyList(),
) : RecyclerView.Adapter<AppPickerAdapter.Holder>() {

    /** One checkable row. [id] keys the selection across filtering/recycling. */
    class Entry(val id: String, val label: String) {
        val searchKey: SearchMatcher.LabelKey by lazy { SearchMatcher.key(label) }
    }

    class Holder(val checkBox: CheckBox) : RecyclerView.ViewHolder(checkBox)

    private val allEntries = entries.toList()
    private var visibleEntries = allEntries

    val selectedIds = LinkedHashSet<String>(initiallySelected)

    /** Same matching as the drawer omnibox; blank query restores the full list. */
    fun filter(query: String) {
        visibleEntries = if (query.isBlank()) allEntries
        else allEntries.filter { SearchMatcher.matches(it.label, it.searchKey, query) }
        notifyDataSetChanged()
    }

    override fun getItemCount() = visibleEntries.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        // Same look as the old inline checkboxes — restructure, not redesign.
        val checkBox = CheckBox(parent.context).apply {
            textSize = 14f
            setTextColor(textColor)
            setPadding(8.dpToPx(), 2.dpToPx(), 0, 2.dpToPx())
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        return Holder(checkBox)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = visibleEntries[position]
        holder.checkBox.apply {
            // A recycled row still carries the previous entry's listener; detach it
            // before setting isChecked or that entry's selection would be mutated.
            setOnCheckedChangeListener(null)
            text = entry.label
            isChecked = entry.id in selectedIds
            setOnCheckedChangeListener { _, checked ->
                if (checked) {
                    if (selectedIds.size < maxSelected) selectedIds.add(entry.id)
                    else isChecked = false // cap reached: reject, as the old pickers did
                } else {
                    selectedIds.remove(entry.id)
                }
            }
        }
    }
}
