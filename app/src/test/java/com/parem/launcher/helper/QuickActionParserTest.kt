package com.parem.launcher.helper

import com.parem.launcher.helper.QuickAction.Alarm
import com.parem.launcher.helper.QuickAction.Event
import com.parem.launcher.helper.QuickAction.Timer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime

class QuickActionParserTest {

    // Saturday 2026-10-03, 10:00
    private val now = LocalDateTime.of(2026, 10, 3, 10, 0)

    private fun parse(q: String, at: LocalDateTime = now) = QuickActionParser.parse(q, at)

    private fun on(day: Int, hour: Int = 0, minute: Int = 0) = LocalDateTime.of(2026, 10, day, hour, minute)

    @Test
    fun alarm_mustParse() {
        val cases = mapOf(
            "alarm 7:30" to Alarm(7, 30, null),
            "alarm 7.30" to Alarm(7, 30, null),
            "alarm 19:30" to Alarm(19, 30, null),
            "alarm 0:05" to Alarm(0, 5, null),
            "alarm 7am" to Alarm(7, 0, null),
            "alarm 7 pm" to Alarm(19, 0, null),
            "alarm 7:30pm" to Alarm(19, 30, null),
            "alarm 12am" to Alarm(0, 0, null),
            "alarm 12pm" to Alarm(12, 0, null),
            "alarm at 7" to Alarm(7, 0, null),
            "Alarm  6:45   gym bag" to Alarm(6, 45, "gym bag"),
            "ALARM 7:30 Wake Up" to Alarm(7, 30, "Wake Up"),
            "äratus kell 7" to Alarm(7, 0, null),
            "äratus 6.15 trenn" to Alarm(6, 15, "trenn"),
            "alarm 7:30 " to Alarm(7, 30, null),
        )
        cases.forEach { (q, expected) -> assertEquals("query '$q'", expected, parse(q)) }
    }

    @Test
    fun timer_mustParse() {
        val cases = mapOf(
            "timer 10m" to Timer(600, null),
            "timer 10 min" to Timer(600, null),
            "timer 1h30m" to Timer(5400, null),
            "timer 1 h 30 min" to Timer(5400, null),
            "timer 90s" to Timer(90, null),
            "timer 2 hours" to Timer(7200, null),
            "timer 24h" to Timer(86400, null),
            "timer 10m eggs" to Timer(600, "eggs"),
            "timer 3 minutes Tea" to Timer(180, "Tea"),
            "taimer 5 minutit" to Timer(300, null),
            "taimer 1 tund" to Timer(3600, null),
            "taimer 30 sek munad" to Timer(30, "munad"),
        )
        cases.forEach { (q, expected) -> assertEquals("query '$q'", expected, parse(q)) }
    }

    @Test
    fun event_mustParse() {
        val cases = mapOf(
            "event friday 3pm dentist" to Event(on(9, 15), false, "dentist"),
            "event tomorrow 9:00 Standup call" to Event(on(4, 9), false, "Standup call"),
            "event today 18:30 dinner" to Event(on(3, 18, 30), false, "dinner"),
            "event friday dentist" to Event(on(9), true, "dentist"),
            "event monday at 10 people meeting" to Event(on(5, 10), false, "people meeting"),
            "event tomorrow 10 people" to Event(on(4), true, "10 people"),
            // Time only: today if still ahead, otherwise tomorrow
            "event 3pm dentist" to Event(on(3, 15), false, "dentist"),
            "event 9:00 dentist" to Event(on(4, 9), false, "dentist"),
            "kohtumine reedel kell 15 hambaarst" to Event(on(9, 15), false, "hambaarst"),
            "sündmus homme sünnipäev" to Event(on(4), true, "sünnipäev"),
            "sündmus esmaspäev 9.30 koosolek" to Event(on(5, 9, 30), false, "koosolek"),
        )
        cases.forEach { (q, expected) -> assertEquals("query '$q'", expected, parse(q)) }
    }

