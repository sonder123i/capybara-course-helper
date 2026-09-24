package com.k2767.course.schedule

import android.content.SharedPreferences

/**
 * 课表的显示偏好。按账号各存一份：切了账号还沿用上一个账号的视图，会看到
 * 一个自己没设置过的课表页。
 *
 * `dayView` 默认 false（周视图）——上游默认落进日视图，我们这边周视图才是
 * 信息密度最高、用户最习惯的那一屏，这一条是刻意与上游不同的。
 */
data class ScheduleDisplayPreferences(val dayView: Boolean = false, val showWeekend: Boolean = true)

class ScheduleDisplayStore(private val prefs: SharedPreferences) {
    fun read(account: String): ScheduleDisplayPreferences {
        if (account.isBlank()) return ScheduleDisplayPreferences()
        return ScheduleDisplayPreferences(
            dayView = prefs.getBoolean(dayKey(account), false),
            showWeekend = prefs.getBoolean(weekendKey(account), true)
        )
    }

    /** 账号为空（未登录且无本地归属）时不写：那类写入会留下没有主人、也读不回来的键。 */
    fun write(account: String, value: ScheduleDisplayPreferences) {
        if (account.isBlank()) return
        prefs.edit().putBoolean(dayKey(account), value.dayView)
            .putBoolean(weekendKey(account), value.showWeekend).apply()
    }

    private fun dayKey(account: String) = "dayView:$account"
    private fun weekendKey(account: String) = "showWeekend:$account"
}
