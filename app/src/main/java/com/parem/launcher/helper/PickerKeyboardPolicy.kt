package com.parem.launcher.helper

/** Search focus and software-keyboard policy for app picker sheets. */
object PickerKeyboardPolicy {
    enum class Mode { LEAVE_FOCUS, FOCUS_ONLY, FOCUS_AND_SHOW }

    fun decide(touchExploration: Boolean, autoShow: Boolean, hardwareKeyboard: Boolean): Mode = when {
        touchExploration -> Mode.LEAVE_FOCUS
        autoShow && !hardwareKeyboard -> Mode.FOCUS_AND_SHOW
        else -> Mode.FOCUS_ONLY
    }
}
