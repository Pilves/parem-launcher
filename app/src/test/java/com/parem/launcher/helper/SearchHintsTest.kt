package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHintsTest {

    private val hints = arrayOf("2+2", "timer 10m", "5 km to mi")

    @Test
    fun rotatesThroughTheExamples() {
        assertEquals("2+2", SearchHints.forOpen(0, hints))
        assertEquals("timer 10m", SearchHints.forOpen(1, hints))
        assertEquals("2+2", SearchHints.forOpen(3, hints))
    }

    @Test
    fun stopsAfterTheLastCountedOpen() {
        assertEquals("timer 10m", SearchHints.forOpen(SearchHints.MAX_OPENS - 1, hints))
        assertEquals("", SearchHints.forOpen(SearchHints.MAX_OPENS, hints))
        assertEquals("", SearchHints.forOpen(SearchHints.MAX_OPENS + 100, hints))
    }
}
