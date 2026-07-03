package com.parem.launcher.helper

import java.text.Normalizer

/**
 * Matching logic for the app drawer omnibox. Pure functions, no Android deps.
 *
 * An app label matches a query when any of these hold:
 *  - the raw label contains the query (case-insensitive)
 *  - the normalized label (diacritics and separators stripped) contains the
 *    normalized query, so "clock work" matches "clockwork" and "café" matches "cafe"
 *  - the query (2+ chars) is a prefix of the label's word initials, so
 *    "gm" matches "Google Maps"
 *  - each space-separated query token prefix-matches the label's words in
 */
object SearchMatcher {

    /** Precomputed per-label matching key. Build once per app list. */
    data class LabelKey(val normalized: String, val initials: String, val words: List<String>)

    private val DIACRITICS = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val SEPARATORS = Regex("[-_+,.·:&()\\s]+")

    private fun stripDiacritics(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITICS, "")

    fun key(label: String): LabelKey {
        val flat = stripDiacritics(label).lowercase()
        val words = flat.split(SEPARATORS).filter { it.isNotEmpty() }
        return LabelKey(
            normalized = words.joinToString(""),
            initials = words.joinToString("") { it.first().toString() },
            words = words
        )
    }

    fun matches(label: String, key: LabelKey, query: String): Boolean {
        if (label.contains(query, ignoreCase = true)) return true
        val flatQuery = stripDiacritics(query).lowercase()
        val q = flatQuery.replace(SEPARATORS, "")
        if (q.isEmpty()) return true
        if (key.normalized.contains(q)) return true
        if (q.length >= 2 && key.initials.startsWith(q)) return true

        // each query token prefix-matches a label word, in order
        val tokens = flatQuery.split(SEPARATORS).filter { it.isNotEmpty() }
        if (tokens.size >= 2) {
            var wordIndex = 0
            for (token in tokens) {
                while (wordIndex < key.words.size && !key.words[wordIndex].startsWith(token)) wordIndex++
                if (wordIndex >= key.words.size) return false
                wordIndex++
            }
            return true
        }
        return false
    }
}
