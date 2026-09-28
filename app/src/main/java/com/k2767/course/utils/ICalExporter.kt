package com.k2767.course.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.k2767.course.schedule.ExamCalendarEvent
import com.k2767.course.schedule.ExamCalendarPlan
import com.k2767.course.schedule.examExportSummary
import com.k2767.course.ui.screen.ScheduleCourseUi
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * iCal (.ics) 课表导出工具
 * 将课表数据转换为标准 iCalendar 格式，可导入到系统日历
 */
object ICalExporter {
    
    // 节次对应的时间（根据学校作息时间调整）
    private val defaultPeriodTimes = mapOf(
        1 to Pair("08:00", "08:45"),
        2 to Pair("08:55", "09:40"),
        3 to Pair("10:00", "10:45"),
        4 to Pair("10:55", "11:40"),
        5 to Pair("14:00", "14:45"),
        6 to Pair("14:55", "15:40"),
        7 to Pair("16:00", "16:45"),
        8 to Pair("16:55", "17:40"),
        9 to Pair("19:00", "19:45"),
        10 to Pair("19:55", "20:40"),
        11 to Pair("20:50", "21:35"),
        12 to Pair("21:45", "22:30")
    )
    
    /**
     * 生成 iCal 文件内容
     * @param courses 课程列表
     * @param semesterStartDate 学期开始日期（周一）
     * @param totalWeeks 总周数
     * @return iCal 格式字符串
     */
    fun generateICalContent(
        courses: List<ScheduleCourseUi>,
        semesterStartDate: Calendar,
        totalWeeks: Int = 20,
        periodTimes: Map<Int, Pair<String, String>> = defaultPeriodTimes
    ): String {
        val sb = StringBuilder()
        
        // iCal 文件头
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Zhengfang Course Assistant//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:我的课表")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")
        
        // 时区定义
        sb.appendLine("BEGIN:VTIMEZONE")
        sb.appendLine("TZID:Asia/Shanghai")
        sb.appendLine("BEGIN:STANDARD")
        sb.appendLine("DTSTART:19700101T000000")
        sb.appendLine("TZOFFSETFROM:+0800")
        sb.appendLine("TZOFFSETTO:+0800")
        sb.appendLine("END:STANDARD")
        sb.appendLine("END:VTIMEZONE")
        
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { timeZone = semesterStartDate.timeZone }
        val now = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        
        courses.forEach { course ->
            // 解析周次
            val weekList = parseWeeks(course.weeks, totalWeeks)
            if (weekList.isEmpty()) return@forEach
            
            // 获取上课时间
            if (course.day !in 1..7 || course.startPeriod < 1 || course.endPeriod < course.startPeriod) return@forEach
            val startTime = periodTimes[course.startPeriod]?.first ?: return@forEach
            val endTime = periodTimes[course.endPeriod]?.second ?: return@forEach
            val validTime = Regex("(?:[01]\\d|2[0-3]):[0-5]\\d")
            if (!validTime.matches(startTime) || !validTime.matches(endTime)) return@forEach
            val startTimeStr = startTime.replace(":", "") + "00"
            val endTimeStr = endTime.replace(":", "") + "00"
            
            // 为每个上课周生成单独的事件（最大兼容性）
            weekList.forEach { week ->
                val eventDate = semesterStartDate.clone() as Calendar
                // 移动到对应周
                eventDate.add(Calendar.DAY_OF_YEAR, (week - 1) * 7)
                // The time base is Monday; Sunday is six days later, never the preceding day.
                eventDate.add(Calendar.DAY_OF_YEAR, course.day - 1)
                
                val eventDateStr = dateFormat.format(eventDate.time)
                
                // 生成 VEVENT
                sb.appendLine("BEGIN:VEVENT")
                sb.appendLine("UID:course-${com.k2767.course.schedule.ScheduleIdentity.digest(course.id)}-$eventDateStr@tyust.edu.cn")
                sb.appendLine("DTSTAMP:$now")
                sb.appendLine("DTSTART;TZID=Asia/Shanghai:${eventDateStr}T$startTimeStr")
                sb.appendLine("DTEND;TZID=Asia/Shanghai:${eventDateStr}T$endTimeStr")
                sb.appendLine("SUMMARY:${escapeText(course.name)}")
                if (course.location.isNotEmpty()) {
                    sb.appendLine("LOCATION:${escapeText(course.location)}")
                }
                val description = buildString {
                    if (course.teacher.isNotEmpty()) append("授课教师: ${course.teacher}")
                    append(if (isNotEmpty()) "\n" else "")
                    append("第${week}周")
                }
                sb.appendLine("DESCRIPTION:${escapeText(description)}")
                sb.appendLine("END:VEVENT")
            }
        }
        
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    private fun escapeText(value: String): String = value.replace("\\", "\\\\")
        .replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\\n").replace(",", "\\,").replace(";", "\\;")
    
    /**
     * 解析周次字符串
     * 支持格式: "1-16周", "1,3,5,7周", "1-8,10-16周", "1-16周(单)" 等
     */
    private fun parseWeeks(weeksStr: String, totalWeeks: Int): List<Int> {
        return com.k2767.course.schedule.ScheduleWeeks.parse(weeksStr).weeks.filter { it <= totalWeeks }.sorted()
    }
    
    /**
     * 检查是否为连续周次
     */
    private fun isConsecutiveWeeks(weeks: List<Int>): Boolean {
        if (weeks.size <= 1) return true
        for (i in 1 until weeks.size) {
            if (weeks[i] - weeks[i - 1] != 1) return false
        }
        return true
    }
    
    /**
     * 导出并分享 iCal 文件
     */
    fun exportAndShare(
        context: Context,
        courses: List<ScheduleCourseUi>,
        semesterStartDate: Calendar,
        totalWeeks: Int = 20,
        periodTimes: Map<Int, Pair<String, String>> = defaultPeriodTimes
    ) {
        try {
            val icsContent = generateICalContent(courses, semesterStartDate, totalWeeks, periodTimes)
            
            // 保存到临时文件
            val fileName = "课表_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.ics"
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { it.write(icsContent.toByteArray()) }
            
            // 使用 FileProvider 获取 URI
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            // 创建分享 Intent
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/calendar"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "导出课表到..."))
            
        } catch (e: Exception) {
            android.util.Log.e("ICalExporter", "导出失败: ${e.message}")
            throw e
        }
    }

    /**
     * 考试单独一份日历。
     *
     * 不跟课表合成一份是因为两者对开学锚点的依赖不同：课程缺锚点时会兜底成
     * 「9 月第一个周一」那种假日期，而考试的时间自带年份，压根不需要锚点。
     */
    fun generateExamCalendarContent(events: List<ExamCalendarEvent>): String {
        val sb = StringBuilder()
        sb.appendLine("BEGIN:VCALENDAR")
        sb.appendLine("VERSION:2.0")
        sb.appendLine("PRODID:-//Zhengfang Course Assistant//CN")
        sb.appendLine("CALSCALE:GREGORIAN")
        sb.appendLine("METHOD:PUBLISH")
        sb.appendLine("X-WR-CALNAME:考试安排")
        sb.appendLine("X-WR-TIMEZONE:Asia/Shanghai")
        sb.appendLine("BEGIN:VTIMEZONE")
        sb.appendLine("TZID:Asia/Shanghai")
        sb.appendLine("BEGIN:STANDARD")
        sb.appendLine("DTSTART:19700101T000000")
        sb.appendLine("TZOFFSETFROM:+0800")
        sb.appendLine("TZOFFSETTO:+0800")
        sb.appendLine("END:STANDARD")
        sb.appendLine("END:VTIMEZONE")

        val zone = TimeZone.getTimeZone("Asia/Shanghai")
        val dayFormat = SimpleDateFormat("yyyyMMdd", Locale.ROOT).apply { timeZone = zone }
        val momentFormat = SimpleDateFormat("'T'HHmmss", Locale.ROOT).apply { timeZone = zone }
        val stamp = SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())

        events.forEach { event ->
            val start = event.timing.startsAt
            sb.appendLine("BEGIN:VEVENT")
            sb.appendLine("UID:${event.uid}")
            sb.appendLine("DTSTAMP:$stamp")
            if (event.timing.allDay) {
                sb.appendLine("DTSTART;VALUE=DATE:${dayFormat.format(start.time)}")
            } else {
                // 没有 DTEND：教务没给结束时间，就不拿节次表反推时长。
                sb.appendLine("DTSTART;TZID=Asia/Shanghai:${dayFormat.format(start.time)}${momentFormat.format(start.time)}")
            }
            sb.appendLine("SUMMARY:${escapeText(event.title)}")
            if (event.location.isNotEmpty()) sb.appendLine("LOCATION:${escapeText(event.location)}")
            sb.appendLine("DESCRIPTION:${escapeText(event.description)}")
            if (!event.timing.allDay) {
                // 各家日历对导入文件里的 VALARM 实现不一，能弹是白捡，不作为提醒依据。
                sb.appendLine("BEGIN:VALARM")
                sb.appendLine("TRIGGER:-PT30M")
                sb.appendLine("ACTION:DISPLAY")
                sb.appendLine("DESCRIPTION:${escapeText(event.title)}")
                sb.appendLine("END:VALARM")
            }
            sb.appendLine("END:VEVENT")
        }
        sb.appendLine("END:VCALENDAR")
        return sb.toString()
    }

    /** 返回导出结果那句话，让调用方 toast 出来——降级和丢弃都必须被说出来。 */
    fun exportExamsAndShare(context: Context, plan: ExamCalendarPlan): String {
        if (plan.events.isNotEmpty()) {
            try {
                val fileName = "考试安排_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.ics"
                val file = File(context.cacheDir, fileName)
                FileOutputStream(file).use { it.write(generateExamCalendarContent(plan.events).toByteArray()) }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                    type = "text/calendar"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }, "导出考试安排到..."))
            } catch (e: Exception) {
                android.util.Log.e("ICalExporter", "考试导出失败: ${e.message}")
                throw e
            }
        }
        return examExportSummary(plan)
    }
}
