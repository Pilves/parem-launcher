package com.parem.launcher.helper

import java.time.Instant

/**
 * Builds the plain-text crash report the user sees and sends themselves
 * (M3-WP11). Only the stack trace and build/device identifiers go in: no
 * prefs, app lists, logcat or contacts.
 */
object CrashReportFormatter {

    /** Keeps the share intent far below the 1 MB binder transaction limit. */
    const val MAX_BYTES = 64 * 1024
    const val TRUNCATED = "… (truncated)"

    fun format(
        throwable: Throwable,
        versionName: String,
        versionCode: Int,
        sdkInt: Int,
        manufacturer: String,
        model: String,
        threadName: String,
        timeMs: Long,
    ): String {
        val header = buildString {
            appendLine("Parem $versionName ($versionCode)")
            appendLine("Android API $sdkInt, $manufacturer $model")
            appendLine("Thread: $threadName")
            appendLine("Time: ${Instant.ofEpochMilli(timeMs)}")
            appendLine()
        }
        return cap(header + throwable.stackTraceToString())
    }

    /** Cuts on a line boundary so the report never ends mid-frame. */
    internal fun cap(text: String, maxBytes: Int = MAX_BYTES): String {
        if (text.toByteArray().size <= maxBytes) return text
        val budget = maxBytes - TRUNCATED.toByteArray().size
        val out = StringBuilder()
        var used = 0
        for (line in text.lineSequence()) {
            val size = line.toByteArray().size + 1
            if (used + size > budget) break
            out.append(line).append('\n')
            used += size
        }
        return out.append(TRUNCATED).toString()
    }
}
