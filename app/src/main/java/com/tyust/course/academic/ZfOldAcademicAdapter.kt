package com.tyust.course.academic

import com.tyust.course.model.SchoolConfig
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URI

internal class ZfOldAcademicAdapter(school: SchoolConfig, session: AcademicSession, transport: AcademicHttpTransport) :
    BaseAcademicAdapter(school, session, transport, AcademicSystem.ZF_OLD), AcademicCaptchaLogin {
    private var selectedQueryUrl: String? = null
    @Volatile private var loginAttempt: AcademicLoginAttempt? = null
    private fun homeUrl() = transport.appUrl("xs_main.aspx?xh=" + java.net.URLEncoder.encode(session.username, "UTF-8"))

    override suspend fun login(credentials: Credentials): LoginResult = serial {
        clearLoginState()
        session.invalidate()
        session.username = credentials.username
        val page = transport.get(transport.appUrl("default2.aspx"))
        val form = AcademicLoginHtml.form(page) ?: return@serial LoginResult(AcademicStatus.PAGE_CHANGED, message = "未找到学校登录表单")
        if (form.select("#txtKeyModulus, input[name=txtKeyModulus]").attr("value").isBlank() ||
            form.select("#txtKeyExponent, input[name=txtKeyExponent]").attr("value").isBlank())
            return@serial LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校密码加密方式暂不支持，请使用教务网页登录")
        val attempt = AcademicLoginAttempt(credentials, page, session.epoch)
        loginAttempt = attempt
        val captcha = AcademicLoginHtml.captcha(form, "txtSecretCode", "TextBox3")
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
        val refreshControl = form.select("input[type=submit][name], button[name]").firstOrNull {
            (it.attr("value") + it.text()).let { text -> text.contains("刷新验证码") || text.contains("换一张") }
        }
        if (refreshControl != null) {
            // Some WebForms schools rotate both SafeKey and the form state in this postback.
            val fields = AcademicHtml.formFields(form, refreshControl.attr("name") to refreshControl.attr("value"))
                .map { (name, value) -> name to if (name in setOf("txtUserName", "TextBox2", "txtSecretCode", "TextBox3")) "" else value }
            val page = transport.postForm(AcademicHtml.action(form, attempt.page.url), fields, attempt.page.url)
            if (AcademicLoginHtml.form(page) == null) throw AcademicException(AcademicStatus.PAGE_CHANGED, "验证码刷新失败，请重新登录")
            attempt.page = page
        }
        val updated = AcademicLoginHtml.form(attempt.page) ?: return@serial null
        AcademicLoginHtml.captcha(updated, "txtSecretCode", "TextBox3")?.load(transport, attempt.page.url, refresh = refreshControl == null)
    }

    override fun clearLoginState() { loginAttempt = null }

    private fun currentLoginAttempt(): AcademicLoginAttempt? = loginAttempt?.takeIf { it.sessionEpoch == session.epoch }
        .also { if (it == null) clearLoginState() }

    private suspend fun submitLogin(attempt: AcademicLoginAttempt, code: String): LoginResult {
        val page = attempt.page
        val form = AcademicLoginHtml.form(page) ?: return finishLogin(LoginResult(AcademicStatus.PAGE_CHANGED, message = "学校登录表单已变化"))
        if (form.select("input[name=txtUserName], input[name=TextBox2]").size != 2)
            return finishLogin(LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校登录表单暂不支持，请使用教务网页登录"))
        val modulus = form.select("#txtKeyModulus, input[name=txtKeyModulus]").attr("value")
        val exponent = form.select("#txtKeyExponent, input[name=txtKeyExponent]").attr("value")
        if (modulus.isBlank() || exponent.isBlank())
            return finishLogin(LoginResult(AcademicStatus.HUMAN_VERIFICATION_REQUIRED, message = "学校密码加密方式暂不支持，请使用教务网页登录"))
        val password = AcademicCrypto.rsaHex(modulus, exponent, attempt.credentials.password)
        val submit = form.select("input[type=submit][name], button[name]").firstOrNull {
            (it.attr("value") + it.text()).replace(Regex("[\\s\u00a0]+"), "").contains("登录")
        }
        val fields = AcademicHtml.formFields(form, submit?.let { it.attr("name") to it.attr("value") }).toMap().toMutableMap()
        fields["txtUserName"] = attempt.credentials.username
        fields["TextBox2"] = password
        form.select("input[name=RadioButtonList1]").firstOrNull { it.attr("value").contains("学生") }
            ?.let { fields[it.attr("name")] = it.attr("value") }
        AcademicLoginHtml.captcha(form, "txtSecretCode", "TextBox3")?.let { fields[it.fieldName] = code }
        val response = transport.postForm(AcademicHtml.action(form, page.url), fields.toList(), page.url)
        AcademicLoginHtml.form(response)?.let { attempt.page = response }
        AcademicLoginHtml.failure(response)?.let { failure ->
            if (failure.status == AcademicStatus.CAPTCHA_REQUIRED) {
                val updatedForm = AcademicLoginHtml.form(attempt.page)
                val captcha = updatedForm?.let { AcademicLoginHtml.captcha(it, "txtSecretCode", "TextBox3") }
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

    override suspend fun loadCourseContext(): CourseContext = serial {
        val page = transport.get(homeUrl())
        requirePage(page)
        val document = Jsoup.parse(page.text, page.url)
        selectedQueryUrl = document.select("a[href]").firstOrNull {
            it.attr("href").substringBefore('?').endsWith("xsxkqk.aspx", true)
        }?.absUrl("href")
        val links = document.select("a[href]").filter { link ->
            listOf("xsxk.aspx", "xstyk.aspx", "xf_xsqxxxk.aspx").any { link.attr("href").contains(it, true) }
        }
        CourseContext(session.epoch, links.map { link ->
            val url = link.absUrl("href")
            CourseScope(URI(url).path, link.text().trim().ifBlank { "学生选课" }, listUrl = url)
        }.distinctBy { it.listUrl })
    }

    private fun requirePage(page: AcademicResponse) {
        if (AcademicHtml.isLoginPage(page.text)) throw AcademicException(AcademicStatus.SESSION_EXPIRED, "登录已失效")
        if (page.code !in 200..299) throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校页面暂不可用")
    }

    private suspend fun pages(url: String, keyword: String = ""): List<AcademicResponse> {
        var page = transport.get(url, homeUrl())
        requirePage(page)
        if (URI(page.url).path.endsWith("/xstyk.aspx", true)) return sportsPages(page)
        var document = Jsoup.parse(page.text, page.url)
        val form = document.firstForm()
        val search = form?.select("input[type=submit], button")?.firstOrNull { it.attr("value").trim() in setOf("确定", "查询", "搜索") }
        if (keyword.isNotBlank() && form?.select("input[name=TextBox1]")?.isNotEmpty() == true && search != null) {
            val fields = AcademicHtml.formFields(form, search.attr("name") to search.attr("value")).filterNot { it.first == "TextBox1" } + ("TextBox1" to keyword)
            page = transport.postForm(AcademicHtml.action(form, page.url), fields, page.url)
            requirePage(page)
        }
        val result = mutableListOf<AcademicResponse>()
        val states = mutableSetOf<String>()
        for (index in 0 until 50) {
            result += page
            document = Jsoup.parse(page.text, page.url)
            val currentForm = document.firstForm() ?: break
            val state = AcademicHtml.hiddenFields(document)["__VIEWSTATE"].orEmpty()
            if (state.isNotBlank() && !states.add(state)) break
            val next = currentForm.select("input[type=submit], button, a[href]").firstOrNull {
                !it.hasAttr("disabled") && (it.attr("value").trim() == "下一页" || it.text().trim() == "下一页")
            } ?: break
            page = postControl(page, next)
            requirePage(page)
            if (index == 49) throw AcademicException(AcademicStatus.PAGE_CHANGED, "课程页数过多，请缩小搜索范围")
        }
        return result
    }

    private suspend fun sportsPages(initial: AcademicResponse): List<AcademicResponse> {
        var page = initial
        var document = Jsoup.parse(page.text, page.url)
        val result = mutableListOf(page)
        if (ZfOldSports.categoryEvent(document) == null) return result
        val initialCategory = ZfOldSports.category(document)
        val categories = ZfOldSports.list(document, "ListBox1")?.select("option[value]")
            ?.filter { AcademicHtml.isEnabledControl(it) && it.attr("value").isNotBlank() }
            ?.map { it.attr("value") }?.distinct().orEmpty()
        if (categories.size > 100) throw AcademicException(AcademicStatus.PAGE_CHANGED, "体育课程分类过多，请使用教务网页查询")
        for (category in categories.filter { it != initialCategory }) {
            val list = ZfOldSports.list(document, "ListBox1")!!
            if (list.select("option[value]").none { it.attr("value") == category }) continue
            page = sportsCategoryPage(page, category)
            document = Jsoup.parse(page.text, page.url)
            result += page
        }
        return result
    }

    private suspend fun sportsCategoryPage(page: AcademicResponse, category: String): AcademicResponse {
        val document = Jsoup.parse(page.text, page.url)
        if (category.isBlank() || ZfOldSports.category(document) == category) return page
        val event = ZfOldSports.categoryEvent(document)
            ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校体育课程分类控件已变化")
        val list = ZfOldSports.list(document, "ListBox1")!!
        if (list.select("option[value]").none { it.attr("value") == category && AcademicHtml.isEnabledControl(it) })
            throw AcademicException(AcademicStatus.PAGE_CHANGED, "体育课程分类已不在学校列表中")
        val form = list.closest("form") ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "缺少体育课程查询表单")
        val fields = AcademicHtml.formFields(form).toMap().toMutableMap().apply {
            put(list.attr("name"), category)
            put("__EVENTTARGET", event.first); put("__EVENTARGUMENT", event.second)
        }
        val result = transport.postForm(AcademicHtml.action(form, page.url), fields.toList(), page.url)
        requirePage(result)
        if (ZfOldSports.category(Jsoup.parse(result.text, result.url)) != category)
            throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校未返回所选体育课程分类")
        return result
    }

    private suspend fun sportsTargetPage(url: String, category: String = ""): AcademicResponse {
        val page = transport.get(url, homeUrl())
        requirePage(page)
        return sportsCategoryPage(page, category)
    }

    private fun dropControl(row: Element): Element? = row.select("a[href], input, button").firstOrNull {
        AcademicHtml.isEnabledControl(it) &&
            (it.text() + it.attr("value")).let { text -> text.contains("退选") || text.contains("退课") || text.trim() == "删除" }
    }

    private fun rowId(row: AcademicTables.Row): String {
        val code = row.value("课程代码", "课程编号", "课程号").ifBlank { row.value("课程名称", "课程名") }
        val section = row.value("教学班号", "教学班", "选课课号")
        return code + ":" + section.ifBlank { (row.value("教师姓名", "上课教师", "教师") + "|" + row.value("上课时间", "时间")).hashCode().toString() }
    }

    private fun offer(row: AcademicTables.Row, pageUrl: String, scope: CourseScope): CourseOffer? {
        if (dropControl(row.element) != null) return null
        val control = row.element.select("input[type=checkbox], input[type=radio]").firstOrNull { !it.hasAttr("disabled") }
        val popup = Regex("""(?:openDiago|window\.open)\s*\(\s*['"]([^'"]+)""").find(row.element.html())?.groupValues?.get(1)
        if (control == null && popup == null) return null
        val code = row.value("课程代码", "课程编号", "课程号").ifBlank { row.value("课程名称", "课程名") }
        val capacity = row.value("容量", "课容量").trim().toIntOrNull()
        val remaining = row.value("余量", "剩余容量").trim().toIntOrNull()
        return CourseOffer(code, row.value("课程名称", "课程名"), row.value("教师姓名", "上课教师", "教师"),
            row.value("上课时间", "时间"), row.value("上课地点", "地点"), row.value("学分"),
            capacity = capacity, selected = if (capacity != null && remaining != null) (capacity - remaining).coerceAtLeast(0) else null, scopeId = scope.id,
            raw = mapOf("pageUrl" to scope.listUrl, "sectionId" to rowId(row), "popupUrl" to popup?.let { URI(pageUrl).resolve(it).toString() }.orEmpty(),
                "sessionEpoch" to session.epoch.toString()))
    }

    override suspend fun listCourses(context: CourseContext, query: CourseQuery): List<CourseOffer> = serial {
        check(context)
        context.scopes.filter { query.scopeId.isBlank() || query.scopeId == it.id }.flatMap { scope ->
            pages(scope.listUrl, query.keyword).flatMap { page ->
                val document = Jsoup.parse(page.text, page.url)
                val sports = if (ZfOldSports.submitControl(document, selected = false) == null) emptyList() else
                    ZfOldSports.entries(document, selected = false).map { entry ->
                        CourseOffer(entry.courseId, entry.name, entry.teacher, entry.time, entry.location, entry.credit,
                            entry.capacity, entry.selected, scope.id, mapOf("pageUrl" to scope.listUrl,
                                "sectionId" to entry.id, "sportsValue" to entry.id, "sportsCategory" to ZfOldSports.category(document),
                                "sessionEpoch" to session.epoch.toString()))
                    }
                AcademicTables.rows(page.text, page.url).mapNotNull { offer(it, page.url, scope) } + sports
            }
        }.filter { (query.keyword.isBlank() || it.name.contains(query.keyword, true)) && (query.teacher.isBlank() || it.teacher.contains(query.teacher, true)) }
            .distinctBy { it.scopeId + it.raw["sectionId"] }
    }

    override suspend fun listSections(course: CourseOffer): List<CourseSection> = serial {
        val popup = course.raw["popupUrl"].orEmpty()
        if (popup.isBlank()) return@serial listOf(CourseSection(course.raw["sectionId"].orEmpty(), course.stableId,
            course.name, course.teacher, course.time, course.location, raw = course.raw))
        val page = transport.get(popup, course.raw["pageUrl"].orEmpty().ifBlank { homeUrl() })
        requirePage(page)
        AcademicTables.rows(page.text, page.url).mapNotNull { row ->
            val control = row.element.select("input[type=radio], input[type=checkbox]").firstOrNull { !it.hasAttr("disabled") } ?: return@mapNotNull null
            CourseSection(control.attr("value"), course.stableId, course.name, row.value("教师姓名", "上课教师"),
                row.value("上课时间"), row.value("上课地点"), raw = course.raw + mapOf("pageUrl" to popup, "popupControl" to control.attr("value")))
        }
    }

    override suspend fun select(target: SelectionTarget): SelectionResult = serial {
        if (!target.confirmed) return@serial SelectionResult(AcademicStatus.VALIDATION_FAILED, "请先确认选课")
        if (target.course.raw["sessionEpoch"] != session.epoch.toString()) return@serial SelectionResult(AcademicStatus.SESSION_EXPIRED)
        val url = target.section.raw["pageUrl"].orEmpty().ifBlank { target.course.raw["pageUrl"].orEmpty() }
        val freshPages = if (target.course.raw.containsKey("sportsValue"))
            listOf(sportsTargetPage(url, target.course.raw["sportsCategory"].orEmpty())) else pages(url, target.course.name)
        for (page in freshPages) {
            if (target.course.raw.containsKey("sportsValue")) {
                val document = Jsoup.parse(page.text, page.url)
                val wantedCategory = target.course.raw["sportsCategory"].orEmpty()
                if (wantedCategory.isNotBlank() && ZfOldSports.category(document) != wantedCategory) continue
                val option = ZfOldSports.list(document, "ListBox2")?.select("option[value]")?.singleOrNull {
                    it.attr("value") == target.section.stableId && AcademicHtml.isEnabledControl(it)
                } ?: continue
                val submit = ZfOldSports.submitControl(document, selected = false)
                    ?: return@serial SelectionResult(AcademicStatus.PAGE_CHANGED, "学校当前未提供体育选课按钮")
                val response = postControl(page, submit, option)
                return@serial SelectionResult(outcome(response), operationMessage(response.text))
            }
            val row = AcademicTables.rows(page.text, page.url).firstOrNull {
                if (target.section.raw.containsKey("popupControl"))
                    it.element.select("input").any { input -> input.attr("value") == target.section.raw["popupControl"] }
                else rowId(it) == target.section.stableId
            } ?: continue
            val control = row.element.select("input[type=checkbox], input[type=radio]").firstOrNull { !it.hasAttr("disabled") }
                ?: return@serial SelectionResult(AcademicStatus.PAGE_CHANGED, "目标教学班控件已变化")
            val form = row.element.closest("form") ?: return@serial SelectionResult(AcademicStatus.PAGE_CHANGED, "缺少选课表单")
            val submit = form.select("input[type=submit], button").firstOrNull {
                AcademicHtml.isEnabledControl(it) && it.attr("value").ifBlank { it.text() }.trim() in setOf("立即提交", "提交", "选定课程", "选课")
            } ?: return@serial SelectionResult(AcademicStatus.UNSUPPORTED, "当前页面的选课确认方式尚未适配，请在学校网页完成")
            val response = postControl(page, submit, control)
            SelectionResult(outcome(response), operationMessage(response.text)).let { return@serial it }
        }
        SelectionResult(AcademicStatus.PAGE_CHANGED, "目标教学班已不在当前页面")
    }

    private fun isSelectedRow(row: AcademicTables.Row): Boolean {
        val table = row.element.closest("table")
        return dropControl(row.element) != null || table?.previousElementSibling()?.text()?.contains("已选") == true ||
            table?.select("caption")?.text()?.contains("已选") == true
    }

    override suspend fun selected(context: CourseContext): List<SelectedCourse> = serial {
        check(context)
        var recognized = false
        val result = mutableListOf<SelectedCourse>()
        selectedQueryUrl?.let { url ->
            val page = transport.get(url, homeUrl())
            requirePage(page)
            val document = Jsoup.parse(page.text, page.url)
            if (document.select("table th, table td").none { it.text().trim() == "课程名称" })
                throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校未返回可识别的已选课程列表")
            recognized = true
            result += AcademicTables.rows(page.text, page.url)
                .filter { it.value("是否选课") !in setOf("否", "未选", "未选课") }
                .map { row ->
                    val id = rowId(row)
                    SelectedCourse(id, row.value("课程名称", "课程名"), row.value("教师姓名", "上课教师", "教师"),
                        row.value("课程代码", "课程编号", "课程号"), id,
                        mapOf("pageUrl" to page.url, "sessionEpoch" to session.epoch.toString(), "canDrop" to "false",
                            "sksj" to row.value("上课时间"), "skdd" to row.value("上课地点"), "xf" to row.value("学分")))
                }.distinctBy { it.stableId }
        }
        val actionable = mutableListOf<SelectedCourse>()
        for (scope in context.scopes) for (page in pages(scope.listUrl)) {
            val document = Jsoup.parse(page.text, page.url)
            if (ZfOldSports.list(document, "ListBox3") != null) {
                recognized = true
                val canDrop = ZfOldSports.submitControl(document, selected = true) != null
                actionable += ZfOldSports.entries(document, selected = true).map { entry ->
                    val enabled = ZfOldSports.list(document, "ListBox3")!!.select("option[value]")
                        .any { it.attr("value") == entry.id && AcademicHtml.isEnabledControl(it) }
                    SelectedCourse(entry.id, entry.name, entry.teacher, entry.courseId, entry.id,
                        mapOf("pageUrl" to scope.listUrl, "sportsValue" to entry.id, "sessionEpoch" to session.epoch.toString(),
                            "canDrop" to (canDrop && enabled).toString(), "sksj" to entry.time, "skdd" to entry.location, "xf" to entry.credit))
                }
            }
            if (document.select("table").any { it.previousElementSibling()?.text()?.contains("已选") == true || it.select("caption").text().contains("已选") }) recognized = true
            for (row in AcademicTables.rows(page.text, page.url).filter(::isSelectedRow)) {
                recognized = true
                val id = rowId(row)
                actionable += SelectedCourse(id, row.value("课程名称", "课程名"), row.value("教师姓名", "上课教师", "教师"),
                    row.value("课程代码", "课程编号", "课程号"), id,
                    mapOf("pageUrl" to scope.listUrl, "sessionEpoch" to session.epoch.toString(),
                        "canDrop" to (dropControl(row.element) != null).toString(),
                        "sksj" to row.value("上课时间"), "skdd" to row.value("上课地点"), "xf" to row.value("学分")))
            }
        }
        if (!recognized) throw AcademicException(AcademicStatus.PAGE_CHANGED, "当前学校已选课程页面尚未适配，请使用教务网页")
        for (entry in actionable.distinctBy { it.stableId }) {
            val matches = result.withIndex().filter { (_, existing) ->
                existing.stableId == entry.stableId || (entry.courseId.isNotBlank() && existing.courseId == entry.courseId &&
                    (entry.teacher.isBlank() || existing.teacher.isBlank() || entry.teacher == existing.teacher) &&
                    (entry.raw["sksj"].isNullOrBlank() || existing.raw["sksj"].isNullOrBlank() || entry.raw["sksj"] == existing.raw["sksj"]))
            }
            if (matches.size == 1) {
                val (index, existing) = matches.single()
                if (entry.raw["canDrop"] == "true" || (entry.raw.containsKey("sportsValue") && existing.raw["canDrop"] != "true"))
                    result[index] = entry.copy(raw = existing.raw + entry.raw.filterValues { it.isNotBlank() })
            } else result += entry
        }
        result.distinctBy { it.stableId }
    }

    override suspend fun drop(target: SelectionTarget): OperationResult = serial {
        if (!target.confirmed) return@serial OperationResult(AcademicStatus.VALIDATION_FAILED, "请先确认退课")
        if (target.course.raw["sessionEpoch"] != session.epoch.toString()) return@serial OperationResult(AcademicStatus.SESSION_EXPIRED)
        val url = target.course.raw["pageUrl"] ?: return@serial OperationResult(AcademicStatus.PAGE_CHANGED, "缺少课程来源页面，请刷新已选列表")
        val freshPages = if (target.course.raw.containsKey("sportsValue")) listOf(sportsTargetPage(url)) else pages(url)
        for (page in freshPages) {
            if (target.course.raw.containsKey("sportsValue")) {
                val document = Jsoup.parse(page.text, page.url)
                val option = ZfOldSports.list(document, "ListBox3")?.select("option[value]")?.singleOrNull {
                    it.attr("value") == target.section.stableId && AcademicHtml.isEnabledControl(it)
                } ?: continue
                val submit = ZfOldSports.submitControl(document, selected = true)
                    ?: return@serial OperationResult(AcademicStatus.UNSUPPORTED, "该体育课程当前不可退选")
                val response = postControl(page, submit, option, write = true)
                return@serial OperationResult(outcome(response), operationMessage(response.text))
            }
            val row = AcademicTables.rows(page.text, page.url).firstOrNull { rowId(it) == target.section.stableId && isSelectedRow(it) } ?: continue
            val control = dropControl(row.element) ?: return@serial OperationResult(AcademicStatus.UNSUPPORTED, "该课程当前不可退选")
            val response = postControl(page, control, write = true)
            return@serial OperationResult(outcome(response), operationMessage(response.text))
        }
        OperationResult(AcademicStatus.PAGE_CHANGED, "目标课程已不在可退选列表")
    }

    private suspend fun postControl(page: AcademicResponse, control: Element, selected: Element? = null, write: Boolean = false): AcademicResponse {
        val form = control.closest("form") ?: throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校表单已变化")
        val fields = AcademicHtml.formFields(form, control.attr("name") to control.attr("value")).toMutableList()
        val event = Regex("""__doPostBack\(['"]([^'"]+)['"],\s*['"]([^'"]*)['"]\)""").find(control.attr("href") + control.attr("onclick"))
        if (event != null) {
            fields.removeAll { it.first == "__EVENTTARGET" || it.first == "__EVENTARGUMENT" }
            fields += "__EVENTTARGET" to event.groupValues[1]
            fields += "__EVENTARGUMENT" to event.groupValues[2]
        } else if (control.attr("name").isBlank()) throw AcademicException(AcademicStatus.PAGE_CHANGED, "当前学校的操作按钮尚未适配")
        if (selected != null) {
            val checkNames = form.select("input[type=checkbox]").map { it.attr("name") }.toSet()
            val name = if (selected.tagName() == "option") selected.closest("select")?.attr("name").orEmpty() else selected.attr("name")
            if (name.isBlank()) throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校选课控件缺少名称")
            fields.removeAll { it.first in checkNames || it.first == name }
            fields += name to AcademicHtml.controlValue(selected)
        }
        return transport.postForm(AcademicHtml.action(form, page.url), fields, page.url, write = write || selected != null)
    }

    private fun operationMessage(html: String): String {
        val alerts = Regex("""alert\s*\(\s*['"]([^'"]+)['"]\s*\)""").findAll(html).map { it.groupValues[1] }.toList()
        return alerts.joinToString(" ").ifBlank { AcademicJson.message(html) }
    }

    private fun outcome(response: AcademicResponse): AcademicStatus {
        if (response.code !in 200..299) return AcademicJson.status(response.text, response.code)
        val html = response.text
        val message = operationMessage(html)
        val status = AcademicJson.status(html)
        if (status == AcademicStatus.VALIDATION_FAILED &&
            Regex("""^(?:选课|退课|退选|选定课程|保存)(?:操作)?成功[!！。\s]*$""").matches(message.trim()))
            return AcademicStatus.SUCCESS
        val alertStatus = AcademicJson.status(message)
        if (alertStatus != AcademicStatus.VALIDATION_FAILED) return alertStatus
        return if (status == AcademicStatus.VALIDATION_FAILED) AcademicStatus.RESULT_UNKNOWN else status
    }
}
