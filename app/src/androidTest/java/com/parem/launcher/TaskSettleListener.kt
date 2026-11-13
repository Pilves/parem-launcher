package com.parem.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.runner.Description
import org.junit.runner.notification.RunListener

/** Keep orchestrator's next package clear from racing a task still exiting. */
class TaskSettleListener : RunListener() {
    override fun testFinished(description: Description) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = instrumentation.targetContext
        val packages = listOf(target.packageName, instrumentation.context.packageName)
        val manager = target.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        manager.appTasks.forEach { task ->
            try {
                task.finishAndRemoveTask()
            } catch (error: RuntimeException) {
                Log.w(TAG, "Could not finish app task", error)
            }
        }

        val packagePattern = packages.joinToString("|") { Regex.escape(it) }
        val taskPattern = Regex("(?:I=|A=\\d+:)(?:$packagePattern)(?=/|\\s|})")
        val deadline = SystemClock.elapsedRealtime() + 5_000
        do {
            val dump = try {
                ParcelFileDescriptor.AutoCloseInputStream(
                    instrumentation.uiAutomation.executeShellCommand("dumpsys -t 2 activity activities")
                ).bufferedReader().use { it.readText() }
            } catch (error: Exception) {
                Log.w(TAG, "Could not inspect activity tasks after ${description.displayName}", error)
                return
            }
            if (dump.isBlank() || !dump.contains("ACTIVITY MANAGER")) {
                Log.w(TAG, "Activity task dump unavailable after ${description.displayName}")
                return
            }
            val tasks = dump.lineSequence().filter {
                it.contains("Task{") && it.contains("type=standard") && taskPattern.containsMatchIn(it)
            }.toList()
            if (tasks.isEmpty()) {
                Log.i(TAG, "Activity tasks settled after ${description.displayName}")
                return
            }
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        Log.w(TAG, "Activity tasks did not settle within 5 seconds after ${description.displayName}")
    }

    private companion object {
        const val TAG = "TaskSettleListener"
    }
}
