package com.parem.launcher.helper

import android.Manifest
import android.content.Context
import android.os.Build
import com.parem.launcher.R

/**
 * What the `full` build (GitHub, F-Droid, IzzyOnDroid) adds to the shared code.
 * The `play` source set has an object of the same name with all of it off, so
 * the pairing code, POST_NOTIFICATIONS and sideload help are absent from that
 * build rather than dead (docs/design/DECISIONS.md).
 */
object ChannelFeatures {

    /** Steps text and button label for the on-phone grant, or null where it can't run. */
    fun pairingPrompt(context: Context): Pair<String, String>? =
        if (GrayscalePairing.isSupported())
            context.getString(R.string.grayscale_grant_wifi) to context.getString(R.string.grayscale_pair_start)
        else null

    fun startPairing(context: Context, askPermission: (String) -> Unit) {
        if (!GrayscalePairing.isSupported()) return
        if (GrayscalePairing.canNotify(context)) GrayscalePairing.start(context)
        else askPermission(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun onNotificationPermission(context: Context, granted: Boolean) {
        if (granted && GrayscalePairing.isSupported()) GrayscalePairing.start(context)
        else context.showToast(R.string.grayscale_pair_needs_notif)
    }

    /** Sideloaded installs get a greyed-out switch on Android 13+; Play installs never do. */
    fun restrictedSettingsNote(context: Context): String? =
        if (Build.VERSION.SDK_INT >= 33) context.getString(R.string.lock_consent_restricted) else null
}
