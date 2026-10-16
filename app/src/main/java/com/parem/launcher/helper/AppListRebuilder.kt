package com.parem.launcher.helper

import java.text.CollationKey

/**
 * Rebuilds the visible app list from cached raw system-query data (PAREM-117).
 *
 * Prefs-driven state — rename labels and the hidden-apps set — is re-applied
 * on every call, so hide/unhide and rename stay correct without any cache
 * invalidation; only the raw LauncherApps query result is cached (in
 * AppListSource.kt), keyed by PackageChangeTracker's stamp.
 *
 * Android-free on purpose: [U] stands in for UserHandle so this is testable
 * on the JVM.
 */
object AppListRebuilder {

    /** The hidden-apps set entry for one app in one profile: `package|userString`. */
    fun hiddenKey(packageName: String, userString: String): String = "$packageName|$userString"

    /**
     * Upgrades a hidden-apps set written before profiles were keyed: a bare
     * package name becomes [hiddenKey] for the personal profile, an entry
     * that already carries a profile is kept as is.
     */
    fun upgradeHiddenApps(hiddenApps: Set<String>, personalUserString: String): Set<String> =
        hiddenApps.mapTo(mutableSetOf()) { if (it.contains("|")) it else hiddenKey(it, personalUserString) }

    /** One launcher activity exactly as the system reported it at query time. */
    data class RawApp<U>(
        val label: String,
        val key: CollationKey?,
        val packageName: String,
        val activityClassName: String?,
        val firstInstallTime: Long,
        val user: U,
        val userString: String,
        val isPrivate: Boolean = false,
    )

    /** A raw app that passed filtering, plus its per-call computed fields. */
    data class Entry<U>(
        val shownLabel: String,
        val isNew: Boolean,
        val app: RawApp<U>,
    )

    fun <U> rebuild(
        rawApps: List<RawApp<U>>,
        ownPackageName: String,
        personalUser: U,
        hiddenApps: Set<String>,
        includeRegularApps: Boolean,
        includeHiddenApps: Boolean,
        includePrivate: Boolean,
        privateLocked: Boolean,
        renameLabel: (String) -> String,
        now: Long,
        newAppWindowMillis: Long,
    ): List<Entry<U>> {
        val result = mutableListOf<Entry<U>>()
        for (app in rawApps) {
            if (app.packageName == ownPackageName) continue
            if (app.isPrivate) {
                // Private Space apps ignore the hidden set, so the hidden list
                // (and a hide/unhide of the main-profile copy) never reaches them
                if (privateLocked || !includePrivate || !includeRegularApps) continue
            } else {
                val hidden = hiddenApps.contains(hiddenKey(app.packageName, app.userString))
                if (hidden && !includeHiddenApps) continue
                if (!hidden && !includeRegularApps) continue
            }
            result.add(
                Entry(
                    shownLabel = if (app.user == personalUser && !app.isPrivate) renameLabel(app.packageName).ifBlank { app.label } else app.label,
                    isNew = (now - app.firstInstallTime) < newAppWindowMillis,
                    app = app,
                )
            )
        }
        // Sorted by the original label's collation key, same as the live path
        // always did — a renamed app keeps its original position. Private
        // apps form their own section after every regular one.
        result.sortWith(compareBy({ it.app.isPrivate }, { it.app.key }))
        return result
    }
}
