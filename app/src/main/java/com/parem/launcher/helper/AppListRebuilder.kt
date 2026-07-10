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

    /** One launcher activity exactly as the system reported it at query time. */
    data class RawApp<U>(
        val label: String,
        val key: CollationKey?,
        val packageName: String,
        val activityClassName: String?,
        val firstInstallTime: Long,
        val user: U,
        val userString: String,
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
        hiddenApps: Set<String>,
        includeRegularApps: Boolean,
        includeHiddenApps: Boolean,
        renameLabel: (String) -> String,
        now: Long,
        newAppWindowMillis: Long,
    ): List<Entry<U>> {
        val result = mutableListOf<Entry<U>>()
        for (app in rawApps) {
            if (app.packageName == ownPackageName) continue
            val hidden = hiddenApps.contains(app.packageName + "|" + app.userString)
            if (hidden && !includeHiddenApps) continue
            if (!hidden && !includeRegularApps) continue
            result.add(
                Entry(
                    shownLabel = renameLabel(app.packageName).ifBlank { app.label },
                    isNew = (now - app.firstInstallTime) < newAppWindowMillis,
                    app = app,
                )
            )
        }
        // Sorted by the original label's collation key, same as the live path
        // always did — a renamed app keeps its original position.
        result.sortWith(compareBy { it.app.key })
        return result
    }
}
