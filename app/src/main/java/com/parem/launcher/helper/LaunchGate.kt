package com.parem.launcher.helper

/** Shared decision for app and resolved-intent launches. */
object LaunchGate {
    enum class Decision { BLOCK, OPEN, LIMIT_WARNING, PAUSE }

    /** Explicit intents identify their receiver without package-visibility queries. */
    fun knownTarget(componentPackage: String?, intentPackage: String?): String? =
        componentPackage?.takeIf { it.isNotBlank() } ?: intentPackage?.takeIf { it.isNotBlank() }

    /** [allowed] is the authoritative current Focus check; never combine separate snapshots. */
    fun decide(allowed: Boolean, gated: Boolean, hasPause: Boolean, overLimit: Boolean): Decision = when {
        !allowed -> Decision.BLOCK
        !gated -> Decision.OPEN
        hasPause -> Decision.PAUSE
        overLimit -> Decision.LIMIT_WARNING
        else -> Decision.OPEN
    }
}
