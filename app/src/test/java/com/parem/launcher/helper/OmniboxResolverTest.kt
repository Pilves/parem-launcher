package com.parem.launcher.helper

import com.parem.launcher.helper.ContactMatcher.Contact
import org.junit.Assert.assertEquals
import org.junit.Test

class OmniboxResolverTest {

    private val contacts = listOf(
        Contact("John Smith", "555 1000"),
        Contact("Kim Mi", "555 2000"),
    )

    private fun resolve(query: String, contacts: List<Contact> = this.contacts) =
        OmniboxResolver.resolve(query, contacts)

    // --- each mode on its own ---

    @Test
    fun calc() {
        assertEquals(OmniboxMode.Calc("6"), resolve("2*3"))
        assertEquals(OmniboxMode.Calc("2.5"), resolve("10/4"))
    }

    @Test
    fun conversion() {
        assertEquals(OmniboxMode.Conversion("3.106856 mi"), resolve("5 km to mi"))
    }

    @Test
    fun dial_keepsTrimmedNumber() {
        assertEquals(OmniboxMode.Dial("+372 5555 1234"), resolve("  +372 5555 1234 "))
        assertEquals(OmniboxMode.Dial("12 34"), resolve("12 34"))
    }

    @Test
    fun webSearch_needsLeadingSpaceAndText() {
        assertEquals(OmniboxMode.WebSearch, resolve(" weather tallinn"))
        assertEquals(OmniboxMode.None, resolve("   "))
    }

    @Test
    fun contact() {
        assertEquals(OmniboxMode.Contact("John Smith", "555 1000"), resolve("john"))
    }

    // --- precedence ---

    @Test
    fun calcBeatsDial_forHyphenatedNumbers() {
        assertEquals(OmniboxMode.Calc("-679"), resolve("555-1234"))
    }

    @Test
    fun calcConversionAndDialBeatWeb_despiteLeadingSpace() {
        assertEquals(OmniboxMode.Calc("4"), resolve(" 2+2"))
        assertEquals(OmniboxMode.Conversion("3.106856 mi"), resolve(" 5 km to mi"))
        assertEquals(OmniboxMode.Dial("555 1234"), resolve(" 555 1234"))
    }

    @Test
    fun webBeatsContact() {
        assertEquals(OmniboxMode.WebSearch, resolve(" john"))
    }

    @Test
    fun conversionBeatsContact_evenWhenAContactMatches() {
        // A contact whose name matches the query exactly still loses to a valid conversion.
        val clash = listOf(Contact("5 km mi", "555 3000"))
        assertEquals(1, ContactMatcher.match("5 km mi", clash).size)
        assertEquals(OmniboxMode.Conversion("3.106856 mi"), resolve("5 km mi", clash))
    }

    // --- recognised but unresolved queries fall through ---

    @Test
    fun expressionThatFailsToEvaluate_fallsThrough() {
        assertEquals(OmniboxMode.None, resolve("1/0"))
    }

    @Test
    fun conversionThatFailsToConvert_fallsThrough() {
        // Mixed categories: not a conversion, and with no contacts it is plain search.
        assertEquals(OmniboxMode.None, resolve("5 c to kg", emptyList()))
    }

    // --- plain app searches resolve to None ---

    @Test
    fun plainAppSearches_areNone() {
        // "123" and "1 2 3" have fewer than the 4 digits dialing needs.
        for (q in listOf("", "a", "chrome", "maps", "set", "-5", "123", "1 2 3")) {
            assertEquals("query '$q'", OmniboxMode.None, resolve(q))
        }
    }

    @Test
    fun letterQueries_areNone_withoutAMatchingContact() {
        assertEquals(OmniboxMode.None, resolve("chrome"))
        assertEquals(OmniboxMode.None, resolve("john", emptyList()))
    }
}
