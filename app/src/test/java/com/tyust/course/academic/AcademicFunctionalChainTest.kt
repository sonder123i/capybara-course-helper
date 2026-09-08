package com.tyust.course.academic

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.UUID

class AcademicFunctionalChainTest {
    @Test fun oldZfPopupSelectionAndWithdrawalUseFreshWebFormsControls() = runBlocking {
        val server = MockWebServer()
        val id = UUID.randomUUID().toString()
        var generation = 0
        var enrolled = false
        var writes = 0
        fun table(operation: String = "") = """<table><caption>已选课程</caption><tr><th>课程名称</th><th>课程代码</th><th>教学班号</th><th>教师姓名</th><th>操作</th></tr>
            ${if (enrolled) "<tr><td>课程甲</td><td>C-$id</td><td>S-$id</td><td>教师甲</td><td>$operation</td></tr>" else ""}</table>"""
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                fun response(body: String) = MockResponse().setBody(body)
                return when (request.requestUrl!!.encodedPath) {
                    "/jsxsd/xs_main.aspx" -> response("""<a href='xsxk.aspx'>学生选课</a><a href='xsxkqk.aspx'>已选查询</a>""")
                    "/jsxsd/xsxkqk.aspx" -> response(table())
                    "/jsxsd/xsxk.aspx" -> if (request.method == "POST") {
                        val body = URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                        assertTrue(body.contains("__VIEWSTATE=view-$generation"))
                        assertTrue(body.contains("__EVENTTARGET=drop-$generation"))
                        enrolled = false; writes++
                        response("<script>alert('退课成功！')</script>")
                    } else {
                        generation++
                        response("""<form action='xsxk.aspx' method='post'><input name='__VIEWSTATE' type='hidden' value='view-$generation'>
                            <table><tr><th>课程名称</th><th>课程代码</th><th>操作</th></tr><tr><td>课程甲</td><td>C-$id</td>
                            <td><a onclick="window.open('classes.aspx?course=$id')">选教学班</a></td></tr></table>
                            ${table("<a href=\"javascript:__doPostBack('drop-$generation','')\">退课</a>")}</form>""")
                    }
                    "/jsxsd/classes.aspx" -> if (request.method == "POST") {
                        val body = URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                        assertTrue(body.contains("__VIEWSTATE=view-$generation"))
                        assertTrue(body.contains("class-$generation=S-$id"))
                        assertTrue(body.contains("confirm=选定课程"))
                        enrolled = true; writes++
                        response("<script>alert('选课成功！')</script>")
                    } else {
                        generation++
                        response("""<form action='classes.aspx?course=$id' method='post'><input name='__VIEWSTATE' type='hidden' value='view-$generation'>
                            <table><tr><th>课程名称</th><th>教师姓名</th><th>上课时间</th><th>操作</th></tr>
                            <tr><td>课程甲</td><td>教师甲</td><td>周五</td><td><input type='radio' name='class-$generation' value='S-$id'></td></tr></table>
                            <input type='submit' name='confirm' value='选定课程'></form>""")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath).apply { username = "student" }
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val context = adapter.loadCourseContext()
            val resolved = adapter.resolveSelection(context, "C-$id", "S-$id", "课程甲")!!
            assertEquals(AcademicStatus.SUCCESS, adapter.select(SelectionTarget(resolved.course, resolved.section, true)).status)
            val selected = adapter.selected(context).single()
            assertEquals("true", selected.raw["canDrop"])
            assertTrue(selected.raw.getValue("pageUrl").endsWith("xsxk.aspx"))
            val course = resolved.course.copy(raw = selected.raw)
            val section = CourseSection(selected.sectionId, selected.courseId, raw = selected.raw)
            assertEquals(AcademicStatus.SUCCESS, adapter.drop(SelectionTarget(course, section, true)).status)
            assertTrue(adapter.selected(context).isEmpty())
            assertEquals(2, writes)
        } finally { server.shutdown() }
    }

