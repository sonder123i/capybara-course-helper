package com.k2767.course.usage

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import android.util.AtomicFile
import com.k2767.course.BuildConfig
import com.k2767.course.manager.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.util.Properties

internal class NoBackupUsageStore(context: Context, filename: String = "anonymous_usage.properties") : UsageStore {
    private val file = AtomicFile(File(context.noBackupFilesDir, filename))

    override fun read(): UsageRecord = runCatching {
        val values = Properties().apply { file.openRead().use(::load) }
        UsageRecord(
            preferences = UsagePreferences(values.getProperty("notice_seen") == "true", values.getProperty("enabled") != "false"),
            installationId = values.getProperty("installation_id")?.takeIf {
                it.matches(Regex("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"))
            },
            lastSuccessDay = values.getProperty("last_success_day")?.takeIf(UsageDay::isValid)
        )
    }.getOrDefault(UsageRecord())

    override fun write(record: UsageRecord) {
        val values = Properties().apply {
            setProperty("notice_seen", record.preferences.noticeSeen.toString())
            setProperty("enabled", record.preferences.enabled.toString())
            record.installationId?.let { setProperty("installation_id", it) }
            record.lastSuccessDay?.let { setProperty("last_success_day", it) }
        }
        val stream = file.startWrite()
        try {
            values.store(stream, null)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}

object UsageStatsManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var reporter: UsageReporter
    val preferences get() = reporter.preferences

    fun initialize(application: Application) {
        if (::reporter.isInitialized) return
        reporter = UsageReporter(
            store = NoBackupUsageStore(application), transport = HttpUsageTransport(),
            version = BuildConfig.VERSION_NAME,
            // 自用改造：是否允许上报匿名统计。判断放在这里（装配层）而不是
            // UsageReporter.canReport()，是为了让 UsageReporter 的通用逻辑及其
            // 单测保持原样——上游那 5 个 UsageReporterTest 不因自用开关而变红。
            eligibleBuild = com.k2767.course.SelfHostConfig.ENABLE_ANONYMOUS_USAGE_STATS &&
                !BuildConfig.DEBUG && !BuildConfig.UI_PREVIEW,
            isDemo = { UserManager.getInstance().isDemoMode }, scope = scope
        )
        val resumed = mutableSetOf<Activity>()
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                resumed.add(activity)
                reporter.setForeground(true)
            }
            override fun onActivityPaused(activity: Activity) {
                resumed.remove(activity)
                reporter.setForeground(resumed.isNotEmpty())
            }
            override fun onActivityDestroyed(activity: Activity) { resumed.remove(activity) }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
        })
    }

    fun acknowledgeNotice(enabled: Boolean) = reporter.acknowledgeNotice(enabled)
    fun setEnabled(enabled: Boolean) = reporter.setEnabled(enabled)

    @JvmStatic fun refreshEligibility() {
        if (::reporter.isInitialized) scope.launch { reporter.refreshEligibility() }
    }
}
