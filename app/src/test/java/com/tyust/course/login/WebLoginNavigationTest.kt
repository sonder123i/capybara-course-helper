package com.tyust.course.login

import com.tyust.course.academic.AcademicUrlPolicy
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class WebLoginNavigationTest {
    @Test fun addressBarSupportsFullAddressesBareDomainsAndSchoolSearch() {
        assertEquals("http://jwxt.hut.edu.cn/jsxsd/", WebLoginNavigation.resolveInput(" http://jwxt.hut.edu.cn/jsxsd/ "))
        assertEquals("https://authserver.ntu.edu.cn/authserver/login", WebLoginNavigation.resolveInput("authserver.ntu.edu.cn/authserver/login"))
        assertEquals("https://jw.example.edu.cn:8443/jsxsd", WebLoginNavigation.resolveInput("jw.example.edu.cn:8443/jsxsd"))
        val query = "湖南工业大学 教务系统 & 登录"
        assertEquals(query, WebLoginNavigation.resolveInput(query)!!.toHttpUrl().queryParameter("q"))
    }

    @Test fun interactiveSsoAndSearchCanCrossDomainsWithoutRelaxingProtocolRequests() {
        val host = listOf("jw.ntu.edu.cn")
        for (url in listOf("https://authserver.ntu.edu.cn/authserver/login", "https://www.bing.com/search?q=ntu", "http://jwxt.hut.edu.cn/jsxsd/")) {
            assertTrue(WebLoginNavigation.isWebUrl(url))
            assertFalse(AcademicUrlPolicy.isAllowed(url, "https", host))
        }
    }

    @Test fun pastedExecutableFileAndCredentialUrlsAreRejected() {
        for (value in listOf("javascript:alert(1)", "file:///sdcard/private", "content://contacts/1", "intent://login", "data:text/html,test", "https://user:secret@jw.example.edu.cn/", "https://")) {
            assertNull(value, WebLoginNavigation.resolveInput(value))
            assertFalse(value, WebLoginNavigation.isWebUrl(value))
        }
        assertNull(WebLoginNavigation.resolveInput("  "))
    }
}
