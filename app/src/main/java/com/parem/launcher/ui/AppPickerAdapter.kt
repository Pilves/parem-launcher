package com.parem.launcher.ui

import android.graphics.Rect
import android.text.InputType
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
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
 * Selection lives in [selectedIds] — never in row views — so it
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

    class Holder(val row: SheetCheckRow) : RecyclerView.ViewHolder(row)

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
        return Holder(SheetCheckRow(parent.context).apply {
            layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val entry = visibleEntries[position]
        holder.row.apply {
            text = entry.label
            checked = entry.id in selectedIds
            setOnClickListener {
                if (entry.id in selectedIds) selectedIds.remove(entry.id)
                else if (selectedIds.size < maxSelected) selectedIds.add(entry.id)
                else announceForAccessibility(context.getString(R.string.a11y_picker_cap, maxSelected))
                checked = entry.id in selectedIds
                onSelectionChanged()
            }
        }
    }
}

/**
 * The search field plus fixed-height list every app-picker sheet shows. The
 * list is the sheet's only scroller; the padding parameters keep each sheet's inset.
 */
fun LinearLayout.addAppPicker(adapter: AppPickerAdapter, searchPaddingLeft: Int = 8.dpToPx(), searchPaddingTop: Int = 4.dpToPx()) {
    addView(EditText(context).apply {
        hint = context.getString(R.string.search_apps)
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
        setTextColor(adapter.textColor)
        setHintTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
        background = null
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        setPadding(searchPaddingLeft, searchPaddingTop, 0, 8.dpToPx())
        doAfterTextChanged { adapter.filter(it?.toString() ?: "") }
        viewTreeObserver.addOnGlobalLayoutListener {
            if (hasFocus() && ViewCompat.getRootWindowInsets(this)
                    ?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                // Resize keeps the field visible; also reveal the first result rows.
                val rows = 2 * resources.getDimensionPixelSize(R.dimen.touch_target_min)
                requestRectangleOnScreen(Rect(0, 0, width, height + rows), true)
            }
        }
    })
    // BottomSheetBehavior asks only the sheet's outer scroller whether a
    // downward drag may pull the sheet, and that scroller sits at its top
    // whenever the sheet fits: a drag in a scrolled list closed the sheet
    // instead of scrolling the list back up.
    addView(object : RecyclerView(context) {
        // Pre-scroll reaches the sheet before the list scrolls. Withheld for a
        // downward drag (dy < 0), the list scrolls first and only the rest goes
        // on to the outer scroller; the sheet ignores what is left after that.
        override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int) =
            dy >= 0 && super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)

        // Keeps the sheet from taking the drag itself, for example when the
        // finger leaves the side of a width-capped sheet.
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
            return super.onInterceptTouchEvent(e)
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            minOf(300.dpToPx(), resources.displayMetrics.heightPixels / 3),
        )
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
    })
}
