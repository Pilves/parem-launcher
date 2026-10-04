package com.parem.launcher.helper

import com.parem.launcher.helper.HomeAccessibilityActions.Action
import com.parem.launcher.helper.HomeAccessibilityActions.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HomeAccessibilityActionsTest {

    private val always = listOf(Kind.ALL_APPS, Kind.SETTINGS, Kind.NOTIFICATIONS, Kind.ADD_WIDGET)

    @Test
    fun everyViewGetsTheRoutesOutOfHome() {
        assertEquals(always, HomeAccessibilityActions.forView(null, null).map { it.kind })
    }

    @Test
    fun configuredSwipeAppsAreAppendedWithTheirNames() {
        assertEquals(
            always.map { Action(it) } + listOf(
                Action(Kind.SWIPE_LEFT_APP, "Camera"),
                Action(Kind.SWIPE_RIGHT_APP, "Phone"),
                Action(Kind.SWIPE_UP_APP, "Maps"),
            ),
            HomeAccessibilityActions.forView("Camera", "Phone", "Maps"),
        )
    }

    @Test
    fun swipeAppNeedsTheGestureOnSetToOpenAnAppAndAPackage() {
        assertEquals("Camera", HomeAccessibilityActions.configuredApp(true, true, "com.cam", "Camera"))
        assertNull(HomeAccessibilityActions.configuredApp(false, true, "com.cam", "Camera"))
        assertNull(HomeAccessibilityActions.configuredApp(true, false, "com.cam", "Camera"))
        assertNull(HomeAccessibilityActions.configuredApp(true, true, "", ""))
    }

    @Test
    fun swipeAppWithoutAStoredNameFallsBackToThePackage() {
        assertEquals("com.cam", HomeAccessibilityActions.configuredApp(true, true, "com.cam", ""))
    }
}
