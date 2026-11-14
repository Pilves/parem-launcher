package com.parem.launcher.helper

/** Widget size estimates for providers that declare no target cells. */
object WidgetPickerLabels {

    /** Android's published cell formula, for a provider that declares no target cell size. */
    fun cells(sizeDp: Int): Int = ((sizeDp + 30) / 70).coerceAtLeast(1)
}
