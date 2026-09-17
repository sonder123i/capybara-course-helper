package com.k2767.course.login

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

internal class MatchingMemoryCookieJar(
    private val clockMillis: () -> Long = System::currentTimeMillis
) : CookieJar {
    private val cookies = mutableListOf<StoredCookie>()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = clockMillis()
        for (cookie in cookies) {
            this.cookies.removeAll { stored -> stored.cookie.hasSameIdentityAs(cookie) }
            if (cookie.expiresAt > now) {
                this.cookies += StoredCookie(cookie, url.host, url.port)
            }
        }
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.map(StoredCookie::cookie).filter { it.matches(url) }
    }

    @Synchronized
    fun cookieHeaderFor(url: HttpUrl, setByHost: String? = null): String {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.asSequence()
            .filter { stored -> setByHost == null || stored.setByHost == setByHost }
            .map(StoredCookie::cookie)
            .filter { cookie -> cookie.matches(url) }
            .joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }
    }

    @Synchronized
    fun clear() {
        cookies.clear()
    }

    /** 返回由指定来源（host:port）的响应种下的全部 Cookie（不受 path 匹配限制），供会话迁移使用。 */
    @Synchronized
    fun cookiesSetBy(host: String, port: Int): List<Cookie> {
        val now = clockMillis()
        cookies.removeAll { it.cookie.expiresAt <= now }
        return cookies.filter { it.setByHost == host && it.setByPort == port }.map(StoredCookie::cookie)
    }

    private fun Cookie.hasSameIdentityAs(other: Cookie): Boolean =
        name == other.name && domain == other.domain && path == other.path

    private data class StoredCookie(val cookie: Cookie, val setByHost: String, val setByPort: Int)
}
