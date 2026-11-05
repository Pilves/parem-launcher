package com.parem.launcher.helper

import android.content.Context

/** The Play build has no in-app pairing: see the `full` source set and docs/design/DECISIONS.md. */
object ChannelFeatures {

    fun pairingPrompt(context: Context): Pair<String, String>? = null

    fun startPairing(context: Context, askPermission: (String) -> Unit) = Unit

    fun onNotificationPermission(context: Context, granted: Boolean) = Unit
}
