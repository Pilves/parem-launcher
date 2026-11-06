package com.parem.launcher

import android.app.Dialog
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
                val row = FrameLayout(instrumentation.targetContext)
                val content = View(instrumentation.targetContext)
                row.addView(content)
                Motion.press(row)
                Motion.stagger(listOf(row))
                Motion.copied(row)
                assertNotNull(content.stateListAnimator)
                row.isPressed = true
                content.stateListAnimator?.jumpToCurrentState()
                assertEquals(1f, row.alpha, 0f)
                assertEquals(1f, row.scaleX, 0f)
                assertEquals(1f, content.scaleX, 0f)
                assertEquals(1f, content.scaleY, 0f)
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
                lateinit var row: FrameLayout
                lateinit var content: View
                scenario.onActivity { activity ->
                    row = FrameLayout(activity).apply { isClickable = true }
                    content = View(activity)
                    row.addView(content, FrameLayout.LayoutParams(100, 100))
                    activity.addContentView(row, FrameLayout.LayoutParams(100, 100))
                    Motion.press(row)
                    row.isPressed = true
                }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(1f, content.scaleX, .001f) }
                device.executeShellCommand("settings put global animator_duration_scale 1")
                Thread.sleep(300)
                scenario.onActivity { row.isPressed = false; row.isPressed = true }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(.97f, content.scaleX, .01f); assertEquals(1f, row.scaleX, 0f) }
                device.executeShellCommand("settings put global animator_duration_scale 0")
                Thread.sleep(300)
                scenario.onActivity { row.isPressed = false; row.isPressed = true }
                Thread.sleep(250)
                scenario.onActivity { assertEquals(1f, content.scaleX, .001f); assertEquals(1f, row.scaleX, 0f) }
            }
        } finally {
            originals.forEach { (name, value) ->
                if (value == "null") device.executeShellCommand("settings delete global $name")
                else device.executeShellCommand("settings put global $name $value")
            }
        }
    }


    @Test fun openPopupTracksRuntimeAnimatorScale() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val names = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
        val originals = names.associateWith { device.executeShellCommand("settings get global $it").trim() }
        try {
            names.forEach { device.executeShellCommand("settings put global $it 1") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var dialog: Dialog
                scenario.onActivity { activity ->
                    dialog = Dialog(activity)
                    Motion.showPopup(dialog)
                    assertEquals(R.style.ParemPopupWindowAnimation, dialog.window!!.attributes.windowAnimations)
                }
                device.executeShellCommand("settings put global animator_duration_scale 0")
                val deadline = System.currentTimeMillis() + 5_000
                var animation = -1
                while (System.currentTimeMillis() < deadline && animation != 0) {
                    scenario.onActivity { animation = dialog.window!!.attributes.windowAnimations }
                    if (animation != 0) Thread.sleep(50)
                }
                assertEquals(0, animation)
                device.executeShellCommand("settings put global animator_duration_scale 1")
                val enableDeadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < enableDeadline && animation != R.style.ParemPopupWindowAnimation) {
                    scenario.onActivity { animation = dialog.window!!.attributes.windowAnimations }
                    if (animation != R.style.ParemPopupWindowAnimation) Thread.sleep(50)
                }
                assertEquals(R.style.ParemPopupWindowAnimation, animation)
                scenario.onActivity { dialog.dismiss() }
            }
        } finally {
            originals.forEach { (name, value) ->
                if (value == "null") device.executeShellCommand("settings delete global $name")
                else device.executeShellCommand("settings put global $name $value")
            }
        }
    }

}
