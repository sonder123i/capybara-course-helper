package com.tyust.course.academic.plugin

import android.os.Build
import android.os.SystemClock
import android.util.Log
import okhttp3.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Connections only: no credentials, cookies or permission decisions are cached. */
internal object PluginHttpClients {
    private val clients = LinkedHashMap<String, OkHttpClient>(16, .75f, true)
    @Synchronized fun client(operation: PluginOperation, url: HttpUrl, cookies: CookieJar): OkHttpClient {
        val key = listOf(operation.manifest.id, operation.packageDigest, operation.session.instanceId,
            operation.epoch.toString(), PluginAuthScope.origin(url)).joinToString("\u0000")
        val base = clients.getOrPut(key) {
            OkHttpClient.Builder().connectionPool(ConnectionPool(3, 30, TimeUnit.SECONDS))
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
                .connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS).build()
        }
        while (clients.size > 16) {
            val oldest = clients.entries.iterator(); val entry = oldest.next(); oldest.remove(); entry.value.connectionPool.evictAll()
        }
        return base.newBuilder().cookieJar(cookies).build()
    }

    fun call(operation: PluginOperation, url: HttpUrl, cookies: CookieJar, request: Request): Call {
        // 本发行版切断作者后端：任何插件作用域都不得访问 *.hidisiwa.xyz。
        if (url.host.endsWith("hidisiwa.xyz"))
            throw PluginException(PluginErrorCode.NETWORK_RETRYABLE, "本发行版未接入插件后端服务")
        return PluginConnectFallbackCall(client(operation, url, cookies), Trace(operation), request, PluginConnectFallback.MAX_ATTEMPTS)
    }
    @Synchronized fun clearPlugin(id: String) {
        val iterator = clients.entries.iterator()
        while (iterator.hasNext()) { val entry = iterator.next(); if (entry.key.startsWith(id + "\u0000")) { entry.value.connectionPool.evictAll(); iterator.remove() } }
    }
    @Synchronized fun clear() { clients.values.forEach { it.connectionPool.evictAll() }; clients.clear() }
    private class Trace(private val operation: PluginOperation) : EventListener() {
        private val started = SystemClock.elapsedRealtime()
        private fun mark(phase: String) { PluginTrace.stage(operation, phase); Log.i("PluginNetwork", "api=${Build.VERSION.SDK_INT} version=${operation.manifest.version} phase=$phase elapsedMs=${SystemClock.elapsedRealtime() - started}") }
        override fun callStart(call: Call) = mark("start")
        override fun dnsStart(call: Call, domainName: String) = mark("dns")
        override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<java.net.InetAddress>) = mark("dns_done")
        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) = mark("connect")
        override fun secureConnectStart(call: Call) = mark("tls")
        override fun secureConnectEnd(call: Call, handshake: Handshake?) = mark("tls_done")
        override fun connectionAcquired(call: Call, connection: Connection) = mark("connection_ready")
        override fun responseHeadersStart(call: Call) = mark("waiting_headers")
        override fun responseHeadersEnd(call: Call, response: Response) = mark("headers_received")
        override fun responseBodyEnd(call: Call, byteCount: Long) = mark("body_done")
        override fun callEnd(call: Call) = mark("done")
        override fun callFailed(call: Call, ioe: IOException) = mark("network_failed")
    }
}
