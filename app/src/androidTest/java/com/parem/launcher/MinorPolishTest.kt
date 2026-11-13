package com.parem.launcher

import android.app.Activity
import android.app.Instrumentation
import android.content.IntentFilter
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.dpToPx
import com.parem.launcher.ui.AppPickerAdapter
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.OnboardingFragment
import com.parem.launcher.ui.addAppPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MinorPolishTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun skipOnboarding(): Prefs = Prefs(context).apply {
        firstOpen = false
        onboardingVersionSeen = Constants.ONBOARDING_VERSION
    }

    @Test
    fun pickerSearchAlignsWithSheetRowsWithAndWithoutParentInsets() {
        skipOnboarding()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val sheetInset = activity.resources.getDimensionPixelSize(R.dimen.sheet_padding_horizontal)
                for (parentInset in listOf(0, 16.dpToPx())) {
                    val parent = LinearLayout(activity).apply {
                        setPaddingRelative(parentInset, 0, parentInset, 0)
                    }
                    parent.addAppPicker(AppPickerAdapter(0, 1, emptyList()))
                    val search = parent.getChildAt(0) as EditText
                    assertEquals(sheetInset, parent.paddingStart + search.paddingStart)
                    assertEquals(sheetInset, parent.paddingEnd + search.paddingEnd)
                }
            }
        }
    }

    @Test
    fun swipeChoiceShowsTheActualDefaultBesideOpenApp() {
        skipOnboarding().apply {
            swipeLeftEnabled = true
            swipeRightEnabled = true
            swipeLeftAction = -1
            swipeRightAction = -1
            appPackageSwipeLeft = ""
            appPackageSwipeRight = ""
            appNameSwipeLeft = "Camera"
            appNameSwipeRight = "Phone"
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), 10_000))
            scenario.onActivity { it.findNavController(R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_settingsFragment) }
            assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "swipeLeftRow")), 10_000))
            val action = context.getString(R.string.open_app)
            for ((row, name) in listOf(R.id.swipeLeftRow to "Camera", R.id.swipeRightRow to "Phone")) {
                scenario.onActivity { it.findViewById<View>(row).performClick() }
                val choice = requireNotNull(device.wait(Until.findObject(By.desc("$action, $name")), 10_000))
                assertTrue("the displayed default should be checked", choice.isChecked)
                device.pressBack()
            }
        }
    }

    @Test
    fun checkedChoiceSummaryIsVisibleAsWellAsAccessible() {
        skipOnboarding()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = BottomSheetMenu(activity)
                    .option("Open app", summary = "Camera", checked = true, singleChoice = true) {}
                    .show()
                try {
                    fun findSummary(view: View): TextView? {
                        if (view is TextView && view.text.toString() == "Camera") return view
                        if (view is ViewGroup) for (i in 0 until view.childCount) {
                            findSummary(view.getChildAt(i))?.let { return it }
                        }
                        return null
                    }
                    val summary = requireNotNull(findSummary(dialog.window!!.decorView))
                    assertTrue("current choice summary should be visible", summary.isShown)
                } finally {
                    dialog.dismiss()
                }
            }
        }
    }

    @Test
    fun repeatedHomeSetupTapsDoNotStackSettingsScreens() {
        skipOnboarding()
        val monitor = Instrumentation.ActivityMonitor(
            IntentFilter(Settings.ACTION_HOME_SETTINGS),
            Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true,
        )
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                assertTrue(device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), 10_000))
                scenario.onActivity { activity ->
                    activity.findNavController(R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_onboardingFragment)
                }
                instrumentation.waitForIdleSync()
                scenario.onActivity { activity ->
                    val host = requireNotNull(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment))
                    val fragment = host.childFragmentManager.primaryNavigationFragment as OnboardingFragment
                    // Simulate a prior denial so this path opens HOME settings.
                    OnboardingFragment::class.java.getDeclaredField("askedForHome").apply {
                        isAccessible = true
                        setBoolean(fragment, true)
                    }
                    val button = activity.findViewById<View>(R.id.btnSetHome)
                    repeat(3) { button.performClick() }
                }
                assertEquals("only one HOME settings request should be dispatched", 1, monitor.hits)
            }
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
