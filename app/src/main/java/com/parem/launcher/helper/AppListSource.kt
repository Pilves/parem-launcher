package com.parem.launcher.helper

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import com.parem.launcher.BuildConfig
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The app-list source: the raw LauncherApps query and its stamp-keyed snapshot
 * cache (PAREM-117), plus the installed-package and user-handle lookups that
 * ride on the same snapshot.
 */
private val collator: java.text.Collator by lazy { java.text.Collator.getInstance() }

/**
 * Snapshot of the raw LauncherApps query, valid only while [stamp] still
 * equals PackageChangeTracker's current stamp (PAREM-117). Immutable and
 * swapped whole, so concurrent IO readers see a complete snapshot or none.
 */
private class RawAppSnapshot(val stamp: Long, val apps: List<AppListRebuilder.RawApp<UserHandle>>)

@Volatile
private var rawAppSnapshot: RawAppSnapshot? = null

suspend fun getAppsList(
    context: Context,
    prefs: Prefs,
    includeRegularApps: Boolean = true,
    includeHiddenApps: Boolean = false,
): MutableList<AppModel> {
    return withContext(Dispatchers.IO) {
        try {
            if (!prefs.hiddenAppsUpdated) upgradeHiddenApps(prefs)

            // Read the stamp before the live query: a package change landing
            // mid-query then invalidates this snapshot instead of hiding under it.
            val stamp = PackageChangeTracker.stamp()
            val snapshot = rawAppSnapshot
            val rawApps = if (snapshot != null && snapshot.stamp == stamp) {
                snapshot.apps
            } else {
                val (fresh, complete) = queryRawApps(context)
                // Never cache a failed/partial query — the next call must retry live.
                if (complete) rawAppSnapshot = RawAppSnapshot(stamp, fresh)
                fresh
            }

            // Renames, hidden-apps filtering and the isNew window are re-applied
            // per call so prefs changes never need cache invalidation.
            AppListRebuilder.rebuild(
                rawApps,
                ownPackageName = BuildConfig.APPLICATION_ID,
                hiddenApps = prefs.hiddenApps,
                includeRegularApps = includeRegularApps,
                includeHiddenApps = includeHiddenApps,
                renameLabel = { prefs.getAppRenameLabel(it) },
                now = System.currentTimeMillis(),
                newAppWindowMillis = Constants.ONE_HOUR_IN_MILLIS,
            ).mapTo(mutableListOf()) {
                AppModel(it.shownLabel, it.app.key, it.app.packageName, it.app.activityClassName, it.isNew, it.app.user)
            }
        } catch (e: Exception) {
            Log.e("Utils", "Failed to get apps list", e)
            mutableListOf()
        }
    }
}

/**
 * The expensive part: one getActivityList binder IPC per profile. Returns
 * whatever was gathered plus whether the query completed — a partial result
 * is still shown (matching the old behavior) but must not be cached.
 */
private fun queryRawApps(context: Context): Pair<List<AppListRebuilder.RawApp<UserHandle>>, Boolean> {
    val rawApps = mutableListOf<AppListRebuilder.RawApp<UserHandle>>()
    return try {
        val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        for (profile in userManager.userProfiles) {
            for (app in launcherApps.getActivityList(null, profile)) {
                val label = app.label.toString()
                // RuleBasedCollator is not thread-safe, and two callers (e.g. the
                // hide flow's getAppList + getHiddenApps coroutines) can both miss
                // the snapshot and run this query concurrently
                val key = synchronized(collator) { collator.getCollationKey(label) }
                rawApps.add(
                    AppListRebuilder.RawApp(
                        label,
                        key,
                        app.applicationInfo.packageName,
                        app.componentName.className,
                        app.firstInstallTime,
                        profile,
                        profile.toString(),
                    )
                )
            }
        }
        Pair(rawApps, true)
    } catch (e: Exception) {
        Log.e("Utils", "Failed to query apps list", e)
        Pair(rawApps, false)
    }
}

// This is to ensure backward compatibility with older app versions
// which did not support multiple user profiles
private fun upgradeHiddenApps(prefs: Prefs) {
    val hiddenAppsSet = prefs.hiddenApps
    val newHiddenAppsSet = mutableSetOf<String>()
    for (hiddenPackage in hiddenAppsSet) {
        if (hiddenPackage.contains("|")) newHiddenAppsSet.add(hiddenPackage)
        else newHiddenAppsSet.add(hiddenPackage + android.os.Process.myUserHandle().toString())
    }
    prefs.hiddenApps = newHiddenAppsSet
    prefs.hiddenAppsUpdated = true
}

fun isPackageInstalled(context: Context, packageName: String, userString: String): Boolean {
    val launcher = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
    val activityInfo = launcher.getActivityList(packageName, getUserHandleFromString(context, userString))
    if (activityInfo.size > 0) return true
    return false
}

/**
 * Cache-aware [isPackageInstalled] for the home-resume hot path (PAREM-117).
 * A hit in a still-valid raw snapshot answers true with zero binder IPCs;
 * everything else (no snapshot, stale stamp, package absent, odd user string)
 * falls through to the live check, so a home slot is only ever cleared on a
 * live system answer.
 */
fun isPackageInstalledCached(context: Context, packageName: String, userString: String): Boolean {
    val snapshot = rawAppSnapshot
    if (snapshot != null && snapshot.stamp == PackageChangeTracker.stamp() &&
        snapshot.apps.any { it.packageName == packageName && it.userString == userString }
    ) return true
    return isPackageInstalled(context, packageName, userString)
}

fun getUserHandleFromString(context: Context, userHandleString: String): UserHandle {
    val userManager = context.getSystemService(Context.USER_SERVICE) as UserManager
    for (userHandle in userManager.userProfiles) {
        if (userHandle.toString() == userHandleString) {
            return userHandle
        }
    }
    return android.os.Process.myUserHandle()
}
