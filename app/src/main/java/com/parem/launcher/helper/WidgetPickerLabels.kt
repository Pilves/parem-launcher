package com.parem.launcher.helper

/** Decides which widget picker rows need their size to tell them apart. Android-free. */
object WidgetPickerLabels {

    class Row(val app: String, val label: String)

    /** True per row when another widget of the same app has the same name. */
    fun needsSize(rows: List<Row>): List<Boolean> {
        val counts = rows.groupingBy { it.app to it.label }.eachCount()
        return rows.map { counts.getValue(it.app to it.label) > 1 }
    }

    /** Android's published cell formula, for a provider that declares no target cell size. */
    fun cells(sizeDp: Int): Int = ((sizeDp + 30) / 70).coerceAtLeast(1)
}
