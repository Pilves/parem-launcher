package com.parem.launcher.helper

/**
 * Android-free rule for a web open (website shortcut, omnibox search, search
 * gesture): it always goes to one known app, so focus, the limit and the pause
 * are checked against the app that actually receives the intent.
 */
object WebLaunchTarget {

    /** The resolver's answer when no default handler is set: the system chooser. */
    const val CHOOSER = "android"

    /**
     * The package to gate and pin the intent on, "" when no app can handle it
     * (nothing to gate; the launch fails with its own toast), or null when focus
     * blocks it. A real default handler is used as is; without one the first
     * focus-allowed [candidates] entry stands in for the chooser.
     */
    fun target(handler: String?, candidates: List<String>, allowed: (String) -> Boolean): String? {
        if (!handler.isNullOrEmpty() && handler != CHOOSER) return if (allowed(handler)) handler else null
        if (candidates.isEmpty()) return ""
        return candidates.firstOrNull(allowed)
    }
}
