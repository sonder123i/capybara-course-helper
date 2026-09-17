package com.k2767.course.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import org.json.JSONObject

/**
 * App 与桌面小组件之间的数据通道。
 *
 * App 侧在课表数据变化时把一份快照写进这里的 SharedPreferences（[write]），
 * 小组件每次刷新时读出来（[read]）并自行计算「今天有哪些课」——所以即使 App
 * 几天没打开、或者已是第二天，小组件也能靠系统定时广播重新算对。
 *
 * 两边只共享这份 JSON 约定（键名见下方常量），互不依赖对方的类。
 */
object CourseWidgetData {
    const val PREFS_NAME = "course_widget"
    const val KEY_SNAPSHOT = "snapshot"

    // 快照根字段
    const val KEY_SCHOOL = "school"
    const val KEY_FIRST_WEEK_DATE = "firstWeekDate"
    const val KEY_PERIODS = "periods"
    const val KEY_COURSES = "courses"

    // 节次时间：p=第几节，s=开始 HH:mm，e=结束 HH:mm
    const val KEY_PERIOD_INDEX = "p"
    const val KEY_PERIOD_START = "s"
    const val KEY_PERIOD_END = "e"

    // 课程：day=星期(1=周一)，start/end=起止节次，weeks=周次表达式
    const val KEY_COURSE_ID = "id"
    const val KEY_COURSE_NAME = "name"
    const val KEY_COURSE_TEACHER = "teacher"
    const val KEY_COURSE_LOCATION = "location"
    const val KEY_COURSE_DAY = "day"
    const val KEY_COURSE_START = "start"
    const val KEY_COURSE_END = "end"
    const val KEY_COURSE_WEEKS = "weeks"

    fun write(context: Context, snapshot: JSONObject) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SNAPSHOT, snapshot.toString())
            .apply()
    }

    fun read(context: Context): WidgetSnapshot? {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SNAPSHOT, null) ?: return null
        return WidgetSnapshot.parse(raw)
    }

    /** 让所有已添加的小组件重新渲染。App 侧写完快照后调用。 */
    suspend fun requestUpdate(context: Context) {
        CourseWidget().updateAll(context)
    }
}