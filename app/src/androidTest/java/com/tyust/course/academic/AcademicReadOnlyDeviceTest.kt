package com.tyust.course.academic

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.model.SchoolConfig
import com.tyust.course.manager.UserManager
import com.tyust.course.manager.StudentLimitManager
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in device smoke test. Supply credentials in the app's private cache, never in the APK. */
@RunWith(AndroidJUnit4::class)
class AcademicReadOnlyDeviceTest {
    @Test fun loginAndReadCoursesWithoutEnrollmentWrites() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val fixtureName = arguments.getString("academicSmokeFile")
        assumeTrue("No live credentials supplied", !fixtureName.isNullOrBlank())
        require(fixtureName == File(fixtureName!!).name) { "Expected a private cache file name" }
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configs = JSONArray(File(context.cacheDir, fixtureName).readText(Charsets.UTF_8))
        val config = (0 until configs.length()).map(configs::getJSONObject).first { it.getString("system") == "qz" }
        val school = SchoolConfig(config.getString("id"), config.optString("name"), config.getString("domain"), config.getString("protocol")).apply {
            basePath = config.getString("basePath")
            academicSystem = config.getString("system")
        }
        val username = config.getString("username")
        val key = AcademicGatewayFactory.accountKey(school, username)
        if (arguments.getString("academicDetect") == "true") {
            assertEquals(AcademicSystem.QZ, AcademicGatewayFactory.detect(school, key))
            println("Academic device auto-detection succeeded")
        }
        val adapter = AcademicGatewayFactory.create(school, key)
        val login = adapter.login(Credentials(username, config.getString("password")))
        assertEquals("Device login failed: ${login.status}", AcademicStatus.SUCCESS, login.status)
        assertTrue("No verified student ID", login.studentId.isNotBlank())
        val courseContext = adapter.loadCourseContext()
        val courses = adapter.listCourses(courseContext, CourseQuery(pageSize = 100))
        assertTrue("No course scopes", courseContext.scopes.isNotEmpty())
        assertTrue("No courses", courses.isNotEmpty())
        assertTrue("No sections", adapter.listSections(courses.first()).isNotEmpty())
        val selected = adapter.selected(courseContext)
        println("Academic device read-only chain: scopes=${courseContext.scopes.size}, courses=${courses.size}, selected=${selected.size}")
        val study = AcademicGatewayFactory.createStudy(school, key)
        val catalog = study.catalog()
        val schedule = study.schedule(catalog.currentTerm)
        val grades = study.grades()
        val exams = study.exams(catalog.currentTerm)
        assertTrue("School schedule should contain courses", schedule.isNotEmpty())
        assertTrue("School grades should contain records", grades.grades.isNotEmpty())
        assertTrue("Grades must include their semesters", grades.grades.all { it.term.isNotBlank() })
        assertTrue("Week ranges should be retained", schedule.all { it.weeks.isNotBlank() })
        assertTrue("Schedule weekdays must be valid", schedule.all { it.day in 1..7 })
        println("Academic device study chain: term=${catalog.currentTerm.id}, schedule=${schedule.size}, grades=${grades.grades.size}, semesters=${AcademicStudyBridge.semesters(grades.grades).size}, exams=${exams.size}")
        if (arguments.getString("academicPrepareUi") == "true") {
            val user = UserManager.getInstance().apply { init(context) }
            val existing = user.supportedSchools.firstOrNull { it.domain == school.domain && it.basePath == school.basePath }
            val registered = if (existing == null) school else SchoolConfig.fromJson(existing.toJson()).apply {
                academicSystem = school.academicSystem
            }
            val policy = StudentLimitManager.checkCanUseStudent(context, registered.id, login.studentName, login.studentId)
            assertTrue("Account limit prevents preparing the requested test account", policy.allowed)
            if (!policy.alreadyBound) assertTrue(StudentLimitManager.recordStudent(context, registered.id, registered.name, login.studentName, login.studentId))
            val cookie = (adapter as SessionBackedAdapter).cookieHeader()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                user.isLoggedIn = false
                if (existing == null) user.addCustomSchool(registered) else user.updateSchoolConfig(registered)
                user.currentSchool = registered
                user.studentId = login.studentId
                user.studentName = login.studentName
                user.isLoggedIn = true
                user.savePasswordLogin(username, cookie, config.getString("password"))
            }
            val storage = user.currentAccountStorageKey
            assertTrue("Password account must support normal session renewal", user.canAutoRelogin())
            assertEquals(courses.size, AcademicCourseBridge.listCourses(registered, storage).courses.size)
            assertEquals(selected.size, AcademicCourseBridge.selectedCourses(registered, storage).size)
            assertEquals(grades.grades.size, AcademicStudyBridge.reader(registered, storage).grades().grades.size)
            println("Password app account prepared and verified through UI bridges: qz")
        }
    }
}
