package com.parem.launcher

import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.FolderApp
import com.parem.launcher.data.FolderGroup
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.FocusModeManager
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import com.parem.launcher.ui.CreateFolderDialog
import com.parem.launcher.ui.FocusModeDialog
import com.parem.launcher.ui.QuietListSheet
import com.parem.launcher.ui.ScreenTimeLimitDialog
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in visual capture on a disposable emulator; not part of the normal test suite. */
@RunWith(AndroidJUnit4::class)
class PickerCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    private fun prepare() {
        assumeTrue(args.getString("picker_capture") == "true")
        assumeTrue(BuildConfig.DEBUG && context.packageName == "com.parem.launcher.debug")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = com.parem.launcher.data.Constants.ONBOARDING_VERSION
            textSizeScale = 1.0f
            autoShowKeyboard = args.getString("picker_ime") == "on"
        }
    }

    private fun capture(name: String) {
        assertTrue("picker search missing", device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 15_000))
        val font = args.getString("picker_font") ?: error("missing font label")
        assertTrue("font scale not applied: ${context.resources.configuration.fontScale}",
            kotlin.math.abs(context.resources.configuration.fontScale - font.toFloat()) < 0.05f)
        var activityScale = 0f
        var keyboardConfig = ""
        var hardwareKeyboard = false
        instrumentation.runOnMainSync {
            val config = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().single().resources.configuration
            activityScale = config.fontScale
            keyboardConfig = "keyboard=${config.keyboard} hardKeyboardHidden=${config.hardKeyboardHidden}"
            hardwareKeyboard = config.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
                config.hardKeyboardHidden != android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES
        }
        assertTrue("activity font scale not applied: $activityScale", kotlin.math.abs(activityScale - font.toFloat()) < 0.05f)
        val wantIme = args.getString("picker_ime") == "on"
        if (wantIme && hardwareKeyboard) {
            // A visible hardware keyboard intentionally suppresses auto-show;
            // an explicit tap still lets us capture the software IME layout.
            checkNotNull(device.findObject(By.clazz("android.widget.EditText"))).click()
        }
        val deadline = SystemClock.elapsedRealtime() + 15_000
        var shown: Boolean? = null
        while (SystemClock.elapsedRealtime() < deadline) {
            val dump = device.executeShellCommand("dumpsys input_method")
            val match = Regex("mInputShown=(true|false)").find(dump)
            shown = match?.groupValues?.get(1)?.toBooleanStrictOrNull()
            if (shown == wantIme) break
            SystemClock.sleep(100)
        }
        if (shown != wantIme) runCatching { saveFailureEvidence() }
        val searchFocused = device.findObject(By.clazz("android.widget.EditText"))?.isFocused
        assertTrue("IME state not verified: wanted=$wantIme observed=$shown $keyboardConfig searchFocused=$searchFocused autoShow=${Prefs(context).autoShowKeyboard}", shown == wantIme)
        val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
        assertTrue("default IME package missing", imePackage.isNotBlank() && imePackage != "null")
        val imeWindowVisible = if (wantIme) device.wait(Until.hasObject(By.pkg(imePackage)), 15_000)
            else device.wait(Until.gone(By.pkg(imePackage)), 15_000)
        if (!imeWindowVisible) runCatching { saveFailureEvidence() }
        assertTrue("IME accessibility window did not reach requested visibility: wanted=$wantIme package=$imePackage", imeWindowVisible)
        val action = when (name) {
            "focus", "folder", "quiet" -> context.getString(R.string.quiet_continue)
            else -> null
        }
        if (action != null) assertTrue("picker action not visible above IME: $action",
            device.wait(Until.hasObject(By.desc(action)), 5_000))
        device.waitForIdle()
        val ime = args.getString("picker_ime") ?: error("missing IME label")
        val file = File(requireNotNull(context.getExternalFilesDir(null)), "ux-pick1-$name-font$font-ime$ime.png")
        assertTrue("screenshot failed", device.takeScreenshot(file))
        device.executeShellCommand("mkdir -p /data/local/tmp/parem-picker-captures")
        device.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/parem-picker-captures/${file.name}")
    }

    private fun clickTextPrefix(text: String) {
        var row = device.wait(Until.findObject(By.textStartsWith(text)), 5_000)
        repeat(6) {
            if (row == null) {
                device.findObject(By.scrollable(true))?.scroll(Direction.DOWN, 0.8f)
                row = device.wait(Until.findObject(By.textStartsWith(text)), 1_000)
            }
        }
        if (row == null) runCatching { saveFailureEvidence() }
        checkNotNull(row) { "row missing: $text" }.click()
    }

    private fun saveFailureEvidence() {
        val dir = File(context.filesDir, "picker-capture-failure").apply { mkdirs() }
        device.dumpWindowHierarchy(File(dir, "hierarchy.xml"))
        device.executeShellCommand("mkdir -p /data/local/tmp/parem-picker-captures")
        device.executeShellCommand("screencap -p /data/local/tmp/parem-picker-captures/failure.png")
        device.executeShellCommand("run-as ${context.packageName} cat files/picker-capture-failure/hierarchy.xml > /data/local/tmp/parem-picker-captures/hierarchy.xml")
    }

    @Test fun captureAllRoutes() {
        prepare()
        try {
            focus()
            folder()
            quiet()
            appLimit()
        } catch (failure: Throwable) {
            val recorded = File(context.filesDir, "picker-capture-failure/hierarchy.xml")
            if (!recorded.exists()) try { saveFailureEvidence() } catch (diagnostic: Exception) { failure.addSuppressed(diagnostic) }
            throw failure
        }
    }

    private fun focus() {
        val apps = runBlocking { getAppsList(context, Prefs(context), includeRegularApps = true) }
            .distinctBy { it.appPackage }.filter { it.appPackage.isNotEmpty() }
        assertTrue("capture needs at least three launcher apps", apps.size >= 3)
        FocusModeManager.disable(context)
        FocusModeManager.setWhitelist(context, apps.take(2).map { it.appPackage }.toSet())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { FocusModeDialog(it, apps.map { app -> app.appPackage to app.appLabel }).show() }
            clickTextPrefix(context.getString(R.string.focus_allowed_apps))
            capture("focus")
        }
    }

    private fun folder() {
        val apps = runBlocking { getAppsList(context, Prefs(context), includeRegularApps = true) }
            .filter { it.appPackage.isNotEmpty() }
        assertTrue("capture needs at least three launcher apps", apps.size >= 3)
        val selected = apps.take(2).map { FolderApp(it.appLabel, it.appPackage, it.activityClassName.orEmpty(), it.user.toString()) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { CreateFolderDialog(it, apps, FolderGroup("Folder", selected)) { _, _ -> }.show() }
            clickTextPrefix(context.getString(R.string.select_apps))
            capture("folder")
        }
    }

    private fun quiet() {
        val apps = runBlocking { getAppsList(context, Prefs(context), includeRegularApps = true) }
            .distinctBy { it.appPackage }.filter { it.appPackage.isNotEmpty() }
        assertTrue("capture needs at least three launcher apps", apps.size >= 3)
        QuietNotificationsManager.setAllowed(context, apps.take(2).map { it.appPackage }.toSet())
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                val fragment = requireNotNull(host.childFragmentManager.primaryNavigationFragment)
                QuietListSheet.startTurnOn(fragment, Prefs(context), { fragment.isAdded && fragment.view != null }, {})
            }
            capture("quiet")
        }
    }

    private fun appLimit() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity -> ScreenTimeLimitDialog(activity, emptyMap(), activity.lifecycleScope).show() }
            clickTextPrefix(context.getString(R.string.add_another_app))
            capture("limit")
        }
    }
}