    @Test fun bothQzSystemsUseFreshSchoolEndpointsAndTokensForSelectionAndWithdrawal() = runBlocking {
        for (system in listOf(AcademicSystem.QZ, AcademicSystem.QZ_OLD)) {
            val server = MockWebServer()
            val suffix = UUID.randomUUID().toString()
            val courseId = "C-$suffix"
            val classId = "S-$suffix"
            var categoryReads = 0
            var writes = 0
            var selected = false
            var restoredRound = false
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.requestUrl!!.encodedPath
                    fun response(body: String) = MockResponse().setBody(body)
                    return when (path) {
                        "/jsxsd/xsxk/xklc_list" -> response(if (system == AcademicSystem.QZ_OLD)
                            """<a onclick="comeInXkIndx('$suffix')">开放轮次</a>""" else "<html>选课轮次</html>")
                        "/jsxsd/xsxk/xklc_list_data" -> response("""{"data":[{"jx0502zbid":"$suffix","xkzt":"1","xnxq01id":"2030-2031-2"}]}""")
                        "/jsxsd/xsxk/newXsxkzx", "/jsxsd/xsxk/xsxk_index" -> {
                            restoredRound = true
                            response("""<a href='/jsxsd/xsxkkc/category-$suffix'>学校分类</a>
                                <a href='/jsxsd/xsxkjg/comeXkjglb'>已选课程</a>""")
                        }
                        "/jsxsd/xsxkkc/category-$suffix" -> {
                            categoryReads++
                            response("""<input id='nonce' name='nonce' type='hidden' value='nonce-$categoryReads'>
                                <script>
                                var table = { sAjaxSource: '/jsxsd/list-$suffix'
                                };
                                function xsxkOper(jx0404id,kcid) {
                                  $.ajax({
                                    url: '/jsxsd/enroll-$suffix?course='+kcid,
                                    type: 'POST',
                                    data: { jx0404id: jx0404id, nonce: ${'$'}('#nonce').val() }
                                  });
                                }
                                </script>""")
                        }
                        "/jsxsd/list-$suffix" -> response("""{"aaData":[{"kcid":"$courseId","jx0404id":"$classId","kcmc":"课程甲"}],"iTotalRecords":1}""")
                        "/jsxsd/enroll-$suffix" -> {
                            assertTrue(restoredRound)
                            assertEquals("POST", request.method)
                            assertEquals(courseId, request.requestUrl!!.queryParameter("course"))
                            val body = URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                            assertTrue(body.contains("jx0404id=$classId"))
                            assertTrue(body.contains("nonce=nonce-$categoryReads"))
                            assertTrue(categoryReads >= 2)
                            selected = true; writes++
                            response("""{"success":true}""")
                        }
                        "/jsxsd/xsxkjg/comeXkjglb" -> {
                            assertTrue(restoredRound)
                            response("""<table><tr><th>课程名称</th><th>课程编号</th><th>操作</th></tr>
                                ${if (selected) """<tr><td>课程甲</td><td>$courseId</td><td><a onclick="xstkOper( '$classId' )">退课</a></td></tr>""" else ""}
                                </table><input name='nonce' type='hidden' value='drop-fresh'>
                                <script src='/jsxsd/js/xsxkjg-$suffix.js'></script>""")
                        }
                        "/jsxsd/js/xsxkjg-$suffix.js" -> response("""function xstkOper(jx0404id) {
                            ${'$'}.ajax({
                              url: '/jsxsd/withdraw-$suffix',
                              type: 'POST',
                              data: { jx0404id: jx0404id, nonce: nonce }
                            });
                        }""")
                        "/jsxsd/withdraw-$suffix" -> {
                            assertEquals("POST", request.method)
                            val body = URLDecoder.decode(request.body.readUtf8(), "UTF-8")
                            assertTrue(body.contains("jx0404id=$classId"))
                            assertTrue(body.contains("nonce=drop-fresh"))
                            selected = false; writes++
                            response("""{"success":true}""")
                        }
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
            server.start()
            try {
                val school = AcademicCoreTest.testSchool(server, system)
                val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
                val http = AcademicHttpTransport(school, session)
                val adapter = if (system == AcademicSystem.QZ) QzAcademicAdapter(school, session, http) else QzOldAcademicAdapter(school, session, http)
                val context = adapter.loadCourseContext()
                assertEquals(1, context.scopes.size)
                val offer = adapter.listCourses(context, CourseQuery()).single()
                val section = adapter.listSections(offer).single()
                assertEquals(AcademicStatus.VALIDATION_FAILED, adapter.select(SelectionTarget(offer, section)).status)
                assertEquals(0, writes)
                assertEquals(AcademicStatus.SUCCESS, adapter.select(SelectionTarget(offer, section, true)).status)
                val enrolled = adapter.selected(context).single()
                assertEquals("true", enrolled.raw["canDrop"])
                restoredRound = false
                val target = SelectionTarget(offer.copy(raw = enrolled.raw), section.copy(raw = enrolled.raw), true)
                assertEquals(AcademicStatus.SUCCESS, adapter.drop(target).status)
                assertTrue(adapter.selected(context).isEmpty())
                assertEquals(2, writes)
            } finally { server.shutdown() }
        }
    }

    @Test fun modernQzSelectedQueryWorksWithoutAnOpenRoundAndUsesTheSchoolSemester() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("<li data-src='/xkgl/xsxkjgcx'>选课结果查询</li>"))
            server.enqueue(MockResponse().setBody("<script>var src='/jsxsd/xkgl/loadXsxkjgList?lx=xkrz';</script>"))
            server.enqueue(MockResponse().setBody("""<form><select name='xnxqid'><option value='2032-2033-2' selected>2032-2033-2</option></select></form>
                <script>initQzTable({
                    url: '/jsxsd/xkgl/loadXsxkjgList?lx=xkrz&type=list'
                });</script>"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"count":2,"data":[{"jx0501id":"selected-1","kch":"C1","kc_mc":"课程甲"}]}"""))
            server.enqueue(MockResponse().setBody("""{"code":0,"count":2,"data":[{"jx0501id":"selected-2","kch":"C2","kc_mc":"课程乙"}]}"""))
            val result = adapter.selected(CourseContext(session.epoch, emptyList()))
            assertEquals(listOf("课程甲", "课程乙"), result.map { it.name })
            repeat(3) { server.takeRequest() }
            val first = server.takeRequest().requestUrl!!
            val second = server.takeRequest().requestUrl!!
            assertEquals("2032-2033-2", first.queryParameter("xnxqid"))
            assertEquals("1", first.queryParameter("pageNum"))
            assertEquals("2", second.queryParameter("pageNum"))
        } finally { server.shutdown() }
    }

    @Test fun oldQzDoesNotTreatAnUnrecognizedTableAsAClosedRound() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ_OLD)
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val adapter = QzOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("<table><tr><td>系统维护中</td></tr></table>"))
            try { adapter.loadCourseContext(); fail("An unrelated table must not become an empty course list") }
            catch (e: AcademicException) { assertEquals(AcademicStatus.PAGE_CHANGED, e.status) }
        } finally { server.shutdown() }
    }
}
