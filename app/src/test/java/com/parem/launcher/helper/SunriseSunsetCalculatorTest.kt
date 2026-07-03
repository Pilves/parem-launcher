package com.parem.launcher.helper

import java.time.LocalDate
import java.time.LocalTime
import java.util.TimeZone
import org.junit.Assert.*
import org.junit.BeforeClass
import org.junit.Test

class SunriseSunsetCalculatorTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Tallinn"))
        }
    }

    @Test
    fun calculate_tallinn_summerSolstice() {
        val date = LocalDate.of(2026, 6, 21)
        val (sunrise, sunset) = SunriseSunsetCalculator.calculate(59.437, 24.754, date)

        assertTrue("Sunrise should be before 06:00", sunrise.isBefore(LocalTime.of(6, 0)))
        assertTrue("Sunset should be after 20:00", sunset.isAfter(LocalTime.of(20, 0)))
        assertTrue("Sunrise should be before sunset", sunrise.isBefore(sunset))
    }

    @Test
    fun calculate_tallinn_winterSolstice() {
        val date = LocalDate.of(2026, 12, 21)
        val (sunrise, sunset) = SunriseSunsetCalculator.calculate(59.437, 24.754, date)

        assertTrue("Sunrise should be after 07:00", sunrise.isAfter(LocalTime.of(7, 0)))
        assertTrue("Sunset should be before 17:00", sunset.isBefore(LocalTime.of(17, 0)))
    }

    @Test
    fun calculate_equator_versionalEquinox() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        val date = LocalDate.of(2026, 3, 21)
        val (sunrise, sunset) = SunriseSunsetCalculator.calculate(0.0, 0.0, date)

        val sunriseBefore = sunrise.isBefore(LocalTime.of(7, 0))
        val sunriseAfter = sunrise.isAfter(LocalTime.of(5, 0))
        assertTrue("Sunrise should be between 05:00 and 07:00", sunriseBefore && sunriseAfter)

        val sunsetBefore = sunset.isBefore(LocalTime.of(19, 0))
        val sunsetAfter = sunset.isAfter(LocalTime.of(17, 0))
        assertTrue("Sunset should be between 17:00 and 19:00", sunsetBefore && sunsetAfter)

        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Tallinn"))
    }

    @Test
    fun calculate_polarNight() {
        val date = LocalDate.of(2026, 12, 21)
        val (sunrise, sunset) = SunriseSunsetCalculator.calculate(78.0, 15.0, date)

        assertEquals(LocalTime.of(10, 0), sunrise)
        assertEquals(LocalTime.of(14, 0), sunset)
    }

    @Test
    fun calculate_midnightSun() {
        val date = LocalDate.of(2026, 6, 21)
        val (sunrise, sunset) = SunriseSunsetCalculator.calculate(78.0, 15.0, date)

        assertEquals(LocalTime.of(2, 0), sunrise)
        assertEquals(LocalTime.of(22, 0), sunset)
    }
}
