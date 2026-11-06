package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModeChangeTest {

    // Configuration.UI_MODE_NIGHT_YES / _NO and AppCompatDelegate.MODE_NIGHT_YES / _NO
    private val night = 0x20
    private val day = 0x10
    private val themeDark = 2
    private val themeLight = 1

    @Test
    fun systemDefault_restylesWhenNightBitsFlip() {
        assertTrue(NightModeChange.needsRestyle(day, night, NightModeChange.FOLLOW_SYSTEM))
        assertTrue(NightModeChange.needsRestyle(night, day, NightModeChange.FOLLOW_SYSTEM))
    }

    @Test
    fun systemDefault_staysPutWhenNightBitsAreUnchanged() {
        assertFalse(NightModeChange.needsRestyle(night, night, NightModeChange.FOLLOW_SYSTEM))
        assertFalse(NightModeChange.needsRestyle(day, day, NightModeChange.FOLLOW_SYSTEM))
    }

    @Test
    fun fixedTheme_neverRestyles() {
        assertFalse(NightModeChange.needsRestyle(day, night, themeDark))
        assertFalse(NightModeChange.needsRestyle(night, day, themeLight))
    }
}
