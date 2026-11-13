package com.parem.launcher.helper

object SearchHints {
    const val MAX_OPENS = 20
    private const val GUIDANCE_OPENS = 5

    fun showGuidance(newInstall: Boolean, opens: Int): Boolean =
        newInstall && opens in 0 until GUIDANCE_OPENS

    // Blank once used up: the examples teach the search field, and then they are noise.
    fun forOpen(opens: Int, hints: Array<String>): String =
        if (opens < MAX_OPENS) hints[opens % hints.size] else ""
}
