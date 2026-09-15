package com.tyust.course.academic

import com.tyust.course.model.SchoolConfig
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.jsoup.Jsoup
import org.json.JSONObject
import kotlinx.coroutines.CancellationException

internal class ZfAcademicAdapter(school: SchoolConfig, session: AcademicSession, transport: AcademicHttpTransport) :
    BaseAcademicAdapter(school, session, transport, AcademicSystem.ZF), AcademicCaptchaLogin {
    @Volatile private var casAttempt: CasLoginAttempt? = null

    private class CasLoginAttempt(
        val sso: ZhengfangCasSsoClient,
        val username: String,
        var password: String,
        var loginPage: ZhengfangCasProtocol.LoginPage,
        var publicKey: ZhengfangCasProtocol.PublicKey,
        val sessionEpoch: Long
    )

    override suspend fun login(credentials: Credentials): LoginResult = serial {
        clearLoginState()
        session.invalidate()
        session.username = credentials.username
        val ssoEntry = discoverSsoEntry()
        if (ssoEntry != null) loginViaCas(ssoEntry, credentials) else loginViaForm(credentials)
    }

    // ---- 正方 CAS 统一身份认证（SSO）登录 ----

    /** 探测 {教务根}/sso/zfiotlogin 是否重定向到正方定制 CAS。 */
    private suspend fun discoverSsoEntry(): ZhengfangCasSsoClient? {
        val base = (school.getFullBasePath().trimEnd('/') + "/").toHttpUrlOrNull() ?: return null
        return ZhengfangCasSsoClient.discover(base, requireHttps = school.protocol.equals("https", true))
    }

    private suspend fun loginViaCas(ssoEntry: ZhengfangCasSsoClient, credentials: Credentials): LoginResult {
        // CAS 域名记入白名单：登录成功后随学校配置持久化，同时修复 WebView 跳转被拦截的问题
        val casUrl = ssoEntry.casLoginUrl
        val defaultPort = if (casUrl.scheme == "https") 443 else 80
        val casHost = buildString {
            append(casUrl.host.lowercase())
            if (casUrl.port != defaultPort) append(':').append(casUrl.port)
        }
        if (casHost.isNotBlank() && !school.allowedAcademicHosts.contains(casHost)) school.allowedAcademicHosts.add(casHost)
        val loginPage = ssoEntry.fetchLoginPage()
        val publicKey = ssoEntry.fetchPublicKey()
        val attempt = CasLoginAttempt(ssoEntry, credentials.username, credentials.password, loginPage, publicKey, session.epoch)
        return if (ssoEntry.fetchKaptchaRequired()) {
            val image = ssoEntry.fetchCaptchaImage()
            casAttempt = attempt
            LoginResult(AcademicStatus.CAPTCHA_REQUIRED, captcha = CaptchaChallenge(image, "authcode", "cas"))
        } else {
            submitCasLogin(attempt, "")
        }
    }

    private suspend fun submitCasLogin(attempt: CasLoginAttempt, authcode: String, retryCount: Int = 0): LoginResult {
        val encrypted = try {
            ZhengfangCasProtocol.encryptPassword(attempt.password, attempt.publicKey)
        } catch (_: ZhengfangCasProtocol.ProtocolException) {
            return finishCasLogin(LoginResult(AcademicStatus.PAGE_CHANGED, message = "统一认证密码加密失败"))
        }
        return when (val outcome = attempt.sso.submitLoginForm(attempt.username, encrypted, attempt.loginPage, authcode)) {
            is ZhengfangCasSsoClient.SubmitOutcome.FlowExecutionError -> {
                // CAS 多节点偶发 flow 状态解码失败: 换新的 execution 重试，无需用户介入
                if (retryCount >= MAX_CAS_RETRIES) {
                    finishCasLogin(LoginResult(AcademicStatus.PAGE_CHANGED, message = "统一认证暂时不可用，请稍后重试"))
                } else {
                    attempt.loginPage = attempt.sso.fetchLoginPage()
                    submitCasLogin(attempt, authcode, retryCount + 1)
                }
            }
            is ZhengfangCasSsoClient.SubmitOutcome.Rejected -> handleCasRejection(attempt, outcome.html)
            is ZhengfangCasSsoClient.SubmitOutcome.Authenticated -> completeCasLogin(attempt)
        }
    }

    private suspend fun handleCasRejection(attempt: CasLoginAttempt, html: String): LoginResult {
        // 失败页通常带新 execution，刷新后供下次提交使用
        try {
            attempt.loginPage = attempt.sso.fetchLoginPage()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // 刷新失败时保留旧 execution，继续按失败页内容分类
        }
        val error = ZhengfangCasProtocol.errorMessage(html)
        return when {
            ZhengfangCasProtocol.indicatesInvalidCredentials(error) || ZhengfangCasProtocol.indicatesInvalidCredentials(html) ->
                finishCasLogin(LoginResult(AcademicStatus.INVALID_CREDENTIALS))
            ZhengfangCasProtocol.indicatesCaptchaProblem(error) || ZhengfangCasProtocol.indicatesCaptchaProblem(html) -> {
                // 按应用约定：验证码被拒时返回不带图片的 CAPTCHA_REQUIRED → 上层提示验证码错误，
                // UI 随后通过 refreshCaptcha 主动获取新图
                casAttempt = attempt
                LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = error.ifBlank { "验证码不正确" })
            }
            else -> finishCasLogin(LoginResult(
                AcademicStatus.VALIDATION_FAILED,
                message = error.ifBlank { "统一认证登录失败，请检查账号密码或稍后重试" }
            ))
        }
    }

    private suspend fun completeCasLogin(attempt: CasLoginAttempt): LoginResult {
        val ticketUrl = attempt.sso.fetchServiceTicket()
        val landing = attempt.sso.exchangeTicket(ticketUrl)
        if (AcademicHtml.isLoginPage(landing.body)) {
            return finishCasLogin(LoginResult(AcademicStatus.SESSION_EXPIRED, message = "教务系统登录会话建立失败，请重试"))
        }
        importTeachingCookies(attempt.sso)
        val identity = validateIdentity()
        return if (identity != null) {
            finishCasLogin(LoginResult(AcademicStatus.SUCCESS, identity.first, identity.second))
        } else {
            finishCasLogin(LoginResult(AcademicStatus.VALIDATION_FAILED, message = "统一认证登录成功，但未能读取学号，请重试或使用教务网页登录"))
        }
    }

    private fun importTeachingCookies(sso: ZhengfangCasSsoClient) {
        val base = (school.getFullBasePath().trimEnd('/') + "/").toHttpUrlOrNull() ?: return
        session.cookies.saveFromResponse(base, sso.teachingCookies())
    }

    private fun finishCasLogin(result: LoginResult): LoginResult = result.also {
        if (it.status != AcademicStatus.CAPTCHA_REQUIRED) clearLoginState()
    }

    private fun currentCasAttempt(): CasLoginAttempt? = casAttempt?.takeIf { it.sessionEpoch == session.epoch }
        .also { if (it == null) clearLoginState() }

    override suspend fun submitCaptcha(code: String): LoginResult = serial {
        val attempt = currentCasAttempt() ?: return@serial LoginResult(AcademicStatus.SESSION_EXPIRED, message = "登录会话已失效，请重新登录")
        if (code.isBlank()) return@serial LoginResult(AcademicStatus.CAPTCHA_REQUIRED, message = "请输入验证码")
        submitCasLogin(attempt, code.trim())
    }

    override suspend fun refreshCaptcha(): CaptchaChallenge? = serial {
        val attempt = currentCasAttempt() ?: return@serial null
        try {
            CaptchaChallenge(attempt.sso.fetchCaptchaImage(), "authcode", "cas")
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    override fun clearLoginState() {
        casAttempt?.sso?.clear()
        casAttempt = null
    }

    // ---- 直登表单登录（无 CAS 入口的学校） ----

    private suspend fun loginViaForm(credentials: Credentials): LoginResult = serial {
        session.username = credentials.username
        val page = transport.get(transport.appUrl("xtgl/login_slogin.html?time=${System.currentTimeMillis()}"))
        val document = Jsoup.parse(page.text, page.url)
        val hidden = AcademicHtml.hiddenFields(document).toMutableMap()
        if (hidden["csrftoken"].isNullOrBlank()) return@serial LoginResult(AcademicStatus.PAGE_CHANGED, message = "Missing csrftoken")
        val captchaImage = document.select("img").firstOrNull { it.attr("src").contains("yzm", true) || it.attr("src").contains("captcha", true) }
        if (captchaImage != null) return@serial LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "Complete the school's captcha in WebView")
        val password = if (hidden["mmsfjm"] == "1") {
            val key = transport.get(transport.appUrl("xtgl/login_getPublicKey.html?time=${System.currentTimeMillis()}&_=${System.currentTimeMillis()}"))
            val json = JSONObject(key.text)
            AcademicCrypto.rsaBase64(json.optString("modulus"), json.optString("exponent"), credentials.password)
        } else credentials.password
        hidden["yhm"] = credentials.username
        hidden["mm"] = password
        hidden["language"] = hidden["language"].orEmpty().ifBlank { "zh_CN" }
        val result = transport.postForm(transport.appUrl("xtgl/login_slogin.html"), hidden.toList(), page.url)
        transport.get(transport.appUrl("xtgl/index_initMenu.html"))
        val identity = validateIdentity()
        when {
            identity != null -> LoginResult(AcademicStatus.SUCCESS, identity.first, identity.second)
            result.text.contains("密码错误") || result.text.contains("用户名或密码") -> LoginResult(AcademicStatus.INVALID_CREDENTIALS, message = "Invalid credentials")
            else -> LoginResult(AcademicStatus.VALIDATION_FAILED, message = "Login response could not be verified")
        }
    }

    private suspend fun validateIdentity(): Pair<String, String>? {
        val response = transport.get(transport.appUrl("xtgl/index_cxYhxxIndex.html?gnmkdm=index"))
        var identity = parseName(Jsoup.parse(response.text, response.url))
        if (identity.second.isBlank()) identity = zfMenuIdentityFallback(identity)
        return identity.takeIf { it.first.isNotBlank() || it.second.isNotBlank() }
    }

    override suspend fun loadCourseContext(): CourseContext = serial {
        val indexResponse = transport.get(transport.appUrl("xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=${school.courseGnmkdm}&layout=default"))
        val document = Jsoup.parse(indexResponse.text, indexResponse.url)
        if (AcademicHtml.isLoginPage(indexResponse.text)) throw AcademicException(AcademicStatus.SESSION_EXPIRED, "登录已失效，请重新登录")
        val indexFields = AcademicHtml.hiddenFields(document)
        val categories = Regex("queryCourse\\s*\\(\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]+)['\"]\\s*,\\s*['\"]([^'\"]*)['\"]\\s*,\\s*['\"]([^'\"]*)['\"]")
            .findAll(indexResponse.text).map { it.groupValues }.toList()
        // 正方 v5 变体（如河北传媒学院）入口页没有 queryCourse 调用：
        // 首个选课分类放在 first* 隐藏域，由页面脚本拷贝到 kklxdm/xkkz_id 等字段后发请求。
        // 不读它们会让分类参数全空，列表接口返回无法识别的内容。
        val categoryRows = if (categories.isNotEmpty()) categories else {
            val firstKklxdm = indexFields["firstKklxdm"].orEmpty()
            val firstXkkzId = indexFields["firstXkkzId"].orEmpty()
            if (firstKklxdm.isNotBlank() && firstXkkzId.isNotBlank()) listOf(listOf("",
                firstKklxdm, firstXkkzId,
                indexFields["firstNjdmId"].orEmpty().ifBlank { indexFields["njdm_id"].orEmpty() },
                indexFields["firstZyhId"].orEmpty().ifBlank { indexFields["zyh_id"].orEmpty() })) else emptyList()
        }
        val contexts = if (categoryRows.isEmpty()) listOf(indexFields) else categoryRows.map { m ->
            val fields = indexFields.toMutableMap()
            fields["kklxdm"] = m[1]; fields["xkkz_id"] = m[2]
            fields["njdm_id"] = m[3]; fields["zyh_id"] = m[4]
            val display = transport.postForm(transport.appUrl("xsxk/zzxkyzb_cxZzxkYzbDisplay.html?gnmkdm=${school.courseGnmkdm}"),
                listOf("xkkz_id" to m[2], "kklxdm" to m[1], "xszxzt" to "1", "njdm_id" to m[3], "zyh_id" to m[4], "kspage" to "0", "jspage" to "0"), indexResponse.url)
            fields.putAll(AcademicHtml.hiddenFields(Jsoup.parse(display.text, display.url)))
            // 首分类来自 first* 变体时，入口页的 kklxmc/xkkz_xh 是空值且 Display 响应不含它们；
            // 放在合并之后补齐，轮次序号 xkkz_xh 是列表请求的必带参数。
            indexFields["firstXkkzXh"]?.takeIf(String::isNotBlank)?.let { fields["xkkz_xh"] = it }
            indexFields["firstKklxmc"]?.takeIf(String::isNotBlank)?.let { fields["kklxmc"] = it }
            fields
        }
        val scopes = contexts.mapIndexed { index, fields ->
            val category = fields["kklxdm"].orEmpty().ifBlank { "default" }
            CourseScope("zf-$index-$category", fields["kklxmc"].orEmpty().ifBlank { category }, fields["xkxnm"].orEmpty(),
                transport.appUrl("xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html?gnmkdm=${school.courseGnmkdm}"),
                transport.appUrl("xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html?gnmkdm=${school.courseGnmkdm}"), fields)
        }
        CourseContext(session.epoch, scopes.ifEmpty {
            listOf(CourseScope("zf-default", "选课", params = indexFields))
        })
    }

    override suspend fun listCourses(context: CourseContext, query: CourseQuery): List<CourseOffer> = serial {
        check(context)
        val result = mutableListOf<CourseOffer>()
        for (scope in context.scopes.filter { query.scopeId.isBlank() || it.id == query.scopeId }) {
            val params = zzxkRequestParams(scope.params, query.keyword,
                kspage = (query.start + 1).toString(), jspage = (query.start + query.pageSize).toString())
            val response = transport.postForm(scope.listUrl.ifBlank { transport.appUrl("xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html?gnmkdm=${school.courseGnmkdm}") }, params.toList())
            AcademicJson.objects(response.text, "tmpList", "courses", "items").forEach {
                val course = offer(it, scope.id, arrayOf("kch_id", "kch"))
                result += course.copy(raw = scope.params + course.raw.filterValues(String::isNotBlank))
            }
        }
        result.filter { query.teacher.isBlank() || it.teacher.contains(query.teacher, true) }
    }

    /**
     * 对齐正方 zzxkYzb.js loadCoursesByPaged 的 requestMap：只提交教务期望的约 40 个控制字段。
     * 整页隐藏域全量提交会被部分学校（如河北传媒学院）判为异常请求并返回通用错误页，
     * 这是"教务返回了无法识别的列表"的一个来源。
     */
    private fun zzxkRequestParams(source: Map<String, String>, keyword: String, kspage: String, jspage: String,
                                  extra: Map<String, String> = emptyMap()): LinkedHashMap<String, String> {
        val params = linkedMapOf<String, String>()
        for (key in ZZXK_REQUEST_FIELDS) source[key]?.let { params[key] = it }
        // 浏览器脚本用 jg_id_1 的值填 jg_id
        source["jg_id_1"]?.let { params["jg_id"] = it }
        if (source["jxbzbkg"] == "1") params["jxbzb"] = source["jxbzb"].orEmpty()
        if (source["jxbzhkg"] == "1") params["zh"] = source["zh"].orEmpty()
        if (keyword.isNotBlank()) params["filter_list[0]"] = keyword
        params["kspage"] = kspage; params["jspage"] = jspage
        params.putAll(extra)
        return params
    }

    override suspend fun listSections(course: CourseOffer): List<CourseSection> = serial {
        val params = zzxkRequestParams(course.raw, keyword = "", kspage = "1", jspage = "200",
            extra = mapOf("kch_id" to course.stableId))
        val response = transport.postForm(transport.appUrl("xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html?gnmkdm=${school.courseGnmkdm}"), params.toList())
        AcademicJson.objects(response.text, "tmpList", "data", "courses", "jxbList").map { section(it, course) }
    }

    override suspend fun select(target: SelectionTarget): SelectionResult = serial {
        if (!target.confirmed) return@serial SelectionResult(AcademicStatus.VALIDATION_FAILED, "User confirmation is required")
        if (target.course.raw["sessionEpoch"] != session.epoch.toString()) return@serial SelectionResult(AcademicStatus.SESSION_EXPIRED)
        val values = (target.course.raw + target.section.raw).toMutableMap()
        values["jxb_ids"] = target.section.stableId
        values["kch_id"] = target.course.stableId
        values["kcmc"] = target.course.name
        values["kklxdm"] = target.course.raw["kklxdm"].orEmpty()
        values["xkkz_id"] = target.course.raw["xkkz_id"].orEmpty()
        val response = transport.postForm(transport.appUrl("xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html?gnmkdm=${school.courseGnmkdm}"), values.toList(), write = true)
        val status = AcademicJson.zfStatus(response.text, response.code)
        if (status == AcademicStatus.SUCCESS) SelectionResult(status, "Selection request accepted", SelectedCourse(target.section.stableId, target.course.name, target.course.teacher, target.course.stableId, target.section.stableId))
        else SelectionResult(status, response.text.take(160))
    }

    override suspend fun selected(context: CourseContext): List<SelectedCourse> = serial {
        check(context)
        val params = zzxkRequestParams(context.scopes.firstOrNull()?.params.orEmpty(), keyword = "", kspage = "1", jspage = "1000")
        val response = transport.postForm(transport.appUrl("xsxk/zzxkyzb_cxZzxkYzbChoosedDisplay.html?gnmkdm=${school.courseGnmkdm}"), params.toList())
        AcademicJson.objects(response.text, "tmpList", "courses", "items", "data").map {
            val course = selected(it)
            course.copy(raw = params + course.raw + ("sessionEpoch" to session.epoch.toString()))
        }
    }

    override suspend fun drop(target: SelectionTarget): OperationResult = serial {
        if (!target.confirmed) return@serial OperationResult(AcademicStatus.VALIDATION_FAILED, "User confirmation is required")
        if (target.course.raw["sessionEpoch"] != session.epoch.toString()) return@serial OperationResult(AcademicStatus.SESSION_EXPIRED)
        val values = listOf("kch_id" to target.course.stableId, "jxb_ids" to target.section.stableId,
            "xkxnm" to target.course.raw["xkxnm"].orEmpty(), "xkxqm" to target.course.raw["xkxqm"].orEmpty(), "txbsfrl" to "0")
        val response = transport.postForm(transport.appUrl("xsxk/zzxkyzb_tuikBcZzxkYzb.html?gnmkdm=${school.courseGnmkdm}"), values, write = true)
        OperationResult(AcademicJson.zfStatus(response.text, response.code), AcademicJson.message(response.text))
    }

    private companion object {
        const val MAX_CAS_RETRIES = 2

        /** 正方 zzxkYzb.js requestMap 的字段清单（浏览器实际提交的控制字段范围）。 */
        val ZZXK_REQUEST_FIELDS = listOf(
            "rwlx", "xklc", "xkly", "bklx_id", "sfkkjyxdxnxq", "kzkcgs",
            "xqh_id", "njdm_id_1", "zyh_id_1", "gnjkxdnj", "zyh_id", "zyfx_id", "njdm_id", "bh_id",
            "bjgkczxbbjwcx", "xbm", "xslbdm", "mzm", "xz", "ccdm", "xsbj", "sfkknj", "sfkkzy", "kzybkxy",
            "sfznkx", "zdkxms", "sfkxq", "bhbcyxkjxb", "sfkcfx", "kkbk", "kkbkdj", "bklbkcj", "sfkgbcx",
            "sfrxtgkcxd", "xkkz_xh", "tykczgxdcs", "xkxnm", "xkxqm", "kklxdm", "bbhzxjxb", "zxgbxkkg",
            "xkkz_id", "rlkz", "xkzgbj")
    }
}
