package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drawer decides the Private Space entry before any binder call for most
 * queries, and reads the entry-hidden setting only while locked. Both rest on
 * these two properties of DrawerRows.showPrivateEntry.
 */
class PrivateEntryGateTest {

    private val labels = listOf("Private space", "Privaatruum", "Espace privé", "Private")
    private val queries = listOf("", " ", "  ", "!", "!private", " private", "  private", "p", "pri", "priv",
        "privat", "private", "PRIVATE", "Private ", "private s", "private space", "private spaces",
        "privaatruum", "espace", "espace priv", "space", "x", "privé")

    @Test
    fun aQueryThatFailsUnlockedAndVisibleFailsInEveryState() {
        var passing = 0
        for (label in labels) for (q in queries) {
            if (DrawerRows.showPrivateEntry(q, label, locked = false, hiddenWhenLocked = false)) {
                passing++
                continue
            }
            for (locked in listOf(false, true)) for (hidden in listOf(false, true)) {
                assertFalse("'$q' / '$label' locked=$locked hidden=$hidden",
                    DrawerRows.showPrivateEntry(q, label, locked, hidden))
            }
        }
        assertTrue("corpus never passes the gate", passing > 0)
    }

    @Test
    fun entryHiddenDecidesNothingWhileUnlocked() {
        for (label in labels) for (q in queries) {
            assertEquals("'$q' / '$label'",
                DrawerRows.showPrivateEntry(q, label, locked = false, hiddenWhenLocked = false),
                DrawerRows.showPrivateEntry(q, label, locked = false, hiddenWhenLocked = true))
        }
    }
}
