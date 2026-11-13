package com.parem.launcher

import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Build
import android.os.Process
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parem.launcher.helper.notifications.QuietNotificationListener
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only on a fresh CI emulator with the separately installed synthetic launcher app. */
@RunWith(AndroidJUnit4::class)
class QuietPositiveFixtureTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val fixture = "com.parem.quietfixture"
    private val component = ComponentName(context, QuietNotificationListener::class.java).flattenToString()
    private fun listener() = QuietNotificationListener.instance

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun post(mode: String) {
        val output = device.executeShellCommand("am start -W -n $fixture/.FixtureActivity --es mode $mode")
        assertFalse("fixture activity failed: $output", output.contains("Error") || output.contains("Exception"))
    }

    @Test
    fun filtersSyntheticLaunchableAppAndReleasesOnAllowAndDisable() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("quiet_positive_fixture") == "true")
        assertTrue("fixture must run on an emulator", Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        assertTrue("fixture requires an initially disabled listener without access", !QuietNotificationsManager.isEnabled(context) && !QuietNotificationsManager.hasAccess(context))
        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
        assertTrue("fixture APK must be installed and launchable", launcherApps.getActivityList(fixture, Process.myUserHandle()).isNotEmpty())
        try {
            QuietNotificationsManager.setEnabled(context, true)
            device.executeShellCommand("cmd notification allow_listener $component")
            waitUntil("listener did not bind") { QuietNotificationsManager.hasAccess(context) && listener() != null }

            post("alert1")
            waitUntil("eligible fixture was not snoozed and tracked") {
                listener()?.waiting()?.any { it.packageName == fixture && it.tag == "parem-quiet-alert1" } == true
            }
            val first = listener()!!.waiting().first { it.packageName == fixture && it.tag == "parem-quiet-alert1" }.key
            assertTrue("first fixture was not tracked", first in QuietNotificationsManager.tracked(context).snoozedAt)

            post("call")
            waitUntil("call-category fixture did not remain active") {
                listener()?.activeNotifications?.any { it.packageName == fixture && it.tag == "parem-quiet-call" } == true
            }
            SystemClock.sleep(1_000)
            assertTrue("call-category fixture did not stay active", listener()?.activeNotifications?.any { it.packageName == fixture && it.tag == "parem-quiet-call" } == true)
            assertFalse("call-category fixture was tracked", listener()?.waiting()?.any { it.packageName == fixture && it.tag == "parem-quiet-call" } == true)

            QuietNotificationsManager.setAllowed(context, setOf(fixture))
            waitUntil("allowlist did not release first fixture") { listener()?.activeNotifications?.any { it.key == first } == true }
            assertFalse("allowlisted fixture remained tracked", first in QuietNotificationsManager.tracked(context).snoozedAt)
            QuietNotificationsManager.setAllowed(context, emptySet())

            post("alert2")
            waitUntil("second eligible fixture was not snoozed") {
                listener()?.waiting()?.any { it.packageName == fixture && it.tag == "parem-quiet-alert2" } == true
            }
            val second = listener()!!.waiting().first { it.packageName == fixture && it.tag == "parem-quiet-alert2" }.key
            QuietNotificationsManager.turnOff(context)
            assertFalse("turnOff left listener enabled", QuietNotificationsManager.isEnabled(context))
            assertFalse("turnOff left released fixture tracked", second in QuietNotificationsManager.tracked(context).snoozedAt)
            // On this disposable emulator only, inspect system active keys after listener unbind.
            waitUntil("turnOff did not return second fixture") { device.executeShellCommand("cmd notification list").lineSequence().any { it.trim() == second } }
        } finally {
            try {
                // Disable first so cancel/repost cannot be filtered again during cleanup.
                QuietNotificationsManager.turnOff(context)
                device.executeShellCommand("am start -W -n $fixture/.FixtureActivity --es mode cancel")
            } finally {
                try {
                    QuietNotificationsManager.setAllowed(context, emptySet())
                } finally {
                    device.executeShellCommand("cmd notification disallow_listener $component")
                }
            }
        }
    }
}
