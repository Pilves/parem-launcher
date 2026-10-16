package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebLaunchTargetTest {

    private val browsers = listOf("com.android.chrome", "org.mozilla.firefox")

    @Test
    fun defaultHandlerIsTheTarget() {
        assertEquals("com.google.android.youtube", WebLaunchTarget.target("com.google.android.youtube", browsers) { true })
    }

    @Test
    fun focusBlockedDefaultHandlerBlocksEvenWithAllowedBrowsers() {
        assertNull(WebLaunchTarget.target("com.android.chrome", browsers) { it == "org.mozilla.firefox" })
    }

    @Test
    fun chooserPinsTheFirstAllowedBrowser() {
        assertEquals("org.mozilla.firefox", WebLaunchTarget.target(WebLaunchTarget.CHOOSER, browsers) { it == "org.mozilla.firefox" })
        assertEquals("com.android.chrome", WebLaunchTarget.target(null, browsers) { true })
    }

    @Test
    fun chooserWithNoAllowedBrowserIsBlocked() {
        assertNull(WebLaunchTarget.target(WebLaunchTarget.CHOOSER, browsers) { false })
    }

    @Test
    fun noBrowserAtAllIsNotGated() {
        assertEquals("", WebLaunchTarget.target(null, emptyList()) { false })
    }
}
