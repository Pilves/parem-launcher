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
    // --- best = the first of match, ties included ---

    private val crowd = listOf(
        Contact("John Smith", "1"), Contact("Ann Lee", "2"), Contact("ann lee", "3"), Contact("ANN LEE", "4"),
        Contact("Joanna Kim", "5"), Contact("José García", "6"), Contact("jose garcia", "7"),
        Contact("Annabel", "8"), Contact("Hannah Anders", "9"), Contact("Dr. Ann", "10"),
        Contact("Zoë Ann", "11"), Contact("Anne-Marie", "12"), Contact("Ánna", "13"),
        Contact("Ian Mann", "14"), Contact("Jo An", "15"), Contact("Ann Lee", "16"), Contact("Bob Jones", "17"),
    )
    private val crowdQueries = listOf("an", "ann", "ANN", " ann ", "jo", "jose", "JOSÉ", "ja", "ann lee",
        "a l", "j s", "mar", "dr", "an.", "nna", "zz", "5+5", "a", "", "  jo  ")

    @Test
    fun match_ordersExactlyAsBeforeAtEveryLimit() {
        for (contacts in listOf(crowd, crowd.reversed())) for (q in crowdQueries) for (limit in listOf(1, 5, 100)) {
            assertEquals("'$q' limit $limit", previousMatch(q, contacts, limit), ContactMatcher.match(q, contacts, limit))
        }
    }

    @Test
    fun best_isTheFirstOfMatch() {
        for (contacts in listOf(crowd, crowd.reversed())) for (q in crowdQueries) {
            assertEquals("'$q'", ContactMatcher.match(q, contacts).firstOrNull(), ContactMatcher.best(q, contacts))
        }
    }

    @Test
    fun best_keepsListOrderAmongEqualContacts() {
        // Same rank and the same name once lowercased: the earlier contact wins
        assertEquals("2", ContactMatcher.best("ann lee", crowd)?.number)
        assertEquals("16", ContactMatcher.best("ann lee", crowd.reversed())?.number)
        assertEquals(listOf("2", "3", "4", "16"), ContactMatcher.match("ann lee", crowd, limit = 4).map { it.number })
    }

    // The ranking as it was before names were lowercased once per contact
    private fun previousMatch(query: String, contacts: List<Contact>, limit: Int): List<Contact> {
        if (!ContactMatcher.looksLikeContactQuery(query)) return emptyList()
        val q = query.trim()
        val nq = SearchMatcher.key(q).normalized
        return contacts
            .mapNotNull { contact ->
                val key = contact.searchKey
                if (!SearchMatcher.matches(contact.name, key, q)) return@mapNotNull null
                contact to when {
                    nq.isEmpty() -> 3
                    key.normalized.startsWith(nq) -> 0
                    key.words.any { it.startsWith(nq) } -> 1
                    else -> 2
                }
            }
            .sortedWith(compareBy({ it.second }, { it.first.name.lowercase() }))
            .map { it.first }
            .take(limit)
    }

    @Test
    fun match_ranksNormalizedWholeNameBeforeLaterWords() {
        val result = ContactMatcher.match(" JÓ.-SE ", listOf(
            Contact("Alice José", "1"),
            Contact("José García", "2"),
        ))
        assertEquals(listOf("José García", "Alice José"), result.map { it.name })
    }
}
