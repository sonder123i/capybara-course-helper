package com.k2767.course.academic

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZhengfangCasEntryTest {
    private val service = "http://jwxt.example.edu.cn/sso/zfiotlogin".toHttpUrl()
    private val casLocation =
        "http://cas.example.edu.cn/cas/login?service=http%3A%2F%2Fjwxt.example.edu.cn%2Fsso%2Fzfiotlogin"

    @Test
    fun acceptsZhengfangCasRedirectAndBuildsClient() {
        val client = ZhengfangCasSsoClient.entryFromRedirect(casLocation, service, requireHttps = false)
        assertNotNull(client)
        assertEquals("http://cas.example.edu.cn/cas/login", client!!.casLoginUrl.toString())
        assertEquals("cas.example.edu.cn", client.casLoginUrl.host)
        assertEquals(service, client.teachingServiceUrl)
    }

    @Test
    fun keepsHttpsWhenRequired() {
        val httpsService = "https://jwxt.example.edu.cn/sso/zfiotlogin".toHttpUrl()
        val httpsLocation =
            "https://cas.example.edu.cn/cas/login?service=https%3A%2F%2Fjwxt.example.edu.cn%2Fsso%2Fzfiotlogin"
        assertNotNull(ZhengfangCasSsoClient.entryFromRedirect(httpsLocation, httpsService, requireHttps = true))
        // https 配置下拒绝 http CAS
        assertNull(ZhengfangCasSsoClient.entryFromRedirect(casLocation, httpsService, requireHttps = true))
    }

    @Test
    fun rejectsSameHostRedirect() {
        val location = "http://jwxt.example.edu.cn/cas/login?service=http%3A%2F%2Fjwxt.example.edu.cn%2Fsso%2Fzfiotlogin"
        assertNull(ZhengfangCasSsoClient.entryFromRedirect(location, service, requireHttps = false))
    }

    @Test
    fun rejectsMissingOrForeignService() {
        assertNull(ZhengfangCasSsoClient.entryFromRedirect("http://cas.example.edu.cn/cas/login", service, false))
        assertNull(
            ZhengfangCasSsoClient.entryFromRedirect(
                "http://cas.example.edu.cn/cas/login?service=http%3A%2F%2Fother.edu.cn%2Fsso%2Fzfiotlogin", service, false
            )
        )
    }

    @Test
    fun rejectsNonCasPathsAndSchemes() {
        assertNull(
            ZhengfangCasSsoClient.entryFromRedirect(
                "http://cas.example.edu.cn/other/login?service=http%3A%2F%2Fjwxt.example.edu.cn%2Fsso%2Fzfiotlogin", service, false
            )
        )
        assertNull(ZhengfangCasSsoClient.entryFromRedirect("javascript:alert(1)", service, false))
        assertNull(ZhengfangCasSsoClient.entryFromRedirect("", service, false))
    }
}
