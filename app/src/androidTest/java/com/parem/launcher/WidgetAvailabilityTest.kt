package com.parem.launcher

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.WidgetOperationResult
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetAvailabilityTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val provider = ComponentName(instrumentation.context.packageName, LandscapeFixtureWidget::class.java.name)

    private fun home(activity: MainActivity): HomeFragment {
        val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        return host.childFragmentManager.primaryNavigationFragment as HomeFragment
    }

    private fun prepare() {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        device.executeShellCommand("appwidget grantbind --package ${context.packageName} --user 0")
    }

    @Test
    fun unavailableIdCanRecoverAndBeRemovedExplicitly() {
        prepare()
        val prefs = Prefs(context)
        var widgetId = -1
        try {
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            fun onHome(block: (MainActivity) -> Unit) {
                instrumentation.runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                    block(activity)
                }
            }
            onHome { activity ->
                widgetId = activity.appWidgetHost.allocateAppWidgetId()
                prefs.setWidgetIdList(listOf(widgetId))
                prefs.setWidgetProvider(widgetId, provider.flattenToString())
                prefs.setWidgetHeight(widgetId, 173)
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val unavailable = By.desc(context.getString(R.string.widget_unavailable))
            assertTrue("unbound widget placeholder missing", device.wait(Until.hasObject(unavailable), 10_000))
            assertEquals(listOf(widgetId), prefs.getWidgetIdList())
            assertEquals(173, prefs.getWidgetHeight(widgetId))
            onHome { activity ->
                assertTrue("fixture bind refused", activity.appWidgetManager.bindAppWidgetIdIfAllowed(widgetId, provider))
            }
            assertTrue(device.pressHome())
            assertTrue("same ID did not recover", device.wait(Until.hasObject(By.text("WP28 fixture widget")), 10_000))
            assertEquals(listOf(widgetId), prefs.getWidgetIdList())
            assertEquals(173, prefs.getWidgetHeight(widgetId))

            // Simulate a no-longer-bound system ID; no real app is uninstalled.
            onHome { activity ->
                activity.appWidgetHost.deleteAppWidgetId(widgetId)
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val placeholder = device.wait(Until.findObject(unavailable), 10_000)
            checkNotNull(placeholder) { "placeholder not restored after ID became inaccessible" }
            assertEquals(listOf(widgetId), prefs.getWidgetIdList())
            placeholder.click()
            val remove = device.wait(Until.findObject(By.desc(context.getString(R.string.remove_widget))), 10_000)
            checkNotNull(remove) { "placeholder removal action missing" }.click()
            assertTrue("saved widget was not removed", prefs.getWidgetIdList().isEmpty())
            val afterRemoval = Prefs(context) // Its in-memory maps are independent of the controller's.
            assertFalse("provider mapping was not removed", afterRemoval.getAllWidgetProviders().containsKey(widgetId))
            assertEquals("saved height was not removed", 200, afterRemoval.getWidgetHeight(widgetId))
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun failedLegacyLookupAndRemovalCannotResurrectWidget() {
        prepare()
        val prefs = Prefs(context)
        try {
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val id = activity.appWidgetHost.allocateAppWidgetId()
                prefs.widgetIds = ""
                prefs.widgetId = id
                val result = runCatching { prefs.migrateWidgetWithProviderLookup { throw IllegalStateException("lookup unavailable") } }
                assertTrue("test did not exercise a throwing provider lookup", result.isFailure)
                assertEquals(listOf(id), prefs.getWidgetIdList())
                assertEquals("failed lookup left legacy marker", -1, prefs.widgetId)
                // Repair the stale-marker state that older versions could leave.
                prefs.widgetId = id
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val unavailable = By.desc(context.getString(R.string.widget_unavailable))
            val placeholder = checkNotNull(device.wait(Until.findObject(unavailable), 10_000))
            placeholder.click()
            checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.remove_widget))), 10_000)).click()
            assertTrue("explicit removal left the ID", prefs.getWidgetIdList().isEmpty())
            assertEquals("explicit removal left the stale legacy marker", -1, prefs.widgetId)
            assertTrue(device.pressHome())
            device.waitForIdle()
            SystemClock.sleep(500)
            assertTrue("removed legacy widget reappeared", prefs.getWidgetIdList().isEmpty())
            assertFalse("removed legacy placeholder reappeared", device.hasObject(unavailable))
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun homeWhileConfigurePendingCannotStartAnotherWidgetOperation() {
        prepare()
        val prefs = Prefs(context)
        try {
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            fun onHome(block: (MainActivity) -> Unit) {
                instrumentation.runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                    block(activity)
                }
            }
            val pickerList = By.clazz("android.widget.ListView")
            onHome { home(it).widgetController!!.showWidgetPicker() }
            assertTrue("control picker did not open", device.wait(Until.hasObject(pickerList), 15_000))
            device.pressBack()
            if (!device.wait(Until.gone(pickerList), 1_000)) device.pressBack()
            check(device.wait(Until.gone(pickerList), 10_000))

            var first = -1
            var second = -1
            var pending = -1
            onHome { activity ->
                first = activity.appWidgetHost.allocateAppWidgetId()
                second = activity.appWidgetHost.allocateAppWidgetId()
                pending = activity.appWidgetHost.allocateAppWidgetId()
                assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(pending, provider))
                prefs.setWidgetIdList(listOf(first, second))
                activity.pendingWidgetOp = Bundle().apply {
                    putInt("stage", 2); putInt("widgetId", pending); putInt("replaceId", first)
                }
            }
            assertTrue(device.pressHome())
            onHome { home(it).widgetController!!.showWidgetPicker() }
            assertFalse("a second widget picker opened", device.wait(Until.hasObject(pickerList), 1_500))
            onHome { activity ->
                assertEquals("pending configure was overwritten", pending, activity.pendingWidgetOp?.getInt("widgetId"))
                assertEquals("original swap target was overwritten", first, activity.pendingWidgetOp?.getInt("replaceId"))
                home(activity).widgetController!!.onPendingWidgetResult(WidgetOperationResult(2, 2, pending, null, true))
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (prefs.getWidgetIdList() != listOf(pending, second) && SystemClock.elapsedRealtime() < deadline)
                SystemClock.sleep(50)
            assertEquals("old configure targeted a different widget", listOf(pending, second), prefs.getWidgetIdList())
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun pendingSwapTracksIdThroughReorderAndDoesNotResurrectRemoval() {
        prepare()
        val prefs = Prefs(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var first = -1
                var second = -1
                var newWidget = -1
                scenario.onActivity { activity ->
                    first = activity.appWidgetHost.allocateAppWidgetId()
                    second = activity.appWidgetHost.allocateAppWidgetId()
                    newWidget = activity.appWidgetHost.allocateAppWidgetId()
                    assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(newWidget, provider))
                    prefs.setWidgetIdList(listOf(second, first)) // Target moved after the picker opened.
                    prefs.setWidgetHeight(second, 234)
                    activity.pendingWidgetOp = Bundle().apply {
                        putInt("stage", 2); putInt("widgetId", newWidget); putInt("replaceId", first)
                    }
                }
                scenario.recreate()
                scenario.onActivity { activity -> home(activity).widgetController!!.onPendingWidgetResult(WidgetOperationResult(2, 2, newWidget, null, true)) }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                while (prefs.getWidgetIdList() != listOf(second, newWidget) && SystemClock.elapsedRealtime() < deadline)
                    SystemClock.sleep(50)
                assertEquals("swap replaced a different widget", listOf(second, newWidget), prefs.getWidgetIdList())
                assertEquals("other widget height changed", 234, prefs.getWidgetHeight(second))

                var abandoned = -1
                scenario.onActivity { activity ->
                    abandoned = activity.appWidgetHost.allocateAppWidgetId()
                    assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(abandoned, provider))
                    activity.pendingWidgetOp = Bundle().apply {
                        putInt("stage", 2); putInt("widgetId", abandoned); putInt("replaceId", first)
                    }
                    home(activity).widgetController!!.onPendingWidgetResult(WidgetOperationResult(2, 2, abandoned, null, true))
                }
                val cleanupDeadline = SystemClock.elapsedRealtime() + 10_000
                var leaked = true
                while (leaked && SystemClock.elapsedRealtime() < cleanupDeadline) {
                    scenario.onActivity { activity -> leaked = abandoned in activity.appWidgetHost.appWidgetIds }
                    if (leaked) SystemClock.sleep(50)
                }
                assertEquals("removed target was resurrected", listOf(second, newWidget), prefs.getWidgetIdList())
                assertFalse("abandoned replacement ID leaked", leaked)
            }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }
    @Test
    fun mismatchedResultCannotConsumePendingConfigureOrDeleteItsId() {
        prepare()
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var pending = -1
                scenario.onActivity { activity ->
                    pending = activity.appWidgetHost.allocateAppWidgetId()
                    activity.pendingWidgetOp = Bundle().apply { putInt("stage", 2); putInt("widgetId", pending); putInt("replaceId", -1) }
                    val controller = home(activity).widgetController!!
                    controller.onPendingWidgetResult(WidgetOperationResult(1, 2, pending, null, false))
                    controller.onPendingWidgetResult(WidgetOperationResult(2, 2, pending, pending + 1, false))
                    controller.onPendingWidgetResult(WidgetOperationResult(2, 2, pending + 1, null, false))
                    assertEquals(pending, activity.pendingWidgetOp?.getInt("widgetId"))
                    assertTrue(pending in activity.appWidgetHost.appWidgetIds)
                    controller.onPendingWidgetResult(WidgetOperationResult(2, 2, pending, null, false))
                    assertEquals(null, activity.pendingWidgetOp)
                    assertFalse(pending in activity.appWidgetHost.appWidgetIds)
                }
            }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun legacyFinishedResultIsAbandonedButInflightOperationSurvives() {
        prepare()
        val prefs = Prefs(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var original = -1
                var pending = -1
                scenario.onActivity { activity ->
                    original = activity.appWidgetHost.allocateAppWidgetId()
                    pending = activity.appWidgetHost.allocateAppWidgetId()
                    prefs.setWidgetIdList(listOf(original))
                    prefs.setWidgetHeight(original, 177)
                    prefs.setWidgetProvider(original, provider.flattenToString())
                    val op = Bundle().apply { putInt("stage", 2); putInt("widgetId", pending); putInt("replaceId", original) }
                    activity.restorePendingWidgetState(Bundle().apply { putBundle("pendingWidgetOp", op) })
                    assertEquals("in-flight operation was lost", pending, activity.pendingWidgetOp?.getInt("widgetId"))
                    activity.restorePendingWidgetState(Bundle().apply {
                        putBundle("pendingWidgetOp", op); putBoolean("pendingWidgetResult", false)
                    })
                    assertEquals(null, activity.pendingWidgetOp)
                    assertEquals(listOf(original), prefs.getWidgetIdList())
                    home(activity).widgetController!!.requestReconcile(force = true)
                }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var pendingAllocated = true
                while (pendingAllocated && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { pendingAllocated = pending in it.appWidgetHost.appWidgetIds }
                    if (pendingAllocated) SystemClock.sleep(50)
                }
                assertFalse("abandoned unsaved allocation was not reclaimed", pendingAllocated)
                assertEquals(listOf(original), prefs.getWidgetIdList())
                val fresh = Prefs(context)
                assertEquals(177, fresh.getWidgetHeight(original))
                assertEquals(provider.flattenToString(), fresh.getAllWidgetProviders()[original])
                scenario.onActivity { assertTrue(original in it.appWidgetHost.appWidgetIds) }
            }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

}
