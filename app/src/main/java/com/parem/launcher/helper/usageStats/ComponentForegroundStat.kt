package com.parem.launcher.helper.usageStats

/**
 * Object representing a timespan that an application was in the foreground.
 *
 * @property beginTime The start of the timespan, in milliseconds since the epoch.
 * @property endTime The end of the timespan, in milliseconds since the epoch.
 * @property packageName The package name of the application.
 */
data class ComponentForegroundStat(
    val beginTime: Long,
    val endTime: Long,
    val packageName: String
)
