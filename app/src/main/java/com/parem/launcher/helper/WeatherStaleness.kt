package com.parem.launcher.helper

/**
 * Pure classification of how old a cached weather reading is, so a fetch
 * failure doesn't silently keep showing an arbitrarily stale temperature.
 *
 * Thresholds per PAREM-106 sign-off:
 * - younger than 3h: FRESH (shown normally)
 * - 3h-24h: STALE (shown dimmed)
 * - older than 24h: EXPIRED (hidden entirely)
 */
object WeatherStaleness {

    const val STALE_THRESHOLD_MS = 3 * 60 * 60 * 1000L
    const val EXPIRED_THRESHOLD_MS = 24 * 60 * 60 * 1000L

    enum class Level { FRESH, STALE, EXPIRED }

    /**
     * @param ageMs age of the cached reading in milliseconds, or null if the
     * age is unknown (e.g. a cache written before this timestamp existed).
     * Unknown age is treated conservatively as STALE, not FRESH.
     */
    fun classify(ageMs: Long?): Level {
        if (ageMs == null || ageMs < 0) return Level.STALE
        return when {
            ageMs < STALE_THRESHOLD_MS -> Level.FRESH
            ageMs < EXPIRED_THRESHOLD_MS -> Level.STALE
            else -> Level.EXPIRED
        }
    }
}
