package com.parem.launcher.helper

/**
 * MainActivity handles uiMode itself (manifest configChanges), so a system
 * dark-mode flip never recreates it. Decides when it must do so by hand.
 */
object NightModeChange {
    // Mirrors AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM; helper/ stays Android-free
    const val FOLLOW_SYSTEM = -1

    /**
     * True when the night bits differ and the app theme follows the system.
     * [oldNight] / [newNight] are uiMode already masked with UI_MODE_NIGHT_MASK.
     */
    fun needsRestyle(oldNight: Int, newNight: Int, appTheme: Int): Boolean =
        appTheme == FOLLOW_SYSTEM && oldNight != newNight
}
