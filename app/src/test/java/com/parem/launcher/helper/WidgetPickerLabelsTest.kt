package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerLabelsTest {

    @Test
    fun cellsFollowAndroidsFormula() {
        assertEquals(1, WidgetPickerLabels.cells(40))
        assertEquals(2, WidgetPickerLabels.cells(110))
        assertEquals(4, WidgetPickerLabels.cells(250))
    }
}
