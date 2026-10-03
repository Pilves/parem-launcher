package com.parem.launcher.helper

/**
 * Opt-in omnibox history (M4-WP16): the most recent drawer launches and
 * submitted queries, newest first, shown while the search field is empty.
 * Android-free; storage lives in Prefs (a device-only file, outside both the
 * settings export and cloud backup).
 */
object OmniboxHistory {

    const val MAX_ENTRIES = 5

    sealed interface Entry {
        /** [key] is "package|user", the same shape as hidden-app entries. */
        data class App(val key: String) : Entry
        /** Kept as typed (a leading space means web search), minus trailing blanks. */
        data class Query(val text: String) : Entry
    }

    fun decode(raw: String): List<Entry> = raw.lineSequence().mapNotNull { line ->
        when {
            line.startsWith("a:") && line.length > 2 -> Entry.App(line.substring(2))
            line.startsWith("q:") && line.length > 2 -> Entry.Query(line.substring(2))
            else -> null
        }
    }.toList()

    fun encode(entries: List<Entry>): String = entries.joinToString("\n") {
        when (it) {
            is Entry.App -> "a:${it.key}"
            is Entry.Query -> "q:${it.text}"
        }
    }

    /** Moves [entry] to the front (dropping its older copy) and caps the list. */
    fun push(entries: List<Entry>, entry: Entry, max: Int = MAX_ENTRIES): List<Entry> {
        val clean = when (entry) {
            is Entry.App -> entry
            // One line per entry: a pasted newline must not split it
            is Entry.Query -> Entry.Query(entry.text.replace('\n', ' ').trimEnd())
        }
        if (clean is Entry.Query && clean.text.isBlank()) return entries
        if (clean is Entry.App && clean.key.isBlank()) return entries
        return (listOf(clean) + entries.filter { it != clean }).take(max)
    }
}
