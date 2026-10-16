package com.parem.launcher.helper

/**
 * The home-slot fitting arithmetic shared by HomeSlotsController (which hides
 * rows that do not fit) and HomeWidgetController (which refuses a widget that
 * would hide one). Pure: heights are pixels already measured by the caller.
 */
object HomeCapacity {

    /** Rows of [rowHeightPx] that fit in [usableHeightPx]; 0 when nothing is measured yet. */
    fun fitting(usableHeightPx: Int, rowHeightPx: Int): Int =
        if (usableHeightPx <= 0 || rowHeightPx <= 0) 0 else usableHeightPx / rowHeightPx

    /** Labels of the occupied slots among the first [shown] that fall beyond [capacity]. */
    fun hiddenLabels(slotLabels: List<String>, shown: Int, capacity: Int): List<String> {
        if (capacity >= shown) return emptyList()
        return slotLabels.take(shown).drop(capacity.coerceAtLeast(0)).filter { it.isNotBlank() }
    }

    fun canAddWidget(roomPx: Int?, widgetHeightPx: Int): Boolean =
        roomPx == null || widgetHeightPx <= roomPx

    /** Pixels left under [rows] rows of [rowHeightPx]; negative when they already overflow. */
    fun room(usableHeightPx: Int, rowHeightPx: Int, rows: Int): Int =
        usableHeightPx - rows * rowHeightPx
}
