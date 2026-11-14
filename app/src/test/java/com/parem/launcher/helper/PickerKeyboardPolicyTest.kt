package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class PickerKeyboardPolicyTest {
    @Test fun respectsAccessibilityKeyboardPreferenceAndHardware() {
        val decide = PickerKeyboardPolicy::decide
        assertEquals(PickerKeyboardPolicy.Mode.LEAVE_FOCUS, decide(true, true, false))
        assertEquals(PickerKeyboardPolicy.Mode.LEAVE_FOCUS, decide(true, false, true))
        assertEquals(PickerKeyboardPolicy.Mode.FOCUS_ONLY, decide(false, false, false))
        assertEquals(PickerKeyboardPolicy.Mode.FOCUS_ONLY, decide(false, true, true))
        assertEquals(PickerKeyboardPolicy.Mode.FOCUS_AND_SHOW, decide(false, true, false))
    }
}
