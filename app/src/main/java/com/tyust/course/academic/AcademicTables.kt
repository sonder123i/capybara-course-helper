package com.tyust.course.academic

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

internal object AcademicTables {
    data class Row(val element: Element, val values: Map<String, String>) {
        fun value(vararg names: String): String = names.asSequence().mapNotNull { values[it] }.firstOrNull { it.isNotBlank() }.orEmpty()
    }

    fun rows(html: String, baseUrl: String): List<Row> {
        val document = Jsoup.parse(html, baseUrl)
        if (AcademicHtml.isLoginPage(html)) throw AcademicException(AcademicStatus.SESSION_EXPIRED, "登录已失效，请重新登录")
        return document.select("table").flatMap { table ->
            val rows = table.select("tr").filter { it.closest("table") === table }
            val header = rows.firstOrNull { row -> row.select("th,td").any { it.text().trim() in setOf("课程名称", "课程名", "课程代码", "课程编号") } }
                ?: return@flatMap emptyList()
            val headings = header.select("th,td").map { it.text().trim().replace(" ", "") }
            rows.dropWhile { it !== header }.drop(1).mapNotNull { row ->
                val cells = row.select("td").filter { it.closest("tr") === row }
                if (cells.size < headings.size) return@mapNotNull null
                val values = headings.zip(cells.map(::cellText)).toMap()
                if (values["课程名称"].orEmpty().ifBlank { values["课程名"].orEmpty() }.isBlank()) null else Row(row, values)
            }
        }
    }

    private fun cellText(cell: Element): String {
        val visible = cell.text().trim()
        val title = cell.attr("title").trim().ifBlank {
            cell.selectFirst("[title]")?.attr("title")?.trim().orEmpty()
        }
        val truncated = visible.endsWith("...") || visible.endsWith("…")
        val prefix = visible.trimEnd('.', '…').trimEnd()
        return if ((visible.isBlank() || truncated) && title.length > prefix.length && title.startsWith(prefix)) title else visible
    }
}
