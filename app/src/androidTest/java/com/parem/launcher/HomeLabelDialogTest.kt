package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import android.widget.EditText
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
import com.parem.launcher.ui.AppMenu
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.WebsiteDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class HomeLabelDialogTest {
    private fun find(view: View, matches: (View) -> Boolean): View? {
        if (matches(view)) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) {
            find(view.getChildAt(i), matches)?.let { return it }
        }
        return null
    }

    @Test fun slotRenameRejectsInvisibleAndKeepsVisibleEmoji() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val prefs = Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
            setHomeAppName(1, "Old label")
            setHomeAppUrl(1, "https://example.com")
        }
        val saved = AtomicReference<String?>(null)
        val device = UiDevice.getInstance(instrumentation)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = requireNotNull(activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment))
                val fragment = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                AppMenu.rename(fragment, "Old label", onHome = true) {
                    prefs.setHomeAppName(1, it)
                    saved.set(it)
                }
            }
            var input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java)), 10_000))
            input.text = "\u200B\u200B"
            requireNotNull(device.findObject(By.desc(context.getString(R.string.save)))).click()
            device.waitForIdle()
            assertNull("invisible input must not be saved", saved.get())
            assertEquals("Old label", prefs.getHomeAppName(1))
            input = requireNotNull(device.findObject(By.clazz(EditText::class.java)))
            val visible = "👩‍👩‍👧‍👦"
            input.text = visible
            requireNotNull(device.findObject(By.desc(context.getString(R.string.save)))).click()
            assertTrue("visible name was not saved", device.wait(Until.gone(By.clazz(EditText::class.java)), 5_000))
            assertEquals(visible, saved.get())
            assertEquals(visible, prefs.getHomeAppName(1))
        }
    }

    @Test fun folderRejectsInvisibleAndWebsiteUsesHostFallback() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val folderSaved = mutableListOf<String>()
                val folder = FolderGroup("Old", listOf(FolderApp("Example", "example.package", userString = "0")))
                val dialog = CreateFolderDialog(activity, emptyList(), folder) { name, _ -> folderSaved.add(name) }.show()
                val root = dialog.window!!.decorView
                val input = requireNotNull(find(root) { it is EditText } as? EditText)
                val save = requireNotNull(find(root) { it.contentDescription == activity.getString(R.string.save) })
                input.setText("\u200B\u200B")
                save.performClick()
                assertTrue(folderSaved.isEmpty())
                input.setText("عَرَبِيّ")
                save.performClick()
                assertEquals(listOf("عَرَبِيّ"), folderSaved)
                dialog.dismiss()

                val websites = mutableListOf<String>()
                val website = WebsiteDialog(activity) { _, label -> websites.add(label) }.show()
                val websiteRoot = website.window!!.decorView
                val address = requireNotNull(find(websiteRoot) { it is EditText && it.hint == activity.getString(R.string.website_url_hint) } as? EditText)
                val label = requireNotNull(find(websiteRoot) { it is EditText && it.hint == activity.getString(R.string.website_label_hint) } as? EditText)
                address.setText("https://example.com/path")
                label.setText("\u200B\u200B")
                requireNotNull(find(websiteRoot) { it.contentDescription == activity.getString(R.string.save) }).performClick()
                assertEquals(listOf("example.com"), websites)
                website.dismiss()
            }
        }
    }
}
