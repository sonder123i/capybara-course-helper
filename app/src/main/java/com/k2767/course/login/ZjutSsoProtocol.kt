package com.k2767.course.login

import org.jsoup.Jsoup

/**
 * 浙江工业大学统一身份认证平台(oauth.zjut.edu.cn, 正方定制 CAS)协议解析与密码加密。
 *
 * 登录页表单 fm1 提交字段: username / password(加密后) / execution / _eventId=submit
 * 验证码开关由 v2/getKaptchaStatus 下发, 图片地址为登录页同目录下的 kaptcha?time=<ts>。
 */
internal object ZjutSsoProtocol {
    class ProtocolException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    data class LoginPage(
        val execution: String,
        val formAction: String?
    )

    data class PublicKey(
        val modulusHex: String,
        val exponentHex: String
    )

    fun parseLoginPage(html: String): LoginPage {
        val document = Jsoup.parse(html)
        val execution = document.select("form input[name=execution]")
            .firstOrNull()
            ?.attr("value")
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: throw ProtocolException("ZJUT login page is missing execution")
        val formAction = document.select("form#fm1")
            .firstOrNull()
            ?.attr("action")
            ?.trim()
            ?.takeIf(String::isNotBlank)
        return LoginPage(execution = execution, formAction = formAction)
    }

    fun parsePublicKey(json: String): PublicKey {
        return try {
            val obj = org.json.JSONObject(json)
            PublicKey(
                modulusHex = obj.getString("modulus").trim(),
                exponentHex = obj.getString("exponent").trim()
            )
        } catch (error: Exception) {
            throw ProtocolException("ZJUT public key response is invalid", error)
        }
    }

    fun parseKaptchaStatus(body: String): Boolean = body.trim().equals("true", ignoreCase = true)

    /**
     * 按登录页 security.js (David Shapiro RSA) 的规格加密密码。
     * 算法与 [ZhengfangRsaCrypto] 共享同一实现，避免多份正方 CAS 适配之间漂移。
     */
    fun encryptPassword(password: String, modulusHex: String, exponentHex: String): String =
        try {
            ZhengfangRsaCrypto.encryptPassword(password, modulusHex, exponentHex)
        } catch (_: ZhengfangRsaCrypto.CryptoException) {
            throw ProtocolException("ZJUT password encryption failed")
        }
}
