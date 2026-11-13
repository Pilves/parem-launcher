package com.parem.launcher

import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only in the dedicated disposable-emulator workflow with a synthetic companion APK. */
@RunWith(AndroidJUnit4::class)
class WidgetProviderFixtureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val fixture = "com.parem.widgetfixture"
    private val plain = ComponentName(fixture, "$fixture.PlainWidget")

    private fun onHome(block: (MainActivity) -> Unit) = instrumentation.runOnMainSync {
        val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
            .filterIsInstance<MainActivity>().single()
        block(activity)
    }

    private fun home(activity: MainActivity): HomeFragment {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as HomeFragment
    }

    @Test
    fun liveUninstallAndRealConfigureResultKeepOtherSlotsSafe() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("widget_fixture") == "true")
        assumeTrue(BuildConfig.DEBUG && context.packageName == "com.parem.launcher.debug")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        check(device.executeShellCommand("pm path $fixture").contains("package:"))
        val prefs = Prefs(context)
        prefs.firstOpen = false
        prefs.onboardingVersionSeen = Constants.ONBOARDING_VERSION
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        device.executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0")
        var first = -1
        var second = -1
        var pending = -1
        try {
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            onHome { activity ->
                first = activity.appWidgetHost.allocateAppWidgetId()
                second = activity.appWidgetHost.allocateAppWidgetId()
                assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(first, plain))
                assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(second, plain))
                prefs.setWidgetIdList(listOf(first, second))
                prefs.setWidgetProvider(first, plain.flattenToString())
                prefs.setWidgetProvider(second, plain.flattenToString())
                prefs.setWidgetHeight(first, 100)
                prefs.setWidgetHeight(second, 110)
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            assertTrue("fixture widgets did not render", device.wait(Until.hasObject(By.text("Parem companion widget")), 15_000))
            assertEquals(context.packageName, device.currentPackageName)
            assertTrue(device.executeShellCommand("pm uninstall $fixture").contains("Success"))
            val unavailable = By.desc(context.getString(R.string.widget_unavailable))
            val deadline = SystemClock.elapsedRealtime() + 20_000
            while (device.findObjects(unavailable).size != 2 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(100)
            assertEquals("provider callback did not replace both widgets", 2, device.findObjects(unavailable).size)
            assertEquals(context.packageName, device.currentPackageName)
            assertEquals(listOf(first, second), prefs.getWidgetIdList())
            assertEquals(100, prefs.getWidgetHeight(first))
            assertEquals(110, prefs.getWidgetHeight(second))

            assertTrue(device.executeShellCommand("pm install -r /data/local/tmp/parem-widget-fixture.apk").contains("Success"))
            // Reinstall dispatches provider changes that can rebuild the placeholder view.
            val clickDeadline = SystemClock.elapsedRealtime() + 10_000
            var clicked = false
            while (!clicked && SystemClock.elapsedRealtime() < clickDeadline) {
                val firstPlaceholder = device.findObjects(unavailable).firstOrNull()
                if (firstPlaceholder != null) try {
                    firstPlaceholder.click()
                    clicked = true
                } catch (_: StaleObjectException) { }
                if (!clicked) SystemClock.sleep(100)
            }
            assertTrue("first unavailable widget could not be opened after reinstall", clicked)
            checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.swap_widget))), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)).text = "configurable"
            checkNotNull(device.wait(Until.findObject(By.text("Parem configurable fixture")), 20_000)).click()
            assertTrue("actual configure activity did not launch", device.wait(Until.hasObject(By.text("Parem fixture configuration")), 15_000))
            assertTrue(device.pressHome())
            assertTrue(device.wait(Until.hasObject(unavailable), 10_000))
            onHome { activity ->
                val operation = checkNotNull(activity.pendingWidgetOp)
                assertEquals(2, operation.getInt("stage"))
                assertEquals(first, operation.getInt("replaceId"))
                pending = operation.getInt("widgetId")
            }
            device.findObjects(unavailable)[1].click()
            checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.swap_widget))), 10_000)).click()
            assertFalse("second picker opened while configuration was unresolved", device.wait(Until.hasObject(By.clazz("android.widget.ListView")), 1_500))
            onHome { activity ->
                assertEquals(pending, activity.pendingWidgetOp?.getInt("widgetId"))
                assertEquals(first, activity.pendingWidgetOp?.getInt("replaceId"))
            }
            device.executeShellCommand("am broadcast -a $fixture.COMPLETE -p $fixture")
            val resultDeadline = SystemClock.elapsedRealtime() + 15_000
            while (prefs.getWidgetIdList() != listOf(pending, second) && SystemClock.elapsedRealtime() < resultDeadline) SystemClock.sleep(100)
            assertEquals("real configuration result replaced the wrong slot", listOf(pending, second), prefs.getWidgetIdList())
            assertEquals(110, prefs.getWidgetHeight(second))
            onHome { assertEquals(null, it.pendingWidgetOp) }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }
}
