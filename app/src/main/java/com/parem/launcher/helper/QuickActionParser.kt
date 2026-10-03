package com.parem.launcher.helper

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Locale

sealed interface QuickAction {
    data class Alarm(val hour: Int, val minute: Int, val label: String?) : QuickAction
    data class Timer(val seconds: Int, val label: String?) : QuickAction
    data class Event(val start: LocalDateTime, val allDay: Boolean, val title: String) : QuickAction
}

/**
 * Omnibox quick actions (M4-WP15): "alarm 7:30", "timer 10m", "event friday
 * 3pm dentist", "remind me 5pm call mum". Keyword first and the whole query
 * must parse, so an app search can never turn into an action; anything that
 * doesn't fit falls through to app search. Pure, no Android deps.
 */
object QuickActionParser {

    private enum class Kind { ALARM, TIMER, EVENT, REMIND }

    private val KEYWORDS = listOf(
        "alarm" to Kind.ALARM, "äratus" to Kind.ALARM,
        "timer" to Kind.TIMER, "taimer" to Kind.TIMER,
        "event" to Kind.EVENT, "sündmus" to Kind.EVENT, "kohtumine" to Kind.EVENT,
        "remind me" to Kind.REMIND, "tuleta meelde" to Kind.REMIND,
    )

    // A bare hour is only a time after one of these: "event 3 people" stays a title
    private val TIME_PREFIXES = setOf("at", "kell")
    private val TIME_REGEX = Regex("(\\d{1,2})(?:[:.](\\d{2}))?(am|pm)?")
    private val MERIDIEMS = setOf("am", "pm")

    private val UNIT_SECONDS: Map<String, Int> = buildMap {
        listOf("s", "sec", "secs", "second", "seconds", "sek", "sekund", "sekundit", "sekundi")
            .forEach { put(it, 1) }
        listOf("m", "min", "mins", "minute", "minutes", "minut", "minutit", "minuti")
            .forEach { put(it, 60) }
        listOf("h", "hr", "hrs", "hour", "hours", "tund", "tundi")
            .forEach { put(it, 3600) }
    }
    private val ATTACHED_DURATION = Regex("(?:\\d{1,6}[a-z]+)+")
    private val DURATION_PART = Regex("(\\d{1,6})([a-z]+)")
    private const val MAX_TIMER_SECONDS = 86_400L

    private val WEEKDAYS: Map<String, DayOfWeek> = buildMap {
        val en = listOf("monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday")
        val et = listOf("esmaspäev", "teisipäev", "kolmapäev", "neljapäev", "reede", "laupäev", "pühapäev")
        // Adessive ("on Friday"): "kohtumine reedel kell 15" is how it's said
        val etOn = listOf("esmaspäeval", "teisipäeval", "kolmapäeval", "neljapäeval", "reedel", "laupäeval", "pühapäeval")
        DayOfWeek.entries.forEachIndexed { i, day ->
            put(en[i], day); put(et[i], day); put(etOn[i], day)
        }
    }

    fun parse(query: String, now: LocalDateTime): QuickAction? {
        // A leading space is an explicit web search
        if (query.startsWith(" ")) return null
        val tokens = query.trim().split(Regex("\\s+"))
        val lower = tokens.map { it.lowercase(Locale.ROOT) }
        val joined = lower.joinToString(" ")
        val (keyword, kind) = KEYWORDS.firstOrNull { joined.startsWith(it.first + " ") } ?: return null
        val start = keyword.count { it == ' ' } + 1
        return when (kind) {
            Kind.ALARM -> {
                val time = time(lower, start) ?: return null
                QuickAction.Alarm(time.hour, time.minute, rest(tokens, time.next))
            }
            Kind.TIMER -> {
                val (seconds, next) = duration(lower, start) ?: return null
                QuickAction.Timer(seconds, rest(tokens, next))
            }
            Kind.EVENT -> {
                var i = start
                val day = day(lower.getOrNull(i), now.toLocalDate())?.also { i++ }
                val time = time(lower, i)?.also { i = it.next }
                val title = rest(tokens, i) ?: return null
                when {
                    time != null -> QuickAction.Event(at(day, time, now), false, title)
                    day != null -> QuickAction.Event(day.atStartOfDay(), true, title)
                    else -> null
                }
            }
            Kind.REMIND -> {
                var i = start
                val day = day(lower.getOrNull(i), now.toLocalDate())?.also { i++ }
                val time = time(lower, i) ?: return null
                val text = rest(tokens, time.next) ?: return null
                // A time alone becomes an alarm because an alarm actually rings;
                // a calendar event only notifies if the calendar's default does
                if (day == null) QuickAction.Alarm(time.hour, time.minute, text)
                else QuickAction.Event(day.atTime(time.hour, time.minute), false, text)
            }
        }
    }

