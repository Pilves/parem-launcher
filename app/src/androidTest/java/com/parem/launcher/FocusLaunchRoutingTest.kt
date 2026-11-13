package com.parem.launcher

import android.app.Instrumentation
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FocusModeManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusLaunchRoutingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun prepare() = Prefs(context).apply {
        firstOpen = false
        onboardingVersionSeen = Constants.ONBOARDING_VERSION
        hideSetDefaultLauncher = true
    }

    private fun waitForHits(monitor: Instrumentation.ActivityMonitor, count: Int): Boolean {
        val end = SystemClock.uptimeMillis() + 3_000
        while (SystemClock.uptimeMillis() < end && monitor.hits < count) SystemClock.sleep(50)
        return monitor.hits >= count
    }

    @Test fun dateTapBlocksUnderFocusAndOpensWhenFocusIsOff() {
        val prefs = prepare()
        prefs.calendarAppPackage = ""
        prefs.dateTimeVisibility = Constants.DateTime.ON
        val intent = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        assertNotNull("calendar fixture must be resolvable", intent.resolveActivity(context.packageManager))
        val filter = IntentFilter(Intent.ACTION_VIEW).apply { addDataScheme("content") }
        val monitor = instrumentation.addMonitor(filter, null, true)
        try {
            FocusModeManager.setWhitelist(context, emptySet())
            FocusModeManager.enable(context, 30)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<View>(R.id.date).performClick() }
                device.waitForIdle()
                assertEquals("blocked date launched an activity", 0, monitor.hits)
                FocusModeManager.disable(context)
                scenario.onActivity { it.findViewById<View>(R.id.date).performClick() }
                assertTrue("control date did not dispatch", waitForHits(monitor, 1))
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            FocusModeManager.disable(context)
        }
    }

    @Test fun cameraGestureIsExemptButCameraAppSlotIsNot() {
        val prefs = prepare()
        val fixturePackage = instrumentation.context.packageName
        prefs.swipeLeftEnabled = true
        prefs.swipeLeftAction = Constants.GestureAction.OPEN_APP
        prefs.appPackageSwipeLeft = ""
        prefs.setHomeAppName(1, "Camera fixture")
        prefs.setHomeAppPackage(1, fixturePackage)
        prefs.setHomeAppActivityClassName(1, LaunchGateFixtureActivity::class.java.name)
        prefs.setHomeAppUser(1, Process.myUserHandle().toString())
        try {
            FocusModeManager.setWhitelist(context, emptySet())
            FocusModeManager.enable(context, 30)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                device.waitForIdle()
                val y = device.displayHeight * 3 / 4
                fun openCamera() {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    assertNotNull("camera handler must be available", resolved)
                    val packageName = resolved!!.activityInfo.packageName
                    device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
                    if (packageName == "android") {
                        val choice = device.wait(Until.findObject(By.text("Focus camera fixture")), 5_000)
                        assertNotNull("camera chooser did not appear", choice)
                        choice.click()
                        device.findObject(By.textMatches("(?i)just once"))?.click()
                        assertTrue("camera fixture did not open", device.wait(Until.hasObject(By.text("Camera fixture activity is open")), 5_000))
                    } else {
                        assertTrue("camera app did not open", device.wait(Until.hasObject(By.pkg(packageName)), 5_000))
                    }
                    device.pressBack()
                    device.waitForIdle()
                }
                openCamera()
                prefs.swipeLeftAction = Constants.GestureAction.OPEN_CAMERA
                openCamera()
                scenario.onActivity { it.findViewById<View>(R.id.homeApp1).performClick() }
                assertTrue("blocked camera app left home", device.wait(Until.hasObject(By.pkg(context.packageName)), 3_000))
                SystemClock.sleep(750)
                assertTrue("blocked camera app opened", !device.hasObject(By.text("Camera fixture activity is open")))
                FocusModeManager.disable(context)
                scenario.onActivity { it.findViewById<View>(R.id.homeApp1).performClick() }
                assertTrue("allowed camera app did not open", device.wait(Until.hasObject(By.text("Camera fixture activity is open")), 5_000))
                device.pressBack()
            }
        } finally {
            FocusModeManager.disable(context)
        }
    }
}
