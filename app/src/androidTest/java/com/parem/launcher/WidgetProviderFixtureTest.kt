package com.parem.launcher

import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.navigation.fragment.NavHostFragment
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
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.Collections
import java.util.IdentityHashMap

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

    /** Test-only observers: placeholders have no production touch listener; container has no hierarchy listener. */
    private fun tracePlaceholders(activity: MainActivity, prefs: Prefs) {
        val seen = Collections.newSetFromMap(IdentityHashMap<View, Boolean>())
        fun stamp() = "uptime=${SystemClock.uptimeMillis()} elapsed=${SystemClock.elapsedRealtime()}"
        fun observe(wrapper: View) {
            val label = (wrapper as? FrameLayout)?.getChildAt(0) as? TextView ?: return
            if (label.text != context.getString(R.string.widget_unavailable) || !seen.add(label)) return
            val id = wrapper.tag
            val identity = System.identityHashCode(label)
            fun state() = "id=$id view=$identity attached=${label.isAttachedToWindow} pressed=${label.isPressed} ids=${prefs.getWidgetIdList()} ${stamp()}"
            Log.i("WidgetTouchTrace", "observe ${state()}")
            label.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) { Log.i("WidgetTouchTrace", "attach ${state()}") }
                override fun onViewDetachedFromWindow(v: View) { Log.i("WidgetTouchTrace", "detach ${state()}") }
            })
            label.setOnTouchListener { _, event ->
                Log.i("WidgetTouchTrace", "touch ${MotionEvent.actionToString(event.actionMasked)} eventUptime=${event.eventTime} downUptime=${event.downTime} ${state()}")
                false // Observe only; retain normal TextView click handling.
            }
        }
        for (containerId in listOf(R.id.widgetContainerAbove, R.id.widgetContainerBelow)) {
            val container = activity.findViewById<ViewGroup>(containerId) ?: continue
            for (i in 0 until container.childCount) observe(container.getChildAt(i))
            container.setOnHierarchyChangeListener(object : ViewGroup.OnHierarchyChangeListener {
                override fun onChildViewAdded(parent: View, child: View) {
                    Log.i("WidgetTouchTrace", "add wrapper=${System.identityHashCode(child)} id=${child.tag} ${stamp()}")
                    observe(child)
                }
                override fun onChildViewRemoved(parent: View, child: View) {
                    Log.i("WidgetTouchTrace", "remove wrapper=${System.identityHashCode(child)} id=${child.tag} ${stamp()}")
                }
            })
        }
    }

    @Test
    fun liveUninstallAndRealConfigureSuccessKeepOtherSlotsSafe() {
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
        var originalActivity: MainActivity? = null
        try {
            pressHomeAndAwaitParem()
            onHome { activity ->
                originalActivity = activity
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

            // Start observers before reinstall, so a rebuild around the one tap is visible.
            onHome { tracePlaceholders(it, prefs) }
            assertTrue(device.executeShellCommand("pm install -r /data/local/tmp/parem-widget-fixture.apk").contains("Success"))
            val target = checkNotNull(device.findObjects(unavailable).firstOrNull()) { "first placeholder missing after reinstall" }
            Log.i("WidgetTouchTrace", "single tap bounds=${target.visibleBounds} uptime=${SystemClock.uptimeMillis()} elapsed=${SystemClock.elapsedRealtime()}")
            target.click()
            val swap = device.wait(Until.findObject(By.desc(context.getString(R.string.swap_widget))), 10_000)
            if (swap == null) {
                try {
                    val hierarchy = ByteArrayOutputStream()
                    device.dumpWindowHierarchy(hierarchy)
                    hierarchy.toString(Charsets.UTF_8.name()).chunked(3000).forEach { Log.e("WidgetFixture", "hierarchy: $it") }
                    context.getExternalFilesDir(null)?.let { device.takeScreenshot(File(it, "widget-fixture-failure.png")) }
                } catch (e: Exception) { Log.e("WidgetFixture", "could not capture diagnostics", e) }
            }
            checkNotNull(swap).click()
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)).text = "configurable"
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.TextView").textStartsWith("Parem configurable fixture")), 20_000)).click()
            assertTrue("actual configure activity did not launch", device.wait(Until.hasObject(By.text("Parem fixture configuration")), 15_000))
            instrumentation.runOnMainSync {
                val operation = checkNotNull(originalActivity?.pendingWidgetOp)
                assertEquals(2, operation.getInt("stage"))
                assertEquals(first, operation.getInt("replaceId"))
                pending = operation.getInt("widgetId")
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
    @Test
    fun homeCancelsRealConfigurationAndPreservesSwapTarget() {
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
        lateinit var owner: MainActivity
        try {
            pressHomeAndAwaitParem()
            onHome { activity ->
                owner = activity
                first = activity.appWidgetHost.allocateAppWidgetId()
                second = activity.appWidgetHost.allocateAppWidgetId()
                prefs.setWidgetIdList(listOf(first, second))
                prefs.setWidgetProvider(first, plain.flattenToString())
                prefs.setWidgetProvider(second, plain.flattenToString())
                prefs.setWidgetHeight(first, 101)
                prefs.setWidgetHeight(second, 111)
                home(activity).widgetController!!.requestReconcile(force = true)
            }
            val unavailable = By.desc(context.getString(R.string.widget_unavailable))
            assertTrue(device.wait(Until.hasObject(unavailable), 10_000))
            device.findObjects(unavailable).first().click()
            checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.swap_widget))), 10_000)).click()
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 20_000)).text = "configurable"
            checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.TextView").textStartsWith("Parem configurable fixture")), 20_000)).click()
            assertTrue(device.wait(Until.hasObject(By.text("Parem fixture configuration")), 15_000))
            instrumentation.runOnMainSync {
                val operation = checkNotNull(owner.pendingWidgetOp)
                assertEquals(2, operation.getInt("stage"))
                assertEquals(first, operation.getInt("replaceId"))
                pending = operation.getInt("widgetId")
            }
            assertEquals("fixture configuration must be foreground before HOME", fixture, device.currentPackageName)
            pressHomeAndAwaitParem()
            assertTrue(device.wait(Until.hasObject(unavailable), 15_000))
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var allocated = true
            while (allocated && SystemClock.elapsedRealtime() < deadline) {
                onHome { allocated = pending in it.appWidgetHost.appWidgetIds }
                if (allocated) SystemClock.sleep(100)
            }
            assertFalse("HOME-cancelled configure allocation remains", allocated)
            assertEquals(listOf(first, second), prefs.getWidgetIdList())
            val fresh = Prefs(context)
            assertEquals(101, fresh.getWidgetHeight(first))
            assertEquals(111, fresh.getWidgetHeight(second))
            assertEquals(plain.flattenToString(), fresh.getAllWidgetProviders()[first])
            assertEquals(plain.flattenToString(), fresh.getAllWidgetProviders()[second])
            onHome { assertEquals(null, it.pendingWidgetOp) }
        } finally {
            device.executeShellCommand("appwidget revokebind --package ${context.packageName} --user 0")
        }
    }

}
