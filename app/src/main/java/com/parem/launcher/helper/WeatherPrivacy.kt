package com.parem.launcher.helper

import java.math.BigDecimal
import java.math.RoundingMode

object WeatherPrivacy {

    // Play counts a location as approximate from 3 km²; this step is about 16 km²
    // at 59°N and stays above 3 km² up to about 84°.
    private val STEP = BigDecimal("0.05")

    /** Nearest 0.05°, always with a dot and never in exponent form, whatever the phone's locale. */
    fun roundForRequest(coordinate: Double): String =
        BigDecimal.valueOf(coordinate).divide(STEP, 0, RoundingMode.HALF_UP).multiply(STEP).toPlainString()
}
