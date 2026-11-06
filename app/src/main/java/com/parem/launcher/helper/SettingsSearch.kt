package com.parem.launcher.helper

/**
 * Finds the settings row a drawer query names (M4-WP17). Pure, no Android
 * deps: [Row.anchor] is an opaque view id the settings screen scrolls to.
 */
object SettingsSearch {

    data class Row(val title: String, val anchor: Int)

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
            val key = SearchMatcher.key(row.title)
            val score = when {
                key.normalized.startsWith(flat) -> 0
                wordsInOrder(tokens, key.words) -> 1
                else -> continue
            }
            if (score < bestScore) {
                best = row
                bestScore = score
            }
        }
        return best
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
