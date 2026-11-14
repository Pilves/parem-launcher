package com.parem.launcher

import android.widget.LinearLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.AppPickerAdapter
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.addAppPicker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class PickerSearchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    private fun prepare() = Prefs(context).apply {
        firstOpen = false
        onboardingVersionSeen = Constants.ONBOARDING_VERSION
        hideSetDefaultLauncher = true
        autoShowKeyboard = false
    }

    @Test fun selectedStayVisibleThroughSearchAndClear() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog
            scenario.onActivity { activity ->
                val adapter = AppPickerAdapter(
                    textColor = android.graphics.Color.BLACK, maxSelected = 5,
                    entries = listOf(AppPickerAdapter.Entry("a", "Alpha"), AppPickerAdapter.Entry("b", "Beta"), AppPickerAdapter.Entry("g", "Gamma")),
                    initiallySelected = listOf("b"),
                )
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; addAppPicker(adapter) }
                dialog = BottomSheetMenu(activity).title("Choose apps").customView(content)
                    .option(activity.getString(android.R.string.ok)) {}
                    .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true)
            }
            try {
                val search = checkNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
                search.text = "Alpha"
                assertTrue(device.wait(Until.hasObject(By.desc("Beta")), 5_000))
                assertTrue(device.wait(Until.hasObject(By.desc("Alpha")), 5_000))
                assertFalse(device.hasObject(By.desc("Gamma")))
                search.text = "no such app"
                assertTrue(device.wait(Until.hasObject(By.desc("Beta")), 5_000))
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.picker_no_other_apps_match))), 5_000))
                checkNotNull(device.wait(Until.findObject(By.text(context.getString(R.string.search_history_clear))), 5_000)).click()
                assertTrue(device.wait(Until.hasObject(By.desc("Gamma")), 5_000))
                assertTrue(checkNotNull(device.findObject(By.desc("Beta"))).isChecked)
            } finally {
                scenario.onActivity { dialog.dismiss() }
            }
        }
    }

    @Test fun singleChoiceIsAnActionAndSelectsOnOneTap() {
        prepare()
        val selected = AtomicReference<String>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var dialog: com.google.android.material.bottomsheet.BottomSheetDialog
            scenario.onActivity { activity ->
                lateinit var adapter: AppPickerAdapter
                adapter = AppPickerAdapter(
                    textColor = android.graphics.Color.BLACK, maxSelected = 1,
                    entries = listOf(AppPickerAdapter.Entry("a", "Alpha")), singleChoice = true,
                ) { selected.set(adapter.selectedIds.single()) }
                val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; addAppPicker(adapter) }
                dialog = BottomSheetMenu(activity).title("Choose app").customView(content)
                    .show(resizeForKeyboard = true, scrollContent = false, fitPicker = true)
            }
            try {
                val row = checkNotNull(device.wait(Until.findObject(By.text("Alpha")), 10_000))
                assertFalse("single choice must not expose a checkbox", row.className == "android.widget.CheckBox")
                row.click()
                val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
                while (selected.get() == null && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
                assertEquals("a", selected.get())
            } finally {
                scenario.onActivity { dialog.dismiss() }
            }
        }
    }
}
