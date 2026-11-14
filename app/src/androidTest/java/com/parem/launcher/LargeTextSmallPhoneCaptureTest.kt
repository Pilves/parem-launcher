package com.parem.launcher

import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
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
import java.io.File

/** Separate capture workflow configures a disposable 320dp-wide emulator. */
@RunWith(AndroidJUnit4::class)
class LargeTextSmallPhoneCaptureTest {
    @Test fun captureOnboardingAndFourHomeSlots() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("smallPhoneCapture") == "true")
        val context = instrumentation.targetContext
        check(context.getSharedPreferences(Prefs.PREFS_NAME, 0).edit().clear().commit())
        val device = UiDevice.getInstance(instrumentation)
        device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS allow")
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
            val firstScreen = waitFor(R.id.homeScreen)
            if (firstScreen == null) {
                // Capture before ActivityScenario closes the screen on assertion failure.
                runCatching {
                    val hierarchy = File(checkNotNull(context.getExternalFilesDir(null)), "onboarding-failure.xml")
                    device.dumpWindowHierarchy(hierarchy)
                    val copied = device.executeShellCommand("cp '${hierarchy.absolutePath}' '$out/onboarding-failure.xml' && echo copied")
                    check(copied.trim() == "copied") { "hierarchy copy failed: $copied" }
                }.onFailure { Log.e("LargeTextCapture", "could not capture failure hierarchy", it) }
                runCatching { capture("onboarding-failure") }
                runCatching {
                    scenario.onActivity { activity ->
                        val screen = activity.findViewById<View>(R.id.homeScreen)
                        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
                        val prefs = Prefs(activity)
                        Log.e("LargeTextCapture", "missing homeScreen: package=${device.currentPackageName} locales=${activity.resources.configuration.locales} firstOpen=${prefs.firstOpen} onboardingSeen=${prefs.onboardingVersionSeen} destination=${host?.navController?.currentDestination?.id} screenPresent=${screen != null} screenVisible=${screen?.visibility} screenShown=${screen?.isShown} screenAttached=${screen?.isAttachedToWindow}")
                    }
                }.onFailure { Log.e("LargeTextCapture", "could not inspect missing screen", it) }
            }
            assertNotNull("first onboarding screen missing", firstScreen)
            capture("onboarding-home")
            clickAfterScrolling(R.id.btnNotNow)
            assertNotNull("Calm onboarding screen missing", waitFor(R.id.calmScreen))
            capture("onboarding-calm-top")
            scenario.onActivity { activity ->
                activity.findViewById<View>(R.id.featureGuideToggle).performClick()
                activity.findViewById<ViewGroup>(R.id.featureGuideRows).getChildAt(0).performClick()
            }
            assertTrue("home guide missing", device.wait(Until.hasObject(By.textContains("Swipe up: all apps.")), 10_000))
            capture("guide-home")
            device.pressBack()
            scenario.onActivity { activity ->
                activity.findViewById<ViewGroup>(R.id.featureGuideRows).getChildAt(1).performClick()
            }
            assertTrue("search guide missing", device.wait(Until.hasObject(By.textContains("Swipe up and type.")), 10_000))
            capture("guide-search")
            device.pressBack()
            scenario.onActivity { activity ->
                val rows = activity.findViewById<ViewGroup>(R.id.calmRows)
                val label = activity.getString(R.string.onboarding_row_slow_down)
                val row = (0 until rows.childCount).map { rows.getChildAt(it) }.first {
                    it.findViewById<TextView>(R.id.rowLabel)?.text?.toString() == label
                }
                row.performClick()
            }
            assertTrue("slow down sheet missing", device.wait(Until.hasObject(By.textContains("Mindful pause asks why")), 10_000))
            capture("slow-down")
            device.pressBack()
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
