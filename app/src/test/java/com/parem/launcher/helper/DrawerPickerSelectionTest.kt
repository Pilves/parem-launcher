package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DrawerPickerSelectionTest {
    private val personal = DrawerPickerSelection.Identity("example.app", "Main", "personal")
    private val work = DrawerPickerSelection.Identity("example.app", "Main", "work")
    private val otherActivity = DrawerPickerSelection.Identity("example.app", "Other", "personal")
    private val rows = listOf(personal, work, otherActivity)

    @Test fun matchesPackageActivityAndProfileWithoutGuessingAmbiguousLegacyValues() {
        assertEquals(1, DrawerPickerSelection.uniqueIndex(work, rows, "personal"))
        assertEquals(2, DrawerPickerSelection.uniqueIndex(otherActivity, rows, "personal"))
        assertEquals(0, DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("example.app", "Main", ""), rows, "personal"))
        assertNull(DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("example.app", "Main", ""), listOf(work), "personal"))
        assertNull(DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("example.app", "", "personal"), rows, "personal"))
        assertEquals(0, DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("example.app", "Main", ""), listOf(personal), "personal"))
        assertNull(DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("", null, null), rows, "personal"))
        assertNull(DrawerPickerSelection.uniqueIndex(DrawerPickerSelection.Identity("missing", null, null), rows, "personal"))
    }
}
