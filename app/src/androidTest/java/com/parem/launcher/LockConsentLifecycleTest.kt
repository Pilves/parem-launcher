package com.parem.launcher

import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import com.parem.launcher.ui.dismissLockSheetOnStop
import com.parem.launcher.ui.showLockConsent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class LockConsentLifecycleTest {
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
