package com.parem.launcher.helper

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.parem.launcher.R
import com.parem.launcher.data.Prefs

class MyAccessibilityService : AccessibilityService() {

    private var lockDescription: String = ""

    override fun onServiceConnected() {
        lockDescription = getString(R.string.lock_layout_description)
        Prefs(applicationContext).apply {
            lockModeOn = true
            lockServiceConnected = true
            // Re-arm the one-time "service is off" explanation for the next time it goes away
            lockServiceOffExplained = false
        }
        super.onServiceConnected()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // No canRetrieveWindowContent, so event.source is null; the event
            // itself carries the clicked view's className and contentDescription.
            if ((event.className == "android.widget.FrameLayout") &&
                (event.contentDescription == lockDescription)
            ) {
                performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            }
        } catch (e: Exception) {
            Log.e("AccessibilityService", "Error handling accessibility event", e)
        }
    }

    override fun onInterrupt() {

    }
}