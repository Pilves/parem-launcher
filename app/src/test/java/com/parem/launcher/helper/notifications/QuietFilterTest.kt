package com.parem.launcher.helper.notifications

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietFilterTest {

    private val always = QuietFilter.alwaysAllowed("com.parem.launcher", "com.dialer", "com.sms")

    private fun silence(
        pkg: String = "com.chat",
        category: String? = null,
        isOngoing: Boolean = false,
        isClearable: Boolean = true,
        isGroupSummary: Boolean = false,
        isMedia: Boolean = false,
        hasLauncherActivity: Boolean = true,
        allowed: Set<String> = emptySet(),
    ) = QuietFilter.shouldSilence(
        pkg, category, isOngoing, isClearable, isGroupSummary, isMedia, { hasLauncherActivity }, allowed, { always }
    )

    @Test
    fun exemptNotificationSkipsTheLookups() {
        val lookup: () -> Nothing = { throw AssertionError("binder lookup for an exempt notification") }
        assertFalse(QuietFilter.shouldSilence("com.chat", null, true, true, false, false, lookup, emptySet(), lookup))
        assertFalse(QuietFilter.shouldSilence("com.chat", "call", false, true, false, false, lookup, emptySet(), lookup))
        assertFalse(QuietFilter.shouldSilence("com.chat", null, false, true, false, false, lookup, setOf("com.chat"), lookup))
    }

    @Test
    fun emptyAllowlistHidesClearableNotificationFromLaunchableApp() {
        assertTrue(silence())
        assertTrue(silence(category = "msg"))
    }

    @Test
    fun allowedAppIsNotHidden() {
        assertFalse(silence(allowed = setOf("com.chat")))
        assertTrue(silence(pkg = "com.other", allowed = setOf("com.chat")))
    }

    @Test
    fun defaultDialerSmsAndOwnPackageAreAlwaysAllowed() {
        assertFalse(silence(pkg = "com.dialer"))
        assertFalse(silence(pkg = "com.sms"))
        assertFalse(silence(pkg = "com.parem.launcher"))
    }

    @Test
    fun neverTouchPackagesAreNotHiddenEvenWithLauncherIcon() {
        for (pkg in listOf(
            "android",
            "com.android.systemui",
            "com.android.phone",
            "com.android.cellbroadcastreceiver",
            "com.google.android.cellbroadcastreceiver",
            "com.android.cellbroadcastreceiver.module",
        )) {
            assertFalse(pkg, silence(pkg = pkg))
        }
    }

    @Test
    fun packageWithoutLauncherActivityIsNeverHidden() {
        assertFalse(silence(hasLauncherActivity = false))
    }

    @Test
    fun exemptCategoriesAreNotHidden() {
        for (category in listOf("call", "alarm", "navigation", "transport")) {
            assertFalse(category, silence(category = category))
        }
    }

    @Test
    fun pausedMediaIsNotHidden() {
        // A paused MediaStyle notification is clearable and not ongoing
        assertFalse(silence(isMedia = true, isOngoing = false, isClearable = true))
    }

    @Test
    fun ongoingOrUnclearableIsNotHidden() {
        assertFalse(silence(isOngoing = true))
        assertFalse(silence(isClearable = false))
    }

    @Test
    fun groupSummaryIsNotHidden() {
        assertFalse(silence(isGroupSummary = true))
    }

    @Test
    fun alwaysAllowedSkipsMissingDefaults() {
        val set = QuietFilter.alwaysAllowed("own", null, "")
        assertTrue("own" in set)
        assertFalse("" in set)
        assertTrue(QuietFilter.NEVER_TOUCH.all { it in set })
    }
}
