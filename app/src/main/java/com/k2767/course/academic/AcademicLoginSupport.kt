package com.k2767.course.academic

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Image challenges continue an existing password attempt; they never start a new session. */
internal interface AcademicCaptchaLogin {
    suspend fun submitCaptcha(code: String): LoginResult
    suspend fun refreshCaptcha(): CaptchaChallenge?
    fun clearLoginState()
}

internal class AcademicLoginAttempt(
    val credentials: Credentials,
    var page: AcademicResponse,
    val sessionEpoch: Long,
    var scripts: String = page.text
)

internal data class AcademicLoginCaptcha(val fieldName: String, val imageUrl: String) {
    suspend fun load(transport: AcademicHttpTransport, referer: String, refresh: Boolean = false): CaptchaChallenge {
        val url = if (refresh) imageUrl.toHttpUrlOrNull()?.newBuilder()
            ?.setQueryParameter("_", System.nanoTime().toString())?.build()?.toString() ?: imageUrl else imageUrl
        return CaptchaChallenge(transport.getImage(url, referer), fieldName, imageUrl)
    }
}

internal object AcademicLoginHtml {
    fun form(page: AcademicResponse): Element? = Jsoup.parse(page.text, page.url).select("form")
        .firstOrNull { it.select("input[type=password]").isNotEmpty() }

    fun captcha(form: Element, vararg fieldNames: String): AcademicLoginCaptcha? {
        val field = form.select("input[name]").firstOrNull { input ->
            fieldNames.any { it.equals(input.attr("name"), true) } && !input.hasAttr("disabled")
        }
        val image = form.select("img[src]").firstOrNull {
            it.id().equals("icode", true) || it.id().equals("SafeCodeImg", true) ||
                it.attr("src").contains("checkcode", true) || it.attr("src").contains("verifycode", true)
        }
        if (field == null && image == null) return null
        val url = image?.absUrl("src").orEmpty()
        if (field == null || url.isBlank()) throw AcademicException(
            AcademicStatus.HUMAN_VERIFICATION_REQUIRED, "学校验证码形式暂不支持，请在教务网页完成验证"
        )
        return AcademicLoginCaptcha(field.attr("name"), url)
    }

    /** Login failures often include the complete form or a server-generated alert script. */
    fun failure(page: AcademicResponse): LoginResult? {
        val document = Jsoup.parse(page.text, page.url)
        val jsonMessage = runCatching { JSONObject(page.text) }.getOrNull()
            ?.let { AcademicJson.string(it, "message", "msg", "msgContent", "error") }.orEmpty()
        val alerts = Regex("""alert\s*\(\s*['"]([^'"\r\n]+)['"]\s*\)""")
            .findAll(page.text).map { it.groupValues[1] }.toList()
        val messages = listOf(jsonMessage, document.select("#showMsg, #error, #errorMessage, #Label1, #lblMessage, .alert-danger, .alert-error").text()) +
            alerts + document.body().text()
        val captchaFailure = Regex("""(?:验证码|校验码|附加码|captcha|verification\s*code).{0,24}(?:错误|不正确|有误|不对|失效|过期|incorrect|invalid|expired)|(?:invalid|incorrect|expired)\s+(?:captcha|verification\s*code)""", RegexOption.IGNORE_CASE)
        messages.firstOrNull { captchaFailure.containsMatchIn(it) }?.let {
            return LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = "验证码错误或已过期，请重新输入")
        }
        val credentialFailure = Regex("""密码错误|密码不正确|密码有误|用户名或密码|账号或密码|帐号或密码|账户或密码|用户名不存在|用户不存在|账号不存在|帐号不存在|学号不存在|(?:invalid|incorrect)\s+(?:username|password|credentials)|password\s+(?:is\s+)?(?:incorrect|invalid)""", RegexOption.IGNORE_CASE)
        messages.firstOrNull { credentialFailure.containsMatchIn(it) }?.let {
            return LoginResult(AcademicStatus.INVALID_CREDENTIALS, message = "用户名或密码不正确")
        }
        if (jsonMessage.contains("验证码")) return LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = "请输入验证码")
        return null
    }
}
