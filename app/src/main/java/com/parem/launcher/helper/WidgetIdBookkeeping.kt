package com.parem.launcher.helper

/**
 * Pure bookkeeping for the three widget-ID stores (AppWidgetHost, WIDGET_IDS,
 * WIDGET_PROVIDERS) so a bind or restore interrupted by activity recreation
 * neither loses a widget nor leaks an ID. See ARCHITECTURE.md trap #2.
 */
object WidgetIdBookkeeping {

    /**
     * WIDGET_IDS after a restore pass: valid widgets stay, invalid ones stay only
     * while a rebind is still possible (provider known), order preserved. Keeping
     * the old ID in the list is what lets a later restore ask again after the
     * rebind dialog was interrupted.
     */
    fun idsToKeep(ids: List<Int>, valid: Set<Int>, restorable: Set<Int>): List<Int> =
        ids.filter { it in valid || it in restorable }

    /** WIDGET_IDS with a rebound widget's new ID in the old one's position. */
    fun replaceId(ids: List<Int>, oldId: Int, newId: Int): List<Int> =
        if (oldId in ids) ids.map { if (it == oldId) newId else it } else ids + newId

    /**
     * Host IDs nothing refers to: not on the home screen and not the one
     * mid-bind/configure. Allocated by an add or restore that never finished.
     */
    fun orphanIds(hostIds: IntArray, ids: List<Int>, pendingId: Int): List<Int> =
        hostIds.filter { it !in ids && it != pendingId }
}
