package com.parem.launcher.helper

/**
 * Row layout of the app drawer around Private Space (M4-WP6). Android-free:
 * [T] stands in for AppModel so this is testable on the JVM.
 */
object DrawerRows {

    /** Only the launch drawer may expose the entry; hidden locked spaces stay undiscoverable. */
    fun showPrivateEntry(query: String, label: String, locked: Boolean, hiddenWhenLocked: Boolean): Boolean {
        if (locked && hiddenWhenLocked) return false
        if (query.isEmpty()) return true
        if (query.startsWith(" ") || query.startsWith("!")) return false
        val term = query.trim()
        // Require the first complete word: ordinary single-letter app searches
        // must not gain a container row or lose their automatic app launch.
        return term.length >= label.substringBefore(' ').length && label.startsWith(term, ignoreCase = true)
    }

    /**
     * Private apps reach the launch drawer only. Every picker shares the same
     * retained app list, and LiveData replays it to a new observer, so this
     * consumer-side gate is what keeps a private app off home slots, swipe,
     * gestures and folders (trap #3).
     */
    fun <T> gate(apps: List<T>, isLaunchDrawer: Boolean, isPrivate: (T) -> Boolean): List<T> =
        if (isLaunchDrawer) apps else apps.filterNot(isPrivate)

    /**
     * Regular rows, then [header] (if any), then private rows, then shortcut
     * rows (M4-WP14, private parents included), then [padding] (if any). The
     * splits are stable partitions, so a usage sort applied before them is
     * kept within each section, and shortcuts stay under every app row.
     */
    fun <T> decorate(
        apps: List<T>,
        isPrivate: (T) -> Boolean,
        header: T?,
        padding: T?,
        headerFirst: Boolean = false,
        isShortcut: (T) -> Boolean = { false },
    ): List<T> {
        val (shortcutRows, appRows) = apps.partition(isShortcut)
        val (privateRows, regular) = appRows.partition(isPrivate)
        return buildList {
            if (headerFirst) header?.let { add(it) }
            addAll(regular)
            if (!headerFirst) header?.let { add(it) }
            addAll(privateRows)
            addAll(shortcutRows)
            padding?.let { add(it) }
        }
    }
}

/** A result list is usable only after the adapter commits it for this query. */
class DrawerFilterState {
    private var generation = 0L
    private var committedQuery: String? = null
    @Synchronized fun generation(): Long = generation
    @Synchronized fun invalidate(): Long { committedQuery = null; return ++generation }
    @Synchronized fun isCurrent(token: Long): Boolean = generation == token
    @Synchronized fun commit(query: String, token: Long): Boolean {
        if (token != generation) return false
        committedQuery = query
        return true
    }
    @Synchronized fun isReady(query: String): Boolean = committedQuery == query
}
