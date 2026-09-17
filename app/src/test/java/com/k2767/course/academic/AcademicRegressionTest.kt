package com.k2767.course.academic

import com.k2767.course.model.SchoolConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class AcademicRegressionTest {
    @Test fun oldQzReadsStudentIdentityFromTheLinkedProfileFrame() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ_OLD)
            val session = AcademicSessionStore().session(school.id, "profile", school.fullBasePath)
            val adapter = QzOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("""<a href='/jsxsd/xsxk/xklc_list'>学生选课</a>
                <iframe src='/jsxsd/framework/xsMain_new.jsp?t1=1'></iframe>"""))
            server.enqueue(MockResponse().setBody("""<div><div class='middletopdwxxtit'>学生姓名：</div><div class='middletopdwxxcont'>测试同学</div></div>
                <div><div class='middletopdwxxtit'>学号：</div><div class='middletopdwxxcont'>2025000001</div></div>"""))
            val result = adapter.validateSession()
            assertEquals(AcademicStatus.SUCCESS, result.status)
            assertEquals("测试同学", result.studentName)
            assertEquals("2025000001", result.studentId)
            assertEquals(result.studentId, session.username)
            server.takeRequest()
            val request = server.takeRequest()
            assertEquals("/jsxsd/framework/xsMain_new.jsp?t1=1", request.path)
            assertEquals(server.url("/jsxsd/framework/xsMain.jsp").toString(), request.getHeader("Referer"))
        } finally { server.shutdown() }
    }

    @Test fun oldZfPreservesFullTimesFromTruncatedTableCells() {
        val html = """<table><tr><th>课程名称</th><th>上课时间</th><th>上课地点</th></tr>
            <tr><td><a title="查看课程信息">课程甲</a></td>
            <td title="周五第9,10节{第3-18周}">周五第9,10节{第...</td>
            <td><span title="实验楼第一教室">实验楼…</span></td></tr></table>"""
        val row = AcademicTables.rows(html, "https://school.example/").single()
        assertEquals("课程甲", row.value("课程名称"))
        assertEquals("周五第9,10节{第3-18周}", row.value("上课时间"))
        assertEquals("实验楼第一教室", row.value("上课地点"))
    }

    @Test fun oldQzReadsSelectedCoursesFromTheSchoolMenuAndSelectedTerm() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ_OLD)
            val session = AcademicSessionStore().session(school.id, "old-query", school.fullBasePath)
            val adapter = QzOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("<li data-url='/xkgl/xsxkjgcx'><a href='javascript:void(0)'>选课结果查询</a></li>"))
            server.enqueue(MockResponse().setBody("""<form name='sxkyxQueryForm' method='post'>
                <select name='xnxqid'><option value='2025-2026-2' selected>2025-2026-2</option></select>
                <input name='context' type='hidden' value='keep'></form>
                <script>document.forms["sxkyxQueryForm"].action = "/jsxsd/xkgl/loadXsxkjgList";</script>"""))
            server.enqueue(MockResponse().setBody("""<table><tr><td>课程名称</td><td>课程编号</td><td>上课老师</td></tr>
                <tr><td>课程甲</td><td>C1</td><td>教师甲</td></tr></table>"""))
            val result = adapter.selected(CourseContext(session.epoch, emptyList())).single()
            assertEquals("课程甲", result.name)
            assertEquals("教师甲", result.teacher)
            assertEquals("false", result.raw["canDrop"])
            server.takeRequest()
            assertEquals("/jsxsd/xkgl/xsxkjgcx", server.takeRequest().path)
            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/jsxsd/xkgl/loadXsxkjgList", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("xnxqid=2025-2026-2"))
            assertTrue(body.contains("context=keep"))
        } finally { server.shutdown() }
    }

    @Test fun expandingOneSectionKeepsOtherClassesAndRounds() {
        fun course(section: String, round: String) = com.k2767.course.model.Course().apply {
            courseId = "C1"
            classId = section
            completeParams = mutableMapOf("academic_scope_id" to round)
        }
        val first = course("S1", "round1")
        val second = course("S2", "round1")
        val otherRound = course("S1", "round2")
        val refreshed = course("S1", "round1").apply { teacher = "Updated" }
        val merged = AcademicCourseBridge.mergeSections(listOf(first, second, otherRound), first, listOf(refreshed))
        assertEquals(3, merged.size)
        assertSame(refreshed, merged.first())
        val bySection = merged.associateBy { it.completeParams["academic_scope_id"] to it.classId }
        assertSame(second, bySection["round1" to "S2"])
        assertSame(otherRound, bySection["round2" to "S1"])
        assertEquals("Updated", bySection["round1" to "S1"]?.teacher)
    }

    @Test fun academicUrlsKeepTheApplicationRoot() {
        assertEquals("", AcademicAddress.parse("https://jw.example.edu.cn/framework/xsMainV.htmlx")!!.basePath)
        assertEquals("", AcademicAddress.parse("http://jw.example.edu.cn/default2.aspx")!!.basePath)
        assertEquals("/custom_jsxsd", AcademicAddress.parse("https://jw.example.edu.cn/custom_jsxsd/")!!.basePath)
        assertEquals("/jwglxt", AcademicAddress.parse("https://jw.example.edu.cn/jwglxt/xtgl/login_slogin.html")!!.basePath)
        assertFalse(AcademicUrlPolicy.isAllowed("https://jw.example.edu.cn:8443/", "https", listOf("jw.example.edu.cn")))
        assertFalse(AcademicUrlPolicy.isAllowed("http://jw.example.edu.cn/", "https", listOf("jw.example.edu.cn")))
        assertFalse(AcademicUrlPolicy.isAllowed("https://user@jw.example.edu.cn/", "https", listOf("jw.example.edu.cn")))
        val oldQz = SchoolConfig("qz_old", "School", "jw.example.edu.cn", "https").apply {
            academicSystem = AcademicSystem.QZ_OLD.id
            basePath = "/custom_jsxsd"
        }
        assertEquals("https://jw.example.edu.cn/custom_jsxsd/", AcademicGatewayFactory.loginUrl(oldQz))
    }

    @Test fun quotedDataTablesSourceKeepsFiltersAndTheOperationQuery() {
        val config = QzScriptParser.parse(AcademicCoreTest.fixture("qz-category.html"), "https://jw.example.edu.cn/jsxsd/xsxkkc/getGgxxk", CourseQuery("大学语文"))!!
        assertTrue(config.listUrl.contains("sfym=false&sfct=true"))
        assertTrue(config.listUrl.contains("kcxx=%25"))
        assertEquals(listOf("kch", "kcmc", "jx0404id"), config.columns)
        assertEquals("GET", config.submitMethod)
        assertTrue(QzScriptParser.expand(config.submitUrl, mapOf("kcid" to "C&1", "cfbs" to "null"), true).contains("kcid=C%261&cfbs=null"))
        assertEquals("0", config.defaults["sfsyjc"])
        assertEquals(setOf("jx0404id", "xkzy", "trjf", "sfsyjc"), config.bodyFields)
        assertFalse(config.requiresVerification)
        assertTrue(QzScriptParser.parse(AcademicCoreTest.fixture("qz-category.html").replace("value=\"0\"", "value=\"1\""), "https://jw.example.edu.cn/")!!.requiresVerification)
    }

    @Test fun categoryDiscoveryUsesOnlyVisibleSchoolMenuItems() {
        val html = """<li onclick="jpbxxk('open')">公共选修</li><script>
        function jpbxxk(type) { switch(type) {
          case 'open': $("#frame").attr('src', "/jsxsd/xsxkkc/getGgxxk"); break;
          case 'hidden': $("#frame").attr('src', "/jsxsd/xsxkkc/getOther"); break;
        }}</script>"""
        assertEquals(listOf("公共选修" to "https://jw.example.edu.cn/jsxsd/xsxkkc/getGgxxk"),
            QzScriptParser.categoryPages(html, "https://jw.example.edu.cn/jsxsd/"))
    }

    @Test fun anEchoedUsernameInALoginFormIsNotAnIdentity() {
        assertEquals("" to "", parseName(AcademicHtml.parse("""<form><input name="userAccount" value="123456"><input type="password"></form>""", "https://jw.example.edu.cn")))
        assertEquals("测试同学" to "123456", parseName(AcademicHtml.parse("""<div class="infoContentTitle">测试同学-123456</div>""", "https://jw.example.edu.cn")))
    }

    @Test fun invalidListsAndConflictRepliesNeverBecomeEmptySuccess() {
        assertEquals(AcademicStatus.CONFLICT, AcademicJson.status("""{"success":true,"yxjx0404id":"previous","message":"还有冲突"}"""))
        assertEquals(AcademicStatus.NO_CAPACITY, AcademicJson.status("""{"success":false,"code":0,"message":"容量已满"}"""))
        assertEquals(AcademicStatus.ROUND_CLOSED, AcademicJson.status("""{"message":"当前阶段只可退课"}"""))
        assertEquals(AcademicStatus.SUCCESS, AcademicJson.zfStatus("\"1\""))
        assertNotEquals(AcademicStatus.SUCCESS, AcademicJson.status("\"1\""))
        try { AcademicJson.objects("<html>maintenance</html>", "data"); fail("Must reject unrecognized list") }
        catch (e: AcademicException) { assertEquals(AcademicStatus.PAGE_CHANGED, e.status) }
        try { AcademicJson.objects("""<form><input type="password"></form>""", "data"); fail("Must detect login") }
        catch (e: AcademicException) { assertEquals(AcademicStatus.SESSION_EXPIRED, e.status) }
        assertEquals(1, AcademicJson.objects("""{"data":{"rows":[{"id":"1"}]}}""", "rows").size)
        try { AcademicJson.objects("""{"code":500,"data":[],"msg":"查询失败"}""", "data"); fail("A rejected list is not proof of absence") }
        catch (e: AcademicException) { assertNotEquals(AcademicStatus.SUCCESS, e.status) }
        assertEquals(AcademicStatus.SUCCESS, AcademicJson.status("""{"success":true,"yxjx0404id":null}"""))
    }

    @Test fun qzPaginationStopsAtTheReportedTotal() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "account", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val page = server.url("/jsxsd/xsxkkc/getGgxxk").toString()
            server.enqueue(MockResponse().setBody(AcademicCoreTest.fixture("qz-category.html")))
            server.enqueue(MockResponse().setBody("""{"aaData":[{"jx02id":"C1","jx0404id":"S1","kcmc":"Course"}],"iTotalDisplayRecords":2}"""))
            server.enqueue(MockResponse().setBody("""{"aaData":[{"jx02id":"C2","jx0404id":"S2","kcmc":"Course"}],"iTotalDisplayRecords":2}"""))
            val result = adapter.listCourses(CourseContext(session.epoch, listOf(CourseScope("scope", "Scope", listUrl=page))), CourseQuery(pageSize=1))
            assertEquals(2, result.size)
            assertEquals(3, server.requestCount)
            server.takeRequest()
            assertTrue(server.takeRequest().body.readUtf8().contains("iDisplayStart=0"))
            assertTrue(server.takeRequest().body.readUtf8().contains("iDisplayStart=1"))
        } finally { server.shutdown() }
    }

    @Test fun choosingAScopeQueriesOnlyItsRoundAndCategory() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "scope-test", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("<html>Round B</html>"))
            server.enqueue(MockResponse().setBody(AcademicCoreTest.fixture("qz-category.html")))
            server.enqueue(MockResponse().setBody("""{"aaData":[{"jx02id":"C2","jx0404id":"S2","kcmc":"课程乙"}],"iTotalDisplayRecords":1}"""))
            val scopes = listOf(
                CourseScope("A", "A", listUrl = server.url("/categoryA").toString(), params = mapOf("roundPage" to server.url("/roundA").toString())),
                CourseScope("B", "B", listUrl = server.url("/categoryB").toString(), params = mapOf("roundPage" to server.url("/roundB").toString())))
            val result = adapter.listCourses(CourseContext(session.epoch, scopes), CourseQuery(scopeId = "B"))
            assertEquals("B", result.single().scopeId)
            assertEquals("/roundB", server.takeRequest().path)
            assertEquals("/categoryB", server.takeRequest().path)
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun aWriteRedirectIsNeverReplayed() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ_OLD)
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val http = AcademicHttpTransport(school, session)
            server.enqueue(MockResponse().setResponseCode(302).addHeader("Location", "/replay"))
            try { http.writeGet(http.appUrl("operation")); fail("Write redirect must be unknown") }
            catch (e: AcademicException) { assertEquals(AcademicStatus.RESULT_UNKNOWN, e.status) }
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun oldPageCharsetAlsoAppliesToTheForm() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val http = AcademicHttpTransport(school, session)
            val charset = java.nio.charset.Charset.forName("GBK")
            server.enqueue(MockResponse().addHeader("Content-Type","text/html; charset=GBK")
                .setBody(okio.Buffer().write("学生".toByteArray(charset))))
            server.enqueue(MockResponse().setBody("ok"))
            assertEquals("学生", http.get(http.appUrl("form")).text)
            http.postForm(http.appUrl("form"), listOf("role" to "学生"))
            server.takeRequest()
            assertEquals("role=%D1%A7%C9%FA", server.takeRequest().body.readUtf8())
        } finally { server.shutdown() }
    }
}
