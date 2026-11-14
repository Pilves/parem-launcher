package com.parem.launcher.helper

import java.text.Collator

/** Keeps selected entries visible while filtering the other choices. */
object AppPickerSections {
    class Entry(val id: String, val label: String) {
        internal val searchKey by lazy { SearchMatcher.key(label) }
    }

    data class Visible(val selected: List<Entry>, val remaining: List<Entry>)

    fun ordered(entries: List<Entry>): List<Entry> = Collator.getInstance().let { collator ->
        entries.distinctBy { it.id }.sortedWith { a, b ->
            collator.compare(a.label, b.label).takeIf { it != 0 } ?: a.id.compareTo(b.id)
        }
    }

    fun visible(entries: List<Entry>, selected: Set<String>, query: String): Visible = Visible(
        selected = entries.filter { it.id in selected },
        remaining = entries.filter { it.id !in selected &&
            (query.isBlank() || SearchMatcher.matches(it.label, it.searchKey, query)) },
    )
}
