package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class WeatherPrivacyTest {

    @Test
    fun roundForRequest_keepsTwoDecimals() {
        assertEquals("59.44", WeatherPrivacy.roundForRequest(59.43696))
        assertEquals("24.75", WeatherPrivacy.roundForRequest(24.75353))
    }

    @Test
    fun roundForRequest_handlesNegativesAndWholeNumbers() {
        assertEquals("-33.87", WeatherPrivacy.roundForRequest(-33.86785))
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
            assertEquals("59.44", WeatherPrivacy.roundForRequest(59.43696))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun keepsPlace_onlyWhileSomethingUsesIt() {
        assertTrue(WeatherPrivacy.keepsPlace(weatherOn = true, sunriseTheme = false))
        assertTrue(WeatherPrivacy.keepsPlace(weatherOn = false, sunriseTheme = true))
        assertFalse(WeatherPrivacy.keepsPlace(weatherOn = false, sunriseTheme = false))
    }
}
