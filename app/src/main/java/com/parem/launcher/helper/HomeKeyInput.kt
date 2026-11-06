package com.parem.launcher.helper

/**
 * Decides what a hardware key pressed on the home screen means for the app
 * drawer. Kept free of KeyEvent so the rules stay unit-testable; MainActivity
 * maps the event onto these arguments.
 */
object HomeKeyInput {

    /**
     * @param isMenuKey the key is KEYCODE_MENU
     * @param unicodeChar KeyEvent.unicodeChar (already shift/caps-applied; 0 when none)
     * @param hasShortcutModifier Ctrl, Alt or Meta is held — those are shortcuts, not typing
     * @return the text to seed drawer search with ("" opens the drawer without a
     * query), or null when the key should be left to the system.
     */
    fun drawerQuery(isMenuKey: Boolean, unicodeChar: Int, hasShortcutModifier: Boolean): String? {
        if (hasShortcutModifier) return null
        if (isMenuKey) return ""
        // Letters and digits only: space, '!' and symbols switch the omnibox into
        // web/bang/calc modes, which a stray key press at home shouldn't do
        if (unicodeChar <= 0 || !Character.isLetterOrDigit(unicodeChar)) return null
        return String(Character.toChars(unicodeChar))
    }
}
