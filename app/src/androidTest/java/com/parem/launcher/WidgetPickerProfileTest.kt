package com.parem.launcher

import android.appwidget.AppWidgetProviderInfo
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** An opt-in measurement against 120 separately registered synthetic providers. */
@RunWith(AndroidJUnit4::class)
class WidgetPickerProfileTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test fun profilePublishedPreviews() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widget_picker_profile") == "true")
        assumeTrue(BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 35)
        assumeTrue(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION; autoShowKeyboard = false }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var providers = 0
            var previews = 0
            var retrievable = 0
            scenario.onActivity { activity ->
                val fixture = activity.appWidgetManager.installedProviders.filter { it.provider.packageName == "com.parem.widgetstress" }
                providers = fixture.size
                previews = fixture.count { it.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0 }
            }
            assertTrue("need >=100 registered synthetic providers; got $providers", providers >= 100)
            assertTrue("need >=100 published generated previews; got $previews", previews >= 100)
            // A third-party generated preview may be unavailable without widget-bind access.
            // Report this separately; the static-image and icon paths do not need that access.
            for (round in listOf("cold", "warm")) {
            device.executeShellCommand("dumpsys gfxinfo ${context.packageName} reset")
            val baselinePssKb = Debug.getPss()
            val start = SystemClock.elapsedRealtime()
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                (host.childFragmentManager.primaryNavigationFragment as HomeFragment).widgetController!!.showWidgetPicker()
            }
            val list = device.wait(Until.findObject(By.clazz("android.widget.ListView")), 30_000)
                ?: throw AssertionError("widget picker list did not appear")
            val openMs = SystemClock.elapsedRealtime() - start
            device.executeShellCommand("screencap -p /data/local/tmp/parem-widget-picker-$round-open.png")
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
                ?: throw AssertionError("widget search did not appear")
            search.text = "Stress widget"
            device.waitForIdle()
            device.executeShellCommand("screencap -p /data/local/tmp/parem-widget-picker-$round-filtered.png")
            var peakPssKb = Debug.getPss()
            val scrollStart = SystemClock.elapsedRealtime()
            var sawLast = device.hasObject(By.textContains("Stress widget 119"))
            var swipes = 0
            while (!sawLast && swipes < 120) {
                list.scroll(Direction.DOWN, 0.85f)
                swipes++
                peakPssKb = maxOf(peakPssKb, Debug.getPss())
                sawLast = device.hasObject(By.textContains("Stress widget 119"))
            }
            val scrollMs = SystemClock.elapsedRealtime() - scrollStart
            assertTrue("could not reach last of $providers providers after $swipes scrolls", sawLast)
            device.executeShellCommand("screencap -p /data/local/tmp/parem-widget-picker-$round-scrolled.png")
            val gfx = device.executeShellCommand("dumpsys gfxinfo ${context.packageName}")
            gfx.lineSequence().filter {
                it.trim().startsWith("Total frames rendered:") || it.trim().startsWith("Janky frames:") ||
                    it.trim().startsWith("90th percentile:") || it.trim().startsWith("95th percentile:") ||
                    it.trim().startsWith("99th percentile:")
            }.forEach { Log.i("ParemWidgetPickerProfile", "$round gfx: ${it.trim()}") }
            Log.i("ParemWidgetPickerProfile", "round=$round providers=$providers generated_previews=$previews open_ms=$openMs scroll_ms=$scrollMs swipes=$swipes baseline_pss_kb=$baselinePssKb peak_pss_kb=$peakPssKb delta_pss_kb=${peakPssKb - baselinePssKb}")
            device.pressBack()
            if (!device.wait(Until.gone(By.clazz("android.widget.ListView")), 1_000)) device.pressBack()
            assertTrue("widget picker did not close", device.wait(Until.gone(By.clazz("android.widget.ListView")), 5_000))
            }
            scenario.onActivity { activity ->
                retrievable = activity.appWidgetManager.installedProviders
                    .filter { it.provider.packageName == "com.parem.widgetstress" }
                    .count { provider -> activity.appWidgetManager.getWidgetPreview(provider.provider, provider.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != null }
            }
            Log.i("ParemWidgetPickerProfile", "retrievable_previews=$retrievable")
        }
    }
}
