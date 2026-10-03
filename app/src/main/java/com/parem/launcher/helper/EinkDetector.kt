package com.parem.launcher.helper

import com.parem.launcher.data.Constants

/**
 * Pure e-ink classification from device identity and display capability.
 * Brand list and Hisense model pattern from upstream Olauncher a9da9d4.
 */
object EinkDetector {

    private val einkOnlyBrands = listOf("onyx", "boox", "dasung", "bigme", "boyue", "meebook", "mudita")

    // Hisense also sells LCD phones, so match only their e-ink line
    private val hisenseEinkModel = Regex("\\bA[579]\\b|TOUCH|HI READER")

    fun isEinkBrand(brand: String, manufacturer: String, model: String): Boolean {
        val b = brand.lowercase()
        val m = manufacturer.lowercase()
        if (einkOnlyBrands.any { b.contains(it) || m.contains(it) }) return true
        if (b.contains("hisense") || m.contains("hisense"))
            return hisenseEinkModel.containsMatchIn(model.uppercase())
        return false
    }

    /**
     * [maxSupportedHz] must be the display's highest supported rate, not the current
     * one: adaptive refresh rate (LTPO) panels drop to 1-10 Hz when idle (Olauncher #724).
     */
    fun isEinkRefreshRate(maxSupportedHz: Float): Boolean =
        maxSupportedHz <= Constants.MIN_ANIM_REFRESH_RATE
}
