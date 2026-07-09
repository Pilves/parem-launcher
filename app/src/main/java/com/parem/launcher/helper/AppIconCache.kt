package com.parem.launcher.helper

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Bounded cache for default app icons: `PackageManager.getApplicationIcon`
 * decodes a fresh Drawable on every call, which is too slow to repeat on
 * every drawer-row bind and every home-screen resume.
 *
 * Stores [Drawable.ConstantState], never Drawable instances — bounds and
 * callbacks are per-instance, so handing one instance to multiple views
 * would corrupt rendering; each caller gets its own via `newDrawable()`.
 *
 * Uses the main-profile icon for all profiles, same as the call sites it
 * replaced. An app updating its icon mid-process would go stale here, so
 * PackageChangeTracker calls [clear] on every package change (PAREM-117).
 */
object AppIconCache {

    private val cache = LruCache<String, Drawable.ConstantState>(128)

    fun get(context: Context, packageName: String): Drawable? {
        cache.get(packageName)?.let { return it.newDrawable(context.resources) }
        return try {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            drawable.constantState?.let { cache.put(packageName, it) }
            drawable
        } catch (_: Exception) {
            null
        }
    }

    fun clear() {
        cache.evictAll()
    }
}
