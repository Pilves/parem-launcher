package com.parem.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import android.os.SystemClock
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.MediaStore
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.ui.BadHabitDialogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.regex.Pattern
import java.io.ByteArrayOutputStream

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

    @Test fun dateTapBlocksUnderFocusAndOpensWhenFocusIsOff() {
        val prefs = prepare()
        prefs.calendarAppPackage = ""
        prefs.dateTimeVisibility = Constants.DateTime.ON
        val intent = Intent(Intent.ACTION_VIEW, CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
        val handler = requireNotNull(context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)) {
            "calendar fixture must be resolvable"
        }.activityInfo.packageName
        // Observe the actual date-route intent without depending on an external calendar's UI.
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                return if (intent.action == Intent.ACTION_VIEW && intent.data == CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build())
                    Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null)
                else null
            }
        }
        instrumentation.addMonitor(monitor)
        fun awaitLaunches(expected: Int) {
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (monitor.hits < expected && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertEquals("date route must launch the calendar intent", expected, monitor.hits)
        }
        try {
            FocusModeManager.setWhitelist(context, emptySet())
            FocusModeManager.enable(context, 30)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<View>(R.id.date).performClick() }
                device.waitForIdle()
                SystemClock.sleep(750)
                assertEquals("blocked date left home", context.packageName, device.currentPackageName)
                assertEquals("blocked date must not launch a calendar", 0, monitor.hits)
                val allowed = if (handler == "android") instrumentation.context.packageName else handler
                FocusModeManager.setWhitelist(context, setOf(allowed))
                scenario.onActivity { it.findViewById<View>(R.id.date).performClick() }
                awaitLaunches(1)
                FocusModeManager.disable(context)
                scenario.onActivity { it.findViewById<View>(R.id.date).performClick() }
                awaitLaunches(2)
                SystemClock.sleep(750)
                assertEquals("blocked date must not launch later", 2, monitor.hits)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            FocusModeManager.disable(context)
        }
    }

    @Test fun clockTapBlocksUnderFocusAndOpensWhenAllowed() {
        val prefs = prepare()
        prefs.clockAppPackage = ""
        prefs.dateTimeVisibility = Constants.DateTime.ON
        val intent = Intent(AlarmClock.ACTION_SHOW_ALARMS)
        val handler = requireNotNull(context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)).activityInfo.packageName
        val allowed = if (handler == "android") instrumentation.context.packageName else handler
        try {
            FocusModeManager.setWhitelist(context, emptySet())
            FocusModeManager.enable(context, 30)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<View>(R.id.clock).performClick() }
                device.waitForIdle()
                SystemClock.sleep(750)
                assertEquals("blocked clock left home", context.packageName, device.currentPackageName)
                FocusModeManager.setWhitelist(context, setOf(allowed))
                scenario.onActivity { it.findViewById<View>(R.id.clock).performClick() }
                assertTrue("allowed clock did not open", device.wait(Until.hasObject(By.pkg(allowed)), 5_000))
                device.pressBack()
            }
        } finally {
            FocusModeManager.disable(context)
        }
    }

    @Test fun explicitHandlerIsBlockedOrOpenedByFocusAllowlist() {
        prepare()
        val fixturePackage = instrumentation.context.packageName
        val intent = Intent().setClassName(fixturePackage, LaunchGateFixtureActivity::class.java.name)
        try {
            FocusModeManager.setWhitelist(context, emptySet())
            FocusModeManager.enable(context, 30)
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                fun launch() = scenario.onActivity { activity ->
                    BadHabitDialogs.gateIntent(activity, activity.lifecycleScope, { !activity.isFinishing }, intent)
                }
                launch()
                device.waitForIdle()
                SystemClock.sleep(750)
                assertEquals("blocked explicit handler left home", context.packageName, device.currentPackageName)
                FocusModeManager.setWhitelist(context, setOf(fixturePackage))
                launch()
                assertTrue("allowed explicit handler did not open", device.wait(Until.hasObject(By.text("Camera fixture activity is open")), 5_000))
                device.pressBack()
            }
        } finally {
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
                fun traceChooser(stage: String, choice: UiObject2) {
                    try {
                        var node: UiObject2? = choice
                        var depth = 0
                        while (node != null && depth < 8) {
                            Log.i("CameraResolverTrace", "$stage ancestor=$depth class=${node.className} bounds=${node.visibleBounds} clickable=${node.isClickable} selected=${node.isSelected} checked=${node.isChecked}")
                            node = node.parent
                            depth++
                        }
                    } catch (e: Exception) { Log.i("CameraResolverTrace", "$stage ancestry unavailable", e) }
                    try {
                        val hierarchy = ByteArrayOutputStream()
                        device.dumpWindowHierarchy(hierarchy)
                        hierarchy.toString(Charsets.UTF_8.name()).chunked(3000).forEach { Log.i("CameraResolverTrace", "$stage hierarchy: $it") }
                    } catch (e: Exception) { Log.i("CameraResolverTrace", "$stage hierarchy unavailable", e) }
                }
                fun openCamera() {
                    val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
                    val resolved = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                    assertNotNull("camera handler must be available", resolved)
                    val packageName = resolved!!.activityInfo.packageName
                    device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
                    if (packageName == "android") {
                        val handlers = context.packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
                        var selected: String? = null
                        for (handler in handlers) {
                            val label = handler.loadLabel(context.packageManager).toString()
                            val choice = device.wait(Until.findObject(By.text(label)), 2_000) ?: continue
                            Log.i("CameraResolverTrace", "intended label=$label package=${handler.activityInfo.packageName}")
                            traceChooser("before", choice)
                            val lookupDeadline = SystemClock.uptimeMillis() + 5_000
                            var clickable: UiObject2? = null
                            while (clickable == null && SystemClock.uptimeMillis() < lookupDeadline) {
                                try {
                                    var node = device.findObject(By.text(label))
                                    while (node != null && !node.isClickable) node = node.parent
                                    clickable = node
                                } catch (_: StaleObjectException) { }
                                if (clickable == null) SystemClock.sleep(100)
                            }
                            checkNotNull(clickable) { "camera chooser row is not clickable" }.click()
                            traceChooser("after", choice)
                            selected = handler.activityInfo.packageName
                            break
                        }
                        assertNotNull("no camera handler was visible in the chooser", selected)
                        val deadline = SystemClock.uptimeMillis() + 10_000
                        var opened = false
                        while (!opened && SystemClock.uptimeMillis() < deadline) {
                            try {
                                opened = device.hasObject(By.pkg(selected!!))
                                if (!opened) device.findObject(By.text(Pattern.compile("(?i)just once")))?.click()
                            } catch (_: StaleObjectException) {
                                // The system resolver can replace its buttons as it finishes loading.
                            }
                            if (!opened) SystemClock.sleep(100)
                        }
                        assertTrue("selected camera app did not open", opened)
                    } else {
                        assertTrue("camera app did not open", device.wait(Until.hasObject(By.pkg(packageName)), 5_000))
                    }
                    device.pressBack()
                    if (!device.wait(Until.hasObject(By.pkg(context.packageName)), 2_000)) {
                        // Some resolvers remain underneath the selected camera activity.
                        device.pressBack()
                    }
                    assertTrue("home did not return after camera", device.wait(Until.hasObject(By.pkg(context.packageName)), 5_000))
                    device.waitForIdle()
                }
                openCamera()
                prefs.swipeLeftAction = Constants.GestureAction.OPEN_CAMERA
                openCamera()
                prefs.swipeLeftAction = Constants.GestureAction.OPEN_APP
                prefs.appPackageSwipeLeft = fixturePackage
                prefs.appActivityClassNameSwipeLeft = LaunchGateFixtureActivity::class.java.name
                prefs.appUserSwipeLeft = Process.myUserHandle().toString()
                device.swipe(device.displayWidth * 4 / 5, y, device.displayWidth / 5, y, 20)
                SystemClock.sleep(750)
                assertTrue("configured camera app gesture opened", !device.hasObject(By.text("Camera fixture activity is open")))
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
