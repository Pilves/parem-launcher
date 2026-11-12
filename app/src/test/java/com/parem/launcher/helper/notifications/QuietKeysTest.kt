package com.parem.launcher.helper.notifications

import com.parem.launcher.helper.notifications.QuietKeys.Posted
import com.parem.launcher.helper.notifications.QuietKeys.Tracked
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietKeysTest {

    private val minute = 60 * 1000L
    private val now = 1_000_000 * minute
    private val keep = QuietFilter.SNOOZE_MS + QuietKeys.PRUNE_SLACK_MS

    @Test
    fun releasedKeyPassesThroughBeforeAMatchingMark() {
        assertEquals(Posted.PASS_THROUGH, QuietKeys.postedAction(now + 1, now, dismissedWhen = 5, postedWhen = 5))
        assertEquals(Posted.PASS_THROUGH, QuietKeys.postedAction(now, now, null, 5))
    }

    @Test
    fun expiredPassThroughFallsBackToMarkAndFilter() {
        assertEquals(Posted.CANCEL, QuietKeys.postedAction(now - 1, now, dismissedWhen = 5, postedWhen = 5))
        assertEquals(Posted.FILTER, QuietKeys.postedAction(now - 1, now, null, 5))
    }

    @Test
    fun unchangedDismissedItemIsCancelledAtExpiry() {
        assertEquals(Posted.CANCEL, QuietKeys.postedAction(null, now, dismissedWhen = 5, postedWhen = 5))
    }

    @Test
    fun newerPostUnderADismissedKeyGoesToTheFilter() {
        assertEquals(Posted.FILTER, QuietKeys.postedAction(null, now, dismissedWhen = 5, postedWhen = 6))
        assertEquals(Posted.FILTER, QuietKeys.postedAction(null, now, null, 5))
    }

    @Test
    fun markCountsOnlyWhileItsSnoozeCanStillBeRunning() {
        assertEquals(5L, QuietKeys.liveMark(5, now - QuietFilter.SNOOZE_MS, now))
        assertEquals(5L, QuietKeys.liveMark(5, now - keep, now))
        // The app cancelled its snoozed item, so no repost came to spend the mark
        assertEquals(null, QuietKeys.liveMark(5, now - keep - 1, now))
        assertEquals(null, QuietKeys.liveMark(5, null, now))
        assertEquals(null, QuietKeys.liveMark(null, now, now))
    }

    @Test
    fun listHidesADismissedItemUntilANewerPost() {
        val marks = mapOf("k" to 5L)
        assertFalse(QuietKeys.isVisible("k", 5, marks))
        assertTrue(QuietKeys.isVisible("k", 6, marks))
        assertTrue(QuietKeys.isVisible("other", 5, marks))
    }

    @Test
    fun pruneKeepsListedKeysWhateverTheirAge() {
        val tracked = Tracked(mapOf("old" to now - 10 * keep), mapOf("old" to 5L))
        assertEquals(tracked, QuietKeys.prune(tracked, setOf("old"), now))
    }

    @Test
    fun pruneAfterARebootKeepsKeysUntilTheirSnoozeHasSurelyEnded() {
        // After a reboot the system lists nothing until each app reposts
        val tracked = Tracked(
            mapOf("recent" to now - 60 * minute, "edge" to now - keep, "gone" to now - keep - 1),
            mapOf("recent" to 1L, "gone" to 2L),
        )
        val kept = QuietKeys.prune(tracked, emptySet(), now)
        assertEquals(setOf("recent", "edge"), kept.snoozedAt.keys)
        assertEquals(mapOf("recent" to 1L), kept.dismissed)
    }

    @Test
    fun pruneDropsAMarkWithoutAKey() {
        val tracked = Tracked(mapOf("k" to now), mapOf("k" to 1L, "orphan" to 2L))
        assertEquals(mapOf("k" to 1L), QuietKeys.prune(tracked, emptySet(), now).dismissed)
    }

    @Test
    fun newlyAllowedAppReleasesOnlyOurSnoozedKeysFromIt() {
        val before = setOf("com.a")
        val after = setOf("com.a", "com.b")
        val snoozed = mapOf(
            "a1" to "com.a",
            "b1" to "com.b",
            "b2" to "com.b",
            "b-user" to "com.b",
            "c1" to "com.c",
        )
        // b-user is the user's own shade snooze; b-shade is ours but back in the shade
        val tracked = setOf("a1", "b1", "b2", "c1", "b-shade")
        assertEquals(setOf("b1", "b2"), QuietKeys.releasable(snoozed, tracked, after - before))
        assertEquals(emptySet<String>(), QuietKeys.releasable(snoozed, tracked, before - before))
    }

    @Test
    fun turningOffReleasesEveryKeyOfOursTheSystemStillHolds() {
        val snoozed = mapOf("a1" to "com.a", "b1" to "com.b", "user" to "com.c")
        val tracked = setOf("a1", "b1", "not-listed")
        assertEquals(setOf("a1", "b1"), QuietKeys.releasable(snoozed, tracked, null))
    }

    @Test
    fun legacyKeysMigrateAsSnoozedNow() {
        val entries = mapOf(
            QuietKeys.LEGACY_NAME to setOf("old1", "old2", "new"),
            QuietKeys.snoozedName("new") to 7L,
            QuietKeys.dismissedName("new") to 9L,
        )
        val tracked = QuietKeys.decode(entries, now)
        assertEquals(mapOf("old1" to now, "old2" to now, "new" to 7L), tracked.snoozedAt)
        assertEquals(mapOf("new" to 9L), tracked.dismissed)
        // One more snooze, then gone if the system never lists them
        assertEquals(setOf("old1", "old2"), QuietKeys.prune(tracked, emptySet(), now + keep).snoozedAt.keys)
        assertTrue(QuietKeys.prune(tracked, emptySet(), now + keep + 1).snoozedAt.isEmpty())
    }

    @Test
    fun decodeKeepsKeysWithSeparatorsAndIgnoresOtherEntries() {
        val key = "0|com.chat|12|snoozed:tag|10123"
        val entries = mapOf(
            QuietKeys.snoozedName(key) to 3L,
            QuietKeys.dismissedName(key) to 4L,
            "snoozed:wrong-type" to "3",
            "unrelated" to 5L,
        )
        assertEquals(Tracked(mapOf(key to 3L), mapOf(key to 4L)), QuietKeys.decode(entries, now))
    }
}
