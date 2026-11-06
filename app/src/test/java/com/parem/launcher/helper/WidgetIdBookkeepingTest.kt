package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetIdBookkeepingTest {

    @Test
    fun idsToKeep_keepsValidAndRestorableInOrder() {
        val kept = WidgetIdBookkeeping.idsToKeep(
            ids = listOf(7, 3, 9, 5),
            valid = setOf(3, 5),
            restorable = setOf(7),
        )
        assertEquals(listOf(7, 3, 5), kept)
    }

    @Test
    fun idsToKeep_dropsInvalidWithoutProvider() {
        assertEquals(emptyList<Int>(), WidgetIdBookkeeping.idsToKeep(listOf(4), emptySet(), emptySet()))
    }

    @Test
    fun replaceId_keepsPosition() {
        assertEquals(listOf(1, 20, 3), WidgetIdBookkeeping.replaceId(listOf(1, 2, 3), oldId = 2, newId = 20))
    }

    @Test
    fun replaceId_appendsWhenOldIdGone() {
        assertEquals(listOf(1, 3, 20), WidgetIdBookkeeping.replaceId(listOf(1, 3), oldId = 2, newId = 20))
    }

    @Test
    fun orphanIds_excludesHomeAndPending() {
        val orphans = WidgetIdBookkeeping.orphanIds(
            hostIds = intArrayOf(1, 2, 3, 4),
            ids = listOf(1, 3),
            pendingId = 4,
        )
        assertEquals(listOf(2), orphans)
    }

    @Test
    fun orphanIds_noPending() {
        assertEquals(listOf(2), WidgetIdBookkeeping.orphanIds(intArrayOf(1, 2), listOf(1), pendingId = -1))
    }
}
