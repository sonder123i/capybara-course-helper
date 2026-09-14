package com.tyust.course.academic

import com.tyust.course.login.ZhengfangRsaCrypto
import org.json.JSONObject
import org.jsoup.Jsoup

/**
 * 正方定制 CAS 统一身份认证协议解析（与浙江工业大学 oauth.zjut.edu.cn、
 * 河北传媒学院 cas.hebic.cn 等同族产品）。
 *
 * 登录页表单 fm1 提交字段: username / password(加密后) / execution / _eventId=submit，
 * 验证码字段 authcode 由 v2/getKaptchaStatus 开关控制，图片为登录页同目录 kaptcha?time=<ts>。
 * 实测链路(2026-09, 河北传媒学院):
 * 1. GET  /cas/login                     → execution 令牌 + JSESSIONID
 * 2. GET  /cas/v2/getPubKey              → RSA 公钥(modulus 每次变化, 动态计算块长)
 * 3. GET  /cas/v2/getKaptchaStatus       → true 时需验证码
 * 4. POST /cas/login                     → 302 表示成功, 响应种下 iPlanetDirectoryPro SSO 会话
 * 5. GET  /cas/login?service=<教务地址>   → ST 票据 → 教务侧验票建立会话
 */
internal object ZhengfangCasProtocol {
    class ProtocolException(message: String, cause: Throwable? = null) : Exception(message, cause)

    data class LoginPage(val execution: String, val formAction: String?)

    data class PublicKey(val modulusHex: String, val exponentHex: String)

    fun parseLoginPage(html: String): LoginPage {
        val document = Jsoup.parse(html)
        val execution = document.select("form input[name=execution]")
            .firstOrNull()
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw ProtocolException("统一认证登录页缺少 execution 参数")
        val formAction = document.select("form#fm1")
            .firstOrNull()
            ?.attr("action")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return LoginPage(execution = execution, formAction = formAction)
    }

    fun parsePublicKey(json: String): PublicKey {
        return try {
            val obj = JSONObject(json)
            PublicKey(
                modulusHex = obj.getString("modulus").trim(),
                exponentHex = obj.getString("exponent").trim()
            )
        } catch (error: Exception) {
            throw ProtocolException("统一认证公钥响应无效", error)
        }
    }

    fun parseKaptchaStatus(body: String): Boolean = body.trim().equals("true", ignoreCase = true)

    fun encryptPassword(password: String, key: PublicKey): String =
        try {
            ZhengfangRsaCrypto.encryptPassword(password, key.modulusHex, key.exponentHex)
        } catch (_: ZhengfangRsaCrypto.CryptoException) {
            throw ProtocolException("统一认证密码加密失败")
        }

    fun errorMessage(html: String): String =
        Jsoup.parse(html).getElementById("errormsg")?.text().orEmpty()

    fun indicatesInvalidCredentials(text: String): Boolean =
        text.contains("用户名或密码不正确") ||
            text.contains("用户名或密码错误") ||
            text.contains("账号或密码不正确") ||
            text.contains("密码错误")

    fun indicatesCaptchaProblem(text: String): Boolean =
        text.contains("验证码") ||
            text.contains("invalid captcha", ignoreCase = true) ||
            text.contains("captcha error", ignoreCase = true) ||
            text.contains("captcha required", ignoreCase = true)
}
