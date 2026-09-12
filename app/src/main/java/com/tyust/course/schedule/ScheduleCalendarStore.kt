package com.tyust.course.schedule

import android.content.SharedPreferences
import org.json.JSONObject

internal class ScheduleCalendarStore(private val preferences: SharedPreferences) {
    fun read(account: String, term: String): ScheduleTimeBase? = preferences.getString("calendar:$account|$term", null)?.let {
        runCatching { ReminderJson.timeBase(JSONObject(it)) }.getOrNull()
    }

    fun write(account: String, term: String, value: ScheduleTimeBase): Boolean {
        if (account.isBlank() || term.isBlank() || read(account, term) == value) return false
        preferences.edit().putString("calendar:$account|$term", ReminderJson.timeBase(value).toString()).apply()
        return true
    }

    /** The caller supplies the actual current term, never the term the user is browsing. */
    fun migrateLegacy(account: String, currentTerm: String, value: ScheduleTimeBase): Boolean {
        val marker = "legacy-calendar-migrated:$account"
        if (account.isBlank() || currentTerm.isBlank() || preferences.contains(marker)) return false
        preferences.edit().putString(marker, currentTerm).apply()
        return value.firstWeekDate.isNotBlank() && read(account, currentTerm) == null && write(account, currentTerm, value)
    }
}
