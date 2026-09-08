package com.tyust.course.academic

import com.tyust.course.model.SchoolConfig
import org.jsoup.Jsoup
import org.json.JSONObject
import java.net.URI

internal class QzOldAcademicAdapter(school: SchoolConfig, session: AcademicSession, transport: AcademicHttpTransport) :
    QzAcademicAdapter(school, session, transport, AcademicSystem.QZ_OLD), AcademicCaptchaLogin {
    @Volatile private var loginAttempt: AcademicLoginAttempt? = null

    override suspend fun login(credentials: Credentials): LoginResult = serial {
        clearLoginState()
        session.invalidate()
        session.username = credentials.username
        val root = transport.get(transport.appUrl(""))
        val page = if (AcademicLoginHtml.form(root) != null) root else transport.get(transport.appUrl("xk/LoginToXk"))
        val document = Jsoup.parse(page.text, page.url)
        val form = AcademicLoginHtml.form(page) ?: return@serial LoginResult(AcademicStatus.PAGE_CHANGED, message = "未找到学校登录表单")
        var scripts = page.text
        if (!scripts.contains("flag=sess") && !scripts.contains("encodeInp")) {
            for (script in AcademicHtml.scriptUrls(document, page.url).filter { it.contains("conwork", true) || it.contains("login", true) }.take(4))
                scripts += "\n" + transport.get(script, page.url).text
        }
        if (!scripts.contains("flag=sess", true) && !(scripts.contains("encodeInp") && scripts.contains("%%%")))
            return@serial LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校登录编码无法识别，请使用教务网页登录")
        val attempt = AcademicLoginAttempt(credentials, page, session.epoch, scripts)
        loginAttempt = attempt
        val captcha = AcademicLoginHtml.captcha(form, "RANDOMCODE")
        if (captcha != null) LoginResult(AcademicStatus.CAPTCHA_REQUIRED, captcha = captcha.load(transport, page.url))
        else submitLogin(attempt, "")
    }

    override suspend fun submitCaptcha(code: String): LoginResult = serial {
        val attempt = currentLoginAttempt() ?: return@serial LoginResult(AcademicStatus.SESSION_EXPIRED, message = "登录会话已失效，请重新登录")
        if (code.isBlank()) return@serial LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = "请输入验证码")
        submitLogin(attempt, code.trim())
    }

    override suspend fun refreshCaptcha(): CaptchaChallenge? = serial {
        val attempt = currentLoginAttempt() ?: return@serial null
        val form = AcademicLoginHtml.form(attempt.page) ?: return@serial null
        AcademicLoginHtml.captcha(form, "RANDOMCODE")?.load(transport, attempt.page.url, refresh = true)
    }

    override fun clearLoginState() { loginAttempt = null }

    private fun currentLoginAttempt(): AcademicLoginAttempt? = loginAttempt?.takeIf { it.sessionEpoch == session.epoch }
        .also { if (it == null) clearLoginState() }

    private suspend fun submitLogin(attempt: AcademicLoginAttempt, code: String): LoginResult {
        val page = attempt.page
        val form = AcademicLoginHtml.form(page) ?: return finishLogin(LoginResult(AcademicStatus.PAGE_CHANGED, message = "学校登录表单已变化"))
        val credentials = attempt.credentials
        val scripts = attempt.scripts
        val fields = AcademicHtml.formFields(form).toMap().toMutableMap()
        if (scripts.contains("flag=sess", true)) {
            val response = transport.postForm(transport.appUrl("xk/LoginToXk?flag=sess"), emptyList(), page.url)
            val token = runCatching { JSONObject(response.text).getString("data") }.getOrDefault(response.text.trim().trim('"'))
            val parts = token.split('#', limit = 2)
            if (parts.size != 2 || parts[1].isBlank()) return finishLogin(LoginResult(AcademicStatus.PAGE_CHANGED, message = "登录编码参数已变化"))
            fields["USERNAME"] = credentials.username
            fields["PASSWORD"] = if (Regex("""PASSWORD[^\r\n;]*\.value\s*=\s*["']{2}""").containsMatchIn(scripts)) "" else credentials.password
            fields["encoded"] = LoginEncoding.qzOldShift(credentials.username, credentials.password, parts[0], parts[1])
        } else if (scripts.contains("encodeInp") && scripts.contains("%%%")) {
            fields["userAccount"] = credentials.username
            fields["userPassword"] = ""
            fields["encoded"] = LoginEncoding.qzOldBase64(credentials.username, credentials.password)
        } else return finishLogin(LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校登录编码无法识别，请使用教务网页登录"))
        AcademicLoginHtml.captcha(form, "RANDOMCODE")?.let { fields[it.fieldName] = code }
        val result = transport.postForm(AcademicHtml.action(form, page.url), fields.toList(), page.url)
        AcademicLoginHtml.form(result)?.let { attempt.page = result }
        AcademicLoginHtml.failure(result)?.let { failure ->
            if (failure.status == AcademicStatus.CAPTCHA_REQUIRED) {
                val updatedForm = AcademicLoginHtml.form(attempt.page)
                val captcha = updatedForm?.let { AcademicLoginHtml.captcha(it, "RANDOMCODE") }
                    ?: return finishLogin(LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校要求在教务网页完成验证"))
                return if (code.isBlank()) failure.copy(captcha = captcha.load(transport, attempt.page.url)) else failure
            }
            return finishLogin(failure)
        }
        return finishLogin(validateSession())
    }

    private fun finishLogin(result: LoginResult): LoginResult = result.also {
        if (it.status != AcademicStatus.CAPTCHA_REQUIRED) clearLoginState()
    }

    override suspend fun validateSession(): LoginResult = serial {
        val page = transport.get(transport.appUrl("framework/xsMain.jsp"))
        if (AcademicHtml.isLoginPage(page.text) || page.code !in 200..299) return@serial LoginResult(AcademicStatus.SESSION_EXPIRED)
        val document = Jsoup.parse(page.text, page.url)
        var identity = parseName(document)
        val valid = identity.first.isNotBlank() || identity.second.isNotBlank() ||
            listOf("学生个人中心", "退出登录", "xsxk/xklc_list", "学生选课").any(page.text::contains)
        if (!valid) return@serial LoginResult(AcademicStatus.SESSION_EXPIRED, message = "无法验证学校登录状态")
        if (identity.first.isBlank()) {
            val profile = document.select("iframe[src]").firstOrNull {
                URI(it.absUrl("src")).path.endsWith("/framework/xsMain_new.jsp")
            }
            if (profile != null) {
                val details = transport.get(profile.absUrl("src"), page.url)
                requirePage(details)
                val found = parseName(Jsoup.parse(details.text, details.url))
                identity = found.first.ifBlank { identity.first } to found.second.ifBlank { identity.second }
            }
        }
        val studentId = identity.second.ifBlank { session.username }
        if (studentId.isNotBlank()) session.username = studentId
        LoginResult(AcademicStatus.SUCCESS, identity.first, studentId)
    }

    override suspend fun loadCourseContext(): CourseContext = serial {
        val page = transport.get(transport.appUrl("xsxk/xklc_list"), transport.appUrl("framework/xsMain.jsp"))
        requirePage(page)
        val document = Jsoup.parse(page.text, page.url)
        val links = document.select("a[href], [onclick]").mapNotNull { element ->
            val round = Regex("""comeInXkIndx\s*\(\s*['"]([^'"]+)""").find(element.attr("onclick") + element.attr("href"))?.groupValues?.get(1)
            val href = if (round != null) transport.appUrl("xsxk/xsxk_index?jx0502zbid=" + encode(round))
                else element.absUrl("href").takeIf { it.contains("/xsxk/") && !it.contains("Oper") && !it.contains("exit") }
            href?.let { Triple(it, round.orEmpty(), element.closest("tr")?.text() ?: element.text()) }
        }.distinctBy { it.first }
        if (links.isEmpty()) {
            val direct = discoverScopes(page, emptyMap(), "选课")
            if (direct.isNotEmpty()) return@serial CourseContext(session.epoch, direct)
            val roundTable = document.select("table").firstOrNull { table ->
                table.select("th").any { it.text().trim() == "选课名称" } &&
                    table.select("th").any { it.text().trim() == "选课时间" }
            }
            if (roundTable?.text()?.let { it.contains("未查询到数据") || it.contains("暂无数据") } == true ||
                AcademicJson.status(page.text) == AcademicStatus.ROUND_CLOSED)
                return@serial CourseContext(session.epoch, emptyList())
            throw AcademicException(AcademicStatus.PAGE_CHANGED, "未发现学校提供的选课轮次")
        }
        val scopes = links.flatMap { (url, id, name) ->
            discoverScopes(transport.get(url, page.url), mapOf("roundId" to id, "roundPage" to url), name)
        }
        CourseContext(session.epoch, scopes)
    }

    override suspend fun selected(context: CourseContext): List<SelectedCourse> = serial {
        check(context)
        selectedInRounds(context) ?: selectedFromMenu("framework/xsMain.jsp")
    }
}
