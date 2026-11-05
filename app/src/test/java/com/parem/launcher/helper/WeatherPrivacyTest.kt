package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class WeatherPrivacyTest {

    @Test
    fun roundForRequest_snapsToTheNearestStep() {
        assertEquals("59.45", WeatherPrivacy.roundForRequest(59.43696))
        assertEquals("24.75", WeatherPrivacy.roundForRequest(24.75353))
        assertEquals("58.40", WeatherPrivacy.roundForRequest(58.38062))
    }

    @Test
    fun roundForRequest_movesTwoPlacesInOneCellToTheSameValue() {
        assertEquals(WeatherPrivacy.roundForRequest(59.431), WeatherPrivacy.roundForRequest(59.469))
    }

    @Test
    fun roundForRequest_handlesNegativesAndWholeNumbers() {
        assertEquals("-33.85", WeatherPrivacy.roundForRequest(-33.86785))
        assertEquals("10.00", WeatherPrivacy.roundForRequest(10.0))
    }

    @Test
    fun roundForRequest_tinyValuesStayPlainDecimals() {
        assertEquals("0.00", WeatherPrivacy.roundForRequest(0.00001))
        assertEquals("0.00", WeatherPrivacy.roundForRequest(-0.00001))
    }

    @Test
    fun roundForRequest_ignoresCommaDecimalLocales() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("59.45", WeatherPrivacy.roundForRequest(59.43696))
        } finally {
            Locale.setDefault(original)
        }
    }
}
