package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherStalenessTest {

    private val hour = 60 * 60 * 1000L

    @Test
    fun classify_freshJustFetched() {
        assertEquals(WeatherStaleness.Level.FRESH, WeatherStaleness.classify(0L))
    }

    @Test
    fun classify_freshJustUnderThreshold() {
        assertEquals(WeatherStaleness.Level.FRESH, WeatherStaleness.classify(3 * hour - 1))
    }

    @Test
    fun classify_staleAtThreshold() {
        assertEquals(WeatherStaleness.Level.STALE, WeatherStaleness.classify(3 * hour))
    }

    @Test
    fun classify_staleJustUnderExpired() {
        assertEquals(WeatherStaleness.Level.STALE, WeatherStaleness.classify(24 * hour - 1))
    }

    @Test
    fun classify_expiredAtThreshold() {
        assertEquals(WeatherStaleness.Level.EXPIRED, WeatherStaleness.classify(24 * hour))
    }

    @Test
    fun classify_expiredFarPast() {
        assertEquals(WeatherStaleness.Level.EXPIRED, WeatherStaleness.classify(365 * 24 * hour))
    }

    @Test
    fun classify_unknownAgeIsConservativelyStale() {
        assertEquals(WeatherStaleness.Level.STALE, WeatherStaleness.classify(null))
    }

    @Test
    fun classify_negativeAgeIsConservativelyStale() {
        // Clock skew or corrupt timestamp: don't trust it as fresh.
        assertEquals(WeatherStaleness.Level.STALE, WeatherStaleness.classify(-1L))
    }
}
