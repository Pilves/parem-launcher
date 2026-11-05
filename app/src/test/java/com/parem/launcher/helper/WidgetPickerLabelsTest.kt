package com.parem.launcher.helper

import com.parem.launcher.helper.WidgetPickerLabels.Row
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetPickerLabelsTest {

    @Test
    fun onlySameNameRowsInOneAppNeedASize() {
        val rows = listOf(Row("Clock", "Clock"), Row("Clock", "Clock"), Row("Clock", "Timer"), Row("Notes", "Clock"))
        assertEquals(listOf(true, true, false, false), WidgetPickerLabels.needsSize(rows))
    }

    @Test
    fun cellsFollowAndroidsFormula() {
        assertEquals(1, WidgetPickerLabels.cells(40))
        assertEquals(2, WidgetPickerLabels.cells(110))
        assertEquals(4, WidgetPickerLabels.cells(250))
    }
}
