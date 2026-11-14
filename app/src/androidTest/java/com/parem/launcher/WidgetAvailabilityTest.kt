package com.parem.launcher

import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
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
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertSame
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

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
            pressHomeAndAwaitParem()
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
            pressHomeAndAwaitParem()
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
            val removalDeadline = SystemClock.elapsedRealtime() + 10_000
            while (prefs.getWidgetIdList().isNotEmpty() && SystemClock.elapsedRealtime() < removalDeadline) SystemClock.sleep(50)
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
            pressHomeAndAwaitParem()
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
            val removalDeadline = SystemClock.elapsedRealtime() + 10_000
            while (prefs.getWidgetIdList().isNotEmpty() && SystemClock.elapsedRealtime() < removalDeadline) SystemClock.sleep(50)
            assertTrue("explicit removal left the ID", prefs.getWidgetIdList().isEmpty())
            assertEquals("explicit removal left the stale legacy marker", -1, prefs.widgetId)
            pressHomeAndAwaitParem()
            device.waitForIdle()
            SystemClock.sleep(500)
            assertTrue("removed legacy widget reappeared", prefs.getWidgetIdList().isEmpty())
            assertFalse("removed legacy placeholder reappeared", device.hasObject(unavailable))
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun seededPendingConfigureCannotStartAnotherWidgetOperation() {
        prepare()
        val prefs = Prefs(context)
        try {
            pressHomeAndAwaitParem()
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
            pressHomeAndAwaitParem()
            onHome { home(it).widgetController!!.showWidgetPicker() }
            assertFalse("a second widget picker opened", device.wait(Until.hasObject(pickerList), 1_500))
            onHome { activity ->
                assertEquals("pending configure was overwritten", pending, activity.pendingWidgetOp?.getInt("widgetId"))
                assertEquals("original swap target was overwritten", first, activity.pendingWidgetOp?.getInt("replaceId"))
                home(activity).widgetController!!.onPendingWidgetResult(true)
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
                scenario.onActivity { activity -> home(activity).widgetController!!.onPendingWidgetResult(true) }
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
                    home(activity).widgetController!!.onPendingWidgetResult(true)
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
    fun forcedRefreshKeepsUnavailablePlaceholderAndRefreshesValidWidget() {
        prepare()
        val prefs = Prefs(context)
        var unavailableId = -1
        var validId = -1
        var placeholder: View? = null
        var validWrapper: View? = null
        try {
            pressHomeAndAwaitParem()
            fun onHome(block: (MainActivity) -> Unit) = instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                block(activity)
            }
            fun wrapper(activity: MainActivity, id: Int): View? =
                listOf(R.id.widgetContainerAbove, R.id.widgetContainerBelow)
                    .mapNotNull { activity.findViewById<ViewGroup>(it)?.findViewWithTag<View>(id) }.firstOrNull()
            onHome { activity ->
                unavailableId = activity.appWidgetHost.allocateAppWidgetId()
                validId = activity.appWidgetHost.allocateAppWidgetId()
                assertTrue(activity.appWidgetManager.bindAppWidgetIdIfAllowed(validId, provider))
                prefs.setWidgetIdList(listOf(unavailableId, validId))
                prefs.setWidgetHeight(unavailableId, 173)
                prefs.setWidgetHeight(validId, 187)
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while ((placeholder == null || validWrapper == null) && SystemClock.elapsedRealtime() < deadline) {
                onHome { activity -> placeholder = wrapper(activity, unavailableId); validWrapper = wrapper(activity, validId) }
                if (placeholder == null || validWrapper == null) SystemClock.sleep(50)
            }
            checkNotNull(placeholder) { "unavailable placeholder missing" }
            checkNotNull(validWrapper) { "valid widget missing" }
            val oldPlaceholder = checkNotNull(placeholder)
            val oldValid = validWrapper
            val detachments = AtomicInteger()
            onHome { activity ->
                oldPlaceholder.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                    override fun onViewAttachedToWindow(v: View) = Unit
                    override fun onViewDetachedFromWindow(v: View) { detachments.incrementAndGet() }
                })
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val refreshDeadline = SystemClock.elapsedRealtime() + 10_000
            var refreshed = false
            while (!refreshed && SystemClock.elapsedRealtime() < refreshDeadline) {
                onHome { activity ->
                    refreshed = wrapper(activity, validId)?.let { it !== oldValid } == true
                    if (refreshed) {
                        assertSame("forced refresh replaced the unchanged placeholder", oldPlaceholder, wrapper(activity, unavailableId))
                        assertEquals("placeholder detached during refresh", 0, detachments.get())
                        assertTrue("placeholder is not attached", oldPlaceholder.isAttachedToWindow)
                    }
                }
                if (!refreshed) SystemClock.sleep(50)
            }
            assertTrue("valid widget was not refreshed", refreshed)
            assertEquals(listOf(unavailableId, validId), prefs.getWidgetIdList())
            assertEquals(173, prefs.getWidgetHeight(unavailableId))
            assertEquals(187, prefs.getWidgetHeight(validId))
            val node = checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.widget_unavailable))), 10_000))
            node.click()
            assertTrue("retained placeholder no longer opens its menu", device.wait(Until.hasObject(By.desc(context.getString(R.string.swap_widget))), 10_000))
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun cancelledConfigureAndRestoredBooleanPreserveSwapAndDropNewId() {
        prepare()
        val prefs = Prefs(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var original = -1
                var replacement = -1
                scenario.onActivity { activity ->
                    original = activity.appWidgetHost.allocateAppWidgetId()
                    replacement = activity.appWidgetHost.allocateAppWidgetId()
                    prefs.setWidgetIdList(listOf(original))
                    prefs.setWidgetHeight(original, 177)
                    prefs.setWidgetProvider(original, provider.flattenToString())
                    activity.pendingWidgetOp = Bundle().apply {
                        putInt("stage", 2); putInt("widgetId", replacement); putInt("replaceId", original)
                    }
                    activity.pendingWidgetResult = false // Legacy deferred Boolean saved across recreation.
                }
                scenario.recreate()
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var allocated = true
                while (allocated && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { allocated = replacement in it.appWidgetHost.appWidgetIds }
                    if (allocated) SystemClock.sleep(50)
                }
                assertFalse("cancelled replacement still allocated", allocated)
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

    @Test
    fun cancelledConfigureAddReleasesOnlyUnsavedNewId() {
        prepare()
        val prefs = Prefs(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var pending = -1
                scenario.onActivity { activity ->
                    pending = activity.appWidgetHost.allocateAppWidgetId()
                    activity.pendingWidgetOp = Bundle().apply {
                        putInt("stage", 2); putInt("widgetId", pending); putInt("replaceId", -1)
                    }
                    home(activity).widgetController!!.onPendingWidgetResult(false)
                }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var allocated = true
                while (allocated && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { allocated = pending in it.appWidgetHost.appWidgetIds }
                    if (allocated) SystemClock.sleep(50)
                }
                assertTrue(prefs.getWidgetIdList().isEmpty())
                assertFalse("cancelled add still allocated", allocated)
            }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

    @Test
    fun cancelledConfigureNeverReleasesAnAlreadySavedId() {
        prepare()
        val prefs = Prefs(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var saved = -1
                scenario.onActivity { activity ->
                    saved = activity.appWidgetHost.allocateAppWidgetId()
                    prefs.setWidgetIdList(listOf(saved))
                    activity.pendingWidgetOp = Bundle().apply {
                        putInt("stage", 2); putInt("widgetId", saved); putInt("replaceId", -1)
                    }
                    home(activity).widgetController!!.onPendingWidgetResult(false)
                }
                val deadline = SystemClock.elapsedRealtime() + 10_000
                var pending = true
                while (pending && SystemClock.elapsedRealtime() < deadline) {
                    scenario.onActivity { pending = it.pendingWidgetOp != null }
                    if (pending) SystemClock.sleep(50)
                }
                assertFalse("cancel result was not processed", pending)
                assertEquals(listOf(saved), prefs.getWidgetIdList())
                scenario.onActivity { assertTrue(saved in it.appWidgetHost.appWidgetIds) }
            }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

}
