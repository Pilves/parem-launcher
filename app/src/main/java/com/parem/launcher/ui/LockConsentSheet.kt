package com.parem.launcher.ui

import android.content.Context
import android.os.Build
import com.parem.launcher.R

/**
 * Play's prominent disclosure for the lock accessibility service. Every route
 * that would open accessibility settings shows this first; accepting is the
 * affirmative consent, so nothing is stored.
 *
 * Exactly one of [onAccept] / [onDecline] runs per sheet. The dismiss listener
 * fires after every option tap (BottomSheetMenu dismisses first, Dialog posts
 * the listener), so [decided] keeps a swipe-away from also declining after accept.
 */
fun showLockConsent(context: Context, onAccept: () -> Unit, onDecline: () -> Unit) {
    var decided = false
    var body = context.getString(R.string.lock_consent_body)
    // Sideloaded installs (GitHub releases) get a greyed-out switch on 13+
    if (Build.VERSION.SDK_INT >= 33)
        body += "\n\n" + context.getString(R.string.lock_consent_restricted)
    BottomSheetMenu(context)
        .title(context.getString(R.string.double_tap_lock))
        .message(body)
        .option(context.getString(R.string.lock_consent_accept)) {
            decided = true
            onAccept()
        }
        .option(context.getString(R.string.lock_consent_decline), dimmed = true) {
            decided = true
            onDecline()
        }
        .onDismiss { if (!decided) onDecline() }
        .show()
}
