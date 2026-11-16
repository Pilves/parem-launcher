package com.parem.launcher.helper

/**
 * Finds the settings row a drawer query names (M4-WP17). Pure, no Android
 * deps: [Row.anchor] is an opaque view id the settings screen scrolls to.
 */
object SettingsSearch {

    data class Row(val title: String, val anchor: Int, val aliases: List<String> = emptyList()) {
        // Keyed once per row instead of on every keystroke; outside the
        // constructor, so equality and copy() are unchanged
        internal val keys: List<SearchMatcher.LabelKey> by lazy { (listOf(title) + aliases).map(SearchMatcher::key) }
    }

    // Shorter queries are mostly the start of an app name; a settings tip on
    // every two-letter prefix would be noise.
    const val MIN_QUERY_LENGTH = 3

    /**
     * The best row for [query], or null. A title that starts with the query
     * beats one where the query's words prefix-match the title's words in
     * order ("keyboard" → "Auto-show keyboard"); ties keep [rows] order.
     */
    fun match(query: String, rows: List<Row>): Row? {
        val tokens = SearchMatcher.key(query).words
        val flat = tokens.joinToString("")
        if (flat.length < MIN_QUERY_LENGTH) return null
        var best: Row? = null
        var bestScore = Int.MAX_VALUE
        for (row in rows) {
            val score = row.keys.mapNotNull { key ->
                when {
                    key.normalized.startsWith(flat) -> 0
                    wordsInOrder(tokens, key.words) -> 1
                    else -> null
                }
            }.minOrNull() ?: continue
            if (score < bestScore) {
                best = row
                bestScore = score
            }
        }
        return best
    }

    /** Precompute the same title/alias keys used by match for the auto-launch guard. */
    fun prefixKeys(rows: List<Row>): List<SearchMatcher.LabelKey> = rows.flatMap { it.keys }

    /** Protect even short queries without changing the minimum length of visible suggestions. */
    fun isPrefix(query: String, keys: List<SearchMatcher.LabelKey>): Boolean {
        if (query.startsWith(" ")) return false
        val tokens = SearchMatcher.key(query).words
        val flat = tokens.joinToString("")
        return flat.isNotEmpty() && keys.any { key ->
            key.normalized.startsWith(flat) || wordsInOrder(tokens, key.words)
        }
    }

    /** Settings prefixes yield to a matching app's own label or word prefix. */
    fun holdsAutoLaunch(query: String, appLabel: String, keys: List<SearchMatcher.LabelKey>): Boolean {
        if (!isPrefix(query, keys)) return false
        val normalized = SearchMatcher.key(query).normalized
        val label = SearchMatcher.key(appLabel)
        return !(label.normalized.startsWith(normalized) || label.words.any { it.startsWith(normalized) })
    }

    private fun wordsInOrder(tokens: List<String>, words: List<String>): Boolean {
        var i = 0
        for (token in tokens) {
            while (i < words.size && !words[i].startsWith(token)) i++
            if (i == words.size) return false
            i++
        }
        return true
    }
}
