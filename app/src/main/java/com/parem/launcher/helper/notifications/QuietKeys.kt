package com.parem.launcher.helper.notifications

/**
 * What the quiet filter remembers about the notifications it hid, and the
 * decisions that rest on it. Android-free: the listener and the manager do the
 * platform calls and the storage, and pass plain values in.
 *
 * The rules follow how Android treats a snoozed key (AOSP NotificationManagerService
 * and SnoozeHelper, android16):
 * - a post on a snoozed key never reaches the shade or a listener; it is merged
 *   into the snoozed record. Chat apps reuse one key per conversation, so a key
 *   we keep snoozed swallows that conversation until the snooze ends.
 * - a listener's cancel on a snoozed key is a no-op.
 * - snoozing an already snoozed key again reschedules its repost, so a short
 *   snooze is "release now". A repost alerts like a new post, then reaches listeners.
 * - after a reboot the system lists nothing as snoozed until each app reposts,
 *   and it snoozes that repost again without telling listeners.
 */
object QuietKeys {

    /** How long the listener leaves a released key's repost alone. */
    const val PASS_THROUGH_MS = 60 * 1000L

    /** A key the system no longer lists is kept this long past its snooze, for the reboot case. */
    const val PRUNE_SLACK_MS = 10 * 60 * 1000L

    // Prefs file entry names. The old format was one string set of keys without times
    const val LEGACY_NAME = "SNOOZED_KEYS"
    private const val SNOOZED = "snoozed:"
    private const val DISMISSED = "dismissed:"

    /**
     * Keys, times and `Notification.when` only, never content.
     * [snoozedAt]: key to when we last snoozed it (wall clock).
     * [dismissed]: key to the `when` of the item the user opened or dismissed.
     */
    data class Tracked(val snoozedAt: Map<String, Long>, val dismissed: Map<String, Long>)

    enum class Posted {
        /** Released by us a moment ago: snoozing it again would undo the release. */
        PASS_THROUGH,

        /** The item the user opened or dismissed, back at expiry: cancel it and forget the key. */
        CANCEL,

        /** Anything else: the filter decides. */
        FILTER,
    }

    /**
     * What the listener does with a post. Pass-through comes first, because
     * releasing forgets the mark; a mark only counts while the app has not posted
     * a newer version under that key.
     */
    fun postedAction(passThroughUntil: Long?, now: Long, dismissedWhen: Long?, postedWhen: Long): Posted = when {
        passThroughUntil != null && now <= passThroughUntil -> Posted.PASS_THROUGH
        dismissedWhen != null && dismissedWhen == postedWhen -> Posted.CANCEL
        else -> Posted.FILTER
    }

    /**
     * A mark counts only while its key's snooze can still be running, which is
     * when the expiry repost it waits for arrives. An app that cancels its own
     * snoozed item gets no repost, and its stale mark must not cancel a later
     * post that happens to carry the same `when`.
     */
    fun liveMark(mark: Long?, snoozedAt: Long?, now: Long): Long? =
        if (mark != null && snoozedAt != null && withinSnooze(snoozedAt, now)) mark else null

    private fun withinSnooze(snoozedAt: Long, now: Long) =
        now - snoozedAt <= QuietFilter.SNOOZE_MS + PRUNE_SLACK_MS

    /** The quiet list hides an item the user opened or dismissed until its app posts a newer version. */
    fun isVisible(key: String, postedWhen: Long, dismissed: Map<String, Long>): Boolean =
        dismissed[key] != postedWhen

    /**
     * Forgets a key, with its mark, only when the system no longer lists it as
     * snoozed and its snooze has surely ended. "Not listed" alone would forget
     * every key after a reboot, while the system still holds them.
     */
    fun prune(tracked: Tracked, listed: Set<String>, now: Long): Tracked {
        val kept = tracked.snoozedAt.filter { (key, at) -> key in listed || withinSnooze(at, now) }
        return Tracked(kept, tracked.dismissed.filterKeys { it in kept })
    }

    /**
     * Our keys to release: only those the system holds as snoozed (a key it no
     * longer lists cannot be released, and one back in the shade must not be
     * snoozed again), from [packages] when given. [snoozed] maps each snoozed key
     * to its package; the user's own shade snoozes are never ours.
     */
    fun releasable(snoozed: Map<String, String>, tracked: Set<String>, packages: Set<String>?): Set<String> =
        snoozed.filter { (key, pkg) -> key in tracked && (packages == null || pkg in packages) }.keys

    fun snoozedName(key: String) = SNOOZED + key

    fun dismissedName(key: String) = DISMISSED + key

    /**
     * Reads the prefs file's entries. Keys from the old format have no time, so
     * they count as snoozed [now]: the prune then keeps them for one more snooze,
     * as long as the system can still be holding any of them.
     */
    fun decode(entries: Map<String, *>, now: Long): Tracked {
        val snoozedAt = HashMap<String, Long>()
        (entries[LEGACY_NAME] as? Set<*>)?.forEach { if (it is String) snoozedAt[it] = now }
        val dismissed = HashMap<String, Long>()
        for ((name, value) in entries) {
            if (value !is Long) continue
            when {
                name.startsWith(SNOOZED) -> snoozedAt[name.removePrefix(SNOOZED)] = value
                name.startsWith(DISMISSED) -> dismissed[name.removePrefix(DISMISSED)] = value
            }
        }
        return Tracked(snoozedAt, dismissed)
    }
}
