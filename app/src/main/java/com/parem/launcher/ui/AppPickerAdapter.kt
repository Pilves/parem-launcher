package com.parem.launcher.ui

import android.content.res.ColorStateList
import android.text.InputType
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.helper.SearchMatcher
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr

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
    val textColor: Int,
    private val maxSelected: Int,
    entries: List<Entry>,
    initiallySelected: Collection<String> = emptyList(),
    private val onSelectionChanged: () -> Unit = {},
) :RecyclerView.Adapter<AppPickerAdapter.Holder>() {

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
            // Default widget tint is the Material accent, which clashes with
            // the launcher's mono palette
            buttonTintList = ColorStateList.valueOf(textColor)
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
                onSelectionChanged()
            }
        }
    }
}

/**
 * The search field plus fixed-height list every app-picker sheet shows. The
 * list height is fixed so rows recycle inside the sheet's scroller; the
 * padding parameters keep each sheet's existing inset.
 */
fun LinearLayout.addAppPicker(adapter: AppPickerAdapter, searchPaddingLeft: Int = 8.dpToPx(), searchPaddingTop: Int = 4.dpToPx()) {
    addView(EditText(context).apply {
        hint = context.getString(R.string.search_apps)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
        setTextColor(adapter.textColor)
        setHintTextColor(context.getColorFromAttr(R.attr.primaryColorTrans50))
        background = null
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        setPadding(searchPaddingLeft, searchPaddingTop, 0, 8.dpToPx())
        doAfterTextChanged { adapter.filter(it?.toString() ?: "") }
    })
    addView(RecyclerView(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 300.dpToPx())
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
    })
}
