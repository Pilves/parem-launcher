package com.parem.launcher

import android.app.ActivityManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue

/** CI experiment: task-list absence is observable, but does not prove a pending system kill was canceled. */
internal fun withExperimentalTaskCleanup(block: (ActivityScenario<MainActivity>) -> Unit) {
    ActivityScenario.launch(MainActivity::class.java).use { scenario ->
        fun finishTask() {
            var taskId = -1
            scenario.onActivity {
                taskId = it.taskId
                Log.i("TaskCleanupExperiment", "finishAndRemoveTask task=$taskId")
                it.finishAndRemoveTask()
            }
            scenario.close()
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val deadline = SystemClock.elapsedRealtime() + 10_000
            while (manager.appTasks.any { it.taskInfo.id == taskId } && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(100)
            }
            val removed = manager.appTasks.none { it.taskInfo.id == taskId }
            Log.i("TaskCleanupExperiment", "task=$taskId absentFromAppTasks=$removed")
            assertTrue("experiment: task $taskId remained in appTasks", removed)
        }
        try {
            block(scenario)
        } catch (failure: Throwable) {
            try { finishTask() } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
            throw failure
        }
        finishTask()
    }
}
