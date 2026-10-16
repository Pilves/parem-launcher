package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class WebLaunchTargetTest {

    @Test
    fun resolvedHandlerIsGated() {
        assertEquals("com.android.chrome", WebLaunchTarget.gatedPackage("com.android.chrome"))
    }

    @Test
    fun appLinkHandlerIsGated() {
        assertEquals("com.google.android.youtube", WebLaunchTarget.gatedPackage("com.google.android.youtube"))
    }

    @Test
    fun chooserIsNotGated() {
        assertEquals("", WebLaunchTarget.gatedPackage(WebLaunchTarget.CHOOSER))
    }

    @Test
    fun missingHandlerIsNotGated() {
        assertEquals("", WebLaunchTarget.gatedPackage(null))
        assertEquals("", WebLaunchTarget.gatedPackage(""))
    }
}