    /**
     * True while the query could still be heading for an action: a keyword
     * prefix of 3+ characters, or a keyword plus anything. The drawer holds
     * auto-launch then, so "alarm 7:30" never opens an app called "Alarmy".
     */
    fun isActionPrefix(query: String): Boolean {
        if (query.startsWith(" ")) return false
        val q = query.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
        return KEYWORDS.any { (keyword, _) ->
            val withSpace = "$keyword "
            (q.length >= 3 && withSpace.startsWith(q)) || q.startsWith(withSpace)
        }
    }

    /** "10 min", "1 h 30 min", "45 s": never "10:00", which reads as minutes or hours. */
    fun durationText(seconds: Int): String = listOfNotNull(
        (seconds / 3600).takeIf { it > 0 }?.let { "$it h" },
        (seconds % 3600 / 60).takeIf { it > 0 }?.let { "$it min" },
        (seconds % 60).takeIf { it > 0 }?.let { "$it s" },
    ).joinToString(" ")

    private class Time(val hour: Int, val minute: Int, val next: Int)

    private fun time(tokens: List<String>, from: Int): Time? {
        var i = from
        val prefixed = tokens.getOrNull(i) in TIME_PREFIXES
        if (prefixed) i++
        val match = TIME_REGEX.matchEntire(tokens.getOrNull(i) ?: return null) ?: return null
        i++
        var meridiem = match.groupValues[3].ifEmpty { null }
        if (meridiem == null && tokens.getOrNull(i) in MERIDIEMS) meridiem = tokens[i++]
        val hour = match.groupValues[1].toInt()
        val minute = match.groupValues[2].ifEmpty { null }?.toInt()
        if (minute == null && meridiem == null && !prefixed) return null
        if (minute != null && minute > 59) return null
        val hour24 = when (meridiem) {
            null -> hour.takeIf { it <= 23 } ?: return null
            else -> {
                if (hour !in 1..12) return null
                hour % 12 + if (meridiem == "pm") 12 else 0
            }
        }
        return Time(hour24, minute ?: 0, i)
    }

    /** Consumes "10m", "1h30m", "1 h 30 min", "2 hours"…; seconds and the next index. */
    private fun duration(tokens: List<String>, from: Int): Pair<Int, Int>? {
        var i = from
        var total = 0L
        while (i < tokens.size) {
            val token = tokens[i]
            val attached = attachedSeconds(token)
            if (attached != null) {
                total += attached
                i++
            } else if (token.length <= 6 && token.all { it.isDigit() } && tokens.getOrNull(i + 1) in UNIT_SECONDS) {
                total += token.toLong() * UNIT_SECONDS.getValue(tokens[i + 1])
                i += 2
            } else break
            if (total > MAX_TIMER_SECONDS) return null
        }
        if (i == from || total < 1) return null
        return total.toInt() to i
    }

    // "10m", "1h30m"; null unless every part has a known unit
    private fun attachedSeconds(token: String): Long? {
        if (!ATTACHED_DURATION.matches(token)) return null
        return DURATION_PART.findAll(token).sumOf { m ->
            val unit = UNIT_SECONDS[m.groupValues[2]] ?: return null
            m.groupValues[1].toLong() * unit
        }
    }

    private fun day(token: String?, today: LocalDate): LocalDate? = when (token) {
        null -> null
        "today", "täna" -> today
        "tomorrow", "homme" -> today.plusDays(1)
        else -> WEEKDAYS[token]?.let { target ->
            // The next one: today's weekday means a week from today
            val ahead = (target.value - today.dayOfWeek.value + 7) % 7
            today.plusDays(if (ahead == 0) 7L else ahead.toLong())
        }
    }

    // A time with no day is the next time the clock shows it
    private fun at(day: LocalDate?, time: Time, now: LocalDateTime): LocalDateTime {
        if (day != null) return day.atTime(time.hour, time.minute)
        val today = now.toLocalDate().atTime(time.hour, time.minute)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun rest(tokens: List<String>, from: Int): String? =
        tokens.drop(from).joinToString(" ").ifEmpty { null }
}
