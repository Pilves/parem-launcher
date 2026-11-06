package com.parem.launcher.helper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebShortcutTest {

    @Test
    fun normalize_addsHttpsWhenNoScheme() {
        assertEquals("https://example.com", WebShortcut.normalize("example.com"))
    }

    @Test
    fun normalize_trimsInput() {
        assertEquals("https://example.com/a", WebShortcut.normalize("  example.com/a \n"))
    }

    @Test
    fun normalize_keepsHttp() {
        assertEquals("http://example.com", WebShortcut.normalize("http://example.com"))
    }

    @Test
    fun normalize_lowercasesScheme() {
        assertEquals("https://example.com", WebShortcut.normalize("HTTPS://example.com"))
    }

    @Test
    fun normalize_acceptsUppercaseHost() {
        assertEquals("https://EXAMPLE.COM/Path", WebShortcut.normalize("EXAMPLE.COM/Path"))
    }

    @Test
    fun normalize_preservesPort() {
        assertEquals("https://example.com:8080/x", WebShortcut.normalize("example.com:8080/x"))
        assertEquals("http://localhost:3000", WebShortcut.normalize("http://localhost:3000"))
        assertEquals("https://localhost:3000", WebShortcut.normalize("localhost:3000"))
    }

    @Test
    fun normalize_preservesIdnHost() {
        assertEquals("https://jõgi.ee", WebShortcut.normalize("jõgi.ee"))
    }

    @Test
    fun normalize_rejectsJavascript() {
        assertNull(WebShortcut.normalize("javascript:alert(1)"))
    }

    @Test
    fun normalize_rejectsIntent() {
        assertNull(WebShortcut.normalize("intent://scan/#Intent;scheme=zxing;end"))
    }

    @Test
    fun normalize_rejectsFile() {
        assertNull(WebShortcut.normalize("file:///sdcard/a.html"))
    }

    @Test
    fun normalize_rejectsBlank() {
        assertNull(WebShortcut.normalize(""))
        assertNull(WebShortcut.normalize("   "))
    }

    @Test
    fun normalize_rejectsMissingHost() {
        assertNull(WebShortcut.normalize("https://"))
        assertNull(WebShortcut.normalize("https:///path"))
    }

    @Test
    fun normalize_rejectsUnparseable() {
        assertNull(WebShortcut.normalize("exa mple.com"))
        assertNull(WebShortcut.normalize("https://exa^mple.com"))
    }

    @Test
    fun defaultLabel_stripsWww() {
        assertEquals("example.com", WebShortcut.defaultLabel("https://www.example.com/page"))
    }

    @Test
    fun defaultLabel_keepsOtherSubdomains() {
        assertEquals("news.example.com", WebShortcut.defaultLabel("https://news.example.com"))
    }

    @Test
    fun defaultLabel_idnAndPort() {
        assertEquals("jõgi.ee", WebShortcut.defaultLabel("https://jõgi.ee:8443/x"))
    }
}
