package com.parem.launcher.helper

/** Dreams are screensavers, not foreground app use, regardless of their package. */
object UsageEventFilter {
    fun isDream(className: String?): Boolean = className == "android.service.dreams.DreamActivity"

    fun mayInferForeground(packageName: String, dreamPackages: Set<String>): Boolean =
        packageName !in dreamPackages
}
