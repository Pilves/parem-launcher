package com.parem.launcher

import android.app.WallpaperManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.lifecycleScope
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.ui.BadHabitDialogs
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith

/** Capture on a disposable CI emulator only, never on a person's phone. */
@RunWith(AndroidJUnit4::class)
class PauseFilmCapture {
    @Test fun recordPause() {
        assumeTrue("explicit capture workflow only", InstrumentationRegistry.getArguments().getString("filmCapture") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        assertTrue("capture is emulator-only", device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        val wallpaper = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(247, 245, 240)) }
        WallpaperManager.getInstance(context).setBitmap(wallpaper)
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
            appTheme = AppCompatDelegate.MODE_NIGHT_NO
        }
        AppLimitManager.setPauseOnly(context, "com.parem.fixture.feed")
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        device.pressHome()
        assertTrue("home missing", device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 15000))
        device.waitForIdle()
        SystemClock.sleep(1800)
        instrumentation.runOnMainSync {
            val activity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            BadHabitDialogs.gateLaunch(activity, activity.lifecycleScope, { !activity.isFinishing },
                "Feed", "com.parem.fixture.feed", {})
        }
        assertTrue("pause sheet missing", device.wait(Until.hasObject(By.text(context.getString(R.string.mindful_pause_question, "Feed"))), 10000))
        SystemClock.sleep(7000)
    }
}
