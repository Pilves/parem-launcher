package com.parem.launcher.helper

/**
 * Pure bookkeeping for the three widget-ID stores (AppWidgetHost, WIDGET_IDS,
 * WIDGET_PROVIDERS) so a bind or restore interrupted by activity recreation
 * neither loses a widget nor leaks an ID. See ARCHITECTURE.md trap #2.
 */
object WidgetIdBookkeeping {

    /** A failed provider query is unknown, just like a null result. */
    fun <T> queryOrUnknown(query: () -> T?, onFailure: (Exception) -> Unit): T? =
        try { query() } catch (e: Exception) { onFailure(e); null }

    /** All saved widgets, including temporarily unavailable ones, occupy capacity. */
    fun canAdd(ids: List<Int>): Boolean = ids.size < 6

    /** A swap never appends when its original target has been removed. */
    fun replaceId(ids: List<Int>, oldId: Int, newId: Int): List<Int>? =
        if (oldId in ids) ids.map { if (it == oldId) newId else it } else null

    /** A legacy saved index cannot safely identify the original target after reorder. */
    fun pendingTarget(savedId: Int?, legacyIndex: Int): Int =
        savedId ?: if (legacyIndex >= 0) -2 else -1

    /**
     * Host IDs nothing refers to: not on the home screen and not the one
     * mid-bind/configure. Allocated by an add or restore that never finished.
     */
    fun orphanIds(hostIds: IntArray, ids: List<Int>, pendingId: Int): List<Int> =
        hostIds.filter { it !in ids && it != pendingId }
}
