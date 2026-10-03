package com.parem.launcher.helper

/**
 * Pure rules for system grayscale (the Secure-settings daltonizer). Parem only
 * ever undoes what it did itself: it saves the user's colour-correction state
 * before writing, restores it exactly, and backs off for good when the user
 * changes colour correction behind its back. See docs/design/M4-WP5.md.
 */
object GrayscalePolicy {

    /** The daltonizer as the system reports it. Monochromacy is mode 0. */
    data class State(val enabled: Boolean, val mode: Int) {
        val isGrey get() = enabled && mode == MODE_MONOCHROMACY
    }

    const val MODE_MONOCHROMACY = 0
    val GREY = State(enabled = true, mode = MODE_MONOCHROMACY)

    enum class Action {
        /** Save the current state, write grey, mark applied. */
        APPLY_AND_SAVE,
        /** Write the saved state back exactly, clear applied and saved. */
        RESTORE,
        /** The user changed colour correction: forget applied/saved/manual, suppress, write nothing. */
        USER_OVERRIDE,
        /** The trigger that was suppressed has ended; the next one may apply again. */
        CLEAR_SUPPRESSION,
        NONE,
    }

    fun desired(manual: Boolean, onFocus: Boolean, focusActive: Boolean, onLimit: Boolean, limitOverride: Boolean, appForeground: Boolean): Boolean =
        manual || (onFocus && focusActive) || (onLimit && limitOverride) || appForeground

    /** Per-app marks (M4-WP21), stored as a CSV of package names like the mindful-pause list. */
    fun isMarked(csv: String?, pkg: String): Boolean = pkg in MindfulPause.parse(csv)

    fun toggle(csv: String?, pkg: String): String {
        val marked = MindfulPause.parse(csv)
        return MindfulPause.serialize(if (pkg in marked) marked - pkg else marked + pkg)
    }

    fun plan(desired: Boolean, applied: Boolean, suppressed: Boolean, current: State): Action = when {
        applied && !current.isGrey -> Action.USER_OVERRIDE
        !desired && suppressed -> Action.CLEAR_SUPPRESSION
        desired && suppressed -> Action.NONE
        // Already grey without Parem: the user's own setting, never taken over
        desired && !applied -> if (current.isGrey) Action.NONE else Action.APPLY_AND_SAVE
        !desired && applied -> Action.RESTORE
        else -> Action.NONE
    }
}
