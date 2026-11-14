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
import java.io.File
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.ui.WidgetPickerOverflow
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
            val container = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            val handle = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 10) }
            val title = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 45) }
            val search = View(context).apply { layoutParams = LinearLayout.LayoutParams(100, 60) }
            val list = ListView(context).apply { layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f) }
            val header = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
            list.addHeaderView(header, null, false)
            list.adapter = ArrayAdapter(context, android.R.layout.simple_list_item_1, (0..19).map { "Widget $it" })
            container.addView(handle)
            container.addView(title)
            container.addView(search)
            container.addView(list)
            val overflow = WidgetPickerOverflow(container, title, search, list, header, 30)
            fun layout(height: Int) {
                container.measure(View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
                container.layout(0, 0, 240, height)
            }

            layout(200)
            overflow.update()
            assertSame("normal title should remain pinned", container, title.parent)
            assertSame("normal search should remain pinned", container, search.parent)
            assertSame("widget ListView remains the single scrolling list", container, list.parent)
            assertTrue("normal list needs room", list.height >= 30)

            // Fixed handle/title/search alone are 115px, greater than the viewport.
            layout(100)
            overflow.update()
            layout(100)
            assertSame("short-window title must be in the list header", header, title.parent)
            assertSame("short-window search must be in the list header", header, search.parent)
            assertSame("widget ListView must not be wrapped by another scroller", container, list.parent)
            assertEquals(1, list.headerViewsCount)
            assertTrue("list must remain usable", list.height > 0 && list.canScrollVertically(1))
            list.setSelection(0)
            assertTrue("title and search can be reached from the top", title.parent === header && search.parent === header)
            list.setSelection(list.count - 1)
            assertEquals("last item remains addressable", 21, list.count)

            layout(200)
            overflow.update()
            layout(200)
            assertSame("title returns to its pinned position", container, title.parent)
            assertSame("search returns to its pinned position", container, search.parent)
            assertSame(list, container.getChildAt(3))
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
                lateinit var header: LinearLayout
                lateinit var container: LinearLayout
                scenario.onActivity { activity ->
                    container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    val title = TextView(activity).apply {
                        text = "Widget picker test"
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 110)
                    }
                    search = EditText(activity).apply {
                        hint = "Widget search test"
                        showSoftInputOnFocus = false
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 120)
                    }
                    header = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    list = ListView(activity).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                        addHeaderView(header, null, false)
                    }
                    val adapter = ArrayAdapter(activity, android.R.layout.simple_list_item_1, (0..19).map { "Provider $it" })
                    list.adapter = adapter
                    search.doAfterTextChanged { adapter.filter.filter(it?.toString().orEmpty()) }
                    list.setOnItemClickListener { _, _, position, _ ->
                        val item = adapter.getItem(position - list.headerViewsCount)
                        if (item != null) selected.set(item)
                    }
                    container.addView(title)
                    container.addView(search)
                    container.addView(list)
                    dialog = Dialog(activity)
                    dialog.setContentView(container)
                    val overflow = WidgetPickerOverflow(container, title, search, list, header, 120)
                    dialog.show()
                    dialog.window!!.setLayout(500, 500)
                    container.post { overflow.update() }
                }
                try {
                    assertTrue("initial search missing", device.wait(Until.hasObject(By.clazz(EditText::class.java)), 10_000))
                    scenario.onActivity {
                        list.setSelection(15)
                        assertTrue("initial search did not take focus", search.requestFocus())
                        dialog.window!!.setLayout(500, 200)
                    }
                    val shrinkDeadline = android.os.SystemClock.elapsedRealtime() + 10_000
                    var movedIntoHeader = false
                    while (!movedIntoHeader && android.os.SystemClock.elapsedRealtime() < shrinkDeadline) {
                        scenario.onActivity { movedIntoHeader = container.height <= 200 && search.parent === header }
                        if (!movedIntoHeader) android.os.SystemClock.sleep(50)
                    }
                    assertTrue("short-window layout did not move search into the header", movedIntoHeader)
                    val focused = device.wait(Until.hasObject(By.clazz(EditText::class.java).focused(true)), 10_000)
                    if (!focused) {
                        context.getExternalFilesDir(null)?.let { directory ->
                            runCatching { device.dumpWindowHierarchy(File(directory, "widget-overflow-failure.xml")) }
                            runCatching { device.takeScreenshot(File(directory, "widget-overflow-failure.png")) }
                        }
                    }
                    assertTrue("search was not reachable with input focus after shrinking", focused)
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
                } finally {
                    scenario.onActivity { dialog.dismiss() }
                }
            }
        } finally {
            instrumentation.setInTouchMode(true)
        }
    }

}
