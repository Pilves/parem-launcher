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

    @Test fun normalDrawerDoesNotShowPickerTitle() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.open(Constants.FLAG_LAUNCH_APP)
            assertTrue(device.wait(Until.hasObject(By.text("Picker Fixture")), timeout))
            assertFalse(device.hasObject(By.res(context.packageName, "drawerPickerTitle")))
        }
    }
}
