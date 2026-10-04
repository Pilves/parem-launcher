package com.parem.launcher.helper

/**
 * The custom accessibility actions home exposes on mainLayout and on each slot.
 * TalkBack, Switch Access and Voice Access consume the raw swipes and the
 * long-press, so every place a gesture leads to needs one of these too (UX-2).
 */
object HomeAccessibilityActions {

    enum class Kind { ALL_APPS, SETTINGS, NOTIFICATIONS, ADD_WIDGET, SWIPE_LEFT_APP, SWIPE_RIGHT_APP, SWIPE_UP_APP }

    data class Action(val kind: Kind, val appName: String = "")

    /** Pass null for a swipe app that isn't configured; see [configuredApp]. */
    fun forView(swipeLeftApp: String?, swipeRightApp: String?, swipeUpApp: String? = null): List<Action> =
        listOfNotNull(
            Action(Kind.ALL_APPS),
            Action(Kind.SETTINGS),
            Action(Kind.NOTIFICATIONS),
            Action(Kind.ADD_WIDGET),
            swipeLeftApp?.let { Action(Kind.SWIPE_LEFT_APP, it) },
            swipeRightApp?.let { Action(Kind.SWIPE_RIGHT_APP, it) },
            swipeUpApp?.let { Action(Kind.SWIPE_UP_APP, it) },
        )

    /**
     * The name to announce for a swipe that opens a chosen app, or null when the
     * swipe is off, does something other than open an app, or has no app picked
     * (it then falls back to camera/dialer, which has no name to announce).
     */
    fun configuredApp(enabled: Boolean, opensApp: Boolean, packageName: String, appName: String): String? =
        if (enabled && opensApp && packageName.isNotEmpty()) appName.ifEmpty { packageName } else null
}
