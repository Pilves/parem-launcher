package com.parem.launcher

import android.os.Build
import android.os.SystemClock
import androidx.core.os.bundleOf
import androidx.navigation.findNavController
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.getAppsList
import com.parem.launcher.helper.AppListRebuilder
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in visual evidence on a disposable emulator, never an ordinary device. */
@RunWith(AndroidJUnit4::class)
class DrawerPickerCaptureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    @Test fun captureAssignmentAndHiddenApps() {
        assumeTrue(args.getString("drawer_picker_capture") == "true")
        assumeTrue(BuildConfig.DEBUG && context.packageName == "com.parem.launcher.debug")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        check(device.executeShellCommand("getprop ro.kernel.qemu").trim() == "1")
        val font = requireNotNull(args.getString("drawer_picker_font"))
        val ime = requireNotNull(args.getString("drawer_picker_ime"))
        val wantIme = ime == "on"
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
            textSizeScale = 1.0f
            autoShowKeyboard = wantIme
            hiddenApps = mutableSetOf()
        }
        val apps = runBlocking { getAppsList(context, Prefs(context), includeRegularApps = true) }
            .filter { it.appPackage.isNotEmpty() && it.appLabel.isNotBlank() && it.user == android.os.Process.myUserHandle() }
            .distinctBy { it.appPackage }
        assertTrue("capture requires at least three launcher apps", apps.size >= 3)
        for ((name, flag) in listOf("assignment" to Constants.FLAG_SET_HOME_APP_1, "hidden" to Constants.FLAG_HIDDEN_APPS)) {
            if (flag == Constants.FLAG_SET_HOME_APP_1) {
                Prefs(context).apply {
                    setHomeAppName(1, apps.first().appLabel)
                    setHomeAppPackage(1, apps.first().appPackage)
                    setHomeAppActivityClassName(1, apps.first().activityClassName)
                    setHomeAppUser(1, apps.first().user.toString())
                    setHomeAppUrl(1, "")
                }
            }
            if (flag == Constants.FLAG_HIDDEN_APPS) {
                Prefs(context).hiddenApps = apps.take(3).mapTo(mutableSetOf()) {
                    AppListRebuilder.hiddenKey(it.appPackage, it.user.toString())
                }
            }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                try {
                    var hardware = false
                    scenario.onActivity { activity ->
                        val config = activity.resources.configuration
                        assertTrue("font scale not applied", kotlin.math.abs(config.fontScale - font.toFloat()) < 0.05f)
                        hardware = config.keyboard != android.content.res.Configuration.KEYBOARD_NOKEYS &&
                            config.hardKeyboardHidden != android.content.res.Configuration.HARDKEYBOARDHIDDEN_YES
                        activity.findNavController(R.id.nav_host_fragment).navigate(
                            R.id.appListFragment, bundleOf(Constants.Key.FLAG to flag)
                        )
                    }
                    val title = context.getString(if (flag == Constants.FLAG_HIDDEN_APPS) R.string.hidden_apps else R.string.select_app)
                    assertTrue("route-specific picker title missing", device.wait(
                        Until.hasObject(By.res(context.packageName, "drawerPickerTitle").text(title)), 15_000))
                    assertTrue("expected real app row missing from $name", device.wait(
                        Until.hasObject(By.res(context.packageName, "appTitle").textStartsWith(apps.first().appLabel)), 15_000))
                    if (flag == Constants.FLAG_SET_HOME_APP_1) assertTrue("current assignment missing", device.wait(
                        Until.hasObject(By.res(context.packageName, "drawerPickerCurrent").textContains(apps.first().appLabel)), 15_000))
                    val searchId = context.resources.getResourceName(androidx.appcompat.R.id.search_src_text)
                    val field = checkNotNull(device.wait(Until.findObject(By.res(searchId)), 15_000))
                    if (!wantIme) {
                        assertFalse("IME-off preference was not applied", Prefs(context).autoShowKeyboard)
                        assertFalse("IME-off picker should not focus search on entry", field.isFocused)
                    }
                    if (wantIme && hardware) field.click()
                    val deadline = SystemClock.elapsedRealtime() + 15_000
                    var shown: Boolean? = null
                    while (SystemClock.elapsedRealtime() < deadline) {
                        shown = Regex("mInputShown=(true|false)").find(device.executeShellCommand("dumpsys input_method"))
                            ?.groupValues?.get(1)?.toBooleanStrictOrNull()
                        if (shown == wantIme) break
                        SystemClock.sleep(100)
                    }
                    assertTrue("IME state not verified: wanted=$wantIme actual=$shown", shown == wantIme)
                    if (wantIme) assertTrue("search has no focus", device.wait(
                        Until.hasObject(By.res(searchId).focused(true)), 5_000))
                    val imePackage = device.executeShellCommand("settings get secure default_input_method").trim().substringBefore('/')
                    assertTrue("default IME unavailable", imePackage.isNotBlank() && imePackage != "null")
                    assertTrue("IME window state incorrect", if (wantIme) device.wait(Until.hasObject(By.pkg(imePackage)), 15_000)
                        else device.wait(Until.gone(By.pkg(imePackage)), 15_000))
                    device.waitForIdle()
                    val file = File(requireNotNull(context.getExternalFilesDir(null)), "ux-pick2-$name-font$font-ime$ime.png")
                    assertTrue("screenshot failed", device.takeScreenshot(file))
                    device.executeShellCommand("mkdir -p /data/local/tmp/parem-drawer-picker-captures")
                    device.executeShellCommand("cp ${file.absolutePath} /data/local/tmp/parem-drawer-picker-captures/${file.name}")
                    if (!wantIme) {
                        field.click()
                        assertTrue("explicit tap should focus picker search", device.wait(
                            Until.hasObject(By.res(searchId).focused(true)), 5_000))
                        val tapDeadline = SystemClock.elapsedRealtime() + 15_000
                        var tapShown = false
                        while (SystemClock.elapsedRealtime() < tapDeadline) {
                            tapShown = Regex("mInputShown=(true|false)").find(device.executeShellCommand("dumpsys input_method"))
                                ?.groupValues?.get(1) == "true"
                            if (tapShown) break
                            SystemClock.sleep(100)
                        }
                        assertTrue("explicit tap should open keyboard", tapShown)
                        assertTrue("IME window missing after tap", device.wait(Until.hasObject(By.pkg(imePackage)), 15_000))
                    }
                } catch (failure: Throwable) {
                    try {
                        saveFailureEvidence(name, font, ime)
                    } catch (diagnostic: Throwable) {
                        failure.addSuppressed(diagnostic)
                    }
                    throw failure
                }
            }
        }
    }

    private fun saveFailureEvidence(route: String, font: String, ime: String) {
        val directory = requireNotNull(context.getExternalFilesDir(null))
        val base = "failure-$route-font$font-ime$ime"
        val hierarchy = File(directory, "$base.xml")
        val out = "/data/local/tmp/parem-drawer-picker-captures"
        val issues = mutableListOf<Throwable>()
        fun verify(path: String, operation: String) {
            val result = device.executeShellCommand("stat -c %s $path").trim()
            check((result.toLongOrNull() ?: 0) > 0) { "$operation did not produce a nonempty file: $result" }
        }
        runCatching {
            val result = device.executeShellCommand("mkdir -p $out")
            val listing = device.executeShellCommand("ls -ld $out")
            check(listing.contains("parem-drawer-picker-captures")) { "diagnostic directory: $result $listing" }
        }.onFailure(issues::add)
        runCatching {
            val target = "$out/$base.png"
            val result = device.executeShellCommand("screencap -p $target")
            android.util.Log.i("DrawerPickerDiag", "screencap output: $result")
            verify(target, "screencap")
        }.onFailure(issues::add)
        runCatching {
            device.dumpWindowHierarchy(hierarchy)
            check(hierarchy.isFile) { "failure hierarchy missing" }
            // Bounded logcat fallback survives shell-copy failures.
            hierarchy.readText().take(100_000).chunked(2_000).forEachIndexed { index, chunk ->
                android.util.Log.i("DrawerPickerDiag", "hierarchy[$index]: $chunk")
            }
            val target = "$out/$base.xml"
            val result = device.executeShellCommand("cp ${hierarchy.absolutePath} $target")
            android.util.Log.i("DrawerPickerDiag", "hierarchy copy output: $result")
            verify(target, "hierarchy copy")
        }.onFailure(issues::add)
        if (issues.isNotEmpty()) throw IllegalStateException("failure diagnostics incomplete").also { error ->
            issues.forEach(error::addSuppressed)
        }
    }
}
