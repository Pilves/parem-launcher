package com.parem.launcher.helper

import android.app.Activity
import android.content.Context

/** The Play build has no in-app pairing or sideload help: see the `full` source set and docs/design/DECISIONS.md. */
object ChannelFeatures {

    fun pairingPrompt(context: Context): Pair<String, String>? = null

    fun startPairing(context: Context, askPermission: (String) -> Unit) = Unit

    fun onNotificationPermission(activity: Activity, granted: Boolean) = Unit

    fun restrictedSettingsNote(context: Context): String? = null
}
