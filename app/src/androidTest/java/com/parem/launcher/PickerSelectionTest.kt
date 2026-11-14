package com.parem.launcher

import android.os.Process
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.AppModel
import com.parem.launcher.data.Constants
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.AppPickerAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PickerSelectionTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun prepare() = Prefs(context).apply {
        firstOpen = false
        onboardingVersionSeen = Constants.ONBOARDING_VERSION
        hideSetDefaultLauncher = true
        autoShowKeyboard = false
    }

    private fun pressParentOption(root: View, description: String): Boolean {
        if (root.contentDescription == description) return root.performClick()
        if (root is ViewGroup) for (i in 0 until root.childCount) {
            if (pressParentOption(root.getChildAt(i), description)) return true
        }
        return false
    }

    private fun selectBetaAndCancel() {
        val beta = checkNotNull(device.wait(Until.findObject(By.desc("Beta")), 15_000)) { "Beta missing" }
        beta.click()
        assertTrue("Beta was not selected", device.wait(Until.hasObject(By.desc("Beta").checked(true)), 5_000))
        device.pressBack()
        assertTrue("child picker did not close", device.wait(Until.gone(By.desc("Beta")), 10_000))
    }

    private fun checkTwoTaps(delayMs: Long, expected: Set<String>) {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            lateinit var adapter: AppPickerAdapter
            scenario.onActivity { activity ->
                adapter = AppPickerAdapter(
                    textColor = android.graphics.Color.BLACK,
                    maxSelected = 5,
                    entries = listOf(AppPickerAdapter.Entry("a", "Alpha"), AppPickerAdapter.Entry("b", "Beta")),
                    initiallySelected = listOf("a", "b"),
                )
                list = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    this.adapter = adapter
                }
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 800),
                )
            }
            device.waitForIdle()
            var x = 0
            var y = 0
            scenario.onActivity {
                val row = requireNotNull(list.findViewHolderForAdapterPosition(1)).itemView
                val point = IntArray(2)
                row.getLocationOnScreen(point)
                x = point[0] + row.width / 2
                y = point[1] + row.height / 2
            }
            assertTrue(device.click(x, y))
            SystemClock.sleep(delayMs)
            assertTrue(device.click(x, y))
            device.waitForIdle()
            scenario.onActivity { assertEquals(expected, adapter.selectedIds.toSet()) }
        }
    }

    @Test fun rapidTapsDoNotDeselectTheNextRow() = checkTwoTaps(200, setOf("b"))

    @Test fun tapAfterGuardCanDeselectTheNextRow() = checkTwoTaps(550, emptySet())

    @Test fun folderChildBackDiscardsItsStagedEdits() {
        prepare()
        val user = Process.myUserHandle()
        val apps = listOf(
            AppModel("Alpha", null, "example.alpha", "AlphaActivity", user = user),
            AppModel("Beta", null, "example.beta", "BetaActivity", user = user),
        )
        val initial = FolderApp("Alpha", "example.alpha", "AlphaActivity", user.toString())
        val saved = AtomicReference<List<FolderApp>>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var parent: com.google.android.material.bottomsheet.BottomSheetDialog
            scenario.onActivity { activity ->
                parent = CreateFolderDialog(activity, apps, FolderGroup("Folder", listOf(initial))) { _, entries ->
                    saved.set(entries)
                }.show()
            }
            val chooser = checkNotNull(device.wait(Until.findObject(By.textStartsWith(context.getString(R.string.select_apps))), 15_000))
            chooser.click()
            selectBetaAndCancel()
            scenario.onActivity { assertTrue("folder Save missing", pressParentOption(parent.window!!.decorView, context.getString(R.string.save))) }
            val deadline = SystemClock.elapsedRealtime() + 5_000
            while (saved.get() == null && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
            assertEquals(listOf(initial), saved.get())
        }
    }

    @Test fun focusChildBackPreservesOriginalAndUnavailableEntries() {
        prepare()
        val original = setOf("example.alpha", "example.unavailable")
        FocusModeManager.disable(context)
        FocusModeManager.setWhitelist(context, original)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var parent: com.google.android.material.bottomsheet.BottomSheetDialog
            scenario.onActivity { activity ->
                parent = FocusModeDialog(activity, listOf("example.alpha" to "Alpha", "example.beta" to "Beta")).show()
            }
            val chooser = checkNotNull(device.wait(Until.findObject(By.textStartsWith(context.getString(R.string.focus_allowed_apps))), 15_000))
            chooser.click()
            selectBetaAndCancel()
            scenario.onActivity { assertTrue("Focus Save missing", pressParentOption(parent.window!!.decorView, context.getString(R.string.save))) }
            assertEquals(original, FocusModeManager.getWhitelist(context))
        }
    }
}
