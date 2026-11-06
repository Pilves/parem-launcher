package com.parem.launcher.ui.settings

import android.view.View
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.parem.launcher.R

// DESIGN 6.3: a settings row is one spoken node. The XML marks the container
// screenReaderFocusable with its label as contentDescription and silences the
// label and value children, so the row itself must carry the value as state.

/** A row whose tap flips it reads "label, switch, on/off". */
internal fun View.bindSwitchRow(on: Boolean) {
    ViewCompat.setStateDescription(this, context.getString(if (on) R.string.on else R.string.off))
    ViewCompat.setAccessibilityDelegate(this, object : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.Switch"
            info.isCheckable = true
            info.isChecked = on
        }
    })
}

/** A row that opens a picker or sheet reads "label, current value"; no role until UX-12 rebuilds it. */
internal fun View.bindValueRow(value: CharSequence?) {
    ViewCompat.setStateDescription(this, value)
}
