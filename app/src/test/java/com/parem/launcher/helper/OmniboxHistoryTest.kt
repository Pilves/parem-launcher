package com.parem.launcher.helper

import com.parem.launcher.helper.OmniboxHistory.Entry
import org.junit.Assert.assertEquals
import org.junit.Test

class OmniboxHistoryTest {

    private val maps = Entry.App("com.maps|UserHandle{0}")
    private val calc = Entry.Query("2+2")
    private val web = Entry.Query(" weather tallinn")

    @Test
    fun encodeDecode_roundTrips() {
        val entries = listOf(maps, calc, web)
        assertEquals(entries, OmniboxHistory.decode(OmniboxHistory.encode(entries)))
    }

    @Test
    fun decode_emptyAndJunk_skipped() {
        assertEquals(emptyList<Entry>(), OmniboxHistory.decode(""))
        assertEquals(listOf(calc), OmniboxHistory.decode("x:nope\na:\nq:2+2\n"))
    }

    @Test
    fun push_newestFirst_dedupes() {
        val list = OmniboxHistory.push(OmniboxHistory.push(listOf(maps), calc), maps)
        assertEquals(listOf(maps, calc), list)
    }

    @Test
    fun push_capsAtMax() {
        var list = emptyList<Entry>()
        for (i in 1..8) list = OmniboxHistory.push(list, Entry.Query("q$i"))
        assertEquals(OmniboxHistory.MAX_ENTRIES, list.size)
        assertEquals(Entry.Query("q8"), list.first())
        assertEquals(Entry.Query("q4"), list.last())
    }

    @Test
    fun push_query_keepsLeadingSpace_trimsTrailing_flattensNewlines() {
        assertEquals(listOf(Entry.Query(" a b")), OmniboxHistory.push(emptyList(), Entry.Query(" a\nb  ")))
    }

    @Test
    fun push_blank_ignored() {
        assertEquals(listOf(maps), OmniboxHistory.push(listOf(maps), Entry.Query("   ")))
        assertEquals(listOf(maps), OmniboxHistory.push(listOf(maps), Entry.App("")))
    }

    @Test
    fun push_sameQueryWithTrailingSpace_dedupes() {
        assertEquals(listOf(calc), OmniboxHistory.push(listOf(calc), Entry.Query("2+2 ")))
    }
}
