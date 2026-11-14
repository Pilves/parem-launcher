package com.parem.launcher

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.TextScale
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Separate capture workflow configures a disposable 320dp-wide emulator. */
@RunWith(AndroidJUnit4::class)
class LargeTextSmallPhoneCaptureTest {
    @Test fun captureOnboardingAndFourHomeSlots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("smallPhoneCapture") == "true")
        val context = instrumentation.targetContext
        check(context.getSharedPreferences(Prefs.PREFS_NAME, 0).edit().clear().commit())
        val device = UiDevice.getInstance(instrumentation)
        val out = "/data/local/tmp/parem-large"
        device.executeShellCommand("mkdir -p $out")
        fun capture(name: String) { device.executeShellCommand("screencap -p $out/$name.png") }
        fun id(resource: Int) = context.resources.getResourceName(resource)
        fun waitFor(resource: Int) = device.wait(Until.findObject(By.res(id(resource))), 10_000)
        fun clickAfterScrolling(target: Int) {
            var button = device.findObject(By.res(id(target)))
            repeat(5) {
                if (button == null) {
                    device.swipe(device.displayWidth / 2, device.displayHeight * 4 / 5,
                        device.displayWidth / 2, device.displayHeight / 3, 20)
                    device.waitForIdle()
                    button = device.findObject(By.res(id(target)))
                }
            }
            assertNotNull("onboarding action unreachable: ${id(target)}", button)
            capture(if (target == R.id.btnDone) "onboarding-calm-bottom" else "onboarding-home-bottom")
            button!!.click()
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertEquals(TextScale.LARGE, Prefs(it).textSizeScale, 0.001f) }
            assertNotNull("first onboarding screen missing", waitFor(R.id.homeScreen))
            capture("onboarding-home")
            clickAfterScrolling(R.id.btnNotNow)
            assertNotNull("Calm onboarding screen missing", waitFor(R.id.calmScreen))
            capture("onboarding-calm-top")
            clickAfterScrolling(R.id.btnDone)
            assertNotNull("home missing", waitFor(R.id.homeAppsLayout))
            scenario.onActivity { activity ->
                val prefs = Prefs(activity)
                assertEquals(4, prefs.homeAppsNum)
                for (slot in 1..4) {
                    prefs.setHomeAppName(slot, "Example $slot")
                    prefs.setHomeAppUrl(slot, "https://example.com/$slot")
                }
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment)!!
                (host.childFragmentManager.primaryNavigationFragment as HomeFragment)
                    .slotsController!!.populateHomeScreen(false)
            }
            device.waitForIdle()
            SystemClock.sleep(500)
            capture("home-four-slots")
            for (slot in 1..4) {
                assertTrue("default slot $slot is not visible", device.hasObject(By.text("Example $slot")))
            }
        }
    }
}
