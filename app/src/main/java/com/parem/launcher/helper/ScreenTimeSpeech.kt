package com.parem.launcher.helper

/**
 * What TalkBack should say for the home screen-time text. "1m" is read as
 * "one metre", so the view gets a spoken form built from these parts.
 */
object ScreenTimeSpeech {

    sealed interface Spoken
    data object UnderAMinute : Spoken
    /** Zero-valued parts are left out, except "0 minutes" when nothing was used. */
    data class Duration(val hours: Long?, val minutes: Long?) : Spoken

    fun of(timeSpentMs: Long): Spoken {
        if (timeSpentMs in 1 until 60_000) return UnderAMinute
        val totalMinutes = timeSpentMs.coerceAtLeast(0) / 60_000
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return Duration(
            hours = hours.takeIf { it > 0 },
            minutes = minutes.takeIf { it > 0 || hours == 0L },
        )
    }
}
