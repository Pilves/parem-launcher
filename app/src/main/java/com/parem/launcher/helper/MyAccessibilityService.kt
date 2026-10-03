package com.parem.launcher.helper

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import com.parem.launcher.R
import com.parem.launcher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    private var lockDescription: String = ""

    override fun onServiceConnected() {
        lockDescription = getString(R.string.lock_layout_description)
        Prefs(applicationContext).lockModeOn = true
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // No canRetrieveWindowContent, so event.source is null; the event
            // itself carries the clicked view's className and contentDescription.
            if ((event.className == "android.widget.FrameLayout") &&
                (event.contentDescription == lockDescription)
            ) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
                }
            }
        } catch (e: Exception) {
            Log.e("AccessibilityService", "Error handling accessibility event", e)
        }
    }

    override fun onInterrupt() {

    }
}