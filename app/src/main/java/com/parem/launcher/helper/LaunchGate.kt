package com.parem.launcher.helper

/** Shared decision for app and resolved-intent launches. */
object LaunchGate {
    enum class Decision { BLOCK, OPEN, LIMIT_WARNING, PAUSE }

    fun decide(focusActive: Boolean, allowed: Boolean, gated: Boolean, hasPause: Boolean, overLimit: Boolean): Decision = when {
        focusActive && !allowed -> Decision.BLOCK
        !gated -> Decision.OPEN
        hasPause -> Decision.PAUSE
        overLimit -> Decision.LIMIT_WARNING
        else -> Decision.OPEN
    }
}
