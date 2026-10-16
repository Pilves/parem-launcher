package com.parem.launcher.helper

/**
 * Whether the launch drawer's empty result should offer the hidden-apps list
 * (M4-WP45). Android-free so the gating is testable on the JVM.
 */
object HiddenAppHint {

    /**
     * True when [query] found no visible row but would match a hidden app's
     * label. The hint never says which app, and while Private Space is locked
     * it is skipped entirely so an empty result stays indistinguishable from a
     * locked one.
     */
    fun show(query: String, visibleRows: Int, privateSpaceLocked: Boolean, hiddenLabels: List<String>): Boolean {
        if (query.isBlank() || visibleRows > 0 || privateSpaceLocked) return false
        return hiddenLabels.any { SearchMatcher.matches(it, SearchMatcher.key(it), query) }
    }
}
