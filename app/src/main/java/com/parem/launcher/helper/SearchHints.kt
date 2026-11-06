package com.parem.launcher.helper

object SearchHints {
    const val MAX_OPENS = 20

    // Blank once used up: the examples teach the search field, and then they are noise.
    fun forOpen(opens: Int, hints: Array<String>): String =
        if (opens < MAX_OPENS) hints[opens % hints.size] else ""
}
