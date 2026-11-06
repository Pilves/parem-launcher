package com.parem.launcher

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Process
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Called explicitly before and after adb install -r; never via the ordinary smoke suite. */
@RunWith(AndroidJUnit4::class)
class Upgrade551Test {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val prefs = context.getSharedPreferences("com.parem.launcher", Context.MODE_PRIVATE)
    private val provider = ComponentName(instrumentation.context.packageName, "com.parem.launcher.LandscapeFixtureWidget")
    private val manager = AppWidgetManager.getInstance(context)

    private fun version(): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
    private fun home() {
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/com.parem.launcher.MainActivity")
        device.pressHome()
        // 6.0 offers new onboarding to upgraded users. Finish it through the UI.
        if (version() == 105L) {
            val notNow = device.wait(Until.findObject(By.res(context.packageName, "btnNotNow")), 3_000)
            notNow?.click()
            val done = UiSelector().resourceIdMatches(".*:id/btnDone")
            val calm = device.wait(Until.hasObject(By.res(context.packageName, "calmScreen")), 5_000)
            if (calm) {
                if (!device.findObject(done).exists()) UiScrollable(UiSelector().scrollable(true)).scrollIntoView(done)
                assertTrue("onboarding Done not available", device.findObject(done).waitForExists(5_000))
                device.findObject(done).click()
            }
        }
        assertTrue("home did not open", device.wait(Until.hasObject(By.res(context.packageName, "homeAppsLayout")), 20_000))
        assertTrue("fixture widget not visible", device.wait(Until.hasObject(By.text("WP28 fixture widget")), 20_000))
        assertTrue("home slot missing", device.wait(Until.hasObject(By.text("Upgrade settings")), 10_000))
        assertTrue("second home slot missing", device.wait(Until.hasObject(By.text("Upgrade tools")), 10_000))
        assertTrue("folder missing", device.wait(Until.hasObject(By.text("Upgrade folder")), 10_000))
    }

    @Test fun seed551() {
        assertEquals("Expected actual v5.5.1", 101L, version())
        val user = Process.myUserHandle().toString()
        val apps = JSONArray().put(JSONObject().put("appName", "Settings").put("packageName", "com.android.settings")
            .put("activityClassName", "com.android.settings.Settings").put("userString", user))
        val folder = JSONObject().put("2", JSONObject().put("name", "Upgrade folder").put("apps", apps))
        val host = AppWidgetHost(context, 1024)
        val widgetId = host.allocateAppWidgetId()
        assertTrue("could not bind fixture widget", manager.bindAppWidgetIdIfAllowed(widgetId, provider))
        assertEquals(provider, manager.getAppWidgetInfo(widgetId)?.provider)
        assertTrue(prefs.edit().clear()
            .putBoolean("FIRST_OPEN", false)
            .putInt("ONBOARDING_VERSION_SEEN", 2)
            .putInt("HOME_APPS_NUM", 4)
            .putInt("APP_THEME", 1) // Light is non-default in v5.5.1.
            .putString("APP_NAME_1", "Upgrade settings")
            .putString("APP_PACKAGE_1", "com.android.settings")
            .putString("APP_ACTIVITY_CLASS_NAME_1", "com.android.settings.Settings")
            .putString("APP_USER_1", user)
            .putString("APP_NAME_3", "Upgrade tools")
            .putString("APP_PACKAGE_3", "com.android.settings")
            .putString("APP_ACTIVITY_CLASS_NAME_3", "com.android.settings.Settings")
            .putString("APP_USER_3", user)
            .putString("FOLDER_DATA", folder.toString())
            .putString("WIDGET_IDS", widgetId.toString())
            .putString("WIDGET_HEIGHTS", "$widgetId:120")
            .putString("WIDGET_PROVIDERS", "$widgetId:${provider.flattenToString()}")
            .commit())
        home()
    }

    @Test fun verify600() {
        assertEquals("Expected v6.0.0", 105L, version())
        assertEquals(1, prefs.getInt("APP_THEME", -99))
        assertEquals("Upgrade settings", prefs.getString("APP_NAME_1", null))
        assertEquals("com.android.settings", prefs.getString("APP_PACKAGE_1", null))
        assertEquals("Upgrade tools", prefs.getString("APP_NAME_3", null))
        assertEquals("com.android.settings", prefs.getString("APP_PACKAGE_3", null))
        val folder = JSONObject(prefs.getString("FOLDER_DATA", null)!!).getJSONObject("2")
        assertEquals("Upgrade folder", folder.getString("name"))
        assertEquals("com.android.settings", folder.getJSONArray("apps").getJSONObject(0).getString("packageName"))
        val widgetId = prefs.getString("WIDGET_IDS", "")!!.toInt()
        assertEquals(provider, manager.getAppWidgetInfo(widgetId)?.provider)
        assertEquals("$widgetId:120", prefs.getString("WIDGET_HEIGHTS", null))
        home()
    }
}
