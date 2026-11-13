package com.parem.launcher

import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.parem.launcher.helper.notifications.QuietFilter
import com.parem.launcher.helper.notifications.QuietNotificationListener
import com.parem.launcher.helper.notifications.QuietNotificationsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic shell notification only: this does not test filtering a real launchable app. */
@RunWith(AndroidJUnit4::class)
class QuietNotificationFixtureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)
    private val component = ComponentName(context, QuietNotificationListener::class.java).flattenToString()
    private val tag = "parem-quiet-ci-fixture"

    private fun waitUntil(message: String, condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 15_000
        while (SystemClock.elapsedRealtime() < until) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        assertTrue(message, condition())
    }

    private fun listener() = QuietNotificationListener.instance
    private fun active(key: String) = listener()?.activeNotifications?.any { it.key == key } == true
    private fun snoozed(key: String) = listener()?.snoozedNotifications?.any { it.key == key } == true

    @Test
    fun seededSnoozeCanBeReleasedOnAllowAndTurnOff() {
        // Never bind a listener or read notifications in the normal suite or on a physical device.
        assumeTrue(InstrumentationRegistry.getArguments().getString("quiet_fixture") == "true")
        assumeTrue(Build.HARDWARE == "ranchu" || Build.PRODUCT.contains("sdk_gphone"))
        assumeTrue(!QuietNotificationsManager.isEnabled(context) && !QuietNotificationsManager.hasAccess(context))
        var key: String? = null
        try {
            QuietNotificationsManager.setEnabled(context, true)
            device.executeShellCommand("cmd notification allow_listener $component")
            waitUntil("listener did not connect") { QuietNotificationsManager.hasAccess(context) && listener() != null }
            device.executeShellCommand("cmd notification post -t ParemFixture $tag synthetic")
            waitUntil("synthetic shell notification did not appear") {
                listener()?.activeNotifications?.any { it.packageName == "com.android.shell" && it.tag == tag } == true
            }
            val fixtureKey = listener()!!.activeNotifications.first { it.packageName == "com.android.shell" && it.tag == tag }.key
            key = fixtureKey
            assertFalse("exempt shell notification was tracked as filtered", fixtureKey in QuietNotificationsManager.tracked(context).snoozedAt)

            fun seedSnooze() {
                listener()!!.snoozeNotification(fixtureKey, QuietFilter.SNOOZE_MS)
                QuietNotificationsManager.markSnoozed(context, fixtureKey)
                waitUntil("fixture was not snoozed by Android") { snoozed(fixtureKey) }
                waitUntil("tracked snooze was not visible in quiet list") { listener()?.waiting()?.any { it.key == fixtureKey } == true }
            }

            seedSnooze()
            QuietNotificationsManager.setAllowed(context, setOf("com.android.shell"))
            waitUntil("allowing the package did not release the fixture") { active(fixtureKey) }
            assertFalse("released key remained tracked", fixtureKey in QuietNotificationsManager.tracked(context).snoozedAt)

            QuietNotificationsManager.setAllowed(context, emptySet())
            seedSnooze()
            QuietNotificationsManager.turnOff(context)
            assertFalse("turnOff left component enabled", QuietNotificationsManager.isEnabled(context))
            assertFalse("turnOff did not forget the released key", fixtureKey in QuietNotificationsManager.tracked(context).snoozedAt)
            // Rebind only to inspect Android's repost. Shell has no launcher activity and is exempt.
            QuietNotificationsManager.setEnabled(context, true)
            waitUntil("fixture was not reposted after turnOff") { active(fixtureKey) }
        } finally {
            // Disposable CI emulator only; touch our synthetic tag and listener, never other keys.
            try {
                key?.let { fixtureKey ->
                    if (snoozed(fixtureKey)) {
                        device.executeShellCommand("cmd notification unsnooze '${fixtureKey.replace("'", "'\\''")}'")
                        waitUntil("fixture did not return for cleanup") { active(fixtureKey) }
                    }
                    listener()?.cancelNotification(fixtureKey)
                    QuietNotificationsManager.forget(context, listOf(fixtureKey))
                }
            } finally {
                QuietNotificationsManager.setAllowed(context, emptySet())
                QuietNotificationsManager.turnOff(context)
                device.executeShellCommand("cmd notification disallow_listener $component")
            }
        }
    }
}
