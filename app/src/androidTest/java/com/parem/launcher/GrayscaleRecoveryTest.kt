package com.parem.launcher

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.data.paremPrefs
import com.parem.launcher.helper.GrayscaleController
import com.parem.launcher.ui.GrayscaleSheet
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GrayscaleRecoveryTest {
    @Test fun regrantThroughRecoveryDoesNotTurnManualBackOn() {
        assumeTrue("disposable emulator only", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val device = UiDevice.getInstance(instrumentation)
        val grantedBefore = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED
        val enabledKey = "accessibility_display_daltonizer_enabled"
        val modeKey = "accessibility_display_daltonizer"
        val originalEnabled = device.executeShellCommand("settings get secure $enabledKey").trim()
        val originalMode = device.executeShellCommand("settings get secure $modeKey").trim()
        Prefs(context).apply { firstOpen = false; onboardingVersionSeen = Constants.ONBOARDING_VERSION }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                device.executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
                device.executeShellCommand("settings put secure $modeKey 0")
                device.executeShellCommand("settings put secure $enabledKey 1")
                val prefs = context.paremPrefs()
                prefs.edit().putBoolean(GrayscaleController.KEY_APPLIED, true)
                    .putBoolean(GrayscaleController.KEY_MANUAL, true)
                    .putBoolean(GrayscaleController.KEY_ON_FOCUS, false)
                    .putBoolean(GrayscaleController.KEY_ON_LIMIT, false)
                    .putBoolean(GrayscaleController.KEY_APP_FG, false)
                    .putBoolean(GrayscaleController.KEY_SUPPRESSED, false)
                    .putBoolean(GrayscaleController.KEY_PREV_ENABLED, false)
                    .putInt(GrayscaleController.KEY_PREV_MODE, 12).commit()
                device.executeShellCommand("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
                scenario.onActivity { activity ->
                    GrayscaleController.setManual(activity, false)
                    assertTrue(GrayscaleController.restorationPendingWithoutAccess(activity))
                    val nav = activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
                    val fragment = checkNotNull(nav.childFragmentManager.primaryNavigationFragment)
                    GrayscaleSheet.open(fragment, onPair = {}, onChanged = {})
                }
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.grayscale_restore_pending))), 10_000))
                checkNotNull(device.findObject(By.desc(context.getString(R.string.grayscale_restore_access)))).click()
                assertTrue(device.wait(Until.hasObject(By.text(context.getString(R.string.grayscale_grant_title))), 10_000))
                device.executeShellCommand("pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
                assertTrue("recovery did not finish", device.wait(Until.hasObject(By.desc(context.getString(R.string.grayscale_now))), 10_000))
                assertFalse("recovery re-enabled manual grayscale", prefs.getBoolean(GrayscaleController.KEY_MANUAL, true))
                assertFalse("restore ownership remained", prefs.getBoolean(GrayscaleController.KEY_APPLIED, true))
                assertEquals("0", device.executeShellCommand("settings get secure $enabledKey").trim())
            }
        } finally {
            context.paremPrefs().edit().putBoolean(GrayscaleController.KEY_APPLIED, false)
                .putBoolean(GrayscaleController.KEY_MANUAL, false)
                .putBoolean(GrayscaleController.KEY_ON_FOCUS, false)
                .putBoolean(GrayscaleController.KEY_ON_LIMIT, false)
                .putBoolean(GrayscaleController.KEY_APP_FG, false).commit()
            fun restore(key: String, value: String) {
                if (value == "null") device.executeShellCommand("settings delete secure $key")
                else device.executeShellCommand("settings put secure $key $value")
            }
            restore(enabledKey, originalEnabled)
            restore(modeKey, originalMode)
            if (!grantedBefore) device.executeShellCommand("pm revoke ${context.packageName} android.permission.WRITE_SECURE_SETTINGS")
        }
    }
}
