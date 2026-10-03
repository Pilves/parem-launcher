package com.parem.launcher.helper

/**
 * Row layout of the app drawer around Private Space (M4-WP6). Android-free:
 * [T] stands in for AppModel so this is testable on the JVM.
 */
object DrawerRows {

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
        isShortcut: (T) -> Boolean = { false },
    ): List<T> {
        val (shortcutRows, appRows) = apps.partition(isShortcut)
        val (privateRows, regular) = appRows.partition(isPrivate)
        return buildList {
            addAll(regular)
            header?.let { add(it) }
            addAll(privateRows)
            addAll(shortcutRows)
            padding?.let { add(it) }
        }
    }
}
