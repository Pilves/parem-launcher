package com.parem.launcher.helper

import java.math.BigDecimal
import java.math.RoundingMode

object WeatherPrivacy {

    /** Two decimals, always with a dot and never in exponent form, whatever the phone's locale. */
    fun roundForRequest(coordinate: Double): String =
        BigDecimal.valueOf(coordinate).setScale(2, RoundingMode.HALF_UP).toPlainString()
}
