package com.parem.launcher.helper

import com.parem.launcher.helper.ContactMatcher.Contact
import org.junit.Assert.*
import org.junit.Test

class ContactMatcherTest {

    private val contacts = listOf(
        Contact("John Smith", "555 1000"),
        Contact("Jane Doe", "555 2000"),
        Contact("Joanna Kim", "555 3000"),
        Contact("Bob Jones", "555 4000"),
        Contact("José García", "555 5000"),
    )

    // --- query gate ---

    @Test
    fun gate_rejectsEmptyAndShort() {
        assertFalse(ContactMatcher.looksLikeContactQuery(""))
        assertFalse(ContactMatcher.looksLikeContactQuery("   "))
        assertFalse(ContactMatcher.looksLikeContactQuery("a"))
    }

    @Test
    fun gate_rejectsNumbersAndOperators() {
        assertFalse(ContactMatcher.looksLikeContactQuery("55"))
        assertFalse(ContactMatcher.looksLikeContactQuery("5+5"))
        assertFalse(ContactMatcher.looksLikeContactQuery("555 1000"))
        assertFalse(ContactMatcher.looksLikeContactQuery("12.5"))
    }

    @Test
    fun gate_acceptsRealNames() {
        assertTrue(ContactMatcher.looksLikeContactQuery("jo"))
        assertTrue(ContactMatcher.looksLikeContactQuery("john s"))
    }

    // --- matching ---

    @Test
    fun match_namePrefix() {
        val result = ContactMatcher.match("john", contacts)
        assertEquals(listOf("John Smith"), result.map { it.name })
    }

    @Test
    fun match_initials() {
        val result = ContactMatcher.match("js", contacts)
        assertEquals(listOf("John Smith"), result.map { it.name })
    }

    @Test
    fun match_carriesNumberThrough() {
        val result = ContactMatcher.match("jane", contacts)
        assertEquals(1, result.size)
        assertEquals("555 2000", result.first().number)
    }

    @Test
    fun match_diacriticsInsensitive() {
        val result = ContactMatcher.match("jose", contacts)
        assertEquals(listOf("José García"), result.map { it.name })
    }

    @Test
    fun match_ranksWholeNamePrefixAboveWordPrefix() {
        // Joanna's whole name starts with "jo"; Bob Jones matches only via the
        // word "Jones", so it ranks below despite coming first alphabetically.
        val list = listOf(
            Contact("Bob Jones", "555 1"),
            Contact("Joanna Kim", "555 2"),
        )
        val result = ContactMatcher.match("jo", list)
        assertEquals(listOf("Joanna Kim", "Bob Jones"), result.map { it.name })
    }

    @Test
    fun match_respectsLimit() {
        val many = (1..10).map { Contact("Sam Number$it", "555 000$it") }
        val result = ContactMatcher.match("sam", many, limit = 3)
        assertEquals(3, result.size)
    }

    // --- strings that must NOT surface contacts ---

    @Test
    fun match_emptyForGatedQueries() {
        assertTrue(ContactMatcher.match("", contacts).isEmpty())
        assertTrue(ContactMatcher.match("a", contacts).isEmpty())
        assertTrue(ContactMatcher.match("5+5", contacts).isEmpty())
        assertTrue(ContactMatcher.match("555 1000", contacts).isEmpty())
    }

    @Test
    fun match_emptyWhenNoNameMatches() {
        assertTrue(ContactMatcher.match("zzq", contacts).isEmpty())
        assertTrue(ContactMatcher.match("xylophone", contacts).isEmpty())
    }

    @Test
    fun match_emptyContactListIsEmpty() {
        assertTrue(ContactMatcher.match("john", emptyList()).isEmpty())
    }
}
