package com.parem.launcher.helper

import com.parem.launcher.helper.WellbeingRowState.Row
import org.junit.Assert.assertEquals
import org.junit.Test

class WellbeingRowStateTest {

    @Test
    fun focus_activeWinsOverSchedule() {
        assertEquals(Row.ON, WellbeingRowState.focus(active = true, scheduleEnabled = true, hasWindows = true))
    }

    @Test
    fun focus_armedScheduleOutsideWindowIsScheduledNotOff() {
        assertEquals(Row.SCHEDULED, WellbeingRowState.focus(active = false, scheduleEnabled = true, hasWindows = true))
    }

    @Test
    fun focus_enabledScheduleWithoutWindowsIsOff() {
        assertEquals(Row.OFF, WellbeingRowState.focus(active = false, scheduleEnabled = true, hasWindows = false))
        assertEquals(Row.OFF, WellbeingRowState.focus(active = false, scheduleEnabled = false, hasWindows = true))
    }

    @Test
    fun grayscale_notGrantedIsSetUpWhateverIsTicked() {
        assertEquals(Row.SET_UP, WellbeingRowState.grayscale(granted = false, manual = true, onFocus = true, onLimit = true))
    }

    @Test
    fun grayscale_manualIsOn() {
        assertEquals(Row.ON, WellbeingRowState.grayscale(granted = true, manual = true, onFocus = false, onLimit = false))
    }

    @Test
    fun grayscale_onlyTriggersTickedIsAutomatic() {
        assertEquals(Row.AUTOMATIC, WellbeingRowState.grayscale(granted = true, manual = false, onFocus = true, onLimit = false))
        assertEquals(Row.AUTOMATIC, WellbeingRowState.grayscale(granted = true, manual = false, onFocus = false, onLimit = true))
    }

    @Test
    fun grayscale_nothingTickedIsOff() {
        assertEquals(Row.OFF, WellbeingRowState.grayscale(granted = true, manual = false, onFocus = false, onLimit = false))
    }

    @Test
    fun appLimits_withoutUsageAccessNeedsAccess() {
        assertEquals(Row.NEEDS_ACCESS, WellbeingRowState.appLimits(usageAccess = false))
        assertEquals(Row.CONFIGURE, WellbeingRowState.appLimits(usageAccess = true))
    }
}
