package com.parem.launcher.helper

/** Filled slots retain their indices; at most the first empty slot is an add target. */
object HomeSlotRows {
    enum class Row { FILLED, HINT, HIDDEN }

    fun render(occupied: List<Boolean>, locked: Boolean): List<Row> {
        val firstEmpty = if (locked) -1 else occupied.indexOf(false)
        return occupied.mapIndexed { index, filled ->
            when {
                filled -> Row.FILLED
                index == firstEmpty -> Row.HINT
                else -> Row.HIDDEN
            }
        }
    }

    /** Keep filled rows ahead of the hint if height is constrained. */
    fun fit(rows: List<Row>, capacity: Int): List<Row> {
        val remaining = capacity.coerceAtLeast(0)
        val filled = rows.count { it == Row.FILLED }
        var shownFilled = 0
        return rows.map { row ->
            when (row) {
                Row.FILLED -> if (shownFilled++ < remaining) row else Row.HIDDEN
                Row.HINT -> if (filled < remaining) row else Row.HIDDEN
                Row.HIDDEN -> row
            }
        }
    }
}
