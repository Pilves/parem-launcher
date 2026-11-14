package com.parem.launcher

import android.app.Dialog
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import androidx.test.core.app.ActivityScenario
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import java.util.concurrent.atomic.AtomicReference
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.installWidgetPickerChrome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPickerOverflowTest {
    @Test fun shortWindowMovesChromeIntoTheSingleScrollableListAndRestoresIt() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 12, 0, 24) }
            val handle = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 10) }
            val title = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 45) }
            val search = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 60) }
            val list = ListView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, (0..19).map { "Widget $it" })
            container.addView(handle)
            container.addView(title)
            container.addView(search)
            container.addView(list)
            val chrome = installWidgetPickerChrome(container, title, search, list, 30, 24)
            fun layout(height: Int) {
                container.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                container.layout(0, 0, 240, height)
            }

            layout(200)
            assertSame("widget list remains a direct sibling", container, list.parent)
            assertSame("chrome remains a direct sibling", container, chrome.parent)
            assertTrue("normal list needs room", list.height >= 30)

            // Fixed handle/title/search alone are 115px, greater than the viewport.
            layout(100)
            assertSame("title remains in scrollable chrome", chrome, (title.parent as View).parent)
            assertSame("search remains in scrollable chrome", chrome, (search.parent as View).parent)
            assertTrue("short chrome and list both need space", chrome.height > 0 && list.height > 0)
            assertTrue("both ends of chrome must be scrollable", chrome.canScrollVertically(1))
            assertEquals("all provider rows remain addressable", 20, list.count)

            layout(200)
            assertTrue("list room is restored", list.height >= 30)
            assertSame(list, container.getChildAt(1))
        }
    }
    @Test fun nonTouchResizeKeepsSearchReachableAndSelectionCorrect() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        val selected = AtomicReference<String>()
        instrumentation.setInTouchMode(false)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                lateinit var dialog: Dialog
                lateinit var search: EditText
                lateinit var list: ListView
                lateinit var container: LinearLayout
                scenario.onActivity { activity ->
                    val density = activity.resources.displayMetrics.density
                    fun dp(value: Int) = (value * density).toInt()
                    container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(12), 0, dp(24)) }
                    val handle = View(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(32), dp(4)).apply { bottomMargin = dp(16) }
                    }
                    val title = TextView(activity).apply {
                        text = "Widget picker test"
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110)
                    }
                    search = EditText(activity).apply {
                        hint = "Widget search test"
                        showSoftInputOnFocus = false
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                    }
                    list = ListView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                    }
                    val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, (0..19).map { "Provider $it" })
                    list.adapter = adapter
                    search.doAfterTextChanged { adapter.filter.filter(it?.toString().orEmpty()) }
                    list.setOnItemClickListener { _, _, position, _ ->
                        val item = adapter.getItem(position)
                        if (item != null) selected.set(item)
                    }
                    container.addView(handle)
                    container.addView(title)
                    container.addView(search)
                    container.addView(list)
                    dialog = Dialog(activity)
                    dialog.setContentView(container)
                    installWidgetPickerChrome(container, title, search, list, dp(48), dp(24))
                    dialog.show()
                    dialog.window!!.setLayout(500, 500)
                }
                try {
                    assertTrue("initial search missing", device.wait(Until.hasObject(By.clazz(EditText::class.java)), 10_000))
                    scenario.onActivity {
                        list.setSelection(15)
                        assertTrue("initial search did not take focus", search.requestFocus())
                        dialog.window!!.setLayout(500, 200)
                    }
                    val shrinkDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
                    var shrunk = false
                    while (!shrunk && android.os.SystemClock.elapsedRealtime() < shrinkDeadline) {
                        scenario.onActivity { shrunk = container.height <= 200 && list.height > 0 }
                        if (!shrunk) android.os.SystemClock.sleep(50)
                    }
                    assertTrue("short-window layout did not leave list space", shrunk)
                    assertTrue("search was not reachable with input focus after shrinking",
                        device.wait(Until.hasObject(By.clazz(EditText::class.java).focused(true)), 10_000))
                    val field = checkNotNull(device.findObject(By.clazz(EditText::class.java).focused(true)))
                    assertTrue("focused search is not visible", field.visibleBounds.height() > 0)
                    field.text = "Provider 7"
                    scenario.onActivity { dialog.window!!.setLayout(500, 500) }
                    val row = device.wait(Until.findObject(By.res("android", "text1").text("Provider 7")), 10_000)
                        ?: throw AssertionError("filtered provider missing after re-expansion")
                    row.click()
                    val deadline = android.os.SystemClock.elapsedRealtime() + 5_000
                    while (selected.get() == null && android.os.SystemClock.elapsedRealtime() < deadline) android.os.SystemClock.sleep(50)
                    assertEquals("Provider 7", selected.get())
                } catch (failure: Throwable) {
                    runCatching { device.executeShellCommand("uiautomator dump /data/local/tmp/widget-overflow-failure.xml") }
                    runCatching { device.executeShellCommand("screencap -p /data/local/tmp/widget-overflow-failure.png") }
                    throw failure
                } finally {
                    scenario.onActivity { dialog.dismiss() }
                }
            }
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

}
