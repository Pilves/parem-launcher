package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class AppPickerSectionsTest {
    @Test fun selectedStayVisibleAndOthersFilterWithoutDuplicates() {
        val entries = AppPickerSections.ordered(listOf(
            AppPickerSections.Entry("c", "Charlie"), AppPickerSections.Entry("a", "Alpha"),
            AppPickerSections.Entry("b", "Beta"), AppPickerSections.Entry("a", "Duplicate"),
        ))
        val filtered = AppPickerSections.visible(entries, setOf("c", "missing"), "alp")
        assertEquals(listOf("c"), filtered.selected.map { it.id })
        assertEquals(listOf("a"), filtered.remaining.map { it.id })
        val reset = AppPickerSections.visible(entries, setOf("c", "missing"), "")
        assertEquals(listOf("a", "b"), reset.remaining.map { it.id })
        assertEquals(listOf("c"), reset.selected.map { it.id })
    }
}
