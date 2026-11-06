package com.parem.launcher.helper

/**
 * Matches app shortcuts (LauncherApps manifest/dynamic/pinned) against the
 * drawer query (M4-WP14). Android-free so it is testable on the JVM.
 *
 * A shortcut is matched on its own label; the app label only joins in for a
 * multi-word query ("maps home"), so a single word never matches across the
 * boundary ("sho" is not "Maps Home"). A query that matches the app label
 * alone stays an app search and lists none of that app's shortcuts.
 */
object ShortcutMatcher {

    /** What the system returns, and the only thing that is cached. */
    data class Raw(val packageName: String, val userKey: String, val id: String, val shortLabel: String, val rank: Int) {
        val appKey: String get() = appKey(packageName, userKey)
    }

    data class Entry(
        val raw: Raw,
        val appLabel: String,
        val appKey: SearchMatcher.LabelKey,
        val shortKey: SearchMatcher.LabelKey,
        val combinedKey: SearchMatcher.LabelKey,
    ) {
        val combinedLabel: String get() = "$appLabel ${raw.shortLabel}"
    }

    private const val MIN_QUERY = 3

    fun appKey(packageName: String, userKey: String) = "$packageName|$userKey"

    /**
     * Joins [raw] with the drawer's displayed (renamed) labels, keyed by
     * [appKey]. A shortcut whose app isn't listed (hidden, locked private,
     * other user) is dropped.
     */
    fun entries(raw: List<Raw>, appLabels: Map<String, String>): List<Entry> = raw.mapNotNull { r ->
        val appLabel = appLabels[r.appKey] ?: return@mapNotNull null
        Entry(r, appLabel, SearchMatcher.key(appLabel), SearchMatcher.key(r.shortLabel), SearchMatcher.key("$appLabel ${r.shortLabel}"))
    }

    fun filter(entries: List<Entry>, query: String, limit: Int = 5): List<Entry> {
        val queryKey = SearchMatcher.key(query)
        if (queryKey.normalized.length < MIN_QUERY) return emptyList()
        val multiToken = queryKey.words.size >= 2
        return entries.asSequence()
            .filter { e ->
                (SearchMatcher.matches(e.raw.shortLabel, e.shortKey, query) ||
                    (multiToken && SearchMatcher.matches(e.combinedLabel, e.combinedKey, query))) &&
                    !SearchMatcher.matches(e.appLabel, e.appKey, query)
            }
            .sortedWith(compareBy<Entry, String>(String.CASE_INSENSITIVE_ORDER) { it.appLabel }.thenBy { it.raw.rank })
            .take(limit)
            .toList()
    }
}
