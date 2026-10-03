package com.parem.launcher.ui

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import com.parem.launcher.MainActivity
import com.parem.launcher.R
import com.parem.launcher.listener.DeviceAdmin

/**
 * Play's prominent disclosure for the lock accessibility service. Every route
 * that would open accessibility settings shows this first; accepting is the
 * affirmative consent, so nothing is stored.
 *
 * Exactly one of [onAccept] / [onDecline] runs per sheet. The dismiss listener
 * fires after every option tap (BottomSheetMenu dismisses first, Dialog posts
 * the listener), so [decided] keeps a swipe-away from also declining after accept.
 * [onUseAdmin], when given, adds the device-admin fallback (M4-WP18); picking it
 * runs neither of the other two.
 */
fun showLockConsent(context: Context, onAccept: () -> Unit, onDecline: () -> Unit, onUseAdmin: (() -> Unit)? = null) {
    var decided = false
    var body = context.getString(R.string.lock_consent_body)
    // Sideloaded installs (GitHub releases) get a greyed-out switch on 13+
    if (Build.VERSION.SDK_INT >= 33)
        body += "\n\n" + context.getString(R.string.lock_consent_restricted)
    val menu = BottomSheetMenu(context)
        .title(context.getString(R.string.double_tap_lock))
        .message(body)
        .option(context.getString(R.string.lock_consent_accept)) {
            decided = true
            onAccept()
        }
    if (onUseAdmin != null)
        menu.option(context.getString(R.string.lock_admin_option)) {
            decided = true
            onUseAdmin()
        }
    menu.option(context.getString(R.string.lock_consent_decline), dimmed = true) {
            decided = true
            onDecline()
        }
        .onDismiss { if (!decided) onDecline() }
        .show()
}

/**
 * Shown once when the lock service worked here before and Android has since
 * turned it off. Unlike [showLockConsent], dismissing leaves the gesture set:
 * the user already chose double-tap lock, Android undid it. It still carries
 * the full disclosure and an explicit accept, because it routes into
 * accessibility settings (M2-WP5: consent before every such route).
 */
fun showLockServiceOff(context: Context, onAccept: () -> Unit, onUseAdmin: (() -> Unit)? = null) {
    var body = context.getString(R.string.lock_service_off_body) +
        "\n\n" + context.getString(R.string.lock_consent_body)
    if (Build.VERSION.SDK_INT >= 33)
        body += "\n\n" + context.getString(R.string.lock_consent_restricted)
    val menu = BottomSheetMenu(context)
        .title(context.getString(R.string.lock_service_off_title))
        .message(body)
        .option(context.getString(R.string.lock_consent_accept), onClick = onAccept)
    if (onUseAdmin != null)
        menu.option(context.getString(R.string.lock_admin_option), onClick = onUseAdmin)
    menu.option(context.getString(R.string.lock_consent_decline), dimmed = true) {}
        .show()
}

/**
 * Opens the system device-admin activation screen for the lock fallback. The
 * PIN tradeoff is in [R.string.admin_permission_message], which the system shows
 * next to its own "Activate" button. [onResult] gets whether it was activated.
 */
fun requestLockAdmin(activity: MainActivity, onResult: (Boolean) -> Unit) {
    val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(activity, DeviceAdmin::class.java))
        .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, activity.getString(R.string.admin_permission_message))
    activity.onAdminResult = onResult
    activity.enableAdminLauncher.launch(intent)
}
