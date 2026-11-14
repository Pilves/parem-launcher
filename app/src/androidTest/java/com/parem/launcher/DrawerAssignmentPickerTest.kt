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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DrawerAssignmentPickerTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val timeout = 15_000L
    private val fixture = AppModel("Picker Fixture", null, "fixture.picker", null, false, Process.myUserHandle())

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
            R.id.appListFragment, bundleOf(Constants.Key.FLAG to flag)
        )
        if (flag != Constants.FLAG_HIDDEN_APPS) model.appList.value = listOf(fixture)
    }

    @Test fun assignmentUsesSearchAndOneTapStillAssignsHomeSlot() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.open(Constants.FLAG_SET_HOME_APP_1)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")
                .text(context.getString(R.string.select_app))), timeout))
            val searchId = context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
            assertTrue("picker search must receive focus without forced IME", device.wait(
                Until.hasObject(By.res(searchId).focused(true)), timeout))
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.findObject(By.text("Picker Fixture")).click()
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), timeout))
            assertEquals("fixture.picker", Prefs(context).getHomeAppPackage(1))
        }
    }

    @Test fun hiddenAppTapOpensManagementInsteadOfLaunching() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.open(Constants.FLAG_HIDDEN_APPS)
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "drawerPickerTitle")
                .text(context.getString(R.string.hidden_apps))), timeout))
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            device.findObject(By.text("Picker Fixture")).click()
            assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.app_menu_show))), timeout))
        }
    }

    @Test fun emptyHiddenAppsShowsEmptyStateDespitePaddingRow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
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

    @Test fun normalDrawerDoesNotShowPickerTitle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.open(Constants.FLAG_LAUNCH_APP)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            assertFalse(device.hasObject(By.res(context.packageName, "drawerPickerTitle")))
        }
    }
}
