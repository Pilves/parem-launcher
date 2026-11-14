package com.parem.launcher.ui

import android.content.Context
import android.graphics.Rect
import android.os.SystemClock
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.TextViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.parem.launcher.R
import com.parem.launcher.helper.AppPickerSections
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.helper.getColorFromAttr
import com.parem.launcher.helper.isEinkDisplay

/** Selection is keyed independently of recycled views and search results. */
class AppPickerAdapter(
    val textColor: Int,
    var maxSelected: Int,
    entries: List<Entry>,
    initiallySelected: Collection<String> = emptyList(),
    private val singleChoice: Boolean = false,
    private val onLimitReached: ((Context) -> Unit)? = null,
    private val onSelectionChanged: () -> Unit = {},
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    class Entry(val id: String, val label: String)

    private sealed class Row {
        class Item(val entry: AppPickerSections.Entry) : Row()
        class Heading(val selected: Boolean) : Row()
        class Empty(val otherApps: Boolean) : Row()
    }

    private val allEntries = AppPickerSections.ordered(entries.map { AppPickerSections.Entry(it.id, it.label) })
    private var query = ""
    private var rows: List<Row> = emptyList()
    private var lastSelectionAt: Long? = null
    val selectedIds = LinkedHashSet<String>(initiallySelected)

    init { rebuild() }

    fun filter(query: String) {
        this.query = query
        rebuild()
        notifyDataSetChanged()
    }

    /** Replaces the staged choices only when the parent accepts its child picker. */
    fun replaceSelection(ids: Collection<String>) {
        selectedIds.clear()
        selectedIds.addAll(ids)
        rebuild()
        notifyDataSetChanged()
    }

    private fun rebuild() {
        val visible = AppPickerSections.visible(allEntries, selectedIds, query)
        rows = if (singleChoice) {
            if (visible.remaining.isEmpty()) listOf(Row.Empty(false)) else visible.remaining.map { Row.Item(it) }
        } else buildList {
            add(Row.Heading(true))
            addAll(visible.selected.map { Row.Item(it) })
            add(Row.Heading(false))
            if (visible.remaining.isEmpty()) add(Row.Empty(selectedIds.isNotEmpty() && query.isNotBlank()))
            else addAll(visible.remaining.map { Row.Item(it) })
        }
    }

    fun visibleAppCount(): Int = rows.count { it is Row.Item }
    override fun getItemCount() = rows.size
    override fun getItemViewType(position: Int) = when (rows[position]) {
        is Row.Item -> 0
        is Row.Heading -> 1
        is Row.Empty -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = when (viewType) {
            0 -> if (singleChoice) TextView(parent.context).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
                setTextColor(textColor)
                minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
                gravity = Gravity.CENTER_VERTICAL
                val padding = resources.getDimensionPixelSize(R.dimen.sheet_padding_horizontal)
                setPadding(padding, 14.dpToPx(), padding, 14.dpToPx())
                setBackgroundResource(if (context.isEinkDisplay()) R.drawable.bg_eink_control else context.selectableBackgroundRes())
                isFocusable = true
                ViewCompat.setScreenReaderFocusable(this, true)
            } else SheetCheckRow(parent.context)
            else -> TextView(parent.context).apply {
                TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetCaption)
                setTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
                val padding = resources.getDimensionPixelSize(R.dimen.sheet_padding_horizontal)
                setPadding(padding, 12.dpToPx(), padding, 8.dpToPx())
                if (viewType == 1) ViewCompat.setAccessibilityHeading(this, true)
            }
        }
        view.layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        return object : RecyclerView.ViewHolder(view) {}
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Heading -> (holder.itemView as TextView).text = if (row.selected)
                holder.itemView.context.getString(R.string.picker_section_selected, selectedIds.size)
            else holder.itemView.context.getString(R.string.a11y_all_apps)
            is Row.Empty -> (holder.itemView as TextView).text = holder.itemView.context.getString(
                if (row.otherApps) R.string.picker_no_other_apps_match else R.string.no_apps_found,
            )
            is Row.Item -> {
                val entry = row.entry
                val view = holder.itemView
                if (view is SheetCheckRow) {
                    view.text = entry.label
                    view.bindChecked(entry.id in selectedIds)
                } else (view as TextView).text = entry.label
                view.setOnClickListener {
                    val now = SystemClock.uptimeMillis()
                    if (!singleChoice && lastSelectionAt?.let { now - it < 400L } == true)
                        return@setOnClickListener
                    if (entry.id in selectedIds) selectedIds.remove(entry.id)
                    else if (selectedIds.size < maxSelected) selectedIds.add(entry.id)
                    else {
                        if (onLimitReached != null) onLimitReached.invoke(view.context)
                        else view.announceForAccessibility(view.context.getString(R.string.a11y_picker_cap, maxSelected))
                        return@setOnClickListener
                    }
                    if (!singleChoice) lastSelectionAt = now
                    if (view is SheetCheckRow) view.checked = entry.id in selectedIds
                    rebuild()
                    notifyDataSetChanged()
                    onSelectionChanged()
                }
            }
        }
    }
}

