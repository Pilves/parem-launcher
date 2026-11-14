package com.parem.launcher

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import java.io.File

/** A single real HOME key; verify the destination even if UiAutomator misses its event. */
internal fun pressHomeAndAwaitParem() {
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val context = instrumentation.targetContext
    val device = UiDevice.getInstance(instrumentation)
    val acknowledged = device.pressHome()
    Log.i("WidgetTestHome", "pressHome returned $acknowledged")
    val deadline = SystemClock.elapsedRealtime() + 15_000
    var resumed = false
    var homeVisible = false
    while (SystemClock.elapsedRealtime() < deadline) {
        instrumentation.runOnMainSync {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .any { it is MainActivity }
        }
        homeVisible = device.currentPackageName == context.packageName &&
            device.hasObject(By.pkg(context.packageName).res(context.resources.getResourceName(R.id.homeAppsLayout)))
        if (resumed && homeVisible) return
        SystemClock.sleep(100)
    }
    runCatching {
        val directory = requireNotNull(context.getExternalFilesDir(null))
        device.dumpWindowHierarchy(File(directory, "widget-home-failure.xml"))
        device.takeScreenshot(File(directory, "widget-home-failure.png"))
        device.executeShellCommand("mkdir -p /data/local/tmp/parem-diag")
        device.executeShellCommand("cp ${directory.absolutePath}/widget-home-failure.* /data/local/tmp/parem-diag/")
    }
    error("HOME did not reach Parem: acknowledged=$acknowledged resumed=$resumed homeVisible=$homeVisible package=${device.currentPackageName}")
}
