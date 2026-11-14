package com.parem.launcher

import android.os.Build
import android.os.SystemClock
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.HomeFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Opt-in screenshots of the actual widget picker on a disposable emulator. */
@RunWith(AndroidJUnit4::class)
class WidgetPickerCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    @Test fun captureWidgetPicker() {
        assumeTrue(args.getString("widget_picker_capture") == "true")
        assumeTrue(BuildConfig.DEBUG && device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        val font = requireNotNull(args.getString("widget_picker_font"))
        val wantIme = args.getString("widget_picker_ime") == "on"
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION; textSizeScale = 1f; autoShowKeyboard = wantIme }
        val selected = AtomicReference<String>()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertTrue("font scale ${activity.resources.configuration.fontScale} != $font",
                    kotlin.math.abs(activity.resources.configuration.fontScale - font.toFloat()) < 0.05f)
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                (host.childFragmentManager.primaryNavigationFragment as HomeFragment).widgetController!!.showWidgetPicker {
                    selected.set(it.provider.flattenToString())
                }
            }
            assertTrue("widget picker missing", device.wait(Until.hasObject(By.clazz("android.widget.ListView")), 30_000))
            assertTrue("widget picker title missing", device.wait(Until.hasObject(By.text(context.getString(R.string.add_widget))), 10_000))
            val search = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 10_000)
                ?: throw AssertionError("widget search missing")
            if (wantIme) search.click()
            val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
            assertTrue("default IME unavailable", imePackage.isNotBlank() && imePackage != "null")
            val expectedWindow = if (wantIme) device.wait(Until.hasObject(By.pkg(imePackage)), 15_000)
                else device.wait(Until.gone(By.pkg(imePackage)), 15_000)
            assertTrue("IME window state not observed: requested=$wantIme", expectedWindow)
            if (wantIme) assertTrue("widget search must have focus", device.wait(Until.hasObject(By.clazz("android.widget.EditText").focused(true)), 5_000))
            val deadline = SystemClock.elapsedRealtime() + 15_000
            var observed: Boolean? = null
            while (SystemClock.elapsedRealtime() < deadline) {
                observed = Regex("mInputShown=(true|false)").find(device.executeShellCommand("dumpsys input_method"))
                    ?.groupValues?.get(1)?.toBooleanStrictOrNull()
                if (observed == wantIme) break
                SystemClock.sleep(100)
            }
            assertTrue("IME reported=$observed requested=$wantIme", observed == wantIme)
            device.waitForIdle()
            val label = if (wantIme) "on" else "off"
            device.executeShellCommand("screencap -p /data/local/tmp/parem-widget-picker-font$font-ime$label.png")
            if (font == "1.0" && !wantIme) {
                search.text = "no such synthetic widget"
                assertTrue("empty search state missing", device.wait(Until.hasObject(By.text(context.getString(R.string.no_widgets_found))), 5_000))
                checkNotNull(device.findObject(By.text(context.getString(R.string.search_history_clear)))).click()
                val refreshedSearch = checkNotNull(device.findObject(By.clazz("android.widget.EditText")))
                refreshedSearch.text = "Stress widget 000"
                val row = device.wait(Until.findObject(By.textStartsWith("Stress widget 000")), 10_000)
                    ?: throw AssertionError("synthetic widget row missing")
                assertTrue("widget row has no size: ${row.text}", row.text.contains("×"))
                row.click()
                val selectionDeadline = SystemClock.elapsedRealtime() + 5_000
                while (selected.get() == null && SystemClock.elapsedRealtime() < selectionDeadline) SystemClock.sleep(50)
                assertEquals(android.content.ComponentName("com.parem.widgetstress", "com.parem.widgetstress.Provider000").flattenToString(), selected.get())
            } else {
                device.pressBack()
                if (!device.wait(Until.gone(By.clazz("android.widget.ListView")), 1_000)) device.pressBack()
            }
            assertTrue("widget picker did not close", device.wait(Until.gone(By.clazz("android.widget.ListView")), 5_000))
        }
    }
}
