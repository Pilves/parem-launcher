package com.parem.launcher.helper

/**
 * Android-free rule for gating a web open (website shortcut, omnibox search,
 * search gesture): a web open is not an app launch, so it is gated only on the
 * app that will actually handle it.
 */
object WebLaunchTarget {

    /** The resolver's answer when no default handler is set: the system chooser. */
    const val CHOOSER = "android"

    /**
     * The package the launch gate runs for, or "" when there is no single app to
     * gate: no handler, or the chooser (focus pins a whitelisted browser later,
     * in MainViewModel.launchUrl; limits cannot apply to a chooser).
     */
    fun gatedPackage(handler: String?): String =
        if (handler.isNullOrEmpty() || handler == CHOOSER) "" else handler
}
