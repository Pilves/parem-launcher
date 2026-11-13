package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
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
                    val delegate = requireNotNull(ViewCompat.getAccessibilityDelegate(row))
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
}
