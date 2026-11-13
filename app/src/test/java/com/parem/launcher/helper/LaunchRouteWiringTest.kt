package com.parem.launcher.helper

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/** Structural checks complement the on-device launch tests; they are not a UI execution pass. */
class LaunchRouteWiringTest {
    private fun function(file: String, name: String): String {
        val relative = "java/com/parem/launcher/$file"
        val source = listOf(File("src/main/$relative"), File("app/src/main/$relative"))
            .first { it.isFile }.readText()
        val start = source.indexOf("fun $name(")
        require(start >= 0) { "$file has no $name" }
        val end = source.indexOf("\n    }", start)
        require(end >= 0) { "$file $name has no end" }
        return source.substring(start, end)
    }

    private fun contains(file: String, name: String, vararg calls: String) {
        val body = function(file, name)
        calls.forEach { assertTrue("$file $name must call $it", body.contains(it)) }
    }

    @Test fun homeDrawerGestureQuietAndIntentRoutesReachTheGate() {
        contains("ui/home/HomeSlotsController.kt", "homeAppClicked", "launchApp(")
        contains("ui/home/HomeSlotsController.kt", "launchApp", "gateLaunch(", "gateWebOpen(")
        contains("ui/home/HomeSlotsController.kt", "openApp", "selectedApp(")
        contains("ui/home/HomeSlotsController.kt", "toggleFolderExpansion", "launchApp(")
        contains("ui/home/HomeSlotsController.kt", "launchSearch", "gateWebOpen(")
        contains("ui/home/HomeGesturesController.kt", "initGestureLetterOverlay", "launchApp(")
        contains("ui/home/HomeGesturesController.kt", "openSwipeApp", "launchApp(")
        contains("ui/home/HomeClockController.kt", "openClockApp", "openSystemIntent(", "launchApp(")
        contains("ui/home/HomeClockController.kt", "openCalendarApp", "openSystemIntent(", "launchApp(")
        contains("ui/home/HomeClockController.kt", "openScreenTimeDigitalWellbeing", "openSystemIntent(")
        contains("ui/home/HomeClockController.kt", "openSystemIntent", "gateIntent(")
        contains("ui/AppDrawerFragment.kt", "onAppClick", "checkBadHabitAndLaunch(", "selectedApp(")
        contains("ui/AppDrawerFragment.kt", "checkBadHabitAndLaunch", "gateLaunch(", "selectedApp(")
        contains("ui/AppDrawerFragment.kt", "launchUrl", "gateWebOpen(", "selectedApp(")
        contains("ui/AppDrawerAdapter.kt", "launchFirstInList", "appClickListener(")
        contains("ui/QuietListSheet.kt", "showList", "gateLaunch(")
        contains("ui/BadHabitDialogs.kt", "gateIntent", "gateWebOpen(")
        contains("ui/BadHabitDialogs.kt", "gateWebOpen", "gateLaunch(")
        contains("ui/BadHabitDialogs.kt", "gateLaunch", "LaunchGate.decide(")
        contains("MainViewModel.kt", "selectedApp", "LaunchGate.decide(")
    }
}
