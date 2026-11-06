package com.parem.launcher

import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parem.launcher.ui.Motion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
                assertNotNull(view.stateListAnimator)
                view.isPressed = true
                view.stateListAnimator.jumpToCurrentState()
                assertEquals(1f, view.alpha, 0f)
                assertEquals(1f, view.scaleX, 0f)
                assertEquals(1f, view.scaleY, 0f)
            }
        } finally {
            if (original == "null") device.executeShellCommand("settings delete global animator_duration_scale")
            else device.executeShellCommand("settings put global animator_duration_scale $original")
        }
    }

    @Test fun pressFeedbackFollowsRuntimeAnimatorScaleChanges() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val names = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val originals = names.associateWith { device.executeShellCommand("settings get global $it").trim() }
        try {
            names.forEach { device.executeShellCommand("settings put global $it 1") }
            device.executeShellCommand("settings put global animator_duration_scale 0")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var view: View
                scenario.onActivity { activity ->
                    view = View(activity)
                    activity.addContentView(view, FrameLayout.LayoutParams(100, 100))
                    Motion.press(view)
                    view.isPressed = true
                }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(1f, view.scaleX, .001f) }
                device.executeShellCommand("settings put global animator_duration_scale 1")
                Thread.sleep(300)
                scenario.onActivity { view.isPressed = false; view.isPressed = true }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(.97f, view.scaleX, .01f) }
                device.executeShellCommand("settings put global animator_duration_scale 0")
                Thread.sleep(300)
                scenario.onActivity { view.isPressed = false; view.isPressed = true }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(1f, view.scaleX, .001f) }
            }
        } finally {
            originals.forEach { (name, value) ->
                if (value == "null") device.executeShellCommand("settings delete global $name")
                else device.executeShellCommand("settings put global $name $value")
            }
        }
    }

}
