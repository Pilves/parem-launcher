package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportFormatterTest {

    private fun format(t: Throwable) = CrashReportFormatter.format(
        t, "6.0.0", 60, 35, "Samsung", "SM-X700", "main", 0L
    )

    @Test
    fun format_includesHeader() {
        val report = format(IllegalStateException("boom"))
        assertTrue(report.startsWith("Parem 6.0.0 (60)\n"))
        assertTrue(report.contains("Android API 35, Samsung SM-X700"))
        assertTrue(report.contains("Thread: main"))
        assertTrue(report.contains("Time: 1970-01-01T00:00:00Z"))
    }

    @Test
    fun format_includesCauseChain() {
        val report = format(RuntimeException("outer", IllegalArgumentException("inner")))
        assertTrue(report.contains("java.lang.RuntimeException: outer"))
        assertTrue(report.contains("Caused by: java.lang.IllegalArgumentException: inner"))
    }

    @Test
    fun format_nullMessageIsSafe() {
        val report = format(NullPointerException())
        assertTrue(report.contains("java.lang.NullPointerException"))
    }

    @Test
    fun format_hugeTraceIsCapped() {
        val e = RuntimeException("deep")
        e.stackTrace = Array(20_000) { StackTraceElement("com.example.Cls", "m$it", "Cls.kt", it) }
        val report = format(e)
        assertTrue(report.toByteArray().size <= CrashReportFormatter.MAX_BYTES)
        assertTrue(report.endsWith(CrashReportFormatter.TRUNCATED))
    }

    @Test
    fun cap_shortTextUnchanged() {
        assertEquals("a\nb", CrashReportFormatter.cap("a\nb", 100))
    }

    @Test
    fun cap_cutsOnLineBoundary() {
        val text = (1..50).joinToString("\n") { "line number $it" }
        val capped = CrashReportFormatter.cap(text, 200)
        assertTrue(capped.toByteArray().size <= 200)
        val kept = capped.removeSuffix(CrashReportFormatter.TRUNCATED).trimEnd('\n').lines()
        val original = text.lines()
        assertEquals(original.take(kept.size), kept)
    }

    @Test
    fun cap_countsMultibyteCharacters() {
        val text = (1..100).joinToString("\n") { "päring õäöü $it" }
        val capped = CrashReportFormatter.cap(text, 300)
        assertTrue(capped.toByteArray().size <= 300)
    }
}
