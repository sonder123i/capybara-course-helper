package com.tyust.course.academic

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 学号/姓名识别：覆盖正方 v5 定制首页变体（河北传媒学院实测形态）。
 * 样本已净化，不含任何真实学生信息。
 */
class ZfIdentityParsingTest {
    private fun parse(html: String) = parseName(Jsoup.parse(html, "https://jwxt.example.edu.cn/"))

    @Test
    fun cxyhxxVariantYieldsNameWithoutId() {
        val identity = parse(AcademicCoreTest.fixture("zf-cxyhxx-variant.html"))
        // 定制变体只有 media-heading（姓名+身份后缀），学号需走 initMenu 回退
        assertEquals("张三", identity.first)
        assertEquals("", identity.second)
    }

    @Test
    fun initMenuSessionUserKeyIsTheStudentId() {
        val identity = parse(AcademicCoreTest.fixture("zf-initmenu.html"))
        assertEquals("20250001", identity.second)
    }

    @Test
    fun standardZfIdentityStillParses() {
        val identity = parse("""<input name="xh" value="20250002"><input name="xm" value="李四">""")
        assertEquals("李四", identity.first)
        assertEquals("20250002", identity.second)
    }

    @Test
    fun nameNormalizesNbspAndRoleSuffixes() {
        assertEquals("王五", parse("""<h4 class="media-heading">王五&nbsp;&nbsp;学生</h4>""").first)
        assertEquals("赵六", parse("""<h4 class="media-heading">赵六&nbsp;&nbsp;教师</h4>""").first)
        assertEquals("孙七", parse("""<div class="user-name"> 孙七 同学 </div>""").first)
    }
}
