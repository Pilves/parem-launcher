package com.parem.launcher.helper.notifications

/**
 * Decides which notifications the quiet-notification listener moves out of
 * the shade. Android-free: the listener resolves the platform facts (launcher
 * entry, media session, default dialer/SMS) and passes plain values in.
 */
object QuietFilter {

    /** How long a hidden notification stays snoozed before the system reposts it. */
    const val SNOOZE_MS = 8 * 60 * 60 * 1000L

    /**
     * System and emergency packages that are never hidden. The cell-broadcast
     * packages are listed because some OEMs give "Emergency alerts" a launcher
     * icon, which the launcher-activity rule alone would let through.
     */
    val NEVER_TOUCH = setOf(
        "android",
        "com.android.systemui",
        "com.android.phone",
        "com.android.cellbroadcastreceiver",
        "com.google.android.cellbroadcastreceiver",
        "com.android.cellbroadcastreceiver.module",
    )

    // Literal values of Notification.CATEGORY_CALL / ALARM / NAVIGATION / TRANSPORT,
    // so this object stays free of android.* imports.
    private val EXEMPT_CATEGORIES = setOf("call", "alarm", "navigation", "transport")

    /** [NEVER_TOUCH] plus our own package and the current default dialer and SMS app. */
    fun alwaysAllowed(ownPackage: String, defaultDialer: String?, defaultSms: String?): Set<String> =
        NEVER_TOUCH + listOfNotNull(ownPackage, defaultDialer, defaultSms).filter { it.isNotEmpty() }

    /** True only when every rule lets the notification be hidden. */
    fun shouldSilence(
        pkg: String,
        category: String?,
        isOngoing: Boolean,
        isClearable: Boolean,
        isGroupSummary: Boolean,
        isMedia: Boolean,
        hasLauncherActivity: Boolean,
        allowed: Set<String>,
        alwaysAllowed: Set<String>,
    ): Boolean {
        if (!hasLauncherActivity) return false
        if (pkg in allowed || pkg in alwaysAllowed) return false
        if (category != null && category in EXEMPT_CATEGORIES) return false
        if (isMedia) return false
        if (isOngoing || !isClearable) return false
        if (isGroupSummary) return false
        return true
    }
}
