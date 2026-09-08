package com.tyust.course.academic

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.jsoup.Jsoup
import org.junit.Assert.*
import org.junit.Test
import java.net.URLDecoder
import java.util.UUID

class ZfOldSportsTest {
    @Test fun aCatalogueIsNotAClassButTheSelectedSportsListIsRead() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "sports", school.fullBasePath)
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val page = """<form><select name='ListBox1' size='4'><option value='catalog'>catalog∥运动项目</option></select>
                <select name='ListBox2' size='4'></select><input type='submit' name='choose' value='选定课程'>
                <h3>已选课程</h3><select name='ListBox3' size='4'>
                <option value='(2033-2034-2)-C7-teacher-3'>体育甲∥1.0∥教师甲∥2.0-0.0∥考试∥周一第9,10节{第3-18周}∥体育馆</option>
                </select></form>"""
            repeat(2) { server.enqueue(MockResponse().setBody(page)) }
            val context = CourseContext(session.epoch, listOf(CourseScope("sports", "体育课", listUrl = server.url("/jsxsd/xstyk.aspx").toString())))
            assertTrue(adapter.listCourses(context, CourseQuery()).isEmpty())
            val selected = adapter.selected(context).single()
            assertEquals("C7", selected.courseId)
            assertEquals("(2033-2034-2)-C7-teacher-3", selected.sectionId)
            assertEquals("体育甲", selected.name)
            assertEquals("教师甲", selected.teacher)
            assertEquals("1.0", selected.raw["xf"])
            assertEquals("周一第9,10节{第3-18周}", selected.raw["sksj"])
            assertEquals("false", selected.raw["canDrop"])
            assertEquals(2, server.requestCount)
            repeat(2) { assertEquals("GET", server.takeRequest().method) }
        } finally { server.shutdown() }
    }

    @Test fun sportsCategoriesSelectionAndWithdrawalUseFreshSchoolControls() = runBlocking {
        val server = MockWebServer()
        val id = UUID.randomUUID().toString()
        val classId = "(2036-2037-1)-C2-$id-1"
        var generation = 0
        var selected = false
        var writes = 0
        fun page(category: String): String {
            generation++
            return """<form action='xstyk.aspx' method='post'><input name='__VIEWSTATE' type='hidden' value='view-$generation'>
                <select name='ListBox1' size='4' onchange="__doPostBack('ListBox1','')">
                <option value='one' ${if (category == "one") "selected" else ""}>项目甲</option>
                <option value='two' ${if (category == "two") "selected" else ""}>项目乙</option></select>
                <span id='Label4'>格式：课程名称‖学分‖教师姓名‖教师职称‖上课时间‖上课地点‖限选‖已选‖面向对象‖校区要求</span>
                <select name='ListBox2' size='4'>${if (category == "two") """<option value='$classId'>体育乙∥1.0∥教师乙∥讲师∥周二第3,4节∥体育馆∥40∥32∥全部∥主校区</option>""" else ""}</select>
                <input type='submit' name='choose-$generation' value='选定课程'>
                <h3>已选课程</h3><select name='ListBox3' size='4'>${if (selected) """<option value='$classId'>体育乙∥1.0∥教师乙∥2.0-0.0∥考试∥周二第3,4节∥体育馆</option>""" else ""}</select>
                <input type='submit' name='drop-$generation' value='退选课程'></form>"""
        }
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                assertEquals("/jsxsd/xstyk.aspx", request.requestUrl!!.encodedPath)
                if (request.method == "GET") return MockResponse().setBody(page("one"))
                val fields = request.body.readUtf8().split('&').associate { pair ->
                    val parts = pair.split('=', limit = 2)
                    URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
                }
                assertEquals("view-$generation", fields["__VIEWSTATE"])
                if (fields["__EVENTTARGET"] == "ListBox1") {
                    assertFalse(fields.keys.any { it.startsWith("choose-") || it.startsWith("drop-") })
                    assertFalse(fields.containsKey("ListBox2"))
                    return MockResponse().setBody(page(fields.getValue("ListBox1")))
                }
                return if (fields["choose-$generation"] == "选定课程") {
                    assertEquals("two", fields["ListBox1"])
                    assertEquals(classId, fields["ListBox2"])
                    selected = true; writes++
                    MockResponse().setBody("<script>alert('选课成功！')</script>")
                } else {
                    assertEquals("退选课程", fields["drop-$generation"])
                    assertEquals(classId, fields["ListBox3"])
                    selected = false; writes++
                    MockResponse().setBody("<script>alert('退选成功！')</script>")
                }
            }
        }
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "sports", school.fullBasePath)
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val context = CourseContext(session.epoch, listOf(CourseScope("sports", "体育课", listUrl = server.url("/jsxsd/xstyk.aspx").toString())))
            val course = adapter.listCourses(context, CourseQuery()).single()
            assertEquals("C2", course.stableId)
            assertEquals(40, course.capacity)
            assertEquals(32, course.selected)
            val section = adapter.listSections(course).single()
            assertEquals(AcademicStatus.SUCCESS, adapter.select(SelectionTarget(course, section, true)).status)
            val enrolled = adapter.selected(context).single()
            assertEquals("true", enrolled.raw["canDrop"])
            assertEquals(classId, enrolled.sectionId)
            val target = SelectionTarget(course.copy(raw = enrolled.raw), section.copy(raw = enrolled.raw), true)
            assertEquals(AcademicStatus.SUCCESS, adapter.drop(target).status)
            assertTrue(adapter.selected(context).isEmpty())
            assertEquals(2, writes)
        } finally { server.shutdown() }
    }

    @Test fun formFieldsDoNotSelectAnUnchosenListBoxOption() {
        val form = Jsoup.parse("""<form>
            <select name='catalogue' size='4'><option value='first'>First</option></select>
            <select name='classes' multiple><option value='first'>First</option></select>
            <select name='semester'><option disabled value='disabled'>Unavailable</option><option value='current'>Current</option></select>
            <select name='chosen' size='4'><option selected value='actual'>Selected</option></select>
            </form>""").selectFirst("form")!!
        assertEquals(mapOf("semester" to "current", "chosen" to "actual"), AcademicHtml.formFields(form).toMap())
    }

    @Test fun malformedSportsRecordsAreReportedInsteadOfHidden() {
        val document = Jsoup.parse("<select name='ListBox3'><option value='id'>unrecognized</option></select>")
        try { ZfOldSports.entries(document, selected = true); fail("Unknown selected records must be reported") }
        catch (e: AcademicException) { assertEquals(AcademicStatus.PAGE_CHANGED, e.status) }
    }

    @Test fun formFieldsIncludeTheClickedButtonAndBrowserDefaultValues() {
        val form = Jsoup.parse("""<form>
            <button name='confirm' value='selected'>选定课程</button><button name='cancel'>取消</button>
            <input name='chosen' type='checkbox' checked><input name='ignored' type='checkbox'>
            <select name='term'><optgroup disabled><option value='old'>Old</option></optgroup><option>2037-2038-1</option></select>
            </form>""").selectFirst("form")!!
        assertEquals(mapOf("confirm" to "selected", "chosen" to "on", "term" to "2037-2038-1"),
            AcademicHtml.formFields(form, "confirm" to "selected").toMap())
        assertEquals("on", AcademicHtml.controlValue(form.selectFirst("input[name=chosen]")!!))
    }
}
