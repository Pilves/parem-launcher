package com.parem.launcher

import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.navigation.findNavController
import androidx.lifecycle.ViewModelProvider
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.dismissOnDestroy
import com.google.android.material.bottomsheet.BottomSheetDialog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.formattedTimeSpent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.io.ByteArrayOutputStream
import java.util.Locale
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {

    @get:Rule
    val testName = TestName()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val context = instrumentation.targetContext
    private val pkg = context.packageName

    // Resolved from the installed APK's own resource table: whether the debug
    // applicationId suffix shows up in accessibility resource ids is not guessed
    private val homeId = context.resources.getResourceName(R.id.homeAppsLayout)
    private val searchId = context.resources.getResourceName(R.id.search)

    @Before
    fun setUp() {
        // clearPackageData wiped prefs: skip the role chooser and onboarding
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val out = device.executeShellCommand(
            "cmd package set-home-activity $pkg/${MainActivity::class.java.name}"
        )
        Log.i(TAG, "set-home-activity: ${out.trim()}; ids: $homeId, $searchId")
        device.pressHome()
        // checkTheme() may recreate the activity right after start, so wait on views
        assertTrue(
            "home layout not shown",
            device.wait(Until.hasObject(By.res(homeId)), TIMEOUT)
        )
    }

    @Test
    fun launcherIsHome() {
        assertTrue(device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
        assertEquals(pkg, device.currentPackageName)
    }

    @Test
    fun swipeUpOpensDrawer() {
        openDrawer()
    }

    @Test
    fun omniboxLaunchesApp() {
        openDrawer()
        // R.id.search is the SearchView container; setText only works on its inner field
        val field = device.wait(
            Until.findObject(By.res("${searchId.substringBefore(':')}:id/search_src_text")),
            TIMEOUT
        )
        assertNotNull("search field not found", field)
        field.click()
        field.text = "Settings"
        // Filtering and list submission are asynchronous. Do not submit an old row.
        val settingsRow = By.res(context.resources.getResourceName(R.id.appTitle)).text("Settings")
        val ready = device.wait(Until.hasObject(By.pkg(SETTINGS_PKG).depth(0)), 500L)
            || device.wait(Until.hasObject(settingsRow), TIMEOUT)
        if (ready && device.currentPackageName != SETTINGS_PKG) device.pressEnter()
        val launched = device.wait(Until.hasObject(By.pkg(SETTINGS_PKG).depth(0)), TIMEOUT)
        if (!launched) {
            val hierarchy = ByteArrayOutputStream()
            device.dumpWindowHierarchy(hierarchy)
            assertTrue("Settings not launched; package=${device.currentPackageName}; " +
                hierarchy.toString("UTF-8"), false)
        }
    }

    @Test
    fun drawerHintStaysBelowSearchAtNormalAndLargeFont() {
        val originalScale = device.executeShellCommand("settings get system font_scale").trim()
        try {
            for (scale in listOf("1.0", "2.0")) {
                device.executeShellCommand("settings put system font_scale $scale")
                device.pressHome()
                assertTrue(device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
                openDrawer()
                val field = device.wait(Until.findObject(By.res(
                    context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
                )), TIMEOUT)
                assertNotNull("search field missing at scale $scale", field)
                field.text = "focus"
                val tip = device.wait(Until.findObject(By.res(
                    context.resources.getResourceName(R.id.appDrawerTip)
                ).textContains("Focus")), TIMEOUT)
                assertNotNull("settings hint missing at scale $scale", tip)
                assertTrue("hint overlaps search at scale $scale",
                    tip.visibleBounds.top >= field.visibleBounds.bottom)
            }
        } finally {
            if (originalScale == "null") device.executeShellCommand("settings delete system font_scale")
            else device.executeShellCommand("settings put system font_scale $originalScale")
        }
    }

    @Test
    fun focusSearchKeepsResultsAboveKeyboard() {
        var sheet: BottomSheetDialog? = null
        try {
            device.waitForIdle(TIMEOUT)
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                sheet = FocusModeDialog(activity, (1..12).map {
                    "example.$it" to "Example $it"
                }).show().dismissOnDestroy(activity)
            }
            val search = device.wait(Until.findObject(By.clazz(EditText::class.java)
                .text(context.getString(R.string.search_apps))), TIMEOUT)
            assertNotNull("Focus app search missing", search)
            search.click()
            search.text = "Example"
            val deadline = System.currentTimeMillis() + TIMEOUT
            var visible = false
            var bounds = ""
            while (!visible && System.currentTimeMillis() < deadline) {
                var keyboardTop = 0
                instrumentation.runOnMainSync {
                    val decor = sheet!!.window!!.decorView
                    val insets = ViewCompat.getRootWindowInsets(decor)
                    if (insets?.isVisible(WindowInsetsCompat.Type.ime()) == true) {
                        keyboardTop = device.displayHeight -
                            insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                    }
                }
                // Filtering can replace a row between lookup and reading its bounds.
                val result = try {
                    device.findObject(By.text("Example 1"))?.visibleBounds
                } catch (_: StaleObjectException) { null }
                bounds = "keyboardTop=$keyboardTop result=$result"
                visible = keyboardTop > 0 && result != null && result.height() > 0 &&
                    result.bottom <= keyboardTop
                if (!visible) android.os.SystemClock.sleep(100)
            }
            assertTrue("Focus results hidden below keyboard: $bounds", visible)
        } finally {
            instrumentation.runOnMainSync { sheet?.dismiss() }
        }
    }

    @Test
    fun rotatesOnLargeScreenOnly() {
        try {
            device.setOrientationLandscape()
            if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                assertTrue(
                    "home layout not shown in landscape",
                    device.wait(Until.hasObject(By.res(homeId)), TIMEOUT)
                )
                // Save real emulator frames on each tablet for WP28 review.
                // The fixture here has no widget; this is not a complete WP28 pass.
                device.executeShellCommand("mkdir -p $DIAG_DIR")
                device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-before.png")
                openDrawer()
                device.pressBack()
                if (!device.wait(Until.hasObject(By.res(homeId)), 1500)) device.pressBack()
                assertTrue("home missing after drawer", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
                device.waitForIdle()
                device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-after.png")
            } else {
                // The portrait lock wins below sw600
                device.waitForIdle()
                assertEquals(0, device.displayRotation)
            }
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun landscapeFirstRunAndScreenTime() {
        if (context.resources.configuration.smallestScreenWidthDp < 600) return
        try {
            device.setOrientationLandscape()
            device.waitForIdle()
            device.executeShellCommand("appops set $pkg GET_USAGE_STATS allow")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                activity.recreate()
            }
            assertTrue("home missing", device.wait(Until.hasObject(By.res(homeId)), TIMEOUT))
            device.waitForIdle()
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].apply {
                    firstOpen.value = true
                    isParemDefault.value = false
                }
            }
            val banner = By.res(context.resources.getResourceName(R.id.setDefaultLauncher))
            val tips = By.res(context.resources.getResourceName(R.id.firstRunTips))
            assertTrue("default banner missing", device.wait(Until.hasObject(banner), TIMEOUT))
            assertFalse("first-run tips overlap the default banner", device.hasObject(tips))
            device.executeShellCommand("mkdir -p $DIAG_DIR")
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-banner.png")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].isParemDefault.value = true
            }
            assertTrue("first-run tips missing after default selection", device.wait(Until.hasObject(tips), TIMEOUT))
            assertFalse("default banner remains", device.hasObject(banner))
            assertTrue("screen-time value missing", device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.tvScreenTime))), TIMEOUT))
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-landscape-first-run.png")
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun landscapeWidgetSurvivesDrawerRoundTrip() {
        if (context.resources.configuration.smallestScreenWidthDp < 600) return
        try {
            device.setOrientationLandscape()
            device.waitForIdle()
            device.executeShellCommand("appwidget grantbind --package $pkg --user 0")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                val provider = ComponentName(instrumentation.context.packageName, LandscapeFixtureWidget::class.java.name)
                val id = activity.appWidgetHost.allocateAppWidgetId()
                assertTrue("fixture widget bind refused", activity.appWidgetManager.bindAppWidgetIdIfAllowed(id, provider))
                Prefs(context).apply {
                    setWidgetIdList(listOf(id))
                    setWidgetProvider(id, provider.flattenToString())
                    setWidgetHeight(id, 600)
                }
                activity.recreate()
            }
            assertTrue("fixture widget missing", device.wait(Until.hasObject(By.text("WP28 fixture widget")), TIMEOUT))
            device.executeShellCommand("mkdir -p $DIAG_DIR")
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-landscape-before.png")
            openDrawer()
            device.pressBack()
            if (!device.wait(Until.hasObject(By.res(homeId)), 1500)) device.pressBack()
            assertTrue("fixture widget missing after drawer", device.wait(Until.hasObject(By.text("WP28 fixture widget")), TIMEOUT))
            device.waitForIdle()
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-landscape-after.png")
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                ViewModelProvider(activity)[MainViewModel::class.java].apply {
                    firstOpen.value = true
                    isParemDefault.value = true
                }
            }
            val tips = device.wait(Until.findObject(By.res(context.resources.getResourceName(R.id.firstRunTips))), TIMEOUT)
            assertNotNull("first-run tips missing with widget", tips)
            device.waitForIdle()
            val widget = device.wait(Until.findObject(By.text("WP28 fixture widget")), TIMEOUT)
            assertNotNull("widget missing with first-run tips", widget)
            device.executeShellCommand("screencap -p $DIAG_DIR/${BuildConfig.FLAVOR}-widget-and-tips.png")
            assertFalse("first-run tips overlap the widget: tips=${tips.visibleBounds}, widget=${widget.visibleBounds}",
                Rect.intersects(tips.visibleBounds, widget.visibleBounds))
        } finally {
            device.setOrientationNatural()
        }
    }

    @Test
    fun durationsFollowThePhoneLanguage() {
        fun inLocale(tag: String) = context.createConfigurationContext(
            Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        ).formattedTimeSpent(12 * 60_000L)
        assertEquals("12m", inLocale("en"))
        assertEquals("12 Min.", inLocale("de"))
        assertEquals("12 мин", inLocale("ru"))
    }

    /**
     * A main-thread stall delivers the down event after GestureDetector's
     * long-press deadline, which it counts from the hardware down time. The
     * swipe's moves are batched to the next frame, so the overdue timer runs
     * first unless the app judges the hold by what it has actually seen.
     */
    @Test
    fun swipeDeliveredAfterAStallStillOpensDrawer() {
        device.waitForIdle(TIMEOUT)
        val x = device.displayWidth / 2f
        val startY = device.displayHeight * 3 / 4f
        val endY = device.displayHeight / 4f
        val downTime = SystemClock.uptimeMillis() - ViewConfiguration.getLongPressTimeout() - 200
        fun inject(action: Int, y: Float, time: Long) {
            val event = MotionEvent.obtain(downTime, time, action, x, y, 0)
            event.source = InputDevice.SOURCE_TOUCHSCREEN
            assertTrue("injection failed", instrumentation.uiAutomation.injectInputEvent(event, true))
            event.recycle()
        }
        inject(MotionEvent.ACTION_DOWN, startY, downTime)
        for (step in 1..20) {
            inject(MotionEvent.ACTION_MOVE, startY + (endY - startY) * step / 20, SystemClock.uptimeMillis())
            SystemClock.sleep(5)
        }
        inject(MotionEvent.ACTION_UP, endY, SystemClock.uptimeMillis())
        assertDrawerOpened()
    }

    @Test
    fun swipeDownRowStaysWhileItsPickerIsOpen() {
        device.waitForIdle(TIMEOUT)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            activity.findNavController(R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_settingsFragment)
        }
        device.waitForIdle(TIMEOUT)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            val row = activity.findViewById<View>(R.id.flSwipeDown)
            val picker = activity.findViewById<View>(R.id.swipeDownSelectLayout)
            activity.findViewById<View>(R.id.tvGestures).performClick()
            assertTrue("row not revealed", row.isVisible)
            row.performClick()
            assertTrue("row vanished on tap", row.isVisible)
            assertTrue("picker not shown", picker.isVisible)
            activity.findViewById<View>(R.id.notifications).performClick()
            assertTrue("row vanished after picking", row.isVisible)
            assertFalse("picker stayed open", picker.isVisible)
        }
    }

    private fun openDrawer() {
        // A visible home node can precede the last startup/rotation layout pass.
        device.waitForIdle(TIMEOUT)
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
        assertDrawerOpened()
    }

    private fun assertDrawerOpened() {
        if (!device.wait(Until.hasObject(By.res(searchId)), TIMEOUT)) {
            val diagnostics = screenDiagnostics()
            Log.e(TAG, diagnostics)
            fail("drawer not opened\n$diagnostics")
        }
    }

    /** Taken at the moment of failure: instrumentation exit kills the process and the evidence with it. */
    private fun screenDiagnostics(): String {
        fun shell(command: String) = device.executeShellCommand(command)
        val name = "${System.currentTimeMillis()}-${testName.methodName}"
        shell("mkdir -p $DIAG_DIR")
        shell("screencap -p $DIAG_DIR/$name.png")
        // executeShellCommand has no shell, so sh -c needs one word: IFS stands in for the spaces
        fun save(command: String, file: String) =
            shell("sh -c ${command.replace(" ", "\${IFS}")}>$DIAG_DIR/$name-$file.txt")
        save("dumpsys window", "window")
        save("dumpsys activity activities", "activities")
        save("dumpsys SurfaceFlinger", "surfaceflinger")
        save("logcat -d -v threadtime", "logcat")
        val hierarchy = ByteArrayOutputStream().also { device.dumpWindowHierarchy(it) }.toString("UTF-8")
        val resumed = shell("dumpsys activity activities").lines()
            .filter { "ResumedActivity" in it || "mFocusedApp" in it || "topResumedActivity" in it }
        val focus = shell("dumpsys window windows").lines().filter { "mCurrentFocus" in it || "mFocusedApp" in it }
        val logs = shell("logcat -d -v threadtime -t 2500").lines().filter { line ->
            LOG_MARKERS.any { it in line }
        }.takeLast(120)
        val nav = shell("settings get secure navigation_mode").trim()
        return listOf(
            "screenshot: $DIAG_DIR/$name.png  package=${device.currentPackageName} nav_mode=$nav",
            "display: ${device.displayWidth}x${device.displayHeight} rotation=${device.displayRotation}",
            "resumed: $resumed", "focus: $focus",
            "logs:", logs.joinToString("\n"),
            "hierarchy:", hierarchy,
        ).joinToString("\n")
    }


    private companion object {
        const val DIAG_DIR = "/data/local/tmp/parem-diag"
        val LOG_MARKERS = listOf("ParemGesture", "ActivityTaskManager", "InputDispatcher", "Choreographer", "AndroidRuntime", "Davey")
        const val TAG = "SmokeTest"
        const val TIMEOUT = 10_000L
        const val SETTINGS_PKG = "com.android.settings"
    }
}
