package com.parem.launcher.helper

/**
 * Android-free rule for a web open (website shortcut, omnibox search, search
 * gesture): focus, the limit and the pause are checked against the app that
 * receives the intent when that app is known.
 */
object WebLaunchTarget {

    /** The resolver's answer when no default handler is set: the system chooser. */
    const val CHOOSER = "android"

    /**
     * The package to gate and pin the intent on, "" when there is no single app
     * to gate, or null when focus blocks the open. A default handler is used as
     * is. Without one the system chooser shows and nothing is gated — the
     * receiver is unknown, an accepted gap — unless focus is active ([allowed]
     * non-null): then the first allowed [candidates] entry stands in for the
     * chooser so a blocked browser is never offered.
     */
    fun target(handler: String?, candidates: List<String>, allowed: ((String) -> Boolean)?): String? {
        val isAllowed = allowed ?: { _: String -> true }
        if (!handler.isNullOrEmpty() && handler != CHOOSER) return if (isAllowed(handler)) handler else null
        if (allowed == null || candidates.isEmpty()) return ""
        return candidates.firstOrNull(allowed)
    }
}
