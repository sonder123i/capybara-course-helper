package com.k2767.course.manager

import android.content.SharedPreferences

enum class AppThemeMode(val storageValue: String, val label: String) {
    System("system", "跟随系统"), Light("light", "浅色"), Dark("dark", "暗色");

    companion object {
        fun decode(value: String?): AppThemeMode = entries.firstOrNull { it.storageValue == value } ?: System
    }
}

internal class AppThemePreferences(private val preferences: SharedPreferences) {
    fun read(): AppThemeMode = AppThemeMode.decode(runCatching { preferences.getString("theme_mode", null) }.getOrNull())
    fun write(mode: AppThemeMode) { preferences.edit().putString("theme_mode", mode.storageValue).apply() }
}

fun resolveDarkTheme(mode: AppThemeMode, systemDark: Boolean): Boolean = when (mode) {
    AppThemeMode.System -> systemDark
    AppThemeMode.Light -> false
    AppThemeMode.Dark -> true
}
