package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerRowsTest {

    private data class Row(val name: String, val isPrivate: Boolean = false, val isShortcut: Boolean = false)

    private val regularB = Row("b")
    private val privateA = Row("a", isPrivate = true)
    private val regularC = Row("c")
    private val header = Row("header", isPrivate = true)
    private val padding = Row("padding")

    private fun decorate(apps: List<Row>, header: Row? = this.header, padding: Row? = this.padding) =
        DrawerRows.decorate(apps, { it.isPrivate }, header, padding) { it.isShortcut }

    @Test
    fun privateEntrySearchFindsContainerInBothLockStates() {
        for (locked in listOf(false, true)) {
            for (query in listOf("", "private", "PRIVATE SPACE", "private sp")) {
                assertTrue(query, DrawerRows.showPrivateEntry(query, "Private space", locked, false))
            }
            for (query in listOf("camera", "unlock", " private", "!private", "s", "p", "pri", "-", " ")) {
                assertFalse(query, DrawerRows.showPrivateEntry(query, "Private space", locked, false))
            }
        }
    }

    @Test
    fun hiddenLockedEntryNeverAppearsEvenForItsExactName() {
        for (query in listOf("", "private", "Private space")) {
            assertFalse(DrawerRows.showPrivateEntry(query, "Private space", true, true))
            assertTrue(DrawerRows.showPrivateEntry(query, "Private space", false, true))
        }
    }

    @Test
    fun searchedEntryDoesNotCreatePrivateAppRows() {
        assertEquals(listOf(header), decorate(emptyList(), padding = null))
        assertEquals(listOf(regularB, header), decorate(listOf(regularB), padding = null))
    }

    @Test
    fun searchedEntryPrecedesMatchingAppsForExplicitEnter() {
        assertEquals(listOf(header, regularB, privateA),
            DrawerRows.decorate(listOf(regularB, privateA), { it.isPrivate }, header, null, headerFirst = true))
    }

    @Test
    fun privateEntryMatchesItsLocalizedLabel() {
        assertTrue(DrawerRows.showPrivateEntry("Privater", "Privater Bereich", true, false))
        assertFalse(DrawerRows.showPrivateEntry("camera", "Privater Bereich", true, false))
    }

    @Test
    fun enterWaitsForTheCommittedQueryAndReload() {
        val state = DrawerFilterState()
        val generation = state.generation()
        state.commit("visible app", generation)
        assertTrue(state.isReady("visible app"))
        assertFalse(state.isReady("hidden app"))
        state.commit("hidden app", generation)
        assertTrue(state.isReady("hidden app"))
        assertFalse(state.isReady("visible app"))
        val newer = state.invalidate()
        assertFalse(state.isReady("hidden app"))
        assertFalse(state.commit("hidden app", generation))
        assertFalse(state.isReady("hidden app"))
        assertFalse(state.isCurrent(generation))
        assertTrue(state.commit("hidden app", newer))
        assertTrue(state.isReady("hidden app"))
        assertFalse(state.isCurrent(generation))
    }

    @Test
    fun decorate_headerBetweenSections_paddingLast() {
        assertEquals(
            listOf(regularB, regularC, header, privateA, padding),
            decorate(listOf(regularB, privateA, regularC))
        )
    }

    @Test
    fun decorate_keepsOrderWithinEachSection() {
        val privateZ = Row("z", isPrivate = true)
        assertEquals(
            listOf(regularC, regularB, header, privateZ, privateA, padding),
            decorate(listOf(privateZ, regularC, privateA, regularB))
        )
    }

    @Test
    fun decorate_headerShownWithZeroPrivateRows() {
        assertEquals(listOf(regularB, header, padding), decorate(listOf(regularB)))
    }

    @Test
    fun decorate_searching_noHeaderNoPadding_privateStillLast() {
        assertEquals(
            listOf(regularB, privateA),
            decorate(listOf(privateA, regularB), header = null, padding = null)
        )
    }

    @Test
    fun decorate_shortcutsUnderEveryAppRow_privateParentsIncluded() {
        val shortcutOfRegular = Row("b:liked", isShortcut = true)
        val shortcutOfPrivate = Row("a:home", isPrivate = true, isShortcut = true)
        assertEquals(
            listOf(regularB, regularC, header, privateA, shortcutOfRegular, shortcutOfPrivate, padding),
            decorate(listOf(shortcutOfRegular, regularB, privateA, shortcutOfPrivate, regularC))
        )
        assertEquals(
            listOf(regularB, privateA, shortcutOfRegular),
            decorate(listOf(shortcutOfRegular, privateA, regularB), header = null, padding = null)
        )
    }

    @Test
    fun gate_launchDrawerKeepsPrivate() {
        val apps = listOf(regularB, privateA)
        assertEquals(apps, DrawerRows.gate(apps, isLaunchDrawer = true) { it.isPrivate })
    }

    @Test
    fun gate_pickersNeverSeePrivate() {
        assertEquals(
            listOf(regularB, regularC),
            DrawerRows.gate(listOf(regularB, privateA, regularC), isLaunchDrawer = false) { it.isPrivate }
        )
    }
}
