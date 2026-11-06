package com.parem.launcher.helper

/** Validate and convert export values before changing any preferences. */
object SettingsImport {
    private val booleans = setOf(
        "FIRST_OPEN", "LOCK_MODE", "CRASH_REPORTS_ENABLED", "OMNIBOX_HISTORY_ENABLED",
        "AUTO_LAUNCH_SINGLE_MATCH", "AUTO_SHOW_KEYBOARD", "KEYBOARD_MESSAGE", "DAILY_WALLPAPER",
        "DIM_WALLPAPER", "HOME_BOTTOM_ALIGNMENT", "HOME_LAYOUT_LOCKED", "STATUS_BAR",
        "SWIPE_LEFT_ENABLED", "SWIPE_RIGHT_ENABLED", "BOLD_FONT", "HIDE_SET_DEFAULT_LAUNCHER",
        "SHOW_ICONS", "APP_DRAWER_SORT_BY_USAGE", "HIDDEN_APPS_UPDATED", "CONTACT_SEARCH_ENABLED",
        "WEATHER_ENABLED", "FOCUS_SCHEDULE_ENABLED", "GESTURE_LETTERS_ENABLED",
        "GRAYSCALE_ON_FOCUS", "GRAYSCALE_ON_LIMIT"
    )
    private val integers = setOf(
        "HOME_APPS_NUM", "HOME_ALIGNMENT", "APP_LABEL_ALIGNMENT", "DATE_TIME_VISIBILITY", "APP_THEME",
        "SWIPE_DOWN_ACTION", "SWIPE_LEFT_ACTION", "SWIPE_RIGHT_ACTION", "THEME_SCHEDULE_MODE", "DOUBLE_TAP_ACTION"
    )
    private val strings = setOf(
        "DAILY_WALLPAPER_URL", "ICON_PACK_PACKAGE", "APP_NAME_SWIPE_LEFT", "APP_NAME_SWIPE_RIGHT",
        "APP_PACKAGE_SWIPE_LEFT", "APP_PACKAGE_SWIPE_RIGHT", "APP_ACTIVITY_CLASS_NAME_SWIPE_LEFT",
        "APP_ACTIVITY_CLASS_NAME_SWIPE_RIGHT", "APP_USER_SWIPE_LEFT", "APP_USER_SWIPE_RIGHT",
        "CLOCK_APP_PACKAGE", "CLOCK_APP_USER", "CLOCK_APP_CLASS_NAME", "CALENDAR_APP_PACKAGE",
        "CALENDAR_APP_USER", "CALENDAR_APP_CLASS_NAME", "BAD_HABIT_APPS", "MINDFUL_PAUSE_APPS",
        "FOCUS_MODE_WHITELIST", "FOCUS_SCHEDULE", "FOLDER_DATA", "GESTURE_LETTER_MAPPINGS",
        "WEATHER_LAT", "WEATHER_LNG", "WEATHER_CITY_NAME", "THEME_LIGHT_TIME", "THEME_DARK_TIME",
        "DOUBLE_TAP_APP_PACKAGE", "DOUBLE_TAP_APP_ACTIVITY", "DOUBLE_TAP_APP_USER", "GRAYSCALE_APPS",
        "QUIET_NOTIF_ALLOWED"
    )
    private val slotKey = Regex("APP_(NAME|PACKAGE|ACTIVITY_CLASS_NAME|USER|URL)_[1-8]")

    fun validate(values: Map<String, Any>, longKeys: Set<String>, floatKeys: Set<String>): Map<String, Any> = values.mapValues { (key, value) ->
        val valid: Any? = when {
            key in booleans -> value as? Boolean
            key in integers -> (value as? Number)?.let { n ->
                n.toLong().takeIf { (n is Int || n is Long) && it >= Int.MIN_VALUE.toLong() && it <= Int.MAX_VALUE.toLong() }?.toInt()
            }
            key in longKeys -> (value as? Number)?.takeIf { it is Int || it is Long }?.toLong()
            key in floatKeys -> (value as? Number)?.toFloat()?.takeIf { it.isFinite() }
            key == "HIDDEN_APPS" -> (value as? List<*>)?.takeIf { it.all { item -> item is String } }?.toSet()
            key in strings || slotKey.matches(key) || key.startsWith("RENAME_") -> value as? String
            // Preserve legacy/unknown keys in old exports without admitting objects or mixed arrays.
            value is Boolean || value is String || value is Int || value is Long -> value
            value is Double && value.isFinite() && value.toFloat().isFinite() -> value.toFloat()
            value is List<*> && value.all { it is String } -> value.toSet()
            else -> null
        }
        requireNotNull(valid) { "Invalid type for $key" }
    }
}
