package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerRowsTest {

    private data class Row(val name: String, val isPrivate: Boolean = false)

    private val regularB = Row("b")
    private val privateA = Row("a", isPrivate = true)
    private val regularC = Row("c")
    private val header = Row("header", isPrivate = true)
    private val padding = Row("padding")

    private fun decorate(apps: List<Row>, header: Row? = this.header, padding: Row? = this.padding) =
        DrawerRows.decorate(apps, { it.isPrivate }, header, padding)

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
