package com.parem.launcher.helper

/**
 * Whether the launch drawer's empty result should offer the hidden-apps list
 * (M4-WP45). Android-free so the gating is testable on the JVM.
 */
object HiddenAppHint {

    /** Fail closed until hidden labels are loaded; never rely on an asynchronous tip for privacy. */
    fun canSearchWeb(query: String, hiddenLabels: List<String>?): Boolean =
        hiddenLabels != null && !show(query, 0, privateSpaceLocked = false, hiddenLabels = hiddenLabels)

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

/** Keeps an older asynchronous hidden-app query from authorizing a web search. */
class HiddenLabelLoad {
    private var generation = 0L
    private var keys: Set<String> = emptySet()
    var labels: List<String>? = null
        private set

    fun begin(hiddenKeys: Set<String>): Long {
        keys = hiddenKeys.toSet()
        labels = if (keys.isEmpty()) emptyList() else null
        return ++generation
    }

    fun isCurrent(token: Long): Boolean = token == generation

    fun complete(token: Long, currentKeys: Set<String>, result: List<String>?) {
        if (token == generation && currentKeys == keys) labels = result
    }
}
