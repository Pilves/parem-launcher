package com.parem.launcher.helper

/** The "k<sep>v,k<sep>v" format the widget and usage-cache prefs are stored in. */
object CsvPairs {
    /**
     * Splits [raw] on ',' and each entry on its first [sep]. Entries without a
     * key before the separator are dropped. [trim] strips the spaces around key
     * and value; the usage cache never trimmed, so it passes false.
     */
    fun parse(raw: String, sep: Char, trim: Boolean = true): List<Pair<String, String>> =
        raw.split(",").mapNotNull { entry ->
            val i = entry.indexOf(sep)
            if (i <= 0) return@mapNotNull null
            val key = entry.substring(0, i)
            val value = entry.substring(i + 1)
            if (trim) key.trim() to value.trim() else key to value
        }
}
