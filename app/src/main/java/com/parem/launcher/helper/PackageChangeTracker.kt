package com.parem.launcher.helper

import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import java.util.concurrent.atomic.AtomicLong

/**
 * One process-wide [LauncherApps.Callback] that turns every package change
 * (install, uninstall, update, profile availability, suspension) into a bump
 * of a monotonic stamp (PAREM-117).
 *
 * Caches of package-derived data record the stamp at fill time and are only
 * trusted while it still equals [stamp] — see the raw app-list snapshot in
 * AppListSource.kt. Nothing rebuilds eagerly; a stale cache is simply skipped
 * on the next read.
 *
 * Registered from the activity-scoped MainViewModel (init/onCleared) with the
 * app context, so it lives exactly as long as anything consuming the caches.
 */
object PackageChangeTracker {

    private val stamp = AtomicLong(0L)
    private var callback: LauncherApps.Callback? = null
    private var registeredWith: LauncherApps? = null

    fun stamp(): Long = stamp.get()

    fun register(context: Context) {
        synchronized(this) {
            if (callback != null) return
            // Changes during an unregistered window (activity gone, process
            // kept warm) were missed — invalidate anything cached before it.
            stamp.incrementAndGet()
            val launcherApps = context.applicationContext
                .getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            val cb = object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) = bump()
                override fun onPackageAdded(packageName: String?, user: UserHandle?) = bump()
                override fun onPackageChanged(packageName: String?, user: UserHandle?) = bump()
                override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = bump()
                override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = bump()
                override fun onPackagesSuspended(packageNames: Array<out String>?, user: UserHandle?) = bump()
                override fun onPackagesUnsuspended(packageNames: Array<out String>?, user: UserHandle?) = bump()
            }
            launcherApps.registerCallback(cb)
            callback = cb
            registeredWith = launcherApps
        }
    }

    fun unregister() {
        synchronized(this) {
            callback?.let { registeredWith?.unregisterCallback(it) }
            callback = null
            registeredWith = null
        }
    }

    private fun bump() {
        stamp.incrementAndGet()
        // An updated app may ship a new icon; both icon caches key on package
        // name alone and can't tell, so drop them wholesale (cheap, rare).
        IconPackManager.clearCache()
        AppIconCache.clear()
    }
}
