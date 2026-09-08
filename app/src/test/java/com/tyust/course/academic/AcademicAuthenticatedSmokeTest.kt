package com.tyust.course.academic

import com.tyust.course.model.SchoolConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Uses a manually authenticated session; this test never selects or drops a course. */
class AcademicAuthenticatedSmokeTest {
    @Test fun verifyImportedSessionReadQueries() = runBlocking {
        val path = System.getenv("ACADEMIC_COOKIE_SMOKE_FILE")
        assumeTrue("No authenticated test session supplied", !path.isNullOrBlank())
        val profiles = JSONArray(File(path!!).readText(Charsets.UTF_8).removePrefix("\uFEFF"))
        val failures = mutableListOf<String>()
        val reports = JSONArray()
        for (i in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(i)
            val school = SchoolConfig(profile.getString("id"), profile.optString("name"), profile.getString("domain"), profile.getString("protocol")).apply {
                academicSystem = profile.getString("system")
                basePath = profile.getString("basePath")
            }
            val username = profile.getString("username")
            val account = AcademicGatewayFactory.accountKey(school, username)
            AcademicGatewayFactory.importCookie(school, account, profile.getString("cookie"), username = username)
            val adapter = AcademicGatewayFactory.create(school, account)
            val study = AcademicGatewayFactory.createStudy(school, account)
            val report = JSONObject().put("system", school.academicSystem)
            suspend fun probe(name: String, read: suspend () -> Any) {
                try { report.put(name, read()) }
                catch (e: Exception) {
                    val status = (e as? AcademicException)?.status?.name ?: e.javaClass.simpleName
                    report.put(name, status)
                    report.put(name + "Message", e.message.orEmpty().take(200))
                    report.put(name + "At", e.stackTrace.firstOrNull { it.className.startsWith("com.tyust.course.academic") }?.toString())
                    failures += "${school.academicSystem}:$name:$status"
                }
            }
            probe("identity") {
                val result = adapter.validateSession()
                if (result.status != AcademicStatus.SUCCESS) throw AcademicException(result.status, "Imported login was not accepted")
                result.status.name
            }
            probe("courses") {
                val context = adapter.loadCourseContext()
                report.put("scopes", context.scopes.size)
                val courses = adapter.listCourses(context, CourseQuery(pageSize = 100))
                report.put("sections", courses.firstOrNull()?.let { adapter.listSections(it).size } ?: 0)
                courses.size
            }
            probe("selected") { adapter.selected(adapter.loadCourseContext()).size }
            probe("schedule") {
                val catalog = study.catalog()
                report.put("currentTerm", catalog.currentTerm.id)
                study.schedule(catalog.currentTerm).size
            }
            probe("grades") {
                val grades = study.grades(null).grades
                report.put("gradedTerms", grades.map { it.term }.distinct().size)
                grades.size
            }
            probe("exams") { study.exams(study.catalog().currentTerm).size }
            reports.put(report)
            println(report.toString())
        }
        System.getenv("ACADEMIC_COOKIE_SMOKE_REPORT")?.let { File(it).writeText(reports.toString(2)) }
        assertEquals("Read-only query failures", emptyList<String>(), failures)
    }
}
