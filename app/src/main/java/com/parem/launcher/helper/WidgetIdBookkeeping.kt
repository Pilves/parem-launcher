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

    /** Results carry no widget ID, so a second operation cannot overlap the first. */
    fun canStartOperation(hasPendingOp: Boolean, hasPendingResult: Boolean): Boolean =
        !hasPendingOp && !hasPendingResult

    /** A pending ID can be released only while it is not saved on home. */
    fun canReleasePending(savedIds: List<Int>, pendingId: Int): Boolean = pendingId !in savedIds

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

/**
 * Host views kept while Home's view is rebuilt (drawer, Settings), keyed by
 * widget ID: a fourth widget-ID store, activity-scoped. A view comes back only
 * for the provider@profile signature it was made for, and only while the
 * package stamp and configuration it was made under still hold (trap #5);
 * every miss means creating a fresh view, as Home did before this cache.
 */
class WidgetViewCache<V : Any> {
    private class Entry<V>(val signature: String, val view: V) {
        var size: Pair<Int, Int>? = null
    }

    private val entries = HashMap<Int, Entry<V>>()
    private var madeUnder: Pair<Long, Any>? = null

    /** Keeps only [ids], and nothing at all once [stamp] or [config] differ from the previous call. */
    fun retain(ids: Collection<Int>, stamp: Long, config: Any) {
        if (madeUnder != stamp to config) entries.clear()
        madeUnder = stamp to config
        entries.keys.retainAll(ids.toSet())
    }

    /** The view made for [signature]; one made for another provider or profile is dropped. */
    fun reusable(id: Int, signature: String): V? {
        val entry = entries[id] ?: return null
        if (entry.signature == signature) return entry.view
        entries.remove(id)
        return null
    }

    fun put(id: Int, signature: String, view: V) {
        entries[id] = Entry(signature, view)
    }

    fun evict(id: Int) {
        entries.remove(id)
    }

    fun clear() = entries.clear()

    /** False only when this is the size the cached view for [id] last reported; records it otherwise. */
    fun sizeChanged(id: Int, widthDp: Int, heightDp: Int): Boolean {
        val entry = entries[id] ?: return true
        if (entry.size == widthDp to heightDp) return false
        entry.size = widthDp to heightDp
        return true
    }
}
