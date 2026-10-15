package com.parem.launcher.helper

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/**
 * Haptics per DESIGN.md section 8: always View.performHapticFeedback with no
 * flags, so the system touch-feedback setting is honoured.
 * CONFIRM and REJECT exist from API 30; minSdk is 29.
 */
object Haptics {

    fun longPress(view: View) {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun confirm(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM
        else HapticFeedbackConstants.KEYBOARD_TAP
        view.performHapticFeedback(constant)
    }

    fun reject(view: View) {
        val constant = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.REJECT
        else HapticFeedbackConstants.LONG_PRESS
        view.performHapticFeedback(constant)
    }
}
