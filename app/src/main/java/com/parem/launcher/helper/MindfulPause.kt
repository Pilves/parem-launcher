package com.parem.launcher.helper

/**
 * Android-free logic for the mindful pause: a short, cancellable wait before a
 * limited ("bad habit") app opens. Storage lives in [AppLimitManager]; the sheet
 * in ui/BadHabitDialogs.
 */
object MindfulPause {

    const val DEFAULT_DELAY_SECONDS = 5

    /** What a gated launch shows before the app opens. */
    enum class Gate { LAUNCH, LIMIT_WARNING, PAUSE }

    /**
     * The pause replaces the limit warning rather than stacking a second sheet
     * on it; the over-limit line moves into the pause sheet instead.
     */
    fun decide(pauseEnabled: Boolean, overLimit: Boolean): Gate = when {
        pauseEnabled -> Gate.PAUSE
        overLimit -> Gate.LIMIT_WARNING
        else -> Gate.LAUNCH
    }

    /** Whole seconds left on the countdown, rounded up so "1" shows until the wait is over. */
    fun secondsRemaining(elapsedMs: Long, delaySeconds: Int = DEFAULT_DELAY_SECONDS): Int {
        val msLeft = delaySeconds * 1000L - elapsedMs.coerceAtLeast(0L)
        if (msLeft <= 0L) return 0
        return ((msLeft + 999L) / 1000L).toInt()
    }

    /** Delay until [secondsRemaining] next changes, so the countdown redraws once a second (e-ink). */
    fun msUntilNextTick(elapsedMs: Long, delaySeconds: Int = DEFAULT_DELAY_SECONDS): Long {
        val msLeft = delaySeconds * 1000L - elapsedMs.coerceAtLeast(0L)
        if (msLeft <= 0L) return 0L
        val rem = msLeft % 1000L
        return if (rem == 0L) 1000L else rem
    }

    fun parse(csv: String?): Set<String> =
        csv.orEmpty().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    fun serialize(packages: Set<String>): String = packages.sorted().joinToString(",")
}
