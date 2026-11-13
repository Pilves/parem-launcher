package com.parem.launcher.helper

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/** Inventory guard only: this does not establish that callers reach the gate at runtime. */
class LaunchSiteAllowlistTest {
    @Test fun mainSourceLaunchSitesHaveAnAuditedReason() {
        val root = listOf(File("src/main/java/com/parem/launcher"), File("app/src/main/java/com/parem/launcher"))
            .first { it.isDirectory }
        val pattern = Regex("""\bstartActivity\s*\(|\.startMainActivity\s*\(|\.startShortcut\s*\(|\.send\s*\(""")
        val observed = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { file ->
            file.readLines().asSequence().map { it.trim() }
                .filter { !it.startsWith("//") && pattern.containsMatchIn(it) }
                .map { "${file.relativeTo(root).invariantSeparatorsPath}|$it" }
        }.toList().sorted()
        // Reasons distinguish gated dispatch from deliberately exempt configuration and actions.
        val allowed = listOf(
            "MainActivity.kt|.option(getString(R.string.okay)) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }" to "Usage access configuration",
            "MainActivity.kt|.option(getString(R.string.agree)) { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }" to "Usage access configuration",
            "MainViewModel.kt|appContext.startActivity(intent)" to "App and shortcut dispatch after the selected-app gate",
            "MainViewModel.kt|launcherApps.startShortcut(appModel.appPackage, shortcutId, sourceBounds, null, appModel.user)" to "App and shortcut dispatch after the selected-app gate",
            "MainViewModel.kt|launcherApps.startMainActivity(component, userHandle, sourceBounds, null)" to "App and shortcut dispatch after the selected-app gate",
            "helper/Extensions.kt|startActivity(selector)" to "Home chooser configuration",
            "helper/Extensions.kt|startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))" to "Home configuration",
            "helper/Extensions.kt|startActivity(intent)" to "Search dispatched after its caller gate",
            "helper/SystemActions.kt|startActivity(intent)" to "Exempt camera and dialer gestures",
            "helper/SystemActions.kt|context.startActivity(intent)" to "Exempt timer alarm and event actions",
            "helper/Utils.kt|startActivity(intent)" to "Settings and recovery links",
            "helper/notifications/QuietNotificationListener.kt|n.contentIntent?.send(context, 0, null, null, null, null, backgroundStartOptions())" to "Notification pending intent dispatched after quiet-list gate",
            "helper/notifications/QuietNotificationsManager.kt|context.startActivity(detail)" to "Notification permission and app-details configuration",
            "helper/notifications/QuietNotificationsManager.kt|context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))" to "Notification permission and app-details configuration",
            "helper/notifications/QuietNotificationsManager.kt|context.startActivity(" to "Notification permission and app-details configuration",
            "ui/AppDrawerFragment.kt|startActivity(" to "Exempt dialer action",
            "ui/BadHabitDialogs.kt|context.startActivity(Intent(intent).apply { if (packageName.isNotEmpty()) setPackage(packageName) })" to "Intent dispatch after gate",
            "ui/BadHabitDialogs.kt|context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))" to "Usage access configuration",
            "ui/CrashReportSheet.kt|context.startActivity(Intent.createChooser(send, null))" to "Crash report sharing",
            "ui/GrayscaleSheet.kt|context.startActivity(Intent.createChooser(send, null))" to "Sharing",
            "ui/OnboardingFragment.kt|startActivity(Intent(Settings.ACTION_HOME_SETTINGS))" to "Home and accessibility configuration",
            "ui/OnboardingFragment.kt|startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))" to "Home and accessibility configuration",
            "ui/OnboardingFragment.kt|startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))" to "Home and accessibility configuration",
            "ui/ScreenTimeLimitDialog.kt|context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))" to "Usage access configuration",
            "ui/home/HomeGesturesController.kt|fragment.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))" to "Accessibility or wallpaper configuration",
            "ui/home/HomeGesturesController.kt|fragment.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))" to "Accessibility or wallpaper configuration",
            "ui/home/HomeGesturesController.kt|fragment.startActivity(Intent(Intent.ACTION_SET_WALLPAPER))" to "Accessibility or wallpaper configuration",
            "ui/settings/GesturesSettingsCard.kt|fragment.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))" to "Accessibility permission configuration",
        )
        assertEquals("Review every added, removed, or changed launch site", allowed.map { it.first }.sorted(), observed)
    }
}
