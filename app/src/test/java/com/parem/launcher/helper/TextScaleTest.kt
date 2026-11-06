package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class TextScaleTest {

    private val eps = 0.0001f

    @Test
    fun storedScale_mapsToNearestStep() {
        assertEquals(TextScale.SMALLER, TextScale.nearestStep(0.6f), eps)
        assertEquals(TextScale.SMALLER, TextScale.nearestStep(0.9f), eps)
        assertEquals(TextScale.DEFAULT, TextScale.nearestStep(1f), eps)
        assertEquals(TextScale.LARGE, TextScale.nearestStep(1.1f), eps)
        assertEquals(TextScale.LARGE, TextScale.nearestStep(1.2f), eps)
        assertEquals(TextScale.LARGER, TextScale.nearestStep(1.3f), eps)
        assertEquals(TextScale.LARGER, TextScale.nearestStep(5f), eps)
    }

    @Test
    fun effective_multipliesSystemScale() {
        assertEquals(1.5f, TextScale.effective(1.5f, TextScale.DEFAULT), eps)
        assertEquals(1.3f, TextScale.effective(1f, TextScale.LARGER), eps)
    }

    @Test
    fun effective_neverBelowFloor() {
        assertEquals(0.85f, TextScale.effective(0.85f, TextScale.SMALLER), eps)
    }

    @Test
    fun effective_extraStepCannotPassTwo_butSystemAboveTwoIsKept() {
        assertEquals(2f, TextScale.effective(2f, TextScale.LARGER), eps)
        assertEquals(2.2f, TextScale.effective(2.2f, TextScale.DEFAULT), eps)
        assertEquals(2.2f, TextScale.effective(2.2f, TextScale.LARGER), eps)
    }
}
