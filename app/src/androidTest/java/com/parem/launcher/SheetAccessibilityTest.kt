package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parem.launcher.ui.AppPickerAdapter
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.SheetCheckRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SheetAccessibilityTest {
    @Test
    fun radioRowsReportCheckedWithoutOnOffAndBindingIsSilent() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val dialog = BottomSheetMenu(activity)
                    .option("Off", checked = true, singleChoice = true) {}
                    .option("Choice", checked = false, singleChoice = true) {}
                    .option("Toggle", checked = true) {}
                    .show()
                try {
                    fun find(view: View, description: String): View? {
                        if (view.contentDescription == description) return view
                        if (view is ViewGroup) {
                            for (i in 0 until view.childCount) find(view.getChildAt(i), description)?.let { return it }
                        }
                        return null
                    }
                    fun node(view: View) = AccessibilityNodeInfoCompat.wrap(view.createAccessibilityNodeInfo())
                    val selected = node(requireNotNull(find(dialog.window!!.decorView, "Off")))
                    assertEquals("android.widget.RadioButton", selected.className)
                    assertTrue(selected.isCheckable)
                    assertTrue(selected.isChecked)
                    assertNull(selected.stateDescription)
                    val unselected = node(requireNotNull(find(dialog.window!!.decorView, "Choice")))
                    assertTrue(unselected.isCheckable)
                    assertFalse(unselected.isChecked)
                    assertNull(unselected.stateDescription)
                    val checkbox = node(requireNotNull(find(dialog.window!!.decorView, "Toggle")))
                    assertEquals("android.widget.CheckBox", checkbox.className)
                    assertTrue(checkbox.isChecked)
                    assertEquals(activity.getString(R.string.on), checkbox.stateDescription?.toString())
                } finally {
                    dialog.dismiss()
                }

                val row = SheetCheckRow(activity).apply { singleChoice = true }
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                root.addView(row)
                try {
                    var events = 0
                    val delegate = ViewCompat.getAccessibilityDelegate(row) ?: AccessibilityDelegateCompat()
                    ViewCompat.setAccessibilityDelegate(row, object : AccessibilityDelegateCompat() {
                        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                            delegate.onInitializeAccessibilityNodeInfo(host, info)
                        }
                        override fun sendAccessibilityEvent(host: View, eventType: Int) {
                            if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) events++
                            delegate.sendAccessibilityEvent(host, eventType)
                        }
                    })
                    row.bindChecked(true)
                    row.bindChecked(false)
                    assertEquals("binding must not announce selection changes", 0, events)
                    row.checked = true
                    assertEquals("a real selection change must be announced", 1, events)
                    row.checked = true
                    assertEquals("an unchanged selection must not be re-announced", 1, events)
                    val info = AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo())
                    assertEquals("android.widget.RadioButton", info.className)
                    assertTrue(info.isChecked)
                    assertNull(info.stateDescription)
                } finally {
                    root.removeView(row)
                }
            }
        }
    }

    @Test
    fun filteredPickerRowsKeepCheckboxSemanticsThroughRebinding() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var list: RecyclerView
            lateinit var adapter: AppPickerAdapter
            scenario.onActivity { activity ->
                adapter = AppPickerAdapter(
                    textColor = android.graphics.Color.BLACK,
                    maxSelected = 5,
                    entries = listOf(
                        AppPickerAdapter.Entry("a", "Alpha selected"),
                        AppPickerAdapter.Entry("b", "Alpha other"),
                        AppPickerAdapter.Entry("c", "Beta selected"),
                    ) + (0 until 30).map { AppPickerAdapter.Entry("other$it", "Other $it") },
                    initiallySelected = listOf("a", "c"),
                )
                list = RecyclerView(activity).apply {
                    layoutManager = LinearLayoutManager(activity)
                    this.adapter = adapter
                }
                activity.findViewById<ViewGroup>(android.R.id.content).addView(
                    list, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 400),
                )
            }
            try {
                device.waitForIdle()
                scenario.onActivity {
                    val first = requireNotNull(list.findViewHolderForAdapterPosition(0)).itemView
                    assertTrue(
                        "test requires RecyclerView's item accessibility delegate",
                        ViewCompat.getAccessibilityDelegate(first) is RecyclerViewAccessibilityDelegate.ItemDelegate,
                    )
                    adapter.filter("Alpha")
                    assertTrue("filter must leave an adapter update pending", list.hasPendingAdapterUpdates())
                    val pending = AccessibilityNodeInfoCompat.wrap(first.createAccessibilityNodeInfo())
                    assertEquals("android.widget.CheckBox", pending.className)
                    assertTrue(pending.isCheckable)
                    assertTrue(pending.isChecked)
                }
                device.waitForIdle()
                scenario.onActivity {
                    fun checkRow(position: Int, expected: Boolean) {
                        val row = requireNotNull(list.findViewHolderForAdapterPosition(position)).itemView
                        val info = AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo())
                        assertEquals("android.widget.CheckBox", info.className)
                        assertTrue(info.isCheckable)
                        assertEquals(expected, info.isChecked)
                    }
                    assertEquals(2, adapter.itemCount)
                    checkRow(0, true)
                    checkRow(1, false)
                    requireNotNull(list.findViewHolderForAdapterPosition(1)).itemView.performClick()
                    checkRow(1, true)
                    adapter.filter("Beta")
                }
                device.waitForIdle()
                scenario.onActivity {
                    val row = requireNotNull(list.findViewHolderForAdapterPosition(0)).itemView
                    val info = AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo())
                    assertEquals("Beta selected", row.contentDescription)
                    assertEquals("android.widget.CheckBox", info.className)
                    assertTrue(info.isCheckable)
                    assertTrue(info.isChecked)
                    adapter.filter("Alpha")
                }
                device.waitForIdle()
                scenario.onActivity {
                    val row = requireNotNull(list.findViewHolderForAdapterPosition(1)).itemView
                    val info = AccessibilityNodeInfoCompat.wrap(row.createAccessibilityNodeInfo())
                    assertEquals("Alpha other", row.contentDescription)
                    assertEquals("android.widget.CheckBox", info.className)
                    assertTrue(info.isCheckable)
                    assertTrue(info.isChecked)
                }
            } finally {
                scenario.onActivity { (list.parent as ViewGroup).removeView(list) }
            }
        }
    }
}
