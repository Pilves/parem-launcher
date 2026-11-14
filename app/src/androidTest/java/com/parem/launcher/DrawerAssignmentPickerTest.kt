package com.parem.launcher

import android.os.Process
import androidx.core.os.bundleOf
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.rules.TestName
import java.io.File
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerAssignmentPickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val timeout = 15_000L
    @get:Rule val testName = TestName()
    private val fixture = AppModel("Picker Fixture", null, "com.android.settings", null, false, Process.myUserHandle())

    private fun ActivityScenario<MainActivity>.useWithEvidence(block: (ActivityScenario<MainActivity>) -> Unit) {
        use { scenario ->
            try {
                block(scenario)
            } catch (failure: Throwable) {
                captureFailure(failure, scenario)
                throw failure
            }
        }
    }

    private fun captureFailure(original: Throwable, scenario: ActivityScenario<MainActivity>? = null) {
        val issues = mutableListOf<Throwable>()
        val name = "picker-${testName.methodName}-${android.os.SystemClock.elapsedRealtime()}"
        val out = "/data/local/tmp/parem-diag"
        fun attempt(block: () -> Unit) { runCatching(block).onFailure(issues::add) }
        attempt { device.executeShellCommand("mkdir -p $out") }
        attempt {
            val screenshot = "$out/$name.png"
            device.executeShellCommand("screencap -p $screenshot")
            check((device.executeShellCommand("stat -c %s $screenshot").trim().toLongOrNull() ?: 0) > 0)
        }
        attempt {
            val hierarchy = File(requireNotNull(context.getExternalFilesDir(null)), "$name.xml")
            device.dumpWindowHierarchy(hierarchy)
            check(hierarchy.length() > 0)
            hierarchy.readText().take(100_000).chunked(2_000).forEachIndexed { index, part ->
                android.util.Log.i("DrawerPickerDiag", "hierarchy[$index]: $part")
            }
            device.executeShellCommand("cp ${hierarchy.absolutePath} $out/$name.xml")
            check((device.executeShellCommand("stat -c %s $out/$name.xml").trim().toLongOrNull() ?: 0) > 0)
        }
        attempt {
            scenario?.onActivity { activity ->
                android.util.Log.i("DrawerPickerDiag", "activityOrientation=${activity.resources.configuration.orientation} destination=${activity.findNavController(R.id.nav_host_fragment).currentDestination?.id} focus=${activity.window.decorView.hasWindowFocus()}")
            }
            android.util.Log.i("DrawerPickerDiag", "display=${device.displayWidth}x${device.displayHeight} contextOrientation=${context.resources.configuration.orientation}")
        }
        for (command in listOf("wm size", "wm density", "dumpsys display | head -c 30000",
            "dumpsys window | head -c 30000", "dumpsys input_method | head -c 30000")) {
            attempt {
                device.executeShellCommand(command).take(30_000).chunked(2_000).forEachIndexed { index, part ->
                    android.util.Log.i("DrawerPickerDiag", "$command [$index]: $part")
                }
            }
        }
        issues.forEach { diagnostic ->
            original.addSuppressed(diagnostic)
            android.util.Log.e("DrawerPickerDiag", "failure evidence incomplete", diagnostic)
        }
    }

    @Before fun prepare() {
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
            autoShowKeyboard = false
        }
    }

    private fun ActivityScenario<MainActivity>.open(flag: Int) = onActivity { activity ->
        val model = ViewModelProvider(activity)[MainViewModel::class.java]
        if (flag == Constants.FLAG_HIDDEN_APPS) model.hiddenApps.value = listOf(fixture)
        activity.findNavController(R.id.nav_host_fragment).navigate(
            R.id.appListFragment, bundleOf(Constants.Key.FLAG to flag, Constants.Key.LETTER to 'A')
        )
        if (flag != Constants.FLAG_HIDDEN_APPS) model.appList.value = listOf(fixture)
    }

    @Test fun assignmentUsesSearchAndOneTapStillAssignsHomeSlot() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_SET_HOME_APP_1)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")
                .text(context.getString(R.string.select_app))), timeout))
            val searchId = context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
            assertTrue("picker search should be unfocused when auto-show is off", device.wait(
                Until.hasObject(By.res(searchId).focused(false)), timeout))
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.findObject(By.text("Picker Fixture")).click()
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), timeout))
            assertEquals(fixture.appPackage, Prefs(context).getHomeAppPackage(1))
        }
    }

    @Test fun currentChoiceIsShownOnlyForAnUnambiguousVisibleProfile() {
        val prefs = Prefs(context)
        prefs.setHomeAppPackage(1, fixture.appPackage)
        prefs.setHomeAppActivityClassName(1, fixture.activityClassName)
        prefs.setHomeAppUser(1, fixture.user.toString())
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_SET_HOME_APP_1)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerCurrent")
                .text(context.getString(R.string.picker_current_app, fixture.appLabel))), timeout))
        }
    }

    @Test fun privateAppDoesNotAppearAsCurrentChoice() {
        val prefs = Prefs(context)
        prefs.setHomeAppPackage(1, fixture.appPackage)
        prefs.setHomeAppActivityClassName(1, fixture.activityClassName)
        prefs.setHomeAppUser(1, fixture.user.toString())
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.onActivity { activity ->
                activity.findNavController(R.id.nav_host_fragment).navigate(
                    R.id.appListFragment, bundleOf(Constants.Key.FLAG to Constants.FLAG_SET_HOME_APP_1)
                )
            }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")), timeout))
            scenario.onActivity { activity ->
                ViewModelProvider(activity)[MainViewModel::class.java].appList.value = listOf(fixture.copy(isPrivate = true))
            }
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.no_apps_found))), timeout))
            assertFalse(device.hasObject(By.res(context.packageName, "drawerPickerCurrent")))
            assertFalse(device.hasObject(By.text("Picker Fixture")))
        }
    }

    @Test fun pickerClearSearchRestoresTheList() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_SET_HOME_APP_1)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            val searchId = context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
            val search = checkNotNull(device.wait(Until.findObject(By.res(searchId)), timeout))
            search.text = "zz_no_match_picker"
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.no_apps_found))), timeout))
            val clear = checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.picker_clear_search))), timeout))
            clear.click()
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            scenario.onActivity { activity ->
                val searchView = requireNotNull(activity.findViewById<androidx.appcompat.widget.SearchView>(R.id.search))
                assertEquals("", searchView.query.toString())
            }
        }
    }

    @Test fun gestureClockAndCalendarAssignmentsKeepSelectedIdentity() {
        val flags = listOf(Constants.FLAG_SET_SWIPE_LEFT_APP, Constants.FLAG_SET_SWIPE_RIGHT_APP,
            Constants.FLAG_SET_CLOCK_APP, Constants.FLAG_SET_CALENDAR_APP,
            Constants.FLAG_SET_DOUBLE_TAP_APP, Constants.FLAG_SET_GESTURE_LETTER_APP)
        for (flag in flags) {
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                scenario.open(flag)
                assertTrue("picker row missing for flag $flag", device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
                device.findObject(By.text("Picker Fixture")).click()
                assertTrue("home missing after selecting flag $flag", device.wait(
                    Until.hasObject(By.res(context.packageName, "homeAppsLayout")), timeout))
                val prefs = Prefs(context)
                val selected = when (flag) {
                    Constants.FLAG_SET_SWIPE_LEFT_APP -> prefs.appPackageSwipeLeft to prefs.appUserSwipeLeft
                    Constants.FLAG_SET_SWIPE_RIGHT_APP -> prefs.appPackageSwipeRight to prefs.appUserSwipeRight
                    Constants.FLAG_SET_CLOCK_APP -> prefs.clockAppPackage to prefs.clockAppUser
                    Constants.FLAG_SET_CALENDAR_APP -> prefs.calendarAppPackage to prefs.calendarAppUser
                    Constants.FLAG_SET_DOUBLE_TAP_APP -> com.parem.launcher.helper.DoubleTapActionManager.getAppPackage(context) to
                        com.parem.launcher.helper.DoubleTapActionManager.getAppUser(context)
                    else -> requireNotNull(com.parem.launcher.helper.GestureLetterManager.getMapping(context, 'A')).let { it.first to it.third }
                }
                assertEquals("wrong selected package/profile for flag $flag", fixture.appPackage to fixture.user.toString(), selected)
            }
        }
    }

    @Test fun silentAppChoiceOpensTheResolvedNotificationSettings() {
        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, fixture.appPackage)
        val settingsPackage = requireNotNull(context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName)
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_PICK_SILENT_APP)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.findObject(By.text("Picker Fixture")).click()
            assertTrue("notification settings did not open", device.wait(Until.hasObject(By.pkg(settingsPackage)), timeout))
            device.pressBack()
        }
    }

    @Test fun backingOutKeepsExistingAssignment() {
        val prefs = Prefs(context)
        prefs.setHomeAppPackage(1, "com.android.settings")
        prefs.setHomeAppName(1, "Original name")
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_SET_HOME_APP_1)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.pressBack()
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), timeout))
            assertEquals("com.android.settings", prefs.getHomeAppPackage(1))
            assertEquals("Original name", prefs.getHomeAppName(1))
        }
    }

    @Test fun hiddenAppTapOpensManagementInsteadOfLaunching() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_HIDDEN_APPS)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")
                .text(context.getString(R.string.hidden_apps))), timeout))
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.findObject(By.text("Picker Fixture")).click()
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.app_menu_show))), timeout))
        }
    }

    @Test fun emptyHiddenAppsShowsEmptyStateDespitePaddingRow() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.onActivity { activity ->
                val model = ViewModelProvider(activity)[MainViewModel::class.java]
                model.hiddenApps.value = emptyList()
                activity.findNavController(R.id.nav_host_fragment).navigate(
                    R.id.appListFragment, bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
                )
            }
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.no_apps_found))), timeout))
        }
    }

    @Test fun shortPickerChromeScrollsAndLeavesAListRow() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.onActivity { activity ->
                val density = activity.resources.displayMetrics.density
                fun dp(value: Int) = (value * density).toInt()
                val root = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                }
                val chrome = com.parem.launcher.ui.DrawerPickerChrome(activity)
                val content = android.widget.LinearLayout(activity).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                }
                val title = android.widget.TextView(activity).apply { text = "Title" }
                val search = android.widget.EditText(activity)
                content.addView(title, android.widget.LinearLayout.LayoutParams(-1, dp(140)))
                content.addView(search, android.widget.LinearLayout.LayoutParams(-1, dp(48)))
                chrome.addView(content)
                root.addView(chrome, android.widget.LinearLayout.LayoutParams(-1, -2))
                val row = android.widget.TextView(activity).apply { text = "App" }
                root.addView(row, android.widget.LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8) })
                root.measure(android.view.View.MeasureSpec.makeMeasureSpec(dp(320), android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(dp(160), android.view.View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertTrue("a row should remain visible", row.height >= dp(48))
                assertTrue("overflowing header should scroll", chrome.canScrollVertically(1))
                chrome.scrollTo(0, content.height)
                assertTrue("search remains reachable", search.top - chrome.scrollY >= 0 &&
                    search.bottom - chrome.scrollY <= chrome.height)
                root.measure(android.view.View.MeasureSpec.makeMeasureSpec(dp(320), android.view.View.MeasureSpec.EXACTLY),
                    android.view.View.MeasureSpec.makeMeasureSpec(dp(56), android.view.View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertTrue("short window must retain a header viewport", chrome.height > 0)
                assertTrue("short window must retain some list viewport", row.height > 0)
                chrome.scrollTo(0, 0)
                assertTrue("short header must remain scrollable", chrome.canScrollVertically(1))
                chrome.scrollTo(0, search.top)
                assertTrue("search top must be reachable", search.top - chrome.scrollY in 0 until chrome.height)
                chrome.scrollTo(0, search.bottom)
                assertTrue("search bottom must be reachable", search.bottom - chrome.scrollY in 1..chrome.height)
            }
        }
    }

    @Test fun landscapePickerAndOrdinaryDrawerRemainUsable() {
        assumeTrue("requires a tablet landscape layout", context.resources.configuration.smallestScreenWidthDp >= 600)
        device.setOrientationNatural()
        try {
            val naturalDeadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (!device.isNaturalOrientation && android.os.SystemClock.elapsedRealtime() < naturalDeadline) {
                android.os.SystemClock.sleep(100)
            }
            assertTrue("tablet did not reach natural rotation", device.isNaturalOrientation)
            if (device.displayWidth <= device.displayHeight) device.setOrientationLeft()
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (device.displayWidth <= device.displayHeight && android.os.SystemClock.elapsedRealtime() < deadline) {
                android.os.SystemClock.sleep(100)
            }
            try {
                assertTrue("tablet did not reach landscape", device.displayWidth > device.displayHeight)
            } catch (failure: Throwable) {
                captureFailure(failure)
                throw failure
            }
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(android.content.res.Configuration.ORIENTATION_LANDSCAPE, activity.resources.configuration.orientation)
                }
                scenario.open(Constants.FLAG_SET_HOME_APP_1)
                assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")), timeout))
                val searchId = context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
                assertTrue(device.wait(Until.hasObject(By.res(searchId)), timeout))
                assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
                scenario.onActivity { activity ->
                    val title = requireNotNull(activity.findViewById<android.view.View>(R.id.drawerPickerTitle))
                    val content = title.parent as android.view.View
                    val chrome = content.parent as android.view.View
                    val root = chrome.parent as android.widget.LinearLayout
                    assertNotNull("landscape layout transition should be restored", root.layoutTransition)
                }
            }
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                scenario.open(Constants.FLAG_LAUNCH_APP)
                assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
                assertFalse(device.hasObject(By.res(context.packageName, "drawerPickerTitle")))
            }
        } finally {
            device.unfreezeRotation()
        }
    }

    @Test fun normalDrawerDoesNotShowPickerTitle() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_LAUNCH_APP)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            assertFalse(device.hasObject(By.res(context.packageName, "drawerPickerTitle")))
        }
    }
}
