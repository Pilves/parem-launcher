package com.parem.launcher

import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.parem.launcher.ui.BottomSheetMenu
import com.parem.launcher.ui.dismissOnDestroy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DismissOnDestroyTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
        override val lifecycle: Lifecycle get() = registry
    }

    /** A window detaches only after its first traversal attached it. */
    private fun ActivityScenario<MainActivity>.awaitAttached(sheet: BottomSheetDialog) {
        val deadline = SystemClock.elapsedRealtime() + 5_000
        var attached = false
        while (!attached && SystemClock.elapsedRealtime() < deadline) {
            instrumentation.waitForIdleSync()
            onActivity { attached = sheet.window!!.decorView.isAttachedToWindow }
        }
        assertTrue("sheet window never attached", attached)
    }

    @Test
    @SingleDevice
    fun dismissedSheetLeavesOwnerAndCallerListenerIntact() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            for (listenerFirst in listOf(true, false)) {
                lateinit var owner: Owner
                lateinit var sheet: BottomSheetDialog
                var dismissed = false
                scenario.onActivity { activity ->
                    owner = Owner()
                    val menu = BottomSheetMenu(activity).option("Row") {}
                    if (listenerFirst) menu.onDismiss { dismissed = true }
                    sheet = menu.show().dismissOnDestroy(owner)
                    if (!listenerFirst) sheet.setOnDismissListener { dismissed = true }
                    assertEquals(1, owner.registry.observerCount)
                }
                scenario.awaitAttached(sheet)
                scenario.onActivity {
                    sheet.dismiss()
                    assertEquals("dismissed sheet still observes its owner", 0, owner.registry.observerCount)
                }
                instrumentation.waitForIdleSync()
                assertTrue("caller's dismiss listener was replaced (listenerFirst=$listenerFirst)", dismissed)
            }
        }
    }

    @Test
    @SingleDevice
    fun ownerDestroyStillDismissesSheet() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var owner: Owner
            lateinit var sheet: BottomSheetDialog
            scenario.onActivity { activity ->
                owner = Owner()
                sheet = BottomSheetMenu(activity).option("Row") {}.show().dismissOnDestroy(owner)
            }
            scenario.awaitAttached(sheet)
            scenario.onActivity {
                owner.registry.currentState = Lifecycle.State.DESTROYED
                assertFalse("sheet outlived its owner", sheet.isShowing)
            }
        }
    }
}
