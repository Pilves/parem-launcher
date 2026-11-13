package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLaunchTargetTest {

    private val browsers = listOf("com.android.chrome", "org.mozilla.firefox")

    @Test
    fun defaultHandlerIsTheTarget() {
        assertEquals("com.google.android.youtube", WebLaunchTarget.target("com.google.android.youtube", browsers, null))
        assertEquals("com.google.android.youtube", WebLaunchTarget.target("com.google.android.youtube", browsers) { true })
    }

    @Test
    fun focusBlockedDefaultHandlerBlocksEvenWithAllowedBrowsers() {
        assertNull(WebLaunchTarget.target("com.android.chrome", browsers) { it == "org.mozilla.firefox" })
    }

    @Test
    fun chooserWithoutFocusIsLeftAlone() {
        assertEquals("", WebLaunchTarget.target(WebLaunchTarget.CHOOSER, browsers, null))
        assertEquals("", WebLaunchTarget.target(null, browsers, null))
    }

    @Test
    fun chooserUnderFocusPinsTheFirstAllowedBrowser() {
        assertEquals("org.mozilla.firefox", WebLaunchTarget.target(WebLaunchTarget.CHOOSER, browsers) { it == "org.mozilla.firefox" })
        assertEquals("com.android.chrome", WebLaunchTarget.target(null, browsers) { true })
    }

    @Test
    fun chooserUnderFocusWithNoAllowedBrowserIsBlocked() {
        assertNull(WebLaunchTarget.target(WebLaunchTarget.CHOOSER, browsers) { false })
    }

    @Test
    fun unknownHandlerWithoutFocusLeavesIntentUnpinned() {
        assertEquals("", WebLaunchTarget.target(null, emptyList(), null))
        assertEquals("", WebLaunchTarget.target(WebLaunchTarget.CHOOSER, emptyList(), null))
    }

    @Test
    fun unknownHandlerUnderFocusBlocksWhenCandidateLookupIsEmptyOrFailed() {
        // A failed query is converted to an empty list by gateWebOpen.
        assertNull(WebLaunchTarget.target(null, emptyList()) { true })
        assertNull(WebLaunchTarget.target(WebLaunchTarget.CHOOSER, emptyList()) { true })
    }

    @Test
    fun knownAllowedDefaultStillWorksWhenCandidateLookupIsEmpty() {
        assertEquals("com.android.chrome", WebLaunchTarget.target("com.android.chrome", emptyList()) { true })
    }
}