    @Test
    fun remind_timeOnly_isLabelledAlarm_withDay_isEvent() {
        assertEquals(Alarm(17, 0, "call mum"), parse("remind me 5pm call mum"))
        assertEquals(Alarm(17, 0, "call mum"), parse("Remind  me at 17 call mum"))
        assertEquals(Event(on(4, 17), false, "call mum"), parse("remind me tomorrow 5pm call mum"))
        assertEquals(Alarm(9, 0, "helista emale"), parse("tuleta meelde kell 9 helista emale"))
        assertEquals(Event(on(4, 9), false, "helista emale"), parse("tuleta meelde homme kell 9 helista emale"))
    }

    @Test
    fun mustNotParse() {
        val queries = listOf(
            // bare keywords and plurals
            "alarm", "alarms", "timer", "timers", "event", "remind", "remind me", "reminders", "calendar",
            // app-like names
            "alarmy", "alarm clock", "timer+", "timer 2", "eventbrite", "events 2026", "äratuskell", "timer plus",
            // out of range
            "alarm 25:00", "alarm 7:61", "alarm 13pm", "alarm 0am", "timer 0m", "timer 25h", "timer 86401s",
            "timer 9999999m",
            // bare hour without at/kell, unknown unit, missing title/text
            "alarm 7", "timer 10x", "event tomorrow", "event friday", "event 3pm", "event dentist",
            "remind me 5pm", "remind me tomorrow", "remind me call mum",
            // leading space is a web search
            " alarm 7:30", " timer 10m",
            // deliberately not parsed in 6.0
            "remind me in 20 min", "alarm in 8 hours", "timer for 10 minutes", "alarm for 7:30",
            "set alarm 7:30", "set a timer 5m", "wake me at 7",
            // no numeric dates
            "event 3.10.2026 dentist",
        )
        queries.forEach { assertNull("query '$it'", parse(it)) }
    }

    @Test
    fun now_edgeCases() {
        val lateNight = LocalDateTime.of(2026, 10, 3, 23, 59)
        assertEquals(Event(on(4, 23, 59), false, "x"), parse("event 23:59 x", lateNight))
        assertEquals(Event(on(4, 0, 0), false, "x"), parse("event 0:00 x", lateNight))
        // A time exactly now is past: tomorrow
        assertEquals(Event(on(4, 10), false, "x"), parse("event 10:00 x"))
        // Today's weekday means next week
        assertEquals(Event(on(10), true, "x"), parse("event saturday x"))
        val sunday = LocalDateTime.of(2026, 10, 4, 12, 0)
        assertEquals(Event(on(11), true, "x"), parse("event sunday x", sunday))
        assertEquals(Event(on(5), true, "x"), parse("event monday x", sunday))
        // Across a month end
        val endOfMonth = LocalDateTime.of(2026, 10, 31, 12, 0)
        assertEquals(Event(LocalDateTime.of(2026, 11, 1, 0, 0), true, "x"), parse("event tomorrow x", endOfMonth))
    }

    @Test
    fun isActionPrefix() {
        val yes = listOf(
            "ala", "alarm", "alarm ", "alarm 7", "timer ", "timer p", "remind m", "äratus 7", "eve",
            "Alarm 7:30", "remind  me 5pm", "tai", "kohtumine reedel",
        )
        val no = listOf("al", "a", "", "alarmy", "ever", "timers", " alarm 7:30", "calendar", "chrome", "remind x")
        yes.forEach { assertEquals("query '$it'", true, QuickActionParser.isActionPrefix(it)) }
        no.forEach { assertEquals("query '$it'", false, QuickActionParser.isActionPrefix(it)) }
    }

    @Test
    fun durationText() {
        assertEquals("10 min", QuickActionParser.durationText(600))
        assertEquals("1 h 30 min", QuickActionParser.durationText(5400))
        assertEquals("1 min 30 s", QuickActionParser.durationText(90))
        assertEquals("24 h", QuickActionParser.durationText(86400))
    }
}
