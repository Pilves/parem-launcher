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
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.helper.FocusModeManager
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

    private fun ActivityScenario<MainActivity>.open(flag: Int, app: AppModel = fixture) = onActivity { activity ->
        val model = ViewModelProvider(activity)[MainViewModel::class.java]
        if (flag == Constants.FLAG_HIDDEN_APPS) model.hiddenApps.value = listOf(app)
        activity.findNavController(R.id.nav_host_fragment).navigate(
            R.id.appListFragment, bundleOf(Constants.Key.FLAG to flag, Constants.Key.LETTER to 'A')
        )
        if (flag != Constants.FLAG_HIDDEN_APPS) model.appList.value = listOf(app)
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
        val app = launchFixture
        val hiddenKey = com.parem.launcher.helper.AppListRebuilder.hiddenKey(app.appPackage, app.user.toString())
        Prefs(context).hiddenApps = mutableSetOf(hiddenKey)
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_HIDDEN_APPS, app)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")
                .text(context.getString(R.string.hidden_apps))), timeout))
            assertTrue(device.wait(Until.hasObject(By.text("Hidden Launch Fixture")), timeout))
            device.findObject(By.text("Hidden Launch Fixture")).click()
            val open = checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.open_app))), timeout))
            val info = checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.a11y_app_info))), timeout))
            val show = checkNotNull(device.wait(Until.findObject(By.desc(context.getString(R.string.app_menu_show))), timeout))
            assertTrue("Uninstall action missing", device.hasObject(By.desc(context.getString(R.string.app_menu_uninstall))))
            assertTrue("Open must precede existing actions", open.visibleBounds.top < info.visibleBounds.top && info.visibleBounds.top < show.visibleBounds.top)
            show.click()
            val deadline = android.os.SystemClock.elapsedRealtime() + timeout
            while (hiddenKey in Prefs(context).hiddenApps && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
            assertFalse("Show did not remove the hidden key", hiddenKey in Prefs(context).hiddenApps)
        }
    }

    private val launchFixture get() = AppModel("Hidden Launch Fixture", null,
        instrumentation.context.packageName, LaunchGateFixtureActivity::class.java.name, false, Process.myUserHandle())

    private fun openHiddenMenu(scenario: ActivityScenario<MainActivity>) {
        scenario.open(Constants.FLAG_HIDDEN_APPS, launchFixture)
        checkNotNull(device.wait(Until.findObject(By.text("Hidden Launch Fixture")), timeout)).click()
        assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.open_app))), timeout))
    }

    @Test fun hiddenOpenLaunchesTheChosenApp() {
        FocusModeManager.disable(context)
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            openHiddenMenu(scenario)
            checkNotNull(device.findObject(By.desc(context.getString(R.string.open_app)))).click()
            assertTrue("hidden app did not open", device.wait(Until.hasObject(By.text("Camera fixture activity is open")), timeout))
            device.pressBack()
        }
    }

    @Test fun hiddenOpenIsBlockedByFocus() {
        FocusModeManager.setWhitelist(context, emptySet())
        FocusModeManager.enable(context, 30)
        try {
            assertFalse(FocusModeManager.isAppAllowed(context, launchFixture.appPackage))
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                openHiddenMenu(scenario)
                checkNotNull(device.findObject(By.desc(context.getString(R.string.open_app)))).click()
                assertTrue("blocked route did not return home", device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), timeout))
                assertFalse("Focus allowed the hidden app", device.wait(Until.hasObject(By.text("Camera fixture activity is open")), 2_000))
                assertEquals(context.packageName, device.currentPackageName)
            }
        } finally { FocusModeManager.disable(context) }
    }

    @Test fun hiddenOpenStillRequiresMindfulPause() {
        FocusModeManager.disable(context)
        AppLimitManager.setPauseOnly(context, launchFixture.appPackage)
        try {
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                openHiddenMenu(scenario)
                checkNotNull(device.findObject(By.desc(context.getString(R.string.open_app)))).click()
                assertTrue("hidden Open skipped mindful pause", device.wait(Until.hasObject(
                    By.text(context.getString(R.string.mindful_pause_question, "Hidden Launch Fixture"))), timeout))
                assertFalse(device.hasObject(By.text("Camera fixture activity is open")))
            }
        } finally { AppLimitManager.removeLimit(context, launchFixture.appPackage) }
    }

    @Test fun hiddenOpenStillChecksItsAppLimit() {
        assumeTrue("disposable emulator only", android.os.Build.HARDWARE == "ranchu" || android.os.Build.PRODUCT.contains("sdk_gphone"))
        FocusModeManager.disable(context)
        AppLimitManager.setLimit(context, launchFixture.appPackage, 15)
        try {
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS ignore")
            ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
                openHiddenMenu(scenario)
                checkNotNull(device.findObject(By.desc(context.getString(R.string.open_app)))).click()
                assertTrue("hidden Open skipped the limit check", device.wait(Until.hasObject(
                    By.text(context.getString(R.string.limit_usage_access_missing))), timeout))
                assertFalse(device.hasObject(By.text("Camera fixture activity is open")))
            }
        } finally {
            AppLimitManager.removeLimit(context, launchFixture.appPackage)
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }

    @Test fun ordinaryDrawerLongPressDoesNotOfferOpen() {
        ActivityScenario.launch(MainActivity::class.java).useWithEvidence { scenario ->
            scenario.open(Constants.FLAG_LAUNCH_APP)
            checkNotNull(device.wait(Until.findObject(By.text("Picker Fixture")), timeout)).longClick()
            assertTrue(device.wait(Until.hasObject(By.desc(context.getString(R.string.a11y_app_info))), timeout))
            assertFalse(device.hasObject(By.desc(context.getString(R.string.open_app))))
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
