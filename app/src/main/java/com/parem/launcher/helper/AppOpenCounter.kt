package com.parem.launcher.helper

import android.content.Context
import java.time.LocalDate

/**
 * Counts how many times each app was launched today (via this launcher).
 * The count resets on the first launch of a new day. Shown next to usage
 * time in the app drawer — "how often" is often more telling than "how long".
 */
object AppOpenCounter {

    private const val PREFS_NAME = "com.parem.launcher"
    private const val KEY_DAY = "OPEN_COUNTS_DAY"
    private const val KEY_COUNTS = "OPEN_COUNTS"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, 0)

    fun increment(context: Context, packageName: String) {
        if (packageName.isBlank()) return
        val p = prefs(context)
        val today = LocalDate.now().toEpochDay()
        val counts = if (p.getLong(KEY_DAY, -1L) == today)
            parse(p.getString(KEY_COUNTS, "") ?: "")
        else
            mutableMapOf()
        counts[packageName] = (counts[packageName] ?: 0) + 1
        p.edit()
            .putLong(KEY_DAY, today)
            .putString(KEY_COUNTS, counts.entries.joinToString(",") { "${it.key}=${it.value}" })
            .apply()
    }

    fun getCounts(context: Context): Map<String, Int> {
        val p = prefs(context)
        if (p.getLong(KEY_DAY, -1L) != LocalDate.now().toEpochDay()) return emptyMap()
        return parse(p.getString(KEY_COUNTS, "") ?: "")
    }

    private fun parse(raw: String): MutableMap<String, Int> {
        if (raw.isBlank()) return mutableMapOf()
        val map = mutableMapOf<String, Int>()
        for (entry in raw.split(",")) {
            val sep = entry.lastIndexOf('=')
            if (sep <= 0) continue
            val n = entry.substring(sep + 1).toIntOrNull() ?: continue
            map[entry.substring(0, sep)] = n
        }
        return map
    }
}
