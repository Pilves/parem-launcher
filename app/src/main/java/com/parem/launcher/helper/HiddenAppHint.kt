package com.parem.launcher.helper

/**
 * Whether the launch drawer's empty result should offer the hidden-apps list
 * (M4-WP45). Android-free so the gating is testable on the JVM.
 */
object HiddenAppHint {

    enum class EmptyTip { HIDDEN_APPS, SEARCH, NO_APPS }

    fun emptyTip(query: String, privateSpaceLocked: Boolean, hiddenLabels: List<String>?): EmptyTip = when {
        show(query, 0, privateSpaceLocked, hiddenLabels.orEmpty()) -> EmptyTip.HIDDEN_APPS
        canSearchWeb(query, hiddenLabels) -> EmptyTip.SEARCH
        else -> EmptyTip.NO_APPS
    }

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
    private var stamp = 0L
    var labels: List<String>? = null
        private set

    fun begin(hiddenKeys: Set<String>, packageStamp: Long = 0L): Long {
        keys = hiddenKeys.toSet()
        stamp = packageStamp
        labels = if (keys.isEmpty()) emptyList() else null
        return ++generation
    }

    fun isCurrent(token: Long): Boolean = token == generation

    fun isStale(currentKeys: Set<String>, packageStamp: Long): Boolean =
        currentKeys != keys || packageStamp != stamp

    fun labelsFor(currentKeys: Set<String>, packageStamp: Long): List<String>? =
        if (currentKeys.isEmpty()) emptyList() else if (isStale(currentKeys, packageStamp)) null else labels

    fun complete(token: Long, currentKeys: Set<String>, result: List<String>?, packageStamp: Long = 0L) {
        if (isCurrent(token) && !isStale(currentKeys, packageStamp)) labels = result
    }
}
