package com.parem.launcher.helper

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NightModeChangeTest {

    private val night = Configuration.UI_MODE_NIGHT_YES
    private val day = Configuration.UI_MODE_NIGHT_NO

    @Test
    fun systemDefault_restylesWhenNightBitsFlip() {
        assertTrue(NightModeChange.needsRestyle(day, night, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        assertTrue(NightModeChange.needsRestyle(night, day, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }

    @Test
    fun systemDefault_ignoresOtherUiModeBits() {
        val car = night or Configuration.UI_MODE_TYPE_CAR
        assertFalse(NightModeChange.needsRestyle(night, car, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
        assertFalse(NightModeChange.needsRestyle(day, day, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM))
    }

    @Test
    fun fixedTheme_neverRestyles() {
        assertFalse(NightModeChange.needsRestyle(day, night, AppCompatDelegate.MODE_NIGHT_YES))
        assertFalse(NightModeChange.needsRestyle(night, day, AppCompatDelegate.MODE_NIGHT_NO))
    }
}
