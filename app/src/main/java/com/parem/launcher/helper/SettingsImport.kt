package com.parem.launcher.helper

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/** Validate and convert export values before changing any preferences. */
object SettingsImport {
    // 2 MiB exceeds a ~1.18 MB stress export with 2,000 renamed/hidden apps and large folders.
    const val MAX_IMPORT_BYTES = 2 * 1024 * 1024
    // Exports are a flat object with optional string arrays. Bound nesting before
    // Android's recursive JSON parser sees untrusted input.
    private const val MAX_JSON_DEPTH = 16

    fun requireSafeJsonDepth(text: String) {
        val open = CharArray(MAX_JSON_DEPTH)
        var depth = 0
        var inString = false
        var escaped = false
        var previous = '\u0000'
        for (ch in text) {
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> { inString = false; previous = '"' }
                }
                continue
            }
            if (ch in " \t\r\n") continue
            when (ch) {
                '"' -> {
                    require(previous == '\u0000' || previous in "{[:,") { "Unexpected quote" }
                    inString = true
                }
                '{', '[' -> {
                    require(depth < MAX_JSON_DEPTH) { "Settings are nested too deeply" }
                    open[depth++] = ch
                }
                '}', ']' -> {
                    require(depth > 0 && open[--depth] == if (ch == '}') '{' else '[') { "Invalid nesting" }
                }
                // Reject non-JSON comments, single quotes and unquoted strings:
                // Android's lenient parser could interpret quotes in them differently.
                else -> require(ch in ":,-+.0123456789eEtrufalsn") { "Invalid JSON character" }
            }
            if (ch != '"') previous = ch
        }
        require(!inString && depth == 0) { "Incomplete settings" }
    }

    fun readBounded(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_IMPORT_BYTES) { "Settings file too large" }
            bytes.write(buffer, 0, count)
        }
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
        requireSafeJsonDepth(text)
        return text
    }
    private val booleans = setOf(
        "HOME_GUIDANCE_INITIALIZED", "HOME_GUIDANCE_NEW_INSTALL", "HOME_GUIDANCE_SWIPE", "HOME_GUIDANCE_LONG_PRESS",
        "FIRST_OPEN", "LOCK_MODE", "CRASH_REPORTS_ENABLED", "OMNIBOX_HISTORY_ENABLED",
        "AUTO_LAUNCH_SINGLE_MATCH", "AUTO_SHOW_KEYBOARD", "KEYBOARD_MESSAGE", "DAILY_WALLPAPER",
        "DIM_WALLPAPER", "HOME_BOTTOM_ALIGNMENT", "HOME_LAYOUT_LOCKED", "STATUS_BAR",
        "SWIPE_LEFT_ENABLED", "SWIPE_RIGHT_ENABLED", "BOLD_FONT", "HIDE_SET_DEFAULT_LAUNCHER",
        "SHOW_ICONS", "APP_DRAWER_SORT_BY_USAGE", "HIDDEN_APPS_UPDATED", "CONTACT_SEARCH_ENABLED",
        "WEATHER_ENABLED", "FOCUS_SCHEDULE_ENABLED", "GESTURE_LETTERS_ENABLED",
        "GRAYSCALE_ON_FOCUS", "GRAYSCALE_ON_LIMIT", "LOCK_SERVICE_CONNECTED", "LOCK_SERVICE_OFF_EXPLAINED",
        "FOCUS_MODE_ENABLED", "GRAYSCALE_MANUAL", "GRAYSCALE_APPLIED", "GRAYSCALE_SUPPRESSED",
        "GRAYSCALE_LIMIT_OVERRIDE", "GRAYSCALE_LEFT_SINCE_OVERRIDE", "GRAYSCALE_PREV_ENABLED",
        "GRAYSCALE_APP_FG", "ONBOARDING_COMPLETE", "PERIODIC_SELF_RECREATE_ENABLED"
    )
    private val integers = setOf(
        "HOME_APPS_NUM", "HOME_ALIGNMENT", "APP_LABEL_ALIGNMENT", "DATE_TIME_VISIBILITY", "APP_THEME",
        "SWIPE_DOWN_ACTION", "SWIPE_LEFT_ACTION", "SWIPE_RIGHT_ACTION", "THEME_SCHEDULE_MODE", "DOUBLE_TAP_ACTION",
        "WIDGET_ID", "WIDGET_PLACEMENT", "ONBOARDING_VERSION_SEEN", "GRAYSCALE_PREV_MODE", "SEARCH_HINT_OPENS"
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
        "QUIET_NOTIF_ALLOWED", "WIDGET_IDS", "WIDGET_HEIGHTS", "WIDGET_PROVIDERS", "CACHED_USAGE_STATS",
        "OPEN_COUNTS", "WEATHER_CACHED_TEMP", "CURRENCY_RATES"
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
            key in strings || slotKey.matches(key) || key.startsWith("RENAME_") -> (value as? String)?.also {
                // Folder JSON is stored as an escaped string and parsed again on home.
                if (key == "FOLDER_DATA" && it.any { ch -> ch == '{' || ch == '[' }) requireSafeJsonDepth(it)
            }
            // Preserve legacy/unknown keys in old exports without admitting objects or mixed arrays.
            value is Boolean || value is String || value is Int || value is Long -> value
            value is Double && value.isFinite() && value.toFloat().isFinite() -> value.toFloat()
            value is List<*> && value.all { it is String } -> value.toSet()
            else -> null
        }
        requireNotNull(valid) { "Invalid type for $key" }
        // Device-only numbers are accepted for old exports and then ignored by Prefs.
        val inRange = when (key) {
            "HOME_APPS_NUM" -> valid is Int && valid in 0..8
            // START, END and CENTER, plus legacy absolute LEFT and RIGHT.
            "HOME_ALIGNMENT", "APP_LABEL_ALIGNMENT" -> valid is Int && valid in setOf(0x800003, 0x800005, 17, 3, 5)
            "DATE_TIME_VISIBILITY", "THEME_SCHEDULE_MODE" -> valid is Int && valid in 0..2
            // AppCompat's supported modes, including legacy AUTO and UNSPECIFIED.
            "APP_THEME" -> valid is Int && valid in setOf(-100, -1, 0, 1, 2, 3)
            "SWIPE_DOWN_ACTION" -> valid is Int && valid in 1..2
            "SWIPE_LEFT_ACTION", "SWIPE_RIGHT_ACTION" -> valid is Int && valid in -1..9
            "DOUBLE_TAP_ACTION" -> valid is Int && valid in 0..9
            // Pre-6.0 text scales ranged from 0.6; older exports may contain 2.0.
            "TEXT_SIZE_SCALE" -> valid is Float && valid in 0.6f..2.0f
            "FIRST_OPEN_TIME", "SCREEN_TIME_LAST_UPDATED", "LAUNCHER_RECREATE_TIMESTAMP" -> valid is Long && valid >= 0L
            else -> true
        }
        require(inRange) { "Invalid value for $key" }
        valid
    }
}
