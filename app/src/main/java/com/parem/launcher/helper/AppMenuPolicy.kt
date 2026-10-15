package com.parem.launcher.helper

/** Package-only settings must never operate on a different profile's copy. */
object AppMenuPolicy {
    enum class Action { INFO, RENAME, LIMIT, PAUSE, GRAYSCALE, HIDE, SHOW, UNINSTALL }

    fun actions(
        personal: Boolean,
        privateApp: Boolean,
        hidden: Boolean,
        home: Boolean,
        hasLimit: Boolean,
        uninstallable: Boolean,
    ): List<Action> = buildList {
        add(Action.INFO)
        if (privateApp) return@buildList
        if (!hidden) {
            if (personal || home) add(Action.RENAME)
            if (personal) {
                add(Action.LIMIT)
                if (hasLimit) add(Action.PAUSE)
                add(Action.GRAYSCALE)
            }
        }
        add(if (hidden) Action.SHOW else Action.HIDE)
        if (!home && personal && uninstallable) add(Action.UNINSTALL)
    }
}
