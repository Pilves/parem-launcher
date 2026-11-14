package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLabelTest {
    @Test fun invisibleOnlyLabelsAreEmpty() {
        for (label in listOf("", " \t\n", "\u00A0\u2007\u202F", "\u200B\u200B", "\u200D\u2060\uFEFF", "\uFE0F", "\u034F", "\u3164", "\u2800", "\uDB40\uDD00")) {
            assertFalse("must reject invisible-only label", HomeLabel.hasVisibleContent(label))
        }
    }

    @Test fun visibleNamesKeepTheirUnicodeSequences() {
        for (label in listOf("abc\u200B", "\u202Bשלום\u202C", "عَرَبِيّ", "e\u0301", "👩‍👩‍👧‍👦", "✈️", "가")) {
            assertTrue("must retain visible label: $label", HomeLabel.hasVisibleContent(label))
        }
    }
}
