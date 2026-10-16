package com.parem.launcher.helper

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate

/**
 * MainActivity handles uiMode itself (manifest configChanges), so a system
 * dark-mode flip never recreates it. Decides when it must do so by hand.
 */
object NightModeChange {
    /** True when the night bits differ and the app theme follows the system. */
    fun needsRestyle(oldUiMode: Int, newUiMode: Int, appTheme: Int): Boolean =
        appTheme == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM &&
            (oldUiMode and Configuration.UI_MODE_NIGHT_MASK) != (newUiMode and Configuration.UI_MODE_NIGHT_MASK)
}
