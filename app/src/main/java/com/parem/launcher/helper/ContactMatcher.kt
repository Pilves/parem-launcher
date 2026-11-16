package com.parem.launcher.helper

/**
 * Matching and ranking for omnibox contact search. Pure functions, no Android
 * deps (the actual provider query lives in [ContactSearchManager]).
 *
 * Name matching reuses [SearchMatcher] so contacts behave like app labels
 * (diacritics-insensitive, initials, word-prefix tokens). On top of that this
 * adds a query gate (so the omnibox doesn't surface contacts for numbers,
 * operators, or one-character fragments) and a rank that floats whole-name and
 * word-prefix matches above looser initials/substring hits.
 */
object ContactMatcher {

    data class Contact(val name: String, val number: String) {
        // Computed eagerly so the cost lands at load time (Dispatchers.IO in
        // ContactSearchManager) — matching runs per keystroke on the main
        // thread and must not re-normalize or re-lowercase every contact's name there.
        val searchKey: SearchMatcher.LabelKey = SearchMatcher.key(name)
        internal val sortName: String = name.lowercase()
    }

    // Stable: contacts that compare equal keep their list order
    private val ORDER = compareBy<Pair<Contact, Int>>({ it.second }, { it.first.sortName })

    private val LETTER = Regex("\\p{L}")

    /**
     * A query is worth searching contacts for only when it has 2+ characters and
     * at least one letter. This keeps pure numbers (dial), operators (calc), and
     * single-key fragments from ever surfacing a contact.
     */
    fun looksLikeContactQuery(query: String): Boolean {
        val q = query.trim()
        return q.length >= 2 && LETTER.containsMatchIn(q)
    }

    /** Contacts whose name matches [query], best matches first, capped at [limit]. */
    fun match(query: String, contacts: List<Contact>, limit: Int = 5): List<Contact> =
        ranked(query, contacts).sortedWith(ORDER).map { it.first }.take(limit)

    /**
     * The first contact [match] would return, without sorting every match: the
     * omnibox shows only one. minWith keeps the earliest of equal contacts, as
     * the stable sort does.
     */
    fun best(query: String, contacts: List<Contact>): Contact? =
        ranked(query, contacts).minWithOrNull(ORDER)?.first

    private fun ranked(query: String, contacts: List<Contact>): List<Pair<Contact, Int>> {
        if (!looksLikeContactQuery(query)) return emptyList()
        val q = SearchMatcher.Query(query.trim())
        val normalizedQuery = q.key.normalized
        return contacts.mapNotNull { contact ->
            val key = contact.searchKey
            if (!SearchMatcher.matches(contact.name, key, q)) return@mapNotNull null
            contact to rank(key, normalizedQuery)
        }
    }

    private fun rank(key: SearchMatcher.LabelKey, nq: String): Int {
        return when {
            nq.isEmpty() -> 3
            key.normalized.startsWith(nq) -> 0        // whole name starts with the query
            key.words.any { it.startsWith(nq) } -> 1  // some name word starts with the query
            else -> 2                                 // looser: initials / substring / tokens
        }
    }

}
