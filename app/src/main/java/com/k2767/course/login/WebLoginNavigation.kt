package com.k2767.course.login

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URLEncoder

/** Interactive browsing is independent of the protocol adapter's host allowlist. */
object WebLoginNavigation {
    fun isWebUrl(value: String): Boolean = value.toHttpUrlOrNull()?.let {
        it.username.isEmpty() && it.password.isEmpty()
    } == true

    fun searchUrl(keyword: String): String =
        "https://www.bing.com/search?q=" + URLEncoder.encode(keyword.trim(), "UTF-8")

    fun resolveInput(input: String): String? {
        val value = input.trim()
        if (value.isEmpty()) return null
        if (value.startsWith("http://", true) || value.startsWith("https://", true))
            return value.toHttpUrlOrNull()?.takeIf { isWebUrl(value) }?.toString()
        // Only web URLs may be entered; do not execute pasted javascript/file links.
        if (Regex("^[A-Za-z][A-Za-z0-9+.-]*:").containsMatchIn(value) &&
            !Regex("^[^/\\s]+:\\d+(?:/|$)").containsMatchIn(value)) return null
        if (value.none(Char::isWhitespace) && (value.substringBefore('/').contains('.') || value.startsWith("localhost"))) {
            val url = "https://$value".toHttpUrlOrNull()
            return url?.takeIf { isWebUrl(it.toString()) }?.toString()
        }
        return searchUrl(value)
    }
}
