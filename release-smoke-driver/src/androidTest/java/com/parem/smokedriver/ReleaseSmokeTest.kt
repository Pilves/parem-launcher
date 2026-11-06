package com.parem.smokedriver

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
import org.junit.Test
import org.junit.runner.RunWith

/** Only platform APIs: it runs against the minified release, not debug classes. */
@RunWith(AndroidJUnit4::class)
class ReleaseSmokeTest {
    @Test
    fun releaseLauncherAndSettingsRoundTrip() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = "com.parem.launcher"
        device.executeShellCommand("cmd package set-home-activity $pkg/com.parem.launcher.MainActivity")
        device.pressHome()
        assertTrue("onboarding missing", device.wait(Until.hasObject(By.res(pkg, "calmScreen")), 15_000))
        UiScrollable(UiSelector().resourceId("$pkg:id/calmScreen"))
            .scrollIntoView(UiSelector().resourceId("$pkg:id/btnDone"))
        val done = device.wait(Until.findObject(By.res(pkg, "btnDone")), 10_000)
        assertNotNull("onboarding Done missing", done)
        done.click()
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
        setWallpaper(device, pkg, true)
        val export = device.wait(Until.findObject(By.res(pkg, "exportSettings")), 10_000)
        assertNotNull("settings did not open", export)
        export.click()
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline &&
            device.executeShellCommand("ls /sdcard/Download/parem_settings.json").trim() != "/sdcard/Download/parem_settings.json") {
            SystemClock.sleep(200)
        }
        assertEquals("settings export missing", "/sdcard/Download/parem_settings.json", device.executeShellCommand("ls /sdcard/Download/parem_settings.json").trim())
        setWallpaper(device, pkg, false)
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
        assertTrue("settings did not return after import", device.wait(Until.hasObject(By.res(pkg, "scrollView")), 15_000))
        val scroll = UiScrollable(UiSelector().resourceId("$pkg:id/scrollView"))
        assertTrue(scroll.scrollIntoView(UiSelector().resourceId("$pkg:id/dailyWallpaperRow")))
        assertTrue("import did not restore wallpaper preference", device.wait(
            Until.hasObject(By.res(pkg, "dailyWallpaperRow").checked(true)), 15_000))
    }

    private fun setWallpaper(device: UiDevice, pkg: String, enabled: Boolean) {
        val scroll = UiScrollable(UiSelector().resourceId("$pkg:id/scrollView"))
        assertTrue("daily wallpaper row missing", scroll.scrollIntoView(UiSelector().resourceId("$pkg:id/dailyWallpaperRow")))
        val row = device.wait(Until.findObject(By.res(pkg, "dailyWallpaperRow")), 10_000)
        assertNotNull("daily wallpaper switch missing", row)
        val before = device.executeShellCommand("dumpsys jobscheduler")
        val bounds = row.visibleBounds
        device.click(bounds.right - 20, bounds.centerY())
        assertTrue("wallpaper switch did not change", device.wait(
            Until.hasObject(By.res(pkg, "dailyWallpaperRow").checked(enabled)), 10_000))
        if (enabled) {
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var jobSeen = false
            while (!jobSeen && SystemClock.elapsedRealtime() < deadline) {
                val after = device.executeShellCommand("dumpsys jobscheduler")
                val service = "$pkg/androidx.work.impl.background.systemjob.SystemJobService"
                jobSeen = after.lines().filter { service in it }.toSet()
                    .minus(before.lines().filter { service in it }.toSet()).isNotEmpty()
                if (!jobSeen) SystemClock.sleep(200)
            }
            assertTrue("WorkManager job not enqueued", jobSeen)
        }
        scroll.scrollToBeginning(20)
    }
}
