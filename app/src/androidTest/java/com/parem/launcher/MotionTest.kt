package com.parem.launcher

import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parem.launcher.ui.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Animation scale is restored even when an assertion fails; the emulator is disposable. */
@RunWith(AndroidJUnit4::class)
class MotionTest {
    @Test fun animatorScaleZeroLeavesFeedbackInstant() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val original = device.executeShellCommand("settings get global animator_duration_scale").trim()
        try {
            device.executeShellCommand("settings put global animator_duration_scale 0")
            instrumentation.runOnMainSync {
                val view = View(instrumentation.targetContext)
                Motion.press(view)
                Motion.stagger(listOf(view))
                Motion.copied(view)
                assertNull(view.stateListAnimator)
                assertEquals(1f, view.alpha, 0f)
                assertEquals(1f, view.scaleX, 0f)
                assertEquals(1f, view.scaleY, 0f)
            }
        } finally {
            if (original == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $original")
        }
    }
}
