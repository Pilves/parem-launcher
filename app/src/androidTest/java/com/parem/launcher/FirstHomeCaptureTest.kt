package com.parem.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Fresh prefs, with onboarding marked complete, on a disposable CI emulator. */
@RunWith(AndroidJUnit4::class)
class FirstHomeCaptureTest {
    @Test fun captureFirstHomeAndOneFilledSlot() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = Prefs(context)
        prefs.onboardingVersionSeen = Constants.ONBOARDING_VERSION
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        device.pressHome()
        assertTrue(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
        device.waitForIdle()
        val dir = "/data/local/tmp/parem-diag"
        device.executeShellCommand("mkdir -p $dir")
        device.executeShellCommand("screencap -p $dir/first-run-home.png")
        assertTrue(device.executeShellCommand("ls $dir/first-run-home.png").contains("first-run-home.png"))
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            prefs.setHomeAppName(1, "Settings")
            prefs.setHomeAppPackage(1, "com.android.settings")
            prefs.setHomeAppUser(1, android.os.Process.myUserHandle().toString())
            val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
            (host.childFragmentManager.primaryNavigationFragment as HomeFragment)
                .slotsController!!.populateHomeScreen(false)
        }
        assertTrue("filled slot not shown", device.wait(Until.hasObject(By.text("Settings")), 10_000))
        device.waitForIdle()
        device.executeShellCommand("screencap -p $dir/first-run-one-slot.png")
        assertTrue(device.executeShellCommand("ls $dir/first-run-one-slot.png").contains("first-run-one-slot.png"))
    }
}
