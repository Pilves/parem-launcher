package com.parem.launcher

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith

/** Only platform APIs: it runs against the minified release, not debug classes. */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    @Test
    fun releaseLauncherAndSettingsRoundTrip() {
        assumeFalse("release-only check", BuildConfig.DEBUG)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = context.packageName
        val prefs = context.getSharedPreferences("com.parem.launcher", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("FIRST_OPEN", false).putInt("ONBOARDING_VERSION_SEEN", 999)
            .putInt("HOME_APPS_NUM", 4).commit()
        device.executeShellCommand("cmd package set-home-activity $pkg/com.parem.launcher.MainActivity")
        device.pressHome()
        assertTrue("release home not visible", device.wait(Until.hasObject(By.res(pkg, "homeAppsLayout")), 15_000))
        device.waitForIdle()
        device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
            device.displayWidth / 2, device.displayHeight / 4, 20)
        val search = device.wait(Until.findObject(By.res(pkg, "search_src_text")), 10_000)
        assertNotNull("drawer search missing", search)
        search.text = "2+2"
        assertTrue("drawer calculation missing", device.wait(Until.hasObject(By.textContains("4")), 10_000))
        device.pressBack()
        device.pressBack()
        assertTrue("home not restored", device.wait(Until.hasObject(By.res(pkg, "homeAppsLayout")), 10_000))
        device.swipe(device.displayWidth / 2, device.displayHeight / 2,
            device.displayWidth / 2, device.displayHeight / 2, 100)
        val settings = device.wait(Until.findObject(By.text("Settings")), 10_000)
        assertNotNull("home Settings action missing", settings)
        settings.click()
        val export = device.wait(Until.findObject(By.res(pkg, "exportSettings")), 10_000)
        assertNotNull("settings did not open", export)
        export.click()
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline &&
            device.executeShellCommand("test -s /sdcard/Download/parem_settings.json && echo ready").trim() != "ready") {
            SystemClock.sleep(200)
        }
        assertTrue("settings export missing", device.executeShellCommand("test -s /sdcard/Download/parem_settings.json && echo ready").trim() == "ready")
        prefs.edit().putInt("HOME_APPS_NUM", 3).commit()
        device.findObject(By.res(pkg, "importSettings")).click()
        val confirm = device.wait(Until.findObject(By.text("Confirm")), 10_000)
        assertNotNull("import confirmation missing", confirm)
        confirm.click()
        var file = device.wait(Until.findObject(By.text("parem_settings.json")), 5_000)
        if (file == null) {
            val downloads = device.wait(Until.findObject(By.text("Downloads")), 5_000)
            assertNotNull("Downloads missing from document picker", downloads)
            downloads.click()
            file = device.wait(Until.findObject(By.text("parem_settings.json")), 10_000)
        }
        assertNotNull("export absent from document picker", file)
        file.click()
        val importedBy = SystemClock.elapsedRealtime() + 15_000
        while (prefs.getInt("HOME_APPS_NUM", -1) != 4 && SystemClock.elapsedRealtime() < importedBy) SystemClock.sleep(100)
        assertEquals("import did not restore preference", 4, prefs.getInt("HOME_APPS_NUM", -1))

        // The import recreates the activity. Reopen settings if it returned home.
        if (!device.wait(Until.hasObject(By.res(pkg, "scrollView")), 10_000)) {
            assertTrue("home missing after import", device.wait(Until.hasObject(By.res(pkg, "homeAppsLayout")), 10_000))
            device.swipe(device.displayWidth / 2, device.displayHeight / 2,
                device.displayWidth / 2, device.displayHeight / 2, 100)
            device.wait(Until.findObject(By.text("Settings")), 10_000).click()
        }
        val scroll = UiScrollable(UiSelector().resourceId("$pkg:id/scrollView"))
        assertTrue("daily wallpaper row missing", scroll.scrollIntoView(UiSelector().resourceId("$pkg:id/dailyWallpaperRow")))
        val row = device.wait(Until.findObject(By.res(pkg, "dailyWallpaperRow")), 10_000)
        assertNotNull("daily wallpaper switch missing", row)
        // Tap the switch side, not the source link on the left.
        val bounds = row.visibleBounds
        device.click(bounds.right - 20, bounds.centerY())
        val scheduledBy = SystemClock.elapsedRealtime() + 15_000
        var scheduled = false
        while (!scheduled && SystemClock.elapsedRealtime() < scheduledBy) {
            val database = context.getDatabasePath("androidx.work.workdb")
            if (database.exists()) {
                SQLiteDatabase.openDatabase(database.path, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM WorkName JOIN WorkSpec ON WorkName.work_spec_id = WorkSpec.id WHERE WorkName.name = ? AND WorkSpec.state IN (0, 1)", arrayOf("WALLPAPER_WORKER_NAME")).use { cursor ->
                        scheduled = cursor.moveToFirst() && cursor.getInt(0) > 0
                    }
                }
            }
            if (!scheduled) SystemClock.sleep(200)
        }
        assertTrue("daily wallpaper job not enqueued", scheduled)
    }
}
