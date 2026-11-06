package com.parem.launcher.helper

/**
 * Value labels for the wellbeing settings rows. "Off" must never hide an armed
 * feature (a focus schedule, automatic grayscale) or a missing permission that
 * silently disables one (app limits without usage access).
 */
object WellbeingRowState {

    enum class Row { OFF, ON, SCHEDULED, AUTOMATIC, SET_UP, CONFIGURE, NEEDS_ACCESS }

    fun focus(active: Boolean, scheduleEnabled: Boolean, hasWindows: Boolean): Row = when {
        active -> Row.ON
        scheduleEnabled && hasWindows -> Row.SCHEDULED
        else -> Row.OFF
    }

    fun grayscale(granted: Boolean, manual: Boolean, onFocus: Boolean, onLimit: Boolean): Row = when {
        !granted -> Row.SET_UP
        manual -> Row.ON
        onFocus || onLimit -> Row.AUTOMATIC
        else -> Row.OFF
    }

    /** Limits count time through usage access; without it no limit can ever fire. */
    fun appLimits(usageAccess: Boolean): Row =
        if (usageAccess) Row.CONFIGURE else Row.NEEDS_ACCESS
}
