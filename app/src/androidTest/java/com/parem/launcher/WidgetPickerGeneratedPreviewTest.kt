package com.parem.launcher

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.graphics.Color
import android.os.Build
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable emulator only: a different package publishes one generated preview. */
@RunWith(AndroidJUnit4::class)
class WidgetPickerGeneratedPreviewTest {
    @Test fun generatedPreviewRendersAndItsTapAddsTheProvider() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assumeTrue(InstrumentationRegistry.getArguments().getString("widget_picker_preview") == "true")
        assumeTrue(BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 35)
        assumeTrue(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        val prefs = Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION; autoShowKeyboard = false }
        // App and test are installed now; only this disposable CI emulator gets the grant.
        device.executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0")
        assertTrue("fixture expects no preexisting widgets", prefs.getWidgetIdList().isEmpty())
        val manager = context.getSystemService(AppWidgetManager::class.java)
        val component = ComponentName("com.parem.widgetstress", "com.parem.widgetstress.Provider000")
        val provider = manager.installedProviders.firstOrNull { it.provider == component }
            ?: throw AssertionError("published fixture provider missing")
        assertTrue("fixture did not publish HOME preview", provider.generatedPreviewCategories and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN != 0)
        assertNotNull("hosted emulator cannot retrieve published preview after disposable bind grant", manager.getWidgetPreview(component, provider.profile, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN))
        fun sentinelCount(): Int = device.executeShellCommand("logcat -d -s ParemPreviewSentinel:I '*:S'")
            .lineSequence().count { "ParemPreviewSentinel" in it && "FIRED" in it }
        val oldSentinels = sentinelCount()
        device.executeShellCommand("am broadcast -n com.parem.widgetstress/.Sentinel")
        val sentinelDeadline = SystemClock.elapsedRealtime() + 5_000
        while (sentinelCount() == oldSentinels && SystemClock.elapsedRealtime() < sentinelDeadline) SystemClock.sleep(100)
        val verifiedSentinels = sentinelCount()
        assertEquals("fixture sentinel did not receive control broadcast", oldSentinels + 1, verifiedSentinels)
        val before = Debug.getPss()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                (host.childFragmentManager.primaryNavigationFragment as HomeFragment).widgetController!!.showWidgetPicker()
            }
            assertTrue("widget picker missing", device.wait(Until.hasObject(By.clazz("android.widget.ListView")), 30_000))
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
                ?: throw AssertionError("widget search missing")
            search.text = "Stress widget 000"
            assertTrue("fixture group missing", device.wait(Until.hasObject(By.text("Parem stress widgets")), 10_000))
            val row = device.wait(Until.findObject(By.clazz("android.widget.TextView").textStartsWith("Stress widget 000")), 10_000)
                ?: throw AssertionError("filtered fixture row missing")
            assertTrue("widget size missing", row.text.contains("×"))
            fun findPreviewPixels(): Triple<Int, Int, Int> {
                device.waitForIdle()
                val bounds = checkNotNull(device.findObject(By.clazz("android.widget.ListView"))).visibleBounds
                val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                var xFound = -1
                var yFound = -1
                var matches = 0
                for (y in bounds.top.coerceAtLeast(0) until bounds.bottom.coerceAtMost(bitmap.height) step 4) {
                    for (x in bounds.left.coerceAtLeast(0) until bounds.right.coerceAtMost(bitmap.width) step 4) {
                        if (bitmap.getPixel(x, y) == Color.rgb(0x32, 0x5a, 0x66)) {
                            matches++
                            if (xFound < 0) { xFound = x; yFound = y }
                        }
                    }
                }
                bitmap.recycle()
                return Triple(matches, xFound, yFound)
            }
            assertTrue("published preview did not visibly render", findPreviewPixels().first > 100)
            assertFalse("preview descendants must be hidden from accessibility", device.hasObject(By.textStartsWith("Synthetic preview")))
            search.text = "Stress widget 001"
            assertTrue("nonpreview row missing after filtering", device.wait(Until.hasObject(By.clazz("android.widget.TextView").textStartsWith("Stress widget 001")), 10_000))
            assertEquals("stale preview remained on another row", 0, findPreviewPixels().first)
            search.text = "Stress widget 000"
            assertTrue("preview row missing after restoring filter", device.wait(Until.hasObject(By.clazz("android.widget.TextView").textStartsWith("Stress widget 000")), 10_000))
            val (matches, previewX, previewY) = findPreviewPixels()
            assertTrue("published preview did not reattach: matching pixels=$matches", matches > 100)
            device.executeShellCommand("screencap -p /data/local/tmp/widget-preview.png")
            assertTrue("could not tap preview area", device.click(previewX + 8, previewY + 8))
            val deadline = SystemClock.elapsedRealtime() + 15_000
            while (prefs.getWidgetIdList().isEmpty() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            val id = prefs.getWidgetIdList().singleOrNull() ?: throw AssertionError("preview row tap did not add exactly one widget")
            val bound = manager.getAppWidgetInfo(id) ?: throw AssertionError("added widget is not bound")
            assertEquals(component, bound.provider)
            assertEquals(provider.profile, bound.profile)
            SystemClock.sleep(500)
            assertEquals("preview's PendingIntent fired instead of row action", verifiedSentinels, sentinelCount())
            System.gc()
            System.runFinalization()
            SystemClock.sleep(1_000)
            Log.i("ParemWidgetPreview", "after_dismiss_pss_kb=${Debug.getPss()} before_picker_pss_kb=$before")
        }
    }
}
