package com.tyust.course.academic

import com.tyust.course.model.SchoolConfig
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object AcademicGatewayFactory {
    private val sessions = AcademicSessionStore()

    /** True when the school opts into the adapter flow, including auto-detection. */
    fun supports(school: SchoolConfig): Boolean {
        return school.academicSystem != AcademicSystem.LEGACY_ZF.id
    }

    suspend fun detect(school: SchoolConfig, accountStorageKey: String): AcademicSystem? {
        val originalPath = school.basePath
        val normalized = AcademicAddress.parse(school.fullBasePath)?.basePath ?: originalPath
        for (root in listOf(normalized, "", "/jsxsd", "/jwglxt").distinct()) {
            val probe = SchoolConfig.fromJson(school.toJson()).apply { basePath = root }
            val session = sessions.session(school.id, accountStorageKey + "_detect", probe.fullBasePath)
            val transport = AcademicHttpTransport(probe, session)
            val candidates = listOf("", "framework/xsMainV.htmlx", "xk/LoginToXk", "xtgl/login_slogin.html", "default2.aspx")
            for (candidate in candidates) {
                val response = try { transport.get(transport.appUrl(candidate)) }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: AcademicException) { continue }
                if (response.code !in 200..299) continue
                val detected = SystemDetector.classify(response.text) ?: continue
                school.basePath = root
                school.academicSystem = detected.id
                school.detectionSource = "automatic"
                if (!school.allowedAcademicHosts.contains(school.domain)) school.allowedAcademicHosts.add(school.domain)
                session.invalidate()
                return detected
            }
        }
        return null
    }

    fun create(school: SchoolConfig, accountStorageKey: String): AcademicProtocolAdapter {
        val system = school.academicType()
        require(system != AcademicSystem.LEGACY_ZF && system != AcademicSystem.AUTO) { "School has no selected academic adapter" }
        val session = sessions.session(school.id, accountStorageKey, school.getFullBasePath())
        val transport = AcademicHttpTransport(school, session)
        return when (system) {
            AcademicSystem.ZF -> ZfAcademicAdapter(school, session, transport)
            AcademicSystem.ZF_OLD -> ZfOldAcademicAdapter(school, session, transport)
            AcademicSystem.QZ -> QzAcademicAdapter(school, session, transport)
            AcademicSystem.QZ_OLD -> QzOldAcademicAdapter(school, session, transport)
            AcademicSystem.LEGACY_ZF, AcademicSystem.AUTO -> error("Academic adapter is not selected")
        }
    }

    fun createStudy(school: SchoolConfig, accountStorageKey: String): AcademicStudyAdapter {
        require(school.academicType() !in setOf(AcademicSystem.AUTO, AcademicSystem.LEGACY_ZF))
        val session = sessions.session(school.id, accountStorageKey, school.fullBasePath)
        return AcademicStudyReader(school, session, AcademicHttpTransport(school, session))
    }

    fun invalidate(school: SchoolConfig, accountStorageKey: String) {
        sessions.invalidate(school.id, accountStorageKey)
    }

    /** Import a Cookie header produced by the restricted WebView into the adapter jar. */
    fun importCookie(school: SchoolConfig, accountStorageKey: String, header: String, replace: Boolean = true, username: String = "") {
        val session = if (replace) sessions.replace(school.id, accountStorageKey, school.fullBasePath)
            else sessions.session(school.id, accountStorageKey, school.fullBasePath)
        if (username.isNotBlank()) session.username = username
        if (!replace && session.cookieHeader().isNotBlank()) return
        val url = (school.getFullBasePath().trimEnd('/') + "/").toHttpUrlOrNull() ?: return
        val parsed = header.split(';').mapNotNull { part ->
            val separator = part.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            runCatching { Cookie.Builder().name(part.substring(0, separator).trim())
                .value(part.substring(separator + 1).trim()).hostOnlyDomain(url.host).path(url.encodedPath)
                .apply { if (url.isHttps) secure() }.build() }.getOrNull()
        }
        session.cookies.saveFromResponse(url, parsed)
    }

    fun accountKey(school: SchoolConfig, username: String): String =
        (school.id + "::" + username.trim()).replace(Regex("[^A-Za-z0-9_.-]"), "_")

    fun loginUrl(school: SchoolConfig): String {
        val path = when (AcademicSystem.fromId(school.academicSystem)) {
            AcademicSystem.ZF -> "xtgl/login_slogin.html"
            AcademicSystem.ZF_OLD -> "default2.aspx"
            AcademicSystem.QZ -> "framework/xsMainV.htmlx"
            // LoginToXk is often a POST-only handler; the root serves the captcha form.
            AcademicSystem.QZ_OLD -> ""
            else -> ""
        }
        return school.fullBasePath.trimEnd('/') + "/" + path
    }

    fun detectAndApply(school: SchoolConfig, html: String): AcademicSystem? {
        val detected = SystemDetector.classify(html) ?: return null
        school.academicSystem = detected.id
        school.detectionSource = "automatic"
        if (!school.allowedAcademicHosts.contains(school.domain)) school.allowedAcademicHosts.add(school.domain)
        return detected
    }
}
