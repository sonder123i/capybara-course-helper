package com.k2767.course

import android.app.Application
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.manager.AppThemeCoordinator
import com.k2767.course.ui.system.GlassRuntimeGuard

class CourseApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        GlassRuntimeGuard.initialize(this)
        AppearanceSettingsManager.initialize(this)
        AppThemeCoordinator.initialize(this)
        val processName = if (android.os.Build.VERSION.SDK_INT >= 28) getProcessName() else {
            getSystemService(android.app.ActivityManager::class.java).runningAppProcesses
                ?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        if (processName == packageName) {
            com.k2767.course.schedule.ScheduleReminderScheduler.get(this).start(this)
            com.k2767.course.usage.UsageStatsManager.initialize(this)
            com.k2767.course.survey.SurveyVisitTracker.initialize(this)
        }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        AppThemeCoordinator.configurationChanged()
    }
}
