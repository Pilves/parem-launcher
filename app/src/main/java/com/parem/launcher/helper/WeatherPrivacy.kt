package com.parem.launcher.helper

import java.math.BigDecimal
import java.math.RoundingMode

object WeatherPrivacy {

    // This step is about 16 km² at 59°N and above 3 km² through 84°.
    private val STEP = BigDecimal("0.05")

    /** Clamp latitude before rounding; longitude must retain its full range. */
    fun latitudeForRequest(latitude: Double): String = roundForRequest(latitude.coerceIn(-84.0, 84.0))

    /** Nearest 0.05°, always with a dot and never in exponent form, whatever the phone's locale. */
    fun roundForRequest(coordinate: Double): String =
        BigDecimal.valueOf(coordinate).divide(STEP, 0, RoundingMode.HALF_UP).multiply(STEP).toPlainString()
}
