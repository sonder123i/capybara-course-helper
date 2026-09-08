package com.tyust.course.academic

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.login.PasswordLoginCallback
import com.tyust.course.login.PasswordLoginGatewayFactory
import com.tyust.course.model.SchoolConfig
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in password test. A human supplies each captcha; browser cookies are never imported. */
@RunWith(AndroidJUnit4::class)
class AcademicPasswordDeviceTest {
    @Test fun passwordLoginAndReadOnlyQueries() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val fixture = args.getString("academicPasswordFile")
        assumeTrue("No private password fixture supplied", !fixture.isNullOrBlank())
        require(fixture == File(fixture!!).name)
        val system = args.getString("academicSystem").orEmpty()
        require(system in setOf("qz", "qz_old", "zf_old"))
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configs = JSONArray(File(context.cacheDir, fixture).readText())
        val config = (0 until configs.length()).map(configs::getJSONObject).first { it.getString("system") == system }
        val school = SchoolConfig(config.getString("id"), config.optString("name"), config.getString("domain"), config.getString("protocol")).apply {
            basePath = config.getString("basePath")
            academicSystem = system
            pageCharset = config.optString("pageCharset", "UTF-8")
        }
        val username = config.getString("username")
        val key = AcademicGatewayFactory.accountKey(school, username)
        val gateway = PasswordLoginGatewayFactory.create(school)
        assertTrue("Must exercise the gateway used by the login screen", gateway is AcademicPasswordLoginGateway)
        val events = Channel<Event>(Channel.UNLIMITED)
        val callback = object : PasswordLoginCallback {
            override fun onSuccess(cookie: String) { events.trySend(Event("success")) }
            override fun onCaptchaRequired(imageBytes: ByteArray) { events.trySend(Event("captcha", imageBytes)) }
            override fun onCaptchaInvalid() { events.trySend(Event("invalid-captcha")) }
            override fun onInvalidCredentials() { events.trySend(Event("invalid-credentials")) }
            override fun onError(message: String) { events.trySend(Event("error", message = message)) }
            override fun onWebLoginRequired(message: String) { events.trySend(Event("web", message = message)) }
        }
        val prefix = "academic-password-$system"
        val challengeFile = File(context.cacheDir, "$prefix-challenge.json")
        val imageFile = File(context.cacheDir, "$prefix-captcha.png")
        val answerFile = File(context.cacheDir, "$prefix-answer.json")
        listOf(challengeFile, imageFile, answerFile).forEach { it.delete() }
        var revision = 0
        try {
            gateway.login(school, username, config.getString("password"), callback)
            withTimeout(10 * 60_000L) {
                var complete = false
                while (!complete) {
                    val event = events.receive()
                    when (event.kind) {
                        "success" -> complete = true
                        "captcha" -> {
                            val image = requireNotNull(event.image)
                            val bitmap = requireNotNull(BitmapFactory.decodeByteArray(image, 0, image.size)) { "Captcha cannot be displayed by Android" }
                            imageFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                            revision++
                            answerFile.delete()
                            challengeFile.writeText(JSONObject().put("system", system).put("revision", revision).put("image", imageFile.name).toString())
                            println("Password captcha ready: system=$system, revision=$revision")
                            var answer: JSONObject? = null
                            while (answer == null) {
                                delay(250)
                                val supplied = if (answerFile.exists()) runCatching { JSONObject(answerFile.readText()) }.getOrNull() else null
                                if (supplied?.optInt("revision") == revision) answer = supplied
                            }
                            answerFile.delete()
                            challengeFile.delete()
                            if (answer.optBoolean("refresh")) {
                                gateway.refreshCaptcha { bytes -> events.trySend(Event(if (bytes == null) "error" else "captcha", bytes, "Captcha refresh failed")) }
                            } else {
                                val code = answer.optString("code").trim()
                                require(code.isNotBlank()) { "A human captcha answer is required" }
                                gateway.submitCaptcha(code, callback)
                            }
                        }
                        "invalid-captcha" -> {
                            println("Password captcha rejected; requesting a fresh image: $system")
                            gateway.refreshCaptcha { bytes -> events.trySend(Event(if (bytes == null) "error" else "captcha", bytes, "Captcha refresh failed")) }
                        }
                        else -> fail("Password login did not finish: ${event.kind}; ${event.message}")
                    }
                }
            }
            gateway.clearSensitiveState()
            val adapter = AcademicGatewayFactory.create(school, key)
            val identity = adapter.validateSession()
            assertEquals("Fresh password session must authenticate", AcademicStatus.SUCCESS, identity.status)
            assertTrue(identity.studentId.isNotBlank())
            val courseContext = adapter.loadCourseContext()
            val courses = if (courseContext.scopes.isEmpty()) emptyList() else adapter.listCourses(courseContext, CourseQuery(pageSize = 100))
            val selected = adapter.selected(courseContext)
            val study = AcademicGatewayFactory.createStudy(school, key)
            val catalog = study.catalog()
            val schedule = study.schedule(catalog.currentTerm)
            val grades = study.grades()
            val exams = study.exams(catalog.currentTerm)
            assertTrue("Expected the authorized student's schedule", schedule.isNotEmpty())
            assertTrue("Expected the authorized student's grades", grades.grades.isNotEmpty())
            println("Password gateway and read-only queries passed: system=$system, captchaRounds=$revision, scopes=${courseContext.scopes.size}, courses=${courses.size}, selected=${selected.size}, schedule=${schedule.size}, grades=${grades.grades.size}, exams=${exams.size}")
        } finally {
            gateway.clearSensitiveState()
            events.close()
            listOf(challengeFile, imageFile, answerFile).forEach { it.delete() }
        }
    }

    private class Event(val kind: String, val image: ByteArray? = null, val message: String = "")
}
