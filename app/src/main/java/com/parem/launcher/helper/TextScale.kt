package com.parem.launcher.helper

import kotlin.math.abs
import kotlin.math.max

/**
 * Parem's text size is a relative step on top of the system font scale
 * (DESIGN.md 3.1). The system value is never reduced; Parem's extra step may
 * not push the product past 2.0.
 */
object TextScale {
    const val SMALLER = 0.85f
    const val DEFAULT = 1f
    const val LARGE = 1.15f
    const val LARGER = 1.3f

    val STEPS = floatArrayOf(SMALLER, DEFAULT, LARGE, LARGER)

    /** Maps any stored scale (pre-6.0 values ran 0.6..1.3) to the closest step. */
    fun nearestStep(stored: Float): Float = STEPS.minByOrNull { abs(it - stored) } ?: DEFAULT

    fun effective(systemScale: Float, step: Float): Float =
        (systemScale * step).coerceIn(SMALLER, max(systemScale, 2f))
}
