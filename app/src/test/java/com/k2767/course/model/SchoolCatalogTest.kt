package com.k2767.course.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchoolCatalogTest {

    private fun entry(
        name: String = "南昌工学院",
        url: String = "http://jwxt.ncpu.edu.cn:8088/jwglxt/xtgl/login_slogin.html",
        sys: String = "zf",
        kind: String = "zhengfang_new",
        pinyin: String = "nanchanggongxueyuan",
        initials: String = "ncgxy",
        initial: String = "N"
    ) = CatalogSchool("cat_1", name, url, sys, kind, pinyin, initials, initial)

    @Test fun loginPageUrlTurnsIntoDomainAndBasePath() {
        val school = entry().toSchoolConfig()!!
        assertEquals("jwxt.ncpu.edu.cn:8088", school.domain)
        assertEquals("http", school.protocol)
        assertEquals("/jwglxt", school.basePath)
        assertEquals("auto", school.academicSystem)
        assertEquals("pending", school.detectionSource)
        assertTrue(school.allowedAcademicHosts.contains("jwxt.ncpu.edu.cn:8088"))
    }

    @Test fun directoryUrlKeepsWhateverPathItHas() {
        assertEquals("", entry(url = "https://jw.example.edu.cn/").toSchoolConfig()!!.basePath)
        assertEquals("/jsxsd", entry(url = "http://jwxt.ahut.edu.cn/jsxsd/").toSchoolConfig()!!.basePath)
        assertEquals("/jwglxt", entry(url = "http://www.gdjw.zjut.edu.cn/jwglxt/").toSchoolConfig()!!.basePath)
    }

    @Test fun pageUrlDropsThePageSegmentAndQuery() {
        val school = entry(
            name = "江西科技师范大学",
            url = "https://cas-jxstnu-edu-cn-s.atrust.jxstnu.edu.cn/lyuapServer/login?service=https%3A%2F%2Fsdpc.jxstnu.edu.cn"
        ).toSchoolConfig()!!
        assertEquals("cas-jxstnu-edu-cn-s.atrust.jxstnu.edu.cn", school.domain)
        assertEquals("/lyuapServer/login", school.basePath)
        assertEquals("https://cas-jxstnu-edu-cn-s.atrust.jxstnu.edu.cn/lyuapServer/login", school.fullBasePath)

        val sso = entry(url = "https://jw.hualixy.edu.cn/sso/ddlogin").toSchoolConfig()!!
        assertEquals("/sso/ddlogin", sso.basePath)
    }

    @Test fun unusableAddressesAreRejected() {
        assertNull(entry(url = "ftp://jw.example.edu.cn/").toSchoolConfig())
        assertNull(entry(url = "not a url").toSchoolConfig())
    }

    @Test fun searchMatchesNamePinyinAndInitials() {
        val school = entry()
        assertTrue(school.matches(""))
        assertTrue(school.matches("南昌"))
        assertTrue(school.matches("nanchang"))
        assertTrue(school.matches("ncgxy"))
        assertFalse(school.matches("beijing"))
        assertFalse(school.matches("bj"))
    }

    @Test fun adapterReadinessFollowsMappedSystem() {
        assertTrue(entry(sys = "zf").adapterReady)
        assertTrue(entry(sys = "qz").adapterReady)
        assertFalse(entry(sys = "", kind = "chaoxing").adapterReady)
        assertEquals("超星", entry(sys = "", kind = "chaoxing").typeLabel)
        assertEquals("正方", entry().typeLabel)
    }

    @Test fun catalogJsonParsesAndSkipsBrokenRows() {
        val json = """
            [
              {"id":"cat_a","name":"安徽大学","url":"https://jw.ahu.edu.cn/","sys":"","kind":"eams5","py":"anhuidaxue","ini":"ahdx","ab":"A"},
              {"id":"cat_b","name":"","url":"https://x.example.edu.cn/","sys":"zf","kind":"zhengfang_new","py":"","ini":"","ab":"X"}
            ]
        """.trimIndent()
        val parsed = SchoolCatalog.parse(json)
        assertEquals(1, parsed.size)
        assertEquals("安徽大学", parsed[0].name)
        assertEquals("A", parsed[0].initial)
    }
}