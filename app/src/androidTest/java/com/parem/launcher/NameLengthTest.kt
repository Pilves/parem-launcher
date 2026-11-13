package com.parem.launcher

import android.text.TextUtils
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FolderManager
import com.parem.launcher.ui.AppMenu
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.WebsiteDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NameLengthTest {
    private fun find(view: View, predicate: (View) -> Boolean): View? {
        if (predicate(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            find(view.getChildAt(i), predicate)?.let { return it }
        }
        return null
    }

    private fun findEditText(view: View): EditText? {
        if (view is EditText) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            findEditText(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun folderAndSlotNamesAreBoundedWithoutDiscardingLegacyLabels() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val oldName = "W".repeat(300)
        val folder = FolderGroup(oldName, listOf(FolderApp("Example", "example.package", userString = "0")))
        FolderManager(context).createFolder(1, oldName, folder.apps)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                val device = UiDevice.getInstance(instrumentation)
                assertTrue(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeApp1))), 10_000))
                scenario.onActivity { activity ->
                    val slot = activity.findViewById<TextView>(R.id.homeApp1)
                    assertEquals(oldName, slot.text.toString())
                    assertEquals(oldName, slot.contentDescription.toString())
                    assertEquals(2, slot.maxLines)
                    assertEquals(TextUtils.TruncateAt.END, slot.ellipsize)
                    val layout = requireNotNull(slot.layout)
                    assertEquals(2, layout.lineCount)
                    assertTrue("Oversized folder label should be ellipsized", layout.getEllipsisCount(1) > 0)

                    val dialog = CreateFolderDialog(activity, emptyList(), folder) { _, _ -> }.show()
                    try {
                        val input = requireNotNull(findEditText(dialog.window!!.decorView))
                        assertEquals("Opening an old folder must not truncate it", oldName, input.text.toString())
                        input.setText("F".repeat(41))
                        assertEquals(40, input.text.length)
                        input.setText("x".repeat(39) + "\uD83D\uDE00")
                        assertEquals("A surrogate pair must not be split", "x".repeat(39), input.text.toString())
                    } finally {
                        dialog.dismiss()
                    }

                    val host = requireNotNull(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment))
                    val fragment = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                    AppMenu.rename(fragment, oldName, onHome = true) { }
                }
                val input = requireNotNull(device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000))
                assertEquals("Opening an old slot name must not truncate it", oldName, input.text)
                input.setText("S".repeat(41))
                assertEquals(40, requireNotNull(input.text).length)
                device.pressBack()
                if (context.resources.configuration.smallestScreenWidthDp >= 600) {
                    try {
                        device.setOrientationLandscape()
                        device.waitForIdle()
                        scenario.onActivity { activity ->
                            val slot = activity.findViewById<TextView>(R.id.homeApp1)
                            assertEquals(2, slot.maxLines)
                            assertEquals(TextUtils.TruncateAt.END, slot.ellipsize)
                            assertEquals(oldName, slot.contentDescription.toString())
                        }
                    } finally {
                        device.setOrientationNatural()
                    }
                }
            }
        } finally {
            FolderManager(context).removeFolder(1)
        }
    }

    @Test
    fun websiteLabelsAreBoundedWithoutTruncatingUrls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val url = "https://" + "a".repeat(200) + ".example/path"
                val saved = mutableListOf<Pair<String, String>>()
                fun saveSite(name: String) {
                    val dialog = WebsiteDialog(activity) { savedUrl, label -> saved.add(savedUrl to label) }.show()
                    try {
                        val root = dialog.window!!.decorView
                        val address = requireNotNull(find(root) { it is EditText && it.hint == activity.getString(R.string.website_url_hint) } as? EditText)
                        val label = requireNotNull(find(root) { it is EditText && it.hint == activity.getString(R.string.website_label_hint) } as? EditText)
                        address.setText(url)
                        if (name.isNotEmpty()) {
                            label.setText("x".repeat(39) + "😀")
                            assertEquals("x".repeat(39), label.text.toString())
                        }
                        label.setText(name)
                        if (name.isNotEmpty()) assertEquals(40, label.text.length)
                        requireNotNull(find(root) { it.contentDescription == activity.getString(R.string.save) }).performClick()
                    } finally {
                        dialog.dismiss()
                    }
                }
                saveSite("x".repeat(300))
                saveSite("")
                assertEquals(listOf(url to "x".repeat(40), url to "a".repeat(40)), saved)
            }
        }
    }
}
