package com.parem.launcher.helper

import java.net.URI

/** URL handling for website shortcuts on home slots and in folders. Android-free. */
object WebShortcut {

    /**
     * Returns a launchable http(s) URL for what the user typed, or null.
     * A missing scheme becomes https; any other scheme (javascript:, intent:,
     * file:, …) is rejected so a slot can only ever open a web page.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        // "localhost:3000" parses as scheme "localhost"; a digit after the
        // colon means host:port, not a scheme
        val scheme = Regex("^([A-Za-z][A-Za-z0-9+.-]*):(.?)").find(trimmed)
        val withScheme =
            if (scheme == null || scheme.groupValues[2].firstOrNull()?.isDigit() == true) "https://$trimmed"
            else trimmed
        val uri = try { URI(withScheme) } catch (_: Exception) { return null }
        val uriScheme = uri.scheme?.lowercase() ?: return null
        if (uriScheme != "http" && uriScheme != "https") return null
        if (host(uri).isNullOrEmpty()) return null
        return uriScheme + withScheme.substring(uriScheme.length)
    }

    /** The host without a leading "www.", used when the user leaves the label blank. */
    fun defaultLabel(url: String): String {
        val host = try { host(URI(url)) } catch (_: Exception) { null } ?: return url
        return host.removePrefix("www.")
    }

    // URI.host is null for IDN (non-ASCII) hosts; fall back to the raw authority
    private fun host(uri: URI): String? =
        uri.host ?: uri.rawAuthority?.substringAfterLast('@')?.substringBefore(':')
}
