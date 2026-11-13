package com.parem.launcher.helper

/** Pull distance in pixels; only a released finger can dismiss the drawer. */
class DrawerPull(private val threshold: Float) {
    var distance = 0f
        private set

    fun pull(delta: Float): Boolean {
        val before = distance
        distance = (distance + delta).coerceAtLeast(0f)
        return before < threshold && distance >= threshold
    }

    fun release(fingerReleased: Boolean): Boolean {
        val close = fingerReleased && distance >= threshold
        distance = 0f
        return close
    }
}
