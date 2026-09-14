package com.tyust.course.academic

import com.tyust.course.login.ZjutSsoProtocol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ZhengfangCasProtocolTest {
    private val fixture: String get() = AcademicCoreTest.fixture("zf-cas-login.html")

    @Test
    fun parseLoginPage_readsExecutionAndFormAction() {
        val page = ZhengfangCasProtocol.parseLoginPage(fixture)
        assertEquals("test-execution-token-0123456789abcdef", page.execution)
        assertEquals("login?v=0.123456789", page.formAction)
    }

    @Test
    fun parseLoginPage_rejectsMissingExecution() {
        assertThrows(ZhengfangCasProtocol.ProtocolException::class.java) {
            ZhengfangCasProtocol.parseLoginPage("""<form id="fm1"><input name="username"><input name="_eventId" value="submit"/></form>""")
        }
    }

    @Test
    fun parseLoginPage_toleratesMissingFormAction() {
        val page = ZhengfangCasProtocol.parseLoginPage(
            """<form id="fm1"><input name="execution" value="e1"/><input name="_eventId" value="submit"/></form>"""
        )
        assertEquals("e1", page.execution)
        assertNull(page.formAction)
    }

    @Test
    fun parsePublicKey_readsModulusAndExponent() {
        val key = ZhengfangCasProtocol.parsePublicKey("""{"modulus":"abc123","exponent":"10001"}""")
        assertEquals("abc123", key.modulusHex)
        assertEquals("10001", key.exponentHex)
    }

    @Test
    fun parsePublicKey_rejectsInvalidJson() {
        assertThrows(ZhengfangCasProtocol.ProtocolException::class.java) {
            ZhengfangCasProtocol.parsePublicKey("not-json")
        }
    }

    @Test
    fun parseKaptchaStatus_matchesZhengfangSemantics() {
        assertTrue(ZhengfangCasProtocol.parseKaptchaStatus("true"))
        assertTrue(ZhengfangCasProtocol.parseKaptchaStatus("  TRUE \n"))
        assertFalse(ZhengfangCasProtocol.parseKaptchaStatus("false"))
        assertFalse(ZhengfangCasProtocol.parseKaptchaStatus(""))
    }

    @Test
    fun encryptPassword_isIdenticalToZjutImplementation() {
        // 同族正方 CAS：加密必须与既有 Zjut 实现逐字节一致（共享算法的对拍锚点）
        val modulus = "b1d2af160ebaa47adfef6e4f98a6f1045bd6dc5bfba4bf427c9a653307424d955bf9bbe79a6f5444b7bb04dc8fd4d4c36063d67fbab5c6732b6a2ed6bbf6f45d"
        val exponent = "10001"
        for (sample in listOf("Test1234", "a", "ab", "P@ss/w0rd-9Xz", "密码pass1234")) {
            assertEquals(
                ZjutSsoProtocol.encryptPassword(sample, modulus, exponent),
                ZhengfangCasProtocol.encryptPassword(sample, ZhengfangCasProtocol.PublicKey(modulus, exponent))
            )
        }
    }

    @Test
    fun encryptPassword_wrapsCryptoFailureAsProtocolException() {
        assertThrows(ZhengfangCasProtocol.ProtocolException::class.java) {
            ZhengfangCasProtocol.encryptPassword("x", ZhengfangCasProtocol.PublicKey("zz", "10001"))
        }
    }

    @Test
    fun errorClassification_distinguishesFailures() {
        assertTrue(ZhengfangCasProtocol.indicatesInvalidCredentials("用户名或密码不正确"))
        assertTrue(ZhengfangCasProtocol.indicatesInvalidCredentials("密码错误，请重试"))
        assertFalse(ZhengfangCasProtocol.indicatesInvalidCredentials("验证码错误"))

        assertTrue(ZhengfangCasProtocol.indicatesCaptchaProblem("验证码不正确"))
        assertTrue(ZhengfangCasProtocol.indicatesCaptchaProblem("Invalid Captcha"))
        assertFalse(ZhengfangCasProtocol.indicatesCaptchaProblem("用户名或密码不正确"))

        val html = """<span id="errormsg">用户名或密码不正确</span>"""
        assertEquals("用户名或密码不正确", ZhengfangCasProtocol.errorMessage(html))
        assertEquals("", ZhengfangCasProtocol.errorMessage("<p>其他内容</p>"))
    }
}
