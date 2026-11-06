package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Test

/** Pins the per-site semantics the three Prefs parsers had before they shared this helper. */
class CsvPairsTest {

    @Test
    fun trimmed_stripsSpacesAroundKeyAndValue() {
        assertEquals(listOf("1" to "2"), CsvPairs.parse(" 1 : 2 ", ':'))
    }

    @Test
    fun untrimmed_keepsSpacesExactly() {
        // The usage cache never trimmed: a padded value then fails toLongOrNull at the call site
        assertEquals(listOf(" a" to " 1 "), CsvPairs.parse(" a= 1 ", '=', trim = false))
    }

    @Test
    fun splitsOnFirstSeparatorOnly() {
        assertEquals(listOf("1" to "com/x:y"), CsvPairs.parse("1:com/x:y", ':'))
    }

    @Test
    fun dropsEmptySegmentsTrailingSeparatorAndMissingKey() {
        assertEquals(listOf("1" to "2"), CsvPairs.parse("1:2,,:5,", ':'))
        assertEquals(emptyList<Pair<String, String>>(), CsvPairs.parse("", ':'))
        assertEquals(emptyList<Pair<String, String>>(), CsvPairs.parse("=1", '=', trim = false))
    }

    @Test
    fun keepsEntriesWithEmptyValueForTheCallSiteToJudge() {
        // widget heights drop "1:" via toIntOrNull, providers via isNotBlank — not here
        assertEquals(listOf("1" to ""), CsvPairs.parse("1:", ':'))
    }
}
