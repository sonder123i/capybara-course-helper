package com.tyust.course.academic

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.manager.StudentLimitManager
import com.tyust.course.manager.UserManager
import com.tyust.course.model.SchoolConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in smoke test using manually authenticated sessions from the private test cache. */
@RunWith(AndroidJUnit4::class)
class AcademicAuthenticatedDeviceTest {
    @Test fun readsLegacySchoolsAndRestoresCookieOnlyAppSessions() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val args = InstrumentationRegistry.getArguments()
        val filename = args.getString("academicCookieSmokeFile")
        assumeTrue("No authenticated test profiles supplied", !filename.isNullOrBlank())
        require(filename == File(filename!!).name)
        val context = instrumentation.targetContext
        val profiles = JSONArray(File(context.cacheDir, filename).readText(Charsets.UTF_8).removePrefix("\uFEFF"))
        val requestedSystem = args.getString("academicSystem").orEmpty()
        var verified = 0
        for (index in 0 until profiles.length()) {
            val profile = profiles.getJSONObject(index)
            val system = profile.getString("system")
            if (requestedSystem.isNotBlank() && requestedSystem != system) continue
            val school = SchoolConfig(profile.getString("id"), profile.getString("name"),
                profile.getString("domain"), profile.getString("protocol")).apply {
                academicSystem = system
                basePath = profile.getString("basePath")
            }
            val username = profile.getString("username")
            val cookie = profile.getString("cookie")
            val key = AcademicGatewayFactory.accountKey(school, "device_probe_$username")
            AcademicGatewayFactory.importCookie(school, key, cookie, username = username)
            val adapter = AcademicGatewayFactory.create(school, key)
            println("Academic live stage: $system identity")
            val login = adapter.validateSession()
            assertEquals("Session validation failed for $system", AcademicStatus.SUCCESS, login.status)
            assertTrue("No student identity for $system", login.studentId.isNotBlank())
            println("Academic live stage: $system course context")
            val courseContext = adapter.loadCourseContext()
            println("Academic live stage: $system course list")
            val courses = adapter.listCourses(courseContext, CourseQuery(pageSize = 100))
            if (courses.isNotEmpty()) assertTrue(adapter.listSections(courses.first()).isNotEmpty())
            println("Academic live stage: $system selected courses")
            val selected = adapter.selected(courseContext)
            val study = AcademicGatewayFactory.createStudy(school, key)
            println("Academic live stage: $system study data")
            val catalog = study.catalog()
            val schedule = study.schedule(catalog.currentTerm)
            val grades = study.grades().grades
            val exams = study.exams(catalog.currentTerm)
            assertTrue("Missing known schedule for $system", schedule.isNotEmpty())
            assertTrue("Missing known grade history for $system", grades.isNotEmpty())
            assertTrue("Missing grade semesters for $system", grades.all { it.term.isNotBlank() })
            assertTrue(schedule.all { it.day in 1..7 && it.startPeriod <= it.endPeriod && it.weeks.isNotBlank() })
            println("Device read-only $system: scopes=${courseContext.scopes.size}, courses=${courses.size}, selected=${selected.size}, schedule=${schedule.size}, grades=${grades.size}, exams=${exams.size}")
            verified++

            if (args.getString("academicPrepareUi") == "true") {
                val isolateAccount = args.getString("academicIsolateUi") == "true"
                val temporaryPreferences = mutableMapOf<String, SharedPreferences>()
                val accountContext = if (isolateAccount) object : ContextWrapper(context) {
                    override fun getApplicationContext(): Context = this
                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        temporaryPreferences.getOrPut(name) {
                            context.getSharedPreferences("academic_authenticated_fixture_${system}_$name", mode)
                                .also { it.edit().clear().commit() }
                        }
                } else context
                val user = UserManager.getInstance()
                try {
                    user.init(accountContext)
                    val existing = user.supportedSchools.firstOrNull { it.domain == school.domain && it.basePath == school.basePath }
                    val registered = if (existing == null) school else SchoolConfig.fromJson(existing.toJson()).apply {
                        academicSystem = system
                        protocol = school.protocol
                        name = school.name
                    }
                    val policy = StudentLimitManager.checkCanUseStudent(accountContext, registered.id, login.studentName, login.studentId)
                    assertTrue("Account limit prevents preparing the requested test account", policy.allowed)
                    if (!policy.alreadyBound) {
                        assertTrue(StudentLimitManager.recordStudent(accountContext, registered.id, registered.name, login.studentName, login.studentId))
                    }
                    instrumentation.runOnMainSync {
                        user.isLoggedIn = false
                        if (existing == null) user.addCustomSchool(registered) else user.updateSchoolConfig(registered)
                        user.currentSchool = registered
                        user.studentId = login.studentId
                        user.studentName = login.studentName.ifBlank { "测试账号" }
                        user.isLoggedIn = true
                        user.saveCookieLogin(cookie)
                    }
                    assertEquals("Cookie-only login must not require a saved password", "", user.username)
                    val account = user.currentAccountStorageKey
                    val appCourses = AcademicCourseBridge.listCourses(registered, account)
                    val appSelected = AcademicCourseBridge.selectedCourses(registered, account)
                    val appStudy = AcademicStudyBridge.reader(registered, account)
                    assertEquals(courses.size, appCourses.courses.size)
                    assertEquals(selected.size, appSelected.size)
                    assertEquals(grades.size, appStudy.grades().grades.size)
                    println("Cookie-only app session prepared and read through UI bridges: $system")
                } finally {
                    if (isolateAccount) {
                        temporaryPreferences.values.forEach { it.edit().clear().commit() }
                        instrumentation.runOnMainSync { user.init(context) }
                    }
                }
            }
        }
        assertTrue("No requested school profile was tested", verified > 0)
    }
}
