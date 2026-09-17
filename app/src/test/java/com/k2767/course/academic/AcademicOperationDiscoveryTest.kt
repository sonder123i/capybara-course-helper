package com.k2767.course.academic

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.UUID

class AcademicOperationDiscoveryTest {
    @Test fun qzDropControlsMustBePresentAndEnabled() {
        for (markup in listOf(
            "<script>xstkOper('class')</script>",
            "<!-- <a onclick=\"xstkOper('class')\">退课</a> -->",
            "<a disabled onclick=\"xstkOper('class')\">退课</a>",
            "<div hidden><a onclick=\"xstkOper('class')\">退课</a></div>",
            "<a style='display: none !important' onclick=\"xstkOper('class')\">退课</a>",
            "<a aria-disabled='true' onclick=\"xstkOper('class')\">退课</a>"
        )) assertNull(markup, QzScriptParser.dropControl(Jsoup.parseBodyFragment(markup)))
        val control = QzScriptParser.dropControl(Jsoup.parseBodyFragment("<a href=\"javascript:doQxtk('record')\">申请退课</a>"))!!
        assertEquals("doQxtk", control.function)
        assertEquals("jx0501id", control.field)
        assertEquals("record", control.id)
    }

    @Test fun qzUnsupportedSubmitDataCannotSilentlyBecomeAnEmptyBody() {
        val operation = """function xstkOper(classId) {
            $.ajax({
                url: '/withdraw',
                type: 'POST',
                data: buildData(classId)
            });
        }"""
        assertNull(QzScriptParser.dropOperation(operation, "https://school.example/"))
        val supported = operation.replace("buildData(classId)", "{ id: classId }")
        val parsed = QzScriptParser.dropOperation(supported, "https://school.example/")!!
        assertEquals("current", QzScriptParser.expand(parsed.fields.getValue("id"), mapOf("jx0404id" to "current")))
    }

    @Test fun independentQzSelectedQueryDiscoversWithdrawalAndRechecksPermission() = runBlocking {
        val server = MockWebServer()
        val id = UUID.randomUUID().toString()
        val term = "2034-2035-2"
        var generation = 0
        var allowed = true
        var writes = 0
        val queries = mutableListOf<String>()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                return when (request.requestUrl!!.encodedPath) {
                    "/jsxsd/framework/xsMainV.htmlx" -> MockResponse().setBody("<li data-url='/xkgl/xsxkjgcx'>选课结果</li>")
                    "/jsxsd/xkgl/xsxkjgcx", "/jsxsd/xkgl/loadXsxkjgList" -> {
                        if (request.requestUrl!!.queryParameter("type") == "list") {
                            queries += request.requestUrl!!.queryParameter("xnxqid").orEmpty()
                            assertEquals("token-$generation", request.requestUrl!!.queryParameter("nonce"))
                            val row = JSONObject().put("jx0501id", id).put("kch", "course-$id").put("kc_mc", "课程甲")
                                .put("operation", if (allowed) "<a onclick=\"doQxtk('$id')\">申请退课</a>" else "")
                            MockResponse().setBody(JSONObject().put("code", 0).put("count", 1).put("data", JSONArray().put(row)).toString())
                        } else {
                            generation++
                            MockResponse().setBody("""<form><select name='xnxqid'>
                                <option value='$term' ${if (generation == 1) "selected" else ""}>$term</option>
                                <option value='2035-2036-1' ${if (generation > 1) "selected" else ""}>2035-2036-1</option>
                                </select><input name='nonce' type='hidden' value='token-$generation'></form>
                                <script>initQzTable({
                                  url: '/jsxsd/xkgl/loadXsxkjgList?type=list&lx=xkrz'
                                });
                                function doQxtk(recordId) {
                                  $.ajax({
                                    url: '/jsxsd/withdraw-$id',
                                    type: 'POST',
                                    data: { id: recordId, nonce: nonce }
                                  });
                                }</script>""")
                        }
                    }
                    "/jsxsd/withdraw-$id" -> {
                        assertTrue(allowed)
                        assertEquals("POST", request.method)
                        val body = URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                        assertTrue(body.contains("id=$id"))
                        assertTrue(body.contains("nonce=token-$generation"))
                        assertTrue(generation > 1)
                        writes++
                        MockResponse().setBody("""{"success":true}""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val selected = adapter.selected(CourseContext(session.epoch, emptyList())).single()
            assertEquals("true", selected.raw["canDrop"])
            val target = SelectionTarget(CourseOffer(selected.courseId, selected.name, scopeId = "", raw = selected.raw),
                CourseSection(selected.sectionId, selected.courseId, raw = selected.raw), confirmed = true)
            assertEquals(AcademicStatus.SUCCESS, adapter.drop(target).status)
            assertEquals(1, writes)
            allowed = false
            assertEquals(AcademicStatus.PAGE_CHANGED, adapter.drop(target).status)
            assertEquals(1, writes)
            assertTrue(queries.size >= 3)
            assertTrue(queries.all { it == term })
        } finally { server.shutdown() }
    }

    @Test fun qzFunctionWithoutARenderedControlDoesNotEnableWithdrawal() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val pageUrl = server.url("/jsxsd/xsxkjg/comeXkjglb").toString()
            val page = """<table><tr><th>课程名称</th><th>课程编号</th><th>操作</th></tr>
                <tr><td>课程甲</td><td>C1</td><td><a disabled onclick="xstkOper('S1')">退课</a></td></tr></table>
                <script>function xstkOper(jx0404id) { $.ajax({
                  url: '/withdraw?class='+jx0404id
                }); }</script>"""
            server.enqueue(MockResponse().setBody(page))
            val selected = adapter.selected(CourseContext(session.epoch, listOf(CourseScope("scope", "scope", params = mapOf("selectedPage" to pageUrl))))).single()
            assertEquals("false", selected.raw["canDrop"])
        } finally { server.shutdown() }
    }

    @Test fun oldZfDisabledWithdrawalIsReadOnly() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("""<form><table><caption>已选课程</caption><tr><th>课程名称</th><th>课程编号</th><th>操作</th></tr>
                <tr><td>课程甲</td><td>C1</td><td><input type='submit' disabled name='drop' value='退课'></td></tr></table></form>"""))
            val context = CourseContext(session.epoch, listOf(CourseScope("scope", "scope", listUrl = server.url("/jsxsd/xsxk.aspx").toString())))
            assertEquals("false", adapter.selected(context).single().raw["canDrop"])
        } finally { server.shutdown() }
    }

    @Test fun oldZfHttpFailureCannotBeOverriddenByASuccessAlert() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val page = """<form method='post'><table><tr><th>课程名称</th><th>课程编号</th><th>操作</th></tr>
                <tr><td>课程甲</td><td>C1</td><td><input type='checkbox' name='course' value='S1'></td></tr></table>
                <input type='submit' name='select' value='选课'></form>"""
            repeat(2) { server.enqueue(MockResponse().setBody(page)) }
            server.enqueue(MockResponse().setResponseCode(400).setBody("<script>alert('选课成功！')</script>"))
            val context = CourseContext(session.epoch, listOf(CourseScope("scope", "scope", listUrl = server.url("/jsxsd/xsxk.aspx").toString())))
            val course = adapter.listCourses(context, CourseQuery()).single()
            val section = adapter.listSections(course).single()
            assertEquals(AcademicStatus.VALIDATION_FAILED, adapter.select(SelectionTarget(course, section, true)).status)
        } finally { server.shutdown() }
    }
}
