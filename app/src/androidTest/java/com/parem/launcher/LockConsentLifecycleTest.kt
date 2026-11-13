package com.parem.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.dismissLockSheetOnStop
import com.parem.launcher.ui.GrayscaleSheet
import com.parem.launcher.ui.QuietListSheet
import com.parem.launcher.ui.showLockConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LockConsentLifecycleTest {
    @Test
    fun grayscaleAndQuietSheetsCloseWhenHomeDestroysSettingsView() {
        // This test temporarily grants secure-settings access and changes the HOME role.
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val grantedBefore = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        if (!grantedBefore) device.executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        check(context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED)
        device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
        val home = By.res(context.resources.getResourceName(R.id.homeAppsLayout))
        val grayOption = By.desc(context.getString(R.string.grayscale_now))
        val quietAccessOption = By.desc(context.getString(R.string.quiet_open_settings))
        val quietContinue = By.desc(context.getString(R.string.quiet_continue))
        val quietNotNow = By.desc(context.getString(R.string.quiet_not_now))
        try {
            // Launch as HOME first. ActivityScenario's LAUNCHER intent creates a
            // different standard task that HOME can destroy and replace.
            check(device.pressHome()) { "initial HOME failed" }
            check(device.wait(Until.hasObject(home), 10_000)) { "initial home not shown" }
            lateinit var settingsFragment: Fragment
            fun openSettingsThen(open: (Fragment) -> Unit) {
                instrumentation.runOnMainSync {
                    val activity = ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
                    val nav = activity.findNavController(R.id.nav_host_fragment)
                    nav.navigate(R.id.action_mainFragment_to_settingsFragment)
                    val host = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                    host.childFragmentManager.executePendingTransactions()
                    settingsFragment = checkNotNull(host.childFragmentManager.primaryNavigationFragment)
                    open(settingsFragment)
                }
            }
            fun pressHomeAndCheckDismissed(vararg options: androidx.test.uiautomator.BySelector) {
                check(device.pressHome()) { "HOME failed" }
                check(device.wait(Until.hasObject(home), 10_000)) { "home not shown after HOME" }
                instrumentation.runOnMainSync {
                    check(settingsFragment.view == null) { "HOME did not destroy the Settings view" }
                }
                for (option in options) check(!device.hasObject(option)) { "sheet survived HOME: $option" }
            }
            openSettingsThen { fragment -> GrayscaleSheet.open(fragment, onPair = {}, onChanged = {}) }
            check(device.wait(Until.hasObject(grayOption), 10_000)) { "grayscale toggles missing" }
            pressHomeAndCheckDismissed(grayOption)

            openSettingsThen { fragment -> QuietListSheet.showNeedsAccess(fragment, starting = false, onChanged = {}) }
            check(device.wait(Until.hasObject(quietAccessOption), 10_000)) { "quiet access sheet missing" }
            pressHomeAndCheckDismissed(quietAccessOption)

            openSettingsThen { fragment -> QuietListSheet.startTurnOn(fragment, Prefs(context), { fragment.view != null }, {}) }
            check(device.wait(Until.hasObject(quietContinue), 15_000)) { "quiet app picker missing" }
            pressHomeAndCheckDismissed(quietContinue)

            openSettingsThen { fragment -> QuietListSheet.startTurnOn(fragment, Prefs(context), { fragment.view != null }, {}) }
            val continueButton = checkNotNull(device.wait(Until.findObject(quietContinue), 15_000)) { "quiet app picker missing" }
            continueButton.click()
            check(device.wait(Until.hasObject(quietNotNow), 10_000)) { "quiet disclosure missing" }
            pressHomeAndCheckDismissed(quietNotNow)
        } finally {
            if (!grantedBefore) device.executeShellCommand("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        }
    }

    @Test
    fun stoppingOwnerDismissesConsentAndDeclinesOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val declines = AtomicInteger()
        val dismissed = CountDownLatch(1)
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val owner = object : LifecycleOwner {
                    val registry = LifecycleRegistry(this)
                    override val lifecycle: Lifecycle = registry
                }
                owner.registry.currentState = Lifecycle.State.RESUMED
                val dialog = showLockConsent(activity, onAccept = { throw AssertionError("unexpected accept") }, onDecline = {
                    declines.incrementAndGet()
                    dismissed.countDown()
                }).dismissLockSheetOnStop(owner)
                owner.registry.currentState = Lifecycle.State.CREATED
                owner.registry.currentState = Lifecycle.State.DESTROYED
                assertFalse(dialog.isShowing)
            }
            check(dismissed.await(5, TimeUnit.SECONDS)) { "decline was not delivered" }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, declines.get())
        }
    }

    @Test
    fun acceptingOrChoosingAdminBeforeStopDoesNotDecline() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val accepts = AtomicInteger()
        val admins = AtomicInteger()
        val declines = AtomicInteger()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (useAdmin in listOf(false, true)) {
                scenario.onActivity { activity ->
                    val owner = object : LifecycleOwner {
                        val registry = LifecycleRegistry(this)
                        override val lifecycle: Lifecycle = registry
                    }
                    owner.registry.currentState = Lifecycle.State.RESUMED
                    val dialog = showLockConsent(activity,
                        onAccept = { accepts.incrementAndGet() },
                        onDecline = { declines.incrementAndGet() },
                        onUseAdmin = { admins.incrementAndGet() },
                    ).dismissLockSheetOnStop(owner)
                    val choice = activity.getString(if (useAdmin) R.string.lock_admin_option else R.string.lock_consent_accept)
                    fun findClickable(view: View): View? {
                        if (view.isClickable && view.contentDescription == choice) return view
                        if (view is ViewGroup) for (i in 0 until view.childCount) {
                            findClickable(view.getChildAt(i))?.let { return it }
                        }
                        return null
                    }
                    checkNotNull(dialog.window?.decorView?.let(::findClickable)) { "consent option missing" }.performClick()
                    owner.registry.currentState = Lifecycle.State.CREATED
                    owner.registry.currentState = Lifecycle.State.DESTROYED
                }
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            assertEquals(1, accepts.get())
            assertEquals(1, admins.get())
            assertEquals(0, declines.get())
        }
    }

}
