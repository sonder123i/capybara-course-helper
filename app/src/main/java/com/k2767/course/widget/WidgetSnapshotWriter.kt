package com.k2767.course.widget

import android.content.Context
import com.k2767.course.schedule.ScheduleCourseRecord
import com.k2767.course.schedule.ScheduleTimeBase
import org.json.JSONArray
import org.json.JSONObject

/** 快照里的一节课时间（与 UI 层的 PeriodTimeUi 解耦，避免这里依赖界面类型）。 */
data class WidgetPeriodTime(val period: Int, val start: String, val end: String)

/**
 * 把课表数据写成桌面「今日课程」小组件要读的快照。
 *
 * 只写入原始数据（学校、开学首周、节次时间、全部课程），"今天上什么"由小组件自己算——
 * 这样即使 App 几天没被打开，系统定时刷新也能让日期翻页、周次推进自动跟上。
 *
 * 字段约定见 [CourseWidgetData] 的常量，两边必须一致。
 */
object WidgetSnapshotWriter {
    suspend fun write(
        context: Context,
        schoolName: String,
        timeBase: ScheduleTimeBase?,
        periods: List<WidgetPeriodTime>,
        courses: List<ScheduleCourseRecord>
    ) {
        val periodArray = JSONArray()
        periods.forEach { time ->
            periodArray.put(
                JSONObject()
                    .put(CourseWidgetData.KEY_PERIOD_INDEX, time.period)
                    .put(CourseWidgetData.KEY_PERIOD_START, time.start)
                    .put(CourseWidgetData.KEY_PERIOD_END, time.end)
            )
        }
        val courseArray = JSONArray()
        courses.forEach { course ->
            courseArray.put(
                JSONObject()
                    .put(CourseWidgetData.KEY_COURSE_ID, course.id)
                    .put(CourseWidgetData.KEY_COURSE_NAME, course.name)
                    .put(CourseWidgetData.KEY_COURSE_TEACHER, course.teacher)
                    .put(CourseWidgetData.KEY_COURSE_LOCATION, course.location)
                    .put(CourseWidgetData.KEY_COURSE_DAY, course.day)
                    .put(CourseWidgetData.KEY_COURSE_START, course.startPeriod)
                    .put(CourseWidgetData.KEY_COURSE_END, course.endPeriod)
                    .put(CourseWidgetData.KEY_COURSE_WEEKS, course.weeks)
            )
        }
        val snapshot = JSONObject()
            .put(CourseWidgetData.KEY_SCHOOL, schoolName)
            .put(CourseWidgetData.KEY_FIRST_WEEK_DATE, timeBase?.firstWeekDate.orEmpty())
            .put(CourseWidgetData.KEY_PERIODS, periodArray)
            .put(CourseWidgetData.KEY_COURSES, courseArray)

        CourseWidgetData.write(context, snapshot)
        CourseWidgetData.requestUpdate(context)
    }
}