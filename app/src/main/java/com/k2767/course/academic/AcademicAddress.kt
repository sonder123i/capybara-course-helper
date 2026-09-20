package com.k2767.course.academic

import java.net.URI
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class AcademicAddress(val protocol: String, val domain: String, val basePath: String) {
    companion object {
        fun parse(input: String): AcademicAddress? = runCatching {
            val uri = URI(input.trim().let { if (it.contains("://")) it else "https://$it" })
            require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null)
            val path = uri.path.orEmpty().trimEnd('/')
            val feature = Regex("/(?:framework|xtgl|xsxk|xsxkkc|xk|xkgl)(?:/|$)").find(path)
            val root = when {
                feature != null -> path.substring(0, feature.range.first)
                Regex("\\.(?:aspx|jsp|htmlx?|do)$", RegexOption.IGNORE_CASE).containsMatchIn(path) -> path.substringBeforeLast('/', "")
                else -> path
            }
            AcademicAddress(uri.scheme, uri.host.lowercase() + if (uri.port >= 0) ":${uri.port}" else "", root)
        }.getOrNull()
    }
}

/** 白名单匹配结果。协议降级由 school.protocol 决定而非白名单，所以单列一档。 */
enum class HostMatch { ALLOWED, PORT_MISMATCH, FOREIGN_HOST }

object AcademicUrlPolicy {
    private val schemes = setOf("http", "https")

    fun isAllowed(value: String, protocol: String, hosts: Collection<String>): Boolean = runCatching {
        val uri = URI(value)
        if (protocol == "https" && uri.scheme != "https") return false
        hostMatch(value, hosts) == HostMatch.ALLOWED
    }.getOrDefault(false)

    /** 只看主机与端口是否命中白名单，忽略请求协议，供调用方区分异域、端口不符与协议降级。 */
    fun hostMatch(value: String, hosts: Collection<String>): HostMatch = runCatching {
        val uri = URI(value)
        if (uri.scheme !in schemes || uri.host.isNullOrBlank() || uri.userInfo != null) return HostMatch.FOREIGN_HOST
        val port = effectivePort(uri.scheme, uri.port)
        var sameHost = false
        for (configured in hosts) {
            // 条目未写端口时按请求协议补默认端口，与 isAllowed 的既有口径保持一致。
            val allowed = URI(if (configured.contains("://")) configured else "${uri.scheme}://$configured")
            if (allowed.host?.equals(uri.host, true) != true) continue
            sameHost = true
            if (effectivePort(uri.scheme, allowed.port) == port) return HostMatch.ALLOWED
        }
        if (sameHost) HostMatch.PORT_MISMATCH else HostMatch.FOREIGN_HOST
    }.getOrDefault(HostMatch.FOREIGN_HOST)

    /**
     * 反向代理后的教务会把绝对 Location 写成 http（实测九江学院 zhjw1.jju.edu.cn 的每个 302）。
     * 同域名、默认端口的 http 跳升级回 https 再跟随，避免为了跟跳转而放宽 TLS；异域或显式端口不猜。
     */
    fun httpsUpgrade(value: String, protocol: String, hosts: Collection<String>): HttpUrl? {
        if (!protocol.equals("https", true)) return null
        val url = value.toHttpUrlOrNull() ?: return null
        if (url.isHttps || url.port != 80) return null
        if (hostMatch(value, hosts) != HostMatch.ALLOWED) return null
        return url.newBuilder().scheme("https").port(443).build()
    }

    private fun effectivePort(scheme: String?, port: Int): Int =
        if (port == -1) if (scheme == "https") 443 else 80 else port
}
