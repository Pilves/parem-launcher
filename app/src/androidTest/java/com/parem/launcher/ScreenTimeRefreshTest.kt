package com.parem.launcher

import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.parem.launcher.data.Constants
import com.parem.launcher.data.Prefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScreenTimeRefreshTest {
    @Test
    fun futurePersistedTimestampRefreshesAndThenThrottles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = Prefs(context).apply {
            firstOpen = false
            onboardingVersionSeen = Constants.ONBOARDING_VERSION
        }
        val before = System.currentTimeMillis()
        val future = before + 86_400_000L
        prefs.screenTimeLastUpdated = future
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].getTodaysScreenTime() }
            val deadline = SystemClock.elapsedRealtime() + 10_000L
            while (prefs.screenTimeLastUpdated == future && SystemClock.elapsedRealtime() < deadline) {
                SystemClock.sleep(50)
            }
            val updated = prefs.screenTimeLastUpdated
            assertTrue("timestamp remained in the future", updated in before..System.currentTimeMillis())
            scenario.onActivity { ViewModelProvider(it)[MainViewModel::class.java].getTodaysScreenTime() }
            SystemClock.sleep(300)
            assertEquals("fresh timestamp must remain throttled", updated, prefs.screenTimeLastUpdated)
        }
    }
}
