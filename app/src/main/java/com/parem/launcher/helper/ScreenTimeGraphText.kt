package com.parem.launcher.helper

/** Android-free text and hit-test logic for the 7-day screen time graph. */
object ScreenTimeGraphText {

    /** Compact label drawn above a bar: "2.3h" or "35m". */
    fun barLabel(millis: Long): String {
        val hours = millis / 3_600_000.0
        return if (hours >= 1.0) {
            String.format("%.1fh", hours)
        } else {
            "${millis / 60_000}m"
        }
    }

    /**
     * Duration as whole hours and minutes for screen readers, which read
     * "2.3h" as a letter. Sub-minute usage rounds to zero minutes so a day
     * with no data still gets a spoken value.
     */
    fun spokenDuration(millis: Long): Pair<Long, Long> {
        val totalMinutes = millis / 60_000
        return Pair(totalMinutes / 60, totalMinutes % 60)
    }

    /**
     * Index of the day column under [x], or null outside the columns. Columns
     * split the width inside [sidePadding] evenly so bars can be thin while
     * the whole column stays tappable.
     */
    fun columnIndex(x: Float, width: Int, sidePadding: Float, count: Int): Int? {
        if (count <= 0) return null
        val slotWidth = (width - sidePadding * 2) / count
        if (slotWidth <= 0f) return null
        val index = ((x - sidePadding) / slotWidth).toInt()
        return index.takeIf { x >= sidePadding && it in 0 until count }
    }
}
