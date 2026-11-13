package com.parem.launcher.helper

/** Reject known invisible-only labels without changing characters in visible names. */
object HomeLabel {
    fun hasVisibleContent(value: String): Boolean = value.codePoints().anyMatch { codePoint ->
        !Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint) &&
            !Character.isISOControl(codePoint) && Character.getType(codePoint) != Character.FORMAT.toInt() &&
            codePoint !in 0xFE00..0xFE0F && codePoint !in 0xE0100..0xE01EF &&
            codePoint !in 0x180B..0x180D && codePoint != 0x180F && codePoint != 0x034F &&
            codePoint != 0x115F && codePoint != 0x1160 && codePoint != 0x3164 &&
            codePoint != 0xFFA0 && codePoint != 0x2800
    }
}
