package com.parem.launcher

import android.os.Build
import android.os.SystemClock
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.findNavController
import androidx.navigation.fragment.NavHostFragment
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.helper.AppLimitManager
import com.parem.launcher.ui.BadHabitDialogs
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PermissionLossGateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val pkg = "com.parem.fixture.limited"

    @Test fun revokedUsageWarnsAndStillRequiresConfiguredPause() {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        val launches = AtomicInteger()
        val cancelled = AtomicInteger()
        AppLimitManager.setLimit(context, pkg, 15)
        AppLimitManager.setPause(context, pkg, true)
        try {
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS ignore")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    BadHabitDialogs.gateLaunch(activity, activity.lifecycleScope, { !activity.isFinishing }, "Fixture", pkg,
                        open = { launches.incrementAndGet() }, onCancel = { cancelled.incrementAndGet() })
                }
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.limit_usage_access_missing))), 10_000))
                assertEquals(0, launches.get())
                checkNotNull(device.findObject(By.desc(context.getString(R.string.open_anyway)))).click()
                assertTrue(device.wait(Until.hasObject(By.textContains("Fixture")), 10_000))
                assertEquals(0, launches.get())
                val ready = context.getString(R.string.mindful_pause_open, "Fixture")
                assertTrue("pause did not finish", device.wait(Until.hasObject(By.text(ready)), 12_000))
                checkNotNull(device.findObject(By.text(ready))).click()
                val deadline = SystemClock.elapsedRealtime() + 3_000
                while (launches.get() == 0 && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(50)
                assertEquals(1, launches.get())
                assertEquals(0, cancelled.get())
            }
        } finally {
            AppLimitManager.removeLimit(context, pkg)
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }
    @Test fun pauseOnlyDoesNotAskForUsageAccess() {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        AppLimitManager.setPauseOnly(context, pkg)
        try {
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS ignore")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    BadHabitDialogs.gateLaunch(activity, activity.lifecycleScope, { !activity.isFinishing }, "Fixture", pkg, open = {})
                }
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.mindful_pause_question, "Fixture"))), 10_000))
                assertFalse(device.hasObject(By.text(context.getString(R.string.limit_usage_access_missing))))
                device.pressBack()
            }
        } finally {
            AppLimitManager.removeLimit(context, pkg)
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }

    @Test fun backCancelsUnavailableWarningOnce() {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        val cancelled = AtomicInteger()
        AppLimitManager.setLimit(context, pkg, 15)
        try {
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS ignore")
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    BadHabitDialogs.gateLaunch(activity, activity.lifecycleScope, { !activity.isFinishing }, "Fixture", pkg,
                        open = { error("unexpected launch") }, onCancel = { cancelled.incrementAndGet() })
                }
                val warning = By.text(context.getString(R.string.limit_usage_access_missing))
                assertTrue(device.wait(Until.hasObject(warning), 10_000))
                device.pressBack()
                assertTrue(device.wait(Until.gone(warning), 10_000))
                assertEquals(1, cancelled.get())
            }
        } finally {
            AppLimitManager.removeLimit(context, pkg)
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }

    @Test fun homeDismissesUnavailableWarning() = checkHomeDismissesWarningOrPause(false)
    @Test fun homeDismissesPauseAfterUnavailableWarning() = checkHomeDismissesWarningOrPause(true)

    private fun checkHomeDismissesWarningOrPause(advanceToPause: Boolean) {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        val cancelled = AtomicInteger()
        AppLimitManager.setLimit(context, pkg, 15)
        AppLimitManager.setPause(context, pkg, advanceToPause)
        try {
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS ignore")
            device.executeShellCommand("cmd package set-home-activity ${context.packageName}/${MainActivity::class.java.name}")
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            lateinit var source: Fragment
            instrumentation.runOnMainSync {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().single()
                activity.findNavController(R.id.nav_host_fragment).navigate(R.id.action_mainFragment_to_settingsFragment)
                val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                nav.childFragmentManager.executePendingTransactions()
                val fragment = checkNotNull(nav.childFragmentManager.primaryNavigationFragment)
                source = fragment
                BadHabitDialogs.gateLaunch(activity, fragment.viewLifecycleOwner.lifecycleScope,
                    { fragment.isAdded && fragment.view != null }, "Fixture", pkg,
                    open = { error("unexpected launch") }, onCancel = { cancelled.incrementAndGet() })
            }
            val warning = By.text(context.getString(R.string.limit_usage_access_missing))
            assertTrue(device.wait(Until.hasObject(warning), 10_000))
            val visibleSheet = if (advanceToPause) {
                checkNotNull(device.findObject(By.desc(context.getString(R.string.open_anyway)))).click()
                val question = By.text(context.getString(R.string.mindful_pause_question, "Fixture"))
                assertTrue(device.wait(Until.hasObject(question), 10_000))
                question
            } else warning
            check(device.pressHome())
            check(device.wait(Until.hasObject(By.res(context.resources.getResourceName(R.id.homeAppsLayout))), 10_000))
            instrumentation.runOnMainSync { check(source.view == null) { "HOME did not destroy the source view" } }
            assertTrue("sheet survived its screen", device.wait(Until.gone(visibleSheet), 10_000))
            assertEquals(1, cancelled.get())
        } finally {
            AppLimitManager.removeLimit(context, pkg)
            device.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS default")
        }
    }

}
