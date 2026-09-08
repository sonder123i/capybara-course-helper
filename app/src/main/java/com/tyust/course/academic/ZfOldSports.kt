package com.tyust.course.academic

import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** The sports page uses WebForms list boxes instead of the ordinary course table. */
internal object ZfOldSports {
    data class Entry(
        val id: String,
        val courseId: String,
        val name: String,
        val credit: String,
        val teacher: String,
        val time: String,
        val location: String,
        val capacity: Int? = null,
        val selected: Int? = null
    )

    fun list(document: Document, name: String): Element? = document.select("select[name]")
        .firstOrNull { it.attr("name").substringAfterLast('$') == name }

    fun entries(document: Document, selected: Boolean): List<Entry> {
        val list = list(document, if (selected) "ListBox3" else "ListBox2") ?: return emptyList()
        val headings = document.select("[id]").firstOrNull { it.id().substringAfterLast('_') == "Label4" }
            ?.text()?.substringAfter('：')?.substringAfter(':')?.split(Regex("[∥‖]"))?.map(String::trim).orEmpty()
        return list.select("option[value]").filter {
            it.attr("value").isNotBlank() && (selected || AcademicHtml.isEnabledControl(it))
        }.map { option ->
            val values = option.text().split(Regex("[∥‖]")).map(String::trim)
            if (values.size < 7) throw AcademicException(AcademicStatus.PAGE_CHANGED, "学校体育教学班信息格式已变化")
            val fields = headings.zip(values).toMap()
            val id = option.attr("value")
            val code = id.substringAfter(")-", "").substringBefore('-').ifBlank { id }
            fun value(label: String, fallback: Int): String = fields[label] ?: values.getOrElse(fallback) { "" }
            if (selected) Entry(id, code, values[0], values[1], values[2], values[5], values[6])
            else Entry(id, code, value("课程名称", 0), value("学分", 1), value("教师姓名", 2),
                value("上课时间", 4), value("上课地点", 5), fields["限选"]?.toIntOrNull(), fields["已选"]?.toIntOrNull())
        }
    }

    fun submitControl(document: Document, selected: Boolean): Element? {
        val list = list(document, if (selected) "ListBox3" else "ListBox2") ?: return null
        if (!AcademicHtml.isEnabledControl(list)) return null
        val form = list.closest("form") ?: return null
        val labels = if (selected) setOf("退选", "退课", "退选课程", "退课申请")
            else setOf("选定课程", "选课", "提交")
        return form.select("input[type=submit], button").firstOrNull {
            AcademicHtml.isEnabledControl(it) && it.attr("value").ifBlank { it.text() }.trim() in labels
        }
    }

    fun category(document: Document): String = list(document, "ListBox1")?.selectFirst("option[selected]")?.attr("value").orEmpty()

    fun categoryEvent(document: Document): Pair<String, String>? {
        val list = list(document, "ListBox1") ?: return null
        if (!AcademicHtml.isEnabledControl(list)) return null
        val script = list.attr("onchange").replace("\\'", "'").replace("\\\"", "\"")
        val event = Regex("""__doPostBack\(\s*['"]([^'"]+)['"]\s*,\s*['"]([^'"]*)['"]\s*\)""").find(script) ?: return null
        if (event.groupValues[1] != list.attr("name")) return null
        return event.groupValues[1] to event.groupValues[2]
    }
}
