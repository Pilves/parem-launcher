package com.parem.launcher.ui.settings

import android.content.Context
import com.parem.launcher.R
import com.parem.launcher.data.Constants
import com.parem.launcher.helper.SettingsSearch
import com.parem.launcher.helper.appUsagePermissionGranted

/**
 * Every settings row the omnibox can open (M4-WP17): its title and the view
 * the settings screen scrolls to — the row's value view, or the row itself
 * when it has none. SettingsSearchIndexTest fails when a row title in
 * fragment_settings.xml is missing here.
 */
object SettingsSearchIndex {

    private val ROWS = listOf(
        R.string.hidden_apps to R.id.paremHiddenApps,
        R.string.set_as_default_launcher to R.id.setLauncher,
        R.string.change_default_launcher to R.id.setLauncher,
        R.string.about_parem to R.id.aboutParem,
        R.string.export_settings to R.id.exportSettings,
        R.string.import_settings to R.id.importSettings,
        R.string.crash_reports to R.id.crashReportsToggle,
        R.string.apps_on_home_screen to R.id.homeAppsNum,
        R.string.show_date_time to R.id.dateTime,
        R.string.home_layout_alignment to R.id.alignment,
        R.string.lock_home_layout to R.id.homeLayoutLockToggle,
        R.string.show_icons to R.id.showIconsToggle,
        R.string.sort_apps_by_usage to R.id.sortByUsage,
        R.string.contact_search to R.id.contactSearchToggle,
        R.string.search_history to R.id.searchHistoryToggle,
        R.string.widget_placement to R.id.widgetPlacement,
        R.string.theme_mode to R.id.appThemeText,
        R.string.text_size to R.id.textSizeValue,
        R.string.bold_font to R.id.boldFont,
        R.string.daily_wallpaper_update to R.id.dailyWallpaperUrl,
        R.string.notification_bar to R.id.statusBar,
        R.string.weather to R.id.weatherToggle,
        R.string.swipe_left_action to R.id.swipeLeftApp,
        R.string.swipe_right_action to R.id.swipeRightApp,
        R.string.swipe_down_for to R.id.swipeDownAction,
        R.string.double_tap_action to R.id.doubleTapAction,
        R.string.auto_show_keyboard to R.id.autoShowKeyboard,
        R.string.gesture_letters to R.id.gestureLettersToggle,
        R.string.screen_time to R.id.screenTimeOnOff,
        R.string.app_limits to R.id.screenTimeLimitsToggle,
        R.string.focus_mode to R.id.focusModeToggle,
        R.string.grayscale to R.id.grayscaleToggle,
        R.string.quiet_notif to R.id.quietNotifToggle,
        R.string.quiet_allowed_apps to R.id.quietNotifAllowed,
        R.string.quiet_make_silent to R.id.quietNotifSilent,
        R.string.github to R.id.github,
        R.string.privacy to R.id.privacy,
    )

    /** Rows the settings cards hide outright are not offered (same conditions as the cards). */
    fun rows(context: Context): List<SettingsSearch.Row> {
        val hidden = buildSet {
            if (Constants.URL_ABOUT_PAREM.isEmpty()) add(R.id.aboutParem)
            if (Constants.URL_PAREM_PRIVACY.isEmpty()) add(R.id.privacy)
            if (Constants.URL_PAREM_GITHUB.isEmpty()) add(R.id.github)
            if (!context.appUsagePermissionGranted()) add(R.id.sortByUsage)
        }
        return ROWS.filter { it.second !in hidden }
            .map { (title, anchor) -> SettingsSearch.Row(context.getString(title), anchor) }
    }
}
