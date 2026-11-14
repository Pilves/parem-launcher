package com.parem.launcher

import android.graphics.Rect
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.accessibility.AccessibilityNodeInfo
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
import java.io.File
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
            try {
                // Wait for the real IME and stable, unobscured Save bounds before a single tap.
                fun readySave(): androidx.test.uiautomator.UiObject2 {
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    var previous: Rect? = null
                    var stable = 0
                    while (SystemClock.elapsedRealtime() < deadline) {
                        var imeVisible = false
                        var imeBottom = 0
                        scenario.onActivity { activity ->
                            val insets = activity.window.decorView.rootWindowInsets
                            imeVisible = insets?.isVisible(WindowInsets.Type.ime()) == true
                            imeBottom = insets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0
                        }
                        val save = device.findObject(By.desc(context.getString(R.string.save)))
                        val bounds = save?.visibleBounds
                        val clear = save != null && save.isEnabled && save.isClickable && bounds != null && !bounds.isEmpty && bounds.bottom <= device.displayHeight - imeBottom
                        stable = if (imeVisible && clear && bounds == previous) stable + 1 else 0
                        if (stable >= 2) {
                            val fresh = device.findObject(By.desc(context.getString(R.string.save)))
                            if (fresh != null && fresh.isEnabled && fresh.isClickable && fresh.visibleBounds == bounds) return fresh
                        }
                        previous = bounds?.let(::Rect)
                        SystemClock.sleep(100)
                    }
                    error("IME and unobscured Save did not settle")
                }
                fun fieldError(node: AccessibilityNodeInfo?): CharSequence? {
                    if (node == null) return null
                    if (node.className == EditText::class.java.name && node.error != null) return node.error
                    for (i in 0 until node.childCount) fieldError(node.getChild(i))?.let { return it }
                    return null
                }
                var input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java)), 10_000))
                readySave()
                input = requireNotNull(device.findObject(By.clazz(EditText::class.java)))
                input.text = "\u200B\u200B"
                readySave().click()
                val errorDeadline = SystemClock.elapsedRealtime() + 10_000
                var error: CharSequence? = null
                while (error == null && SystemClock.elapsedRealtime() < errorDeadline) {
                    error = instrumentation.uiAutomation.windows.firstNotNullOfOrNull { fieldError(it.root) }
                    if (error == null) SystemClock.sleep(100)
                }
                assertEquals(context.getString(R.string.app_menu_enter_name), error?.toString())
                assertNull("invisible input must not be saved", saved.get())
                assertEquals("Old label", prefs.getHomeAppName(1))
                input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java)), 10_000))
                val visible = "👩‍👩‍👧‍👦"
                input.text = visible
                readySave().click()
                assertTrue("visible name was not saved", device.wait(Until.gone(By.clazz(EditText::class.java)), 5_000))
                assertEquals(visible, saved.get())
                assertEquals(visible, prefs.getHomeAppName(1))
            } catch (failure: Throwable) {
                val directory = requireNotNull(context.getExternalFilesDir(null))
                runCatching { device.dumpWindowHierarchy(File(directory, "home-label-failure.xml")) }
                runCatching { device.takeScreenshot(File(directory, "home-label-failure.png")) }
                runCatching {
                    device.executeShellCommand("mkdir -p /data/local/tmp/parem-diag")
                    device.executeShellCommand("cp ${directory.absolutePath}/home-label-failure.* /data/local/tmp/parem-diag/")
                }
                runCatching {
                    scenario.onActivity { activity ->
                        val insets = activity.window.decorView.rootWindowInsets
                        Log.e("HomeLabelTest", "failure: windowFocus=${activity.hasWindowFocus()} activityAttached=${activity.window.decorView.isAttachedToWindow} ime=${insets?.isVisible(WindowInsets.Type.ime())} windows=${instrumentation.uiAutomation.windows.map { it.root?.className }}", failure)
                    }
                }
                throw failure
            }
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
