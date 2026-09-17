package com.k2767.course.academic

import com.k2767.course.login.MatchingMemoryCookieJar
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Cookie
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 正方定制 CAS 统一身份认证的 SSO 登录客户端（suspend 版）。
 *
 * 覆盖 CAS 与教务两个域名，使用独立的内存 CookieJar，与 [AcademicHttpTransport]
 * 的学校域名白名单互不影响；登录完成后由调用方把教务域 Cookie 迁入 AcademicSession。
 *
 * 链路: 登录页(execution) → v2/getPubKey → v2/getKaptchaStatus → POST 账密
 * → iPlanetDirectoryPro 会话 → service 票据 → 教务验票落地。
 */
internal class ZhengfangCasSsoClient private constructor(
    val casLoginUrl: HttpUrl,
    val teachingServiceUrl: HttpUrl,
    private val requireHttps: Boolean
) {
    private val cookieJar = MatchingMemoryCookieJar()
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .followSslRedirects(false)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    sealed interface SubmitOutcome {
        /** CAS 会话已建立（iPlanetDirectoryPro 已种下）。 */
        data class Authenticated(val location: String) : SubmitOutcome

        /** 登录被拒绝，携带失败页 HTML 供错误分类。 */
        data class Rejected(val html: String) : SubmitOutcome

        /** CAS 多节点偶发 flow 状态解码失败，需换新 execution 重试。 */
        data class FlowExecutionError(val location: String) : SubmitOutcome
    }

    data class ExchangeResult(val finalUrl: HttpUrl, val body: String)

    suspend fun fetchLoginPage(): ZhengfangCasProtocol.LoginPage {
        val response = get(casLoginUrl)
        response.use {
            if (!it.isSuccessful) throw casError("统一认证服务返回错误 (${it.code})")
            val html = it.body?.string().orEmpty()
            return try {
                ZhengfangCasProtocol.parseLoginPage(html)
            } catch (_: ZhengfangCasProtocol.ProtocolException) {
                throw casError("无法读取统一认证登录参数", AcademicStatus.PAGE_CHANGED)
            }
        }
    }

    suspend fun fetchPublicKey(): ZhengfangCasProtocol.PublicKey {
        val url = casLoginUrl.resolve(PUB_KEY_PATH) ?: throw casError("统一认证公钥地址无效", AcademicStatus.PAGE_CHANGED)
        val response = get(url, referer = casLoginUrl)
        response.use {
            if (!it.isSuccessful) throw casError("统一认证公钥获取失败 (${it.code})")
            return try {
                ZhengfangCasProtocol.parsePublicKey(it.body?.string().orEmpty())
            } catch (_: ZhengfangCasProtocol.ProtocolException) {
                throw casError("统一认证公钥格式无效", AcademicStatus.PAGE_CHANGED)
            }
        }
    }

    suspend fun fetchKaptchaRequired(): Boolean {
        val url = casLoginUrl.resolve(KAPTCHA_STATUS_PATH) ?: throw casError("统一认证验证码状态地址无效", AcademicStatus.PAGE_CHANGED)
        val response = get(url, referer = casLoginUrl)
        response.use {
            if (!it.isSuccessful) throw casError("无法获取验证码开关状态 (${it.code})")
            return ZhengfangCasProtocol.parseKaptchaStatus(it.body?.string().orEmpty())
        }
    }

    fun captchaImageUrl(): HttpUrl =
        casLoginUrl.resolve("$KAPTCHA_IMAGE_PATH?time=${System.currentTimeMillis()}")
            ?: throw casError("统一认证验证码地址无效", AcademicStatus.PAGE_CHANGED)

    suspend fun fetchCaptchaImage(url: HttpUrl = captchaImageUrl()): ByteArray {
        val response = get(url, referer = casLoginUrl)
        response.use {
            if (!it.isSuccessful) throw casError("获取统一认证验证码失败 (${it.code})")
            return it.body?.bytes() ?: throw casError("统一认证验证码内容为空", AcademicStatus.PAGE_CHANGED)
        }
    }

    suspend fun submitLoginForm(
        username: String,
        encryptedPassword: String,
        page: ZhengfangCasProtocol.LoginPage,
        authcode: String
    ): SubmitOutcome {
        val postUrl = if (page.formAction.isNullOrBlank()) {
            casLoginUrl
        } else {
            casLoginUrl.resolve(page.formAction) ?: casLoginUrl
        }
        val body = FormBody.Builder()
            .add("username", username)
            .add("password", encryptedPassword)
            .add("execution", page.execution)
            .add("_eventId", "submit")
            .apply { if (authcode.isNotEmpty()) add("authcode", authcode) }
            .build()
        val request = Request.Builder().url(postUrl)
            .header("Referer", casLoginUrl.toString())
            .header("Origin", originOf(casLoginUrl))
            .post(body)
            .build()
        val response = execute(request)
        response.use {
            if (it.code in REDIRECT_CODES) {
                val location = it.header("Location").orEmpty()
                if (location.contains(FLOW_ERROR_MARKER)) return SubmitOutcome.FlowExecutionError(location)
                if (hasCasSession()) return SubmitOutcome.Authenticated(location)
                // 未取得 SSO 会话的 302: 跟随一次读取失败页
                val next = location.takeIf(String::isNotBlank)?.let(it.request.url::resolve)
                if (next != null && next.host == casLoginUrl.host) {
                    val follow = execute(Request.Builder().url(next).header("User-Agent", USER_AGENT).get().build())
                    follow.use { f -> return SubmitOutcome.Rejected(f.body?.string().orEmpty()) }
                }
                return SubmitOutcome.Rejected("")
            }
            return SubmitOutcome.Rejected(it.body?.string().orEmpty())
        }
    }

    /** CAS 会话已建立后，换取指向教务系统的服务票据跳转地址。 */
    suspend fun fetchServiceTicket(): HttpUrl {
        val url = casLoginUrl.newBuilder()
            .setQueryParameter("service", teachingServiceUrl.toString())
            .build()
        val response = get(url, referer = casLoginUrl)
        response.use {
            if (it.code !in REDIRECT_CODES) throw casError("统一认证未返回服务票据", AcademicStatus.PAGE_CHANGED)
            val location = it.header("Location").orEmpty()
            val target = url.resolve(location) ?: throw casError("统一认证票据跳转无效", AcademicStatus.PAGE_CHANGED)
            if (target.host != teachingServiceUrl.host || target.port != teachingServiceUrl.port)
                throw casError("统一认证票据跳转到了意外的域名", AcademicStatus.PAGE_CHANGED)
            if (target.queryParameter("ticket").isNullOrBlank()) throw casError("统一认证未返回有效票据", AcademicStatus.PAGE_CHANGED)
            return target
        }
    }

    /** 用票据在教务域换取登录会话，手动跟随教务域内的重定向直至落地页。 */
    suspend fun exchangeTicket(ticketUrl: HttpUrl): ExchangeResult {
        var url = ticketUrl
        var redirects = 0
        while (true) {
            val response = get(url, referer = casLoginUrl)
            if (response.code in REDIRECT_CODES) {
                val location = response.header("Location").orEmpty()
                val next = url.resolve(location) ?: throw casError("教务登录返回了无效跳转", AcademicStatus.PAGE_CHANGED)
                // 正方系统的票据校验链会先经 http://<教务host>/ticketlogin 再 302 回 https（实测河北传媒学院）。
                // 与浏览器行为一致：允许教务同 host 的跨协议/跨端口中间跳，由落地页决定登录成败。
                if (next.host != teachingServiceUrl.host) throw casError("教务登录跳转到了意外的域名", AcademicStatus.PAGE_CHANGED)
                response.close()
                if (++redirects > MAX_REDIRECTS) throw casError("教务登录跳转次数过多", AcademicStatus.PAGE_CHANGED)
                url = next
                continue
            }
            response.use {
                if (!it.isSuccessful) throw casError("教务系统返回错误 (${it.code})")
                return ExchangeResult(it.request.url, it.body?.string().orEmpty())
            }
        }
    }

    /** 教务域种下的全部 Cookie，供迁入 AcademicSession。 */
    fun teachingCookies(): List<Cookie> = cookieJar.cookiesSetBy(teachingServiceUrl.host, teachingServiceUrl.port)

    fun hasCasSession(): Boolean =
        cookieJar.cookiesSetBy(casLoginUrl.host, casLoginUrl.port).any { it.name == SSO_SESSION_COOKIE }

    fun clear() = cookieJar.clear()

    // ---- 内部基础设施 ----

    private suspend fun get(url: HttpUrl, referer: HttpUrl? = null): Response {
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT).get()
        if (referer != null) builder.header("Referer", referer.toString())
        return execute(builder.build())
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private fun casError(message: String, status: AcademicStatus = AcademicStatus.NETWORK_RETRYABLE): AcademicException =
        AcademicException(status, message)

    private fun originOf(url: HttpUrl): String {
        val defaultPort = if (url.scheme == "https") 443 else 80
        val port = if (url.port == defaultPort) "" else ":${url.port}"
        return "${url.scheme}://${url.host}$port"
    }

    companion object {
        private const val PUB_KEY_PATH = "v2/getPubKey"
        private const val KAPTCHA_STATUS_PATH = "v2/getKaptchaStatus"
        private const val KAPTCHA_IMAGE_PATH = "kaptcha"
        private const val SSO_SESSION_COOKIE = "iPlanetDirectoryPro"
        private const val FLOW_ERROR_MARKER = "Error+decoding+flow+execution"
        private const val MAX_REDIRECTS = 8
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 12) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        private val probeClient = OkHttpClient.Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        /**
         * 探测教务系统的正方 CAS SSO 入口：GET {教务根}/sso/zfiotlogin（不跟随重定向）。
         * 若 302 跳到异域 /cas/login 且 service 指回教务域，返回可用的 SSO 客户端。
         */
        suspend fun discover(teachingBase: HttpUrl, requireHttps: Boolean): ZhengfangCasSsoClient? {
            val probeUrl = teachingBase.newBuilder()
                .encodedPath(teachingBase.encodedPath.trimEnd('/') + SSO_ENTRY_PATH)
                .query(null)
                .fragment(null)
                .build()
            val response = try {
                executeProbe(probeUrl)
            } catch (_: Exception) {
                return null
            }
            response.use {
                if (it.code !in REDIRECT_CODES) return null
                val location = it.header("Location").orEmpty()
                return entryFromRedirect(location, probeUrl, requireHttps)
            }
        }

        /** 校验 SSO 入口重定向并构造客户端；不满足正方 CAS 特征时返回 null。 */
        internal fun entryFromRedirect(
            location: String,
            serviceUrl: HttpUrl,
            requireHttps: Boolean
        ): ZhengfangCasSsoClient? {
            val target = serviceUrl.resolve(location) ?: return null
            if (target.scheme != "https" && target.scheme != "http") return null
            if (requireHttps && !target.isHttps) return null
            if (target.host == serviceUrl.host && target.port == serviceUrl.port) return null
            if (!target.encodedPath.endsWith(CAS_LOGIN_PATH)) return null
            val service = target.queryParameter("service")?.let { serviceUrl.resolve(it) } ?: return null
            if (service.host != serviceUrl.host || service.port != serviceUrl.port) return null
            val casLogin = target.newBuilder().query(null).fragment(null).build()
            return ZhengfangCasSsoClient(casLogin, serviceUrl, requireHttps)
        }

        private suspend fun executeProbe(url: HttpUrl): Response =
            suspendCancellableCoroutine { continuation ->
                val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).get().build()
                val call = probeClient.newCall(request)
                continuation.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) continuation.resume(response) else response.close()
                    }
                })
            }

        private const val SSO_ENTRY_PATH = "/sso/zfiotlogin"
        private const val CAS_LOGIN_PATH = "/cas/login"
    }
}
