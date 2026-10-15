package com.parem.launcher.helper

import com.parem.launcher.helper.AppMenuPolicy.Action.*
import org.junit.Assert.assertEquals
import org.junit.Test

class AppMenuPolicyTest {
    @Test fun personalAppKeepsStableOrderAndExactDestructiveAction() {
        assertEquals(listOf(INFO, RENAME, LIMIT, PAUSE, GRAYSCALE, HIDE, UNINSTALL),
            AppMenuPolicy.actions(true, false, false, false, true, true))
        assertEquals(listOf(INFO, RENAME, LIMIT, GRAYSCALE, HIDE),
            AppMenuPolicy.actions(true, false, false, true, false, true))
    }

    @Test fun workAppCannotChangePersonalPackageSettingsButKeepsLocalHomeRename() {
        assertEquals(listOf(INFO, HIDE), AppMenuPolicy.actions(false, false, false, false, true, true))
        assertEquals(listOf(INFO, RENAME, HIDE), AppMenuPolicy.actions(false, false, false, true, true, true))
    }

    @Test fun privateAndHiddenContextsCannotLeakOtherActions() {
        assertEquals(listOf(INFO), AppMenuPolicy.actions(false, true, false, false, true, true))
        assertEquals(listOf(INFO, SHOW, UNINSTALL), AppMenuPolicy.actions(true, false, true, false, true, true))
        assertEquals(listOf(INFO, SHOW), AppMenuPolicy.actions(false, false, true, false, true, true))
        assertEquals(listOf(INFO, RENAME, LIMIT, GRAYSCALE, HIDE),
            AppMenuPolicy.actions(true, false, false, false, false, false))
    }
}
