package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageEventFilterTest {
    @Test
    fun dreamClassIsExcludedForOpenAndCloseWithoutExcludingNormalClock() {
        val dream = "android.service.dreams.DreamActivity"
        // The same class decision is applied to resumed, paused, stopped and
        // prior-day events before either the aggregator or guardian sees them.
        assertTrue(UsageEventFilter.isDream(dream))
        assertFalse(UsageEventFilter.isDream("com.android.deskclock.DeskClock"))
        assertFalse(UsageEventFilter.isDream(null))
        assertFalse(UsageEventFilter.isDream("other.package.DreamActivity"))
    }

    @Test
    fun fallbackCannotCountForegroundProcessWhoseDreamWasSeen() {
        val dreamPackages = setOf("com.google.android.deskclock", "another.dream")
        assertFalse(UsageEventFilter.mayInferForeground("com.google.android.deskclock", dreamPackages))
        assertFalse(UsageEventFilter.mayInferForeground("another.dream", dreamPackages))
        assertTrue(UsageEventFilter.mayInferForeground("other.clock", dreamPackages))
        assertTrue(UsageEventFilter.mayInferForeground("com.google.android.deskclock", emptySet()))
    }

    @Test
    fun lastActivityClassDistinguishesDreamFromNormalClockUse() {
        val dream = "android.service.dreams.DreamActivity"
        val clock = "com.android.deskclock.DeskClock"
        var active = UsageEventFilter.dreamActiveAfter(false, dream, opening = true)
        assertTrue(active) // dream started before an otherwise event-free query
        active = UsageEventFilter.dreamActiveAfter(active, clock, opening = true)
        assertFalse(active) // normal Clock resumed after the dream
        active = UsageEventFilter.dreamActiveAfter(active, dream, opening = true)
        assertTrue(active)
        active = UsageEventFilter.dreamActiveAfter(active, dream, opening = false)
        assertFalse(active)
    }
}