/** Search and one recycled list; picker layout pins search and actions around the list. */
fun LinearLayout.addAppPicker(adapter: AppPickerAdapter) {
    val inset = resources.getDimensionPixelSize(R.dimen.sheet_padding_horizontal)
    val startInset = (inset - paddingStart).coerceAtLeast(0)
    val endInset = (inset - paddingEnd).coerceAtLeast(0)
    val searchRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    val search = EditText(context).apply {
        tag = "parem_picker_search"
        hint = context.getString(R.string.search_apps)
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
        setTextColor(adapter.textColor)
        setHintTextColor(context.getColorFromAttr(R.attr.textSecondaryColor))
        background = null
        inputType = InputType.TYPE_CLASS_TEXT
        isSingleLine = true
        setPaddingRelative(startInset, 4.dpToPx(), endInset, 8.dpToPx())
    }
    val clear = TextView(context).apply {
        text = context.getString(R.string.search_history_clear)
        TextViewCompat.setTextAppearance(this, R.style.TextAppearance_Parem_SheetBody)
        setTextColor(adapter.textColor)
        gravity = Gravity.CENTER
        minimumHeight = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        minimumWidth = resources.getDimensionPixelSize(R.dimen.touch_target_min)
        setPaddingRelative(8.dpToPx(), 0, endInset, 0)
        setOnClickListener { search.text.clear() }
        visibility = View.GONE
    }
    searchRow.addView(search, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
    searchRow.addView(clear)
    addView(searchRow)
    var announcement: Runnable? = null
    search.doAfterTextChanged {
        adapter.filter(it?.toString().orEmpty())
        clear.visibility = if (it.isNullOrEmpty()) View.GONE else View.VISIBLE
        search.setPaddingRelative(startInset, search.paddingTop, if (it.isNullOrEmpty()) endInset else 0, search.paddingBottom)
        announcement?.let(search::removeCallbacks)
        announcement = Runnable {
            if (search.isAttachedToWindow) search.announceForAccessibility(
                context.getString(R.string.picker_result_count, adapter.visibleAppCount()),
            )
        }.also { task -> search.postDelayed(task, 400) }
    }
    search.viewTreeObserver.addOnGlobalLayoutListener {
        if (search.hasFocus() && ViewCompat.getRootWindowInsets(search)?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
            val rows = 2 * resources.getDimensionPixelSize(R.dimen.touch_target_min)
            search.requestRectangleOnScreen(Rect(0, 0, search.width, search.height + rows), true)
        }
    }
    addView(object : RecyclerView(context) {
        override fun dispatchNestedPreScroll(dx: Int, dy: Int, consumed: IntArray?, offsetInWindow: IntArray?, type: Int) =
            dy >= 0 && super.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow, type)
        override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_DOWN) parent.requestDisallowInterceptTouchEvent(true)
            return super.onInterceptTouchEvent(e)
        }
    }.apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        layoutManager = LinearLayoutManager(context)
        this.adapter = adapter
    })
}
