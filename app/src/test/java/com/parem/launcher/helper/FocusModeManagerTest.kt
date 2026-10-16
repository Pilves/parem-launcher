package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class FocusModeManagerTest {

    @Test
    fun mergeWhitelist_keepsEntriesThePickerCouldNotOffer() {
        // A32: a whitelisted Private Space app is not on offer while the space
        // is locked; saving must not drop it
        val merged = FocusModeManager.mergeWhitelist(
            selected = listOf("com.mail"),
            current = setOf("com.whatsapp", "com.private.bank", "com.mail"),
            offered = setOf("com.mail", "com.whatsapp", "com.maps"),
        )
        assertEquals(setOf("com.mail", "com.private.bank"), merged)
    }

    @Test
    fun mergeWhitelist_freshChoicesWinTheCap() {
        val merged = FocusModeManager.mergeWhitelist(
            selected = listOf("a", "b", "c", "d"),
            current = setOf("x", "y"),
            offered = setOf("a", "b", "c", "d"),
        )
        assertEquals(5, merged.size)
        assertEquals(setOf("a", "b", "c", "d", "x"), merged)
    }
}
