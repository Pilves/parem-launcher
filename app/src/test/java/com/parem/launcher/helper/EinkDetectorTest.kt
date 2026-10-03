package com.parem.launcher.helper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EinkDetectorTest {

    private val einkBrands = listOf("onyx", "boox", "dasung", "bigme", "boyue", "meebook", "mudita")

    @Test
    fun einkBrand_matchesInBrandField() {
        einkBrands.forEach { assertTrue(it, EinkDetector.isEinkBrand(it.uppercase(), "unknown", "X1")) }
    }

    @Test
    fun einkBrand_matchesInManufacturerField() {
        einkBrands.forEach { assertTrue(it, EinkDetector.isEinkBrand("generic", it.uppercase(), "X1")) }
    }

    @Test
    fun hisense_einkModelsMatch() {
        listOf("A5", "A7", "A9", "A9 PRO", "HI READER", "a5 pro cc", "Touch Lite").forEach {
            assertTrue(it, EinkDetector.isEinkBrand("Hisense", "Hisense", it))
        }
    }

    @Test
    fun hisense_lcdModelsDoNotMatch() {
        listOf("H60", "INFINITY H50", "A50", "HLTE").forEach {
            assertFalse(it, EinkDetector.isEinkBrand("Hisense", "Hisense", it))
        }
    }

    @Test
    fun hisense_matchesViaManufacturerOnly() {
        assertTrue(EinkDetector.isEinkBrand("generic", "HISENSE", "A9"))
    }

    @Test
    fun mainstreamBrandsDoNotMatch() {
        assertFalse(EinkDetector.isEinkBrand("samsung", "samsung", "SM-S918B"))
        assertFalse(EinkDetector.isEinkBrand("google", "Google", "Pixel 8"))
        // Hisense model pattern applies only to Hisense
        assertFalse(EinkDetector.isEinkBrand("google", "Google", "A9"))
    }

    @Test
    fun refreshRate_atThresholdIsEink() {
        assertTrue(EinkDetector.isEinkRefreshRate(10f))
    }

    @Test
    fun refreshRate_aboveThresholdIsNotEink() {
        assertFalse(EinkDetector.isEinkRefreshRate(10.1f))
        assertFalse(EinkDetector.isEinkRefreshRate(60f))
    }
}
