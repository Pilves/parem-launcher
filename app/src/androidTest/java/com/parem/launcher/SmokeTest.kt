package com.parem.launcher

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SmokeTest {

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
    fun rotatesOnLargeScreenOnly() {
        try {
            device.setOrientationLandscape()
            if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                assertTrue(
                    "home layout not shown in landscape",
                    device.wait(Until.hasObject(By.res(homeId)), TIMEOUT)
                )
                openDrawer()
            } else {
                // The portrait lock wins below sw600
                device.waitForIdle()
                assertEquals(0, device.displayRotation)
            }
        } finally {
            device.setOrientationNatural()
        }
    }

    private fun openDrawer() {
        val w = device.displayWidth
        val h = device.displayHeight
        device.swipe(w / 2, h * 3 / 4, w / 2, h / 4, 20)
        assertTrue(
            "drawer not opened",
            device.wait(Until.hasObject(By.res(searchId)), TIMEOUT)
        )
    }

    private companion object {
        const val TAG = "SmokeTest"
        const val TIMEOUT = 10_000L
        const val SETTINGS_PKG = "com.android.settings"
    }
}
