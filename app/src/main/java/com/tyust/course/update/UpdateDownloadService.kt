package com.tyust.course.update

import android.app.*
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.app.NotificationCompat
import com.tyust.course.R
import com.tyust.course.ui.system.GlassWindowHost
import com.tyust.course.ui.theme.CourseSelectorTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.distinctUntilChangedBy

class UpdateDownloadService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val manager by lazy { UpdateManager.getInstance(this) }
    private val notifications get() = getSystemService(NotificationManager::class.java)
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel("app_updates", "应用更新", NotificationManager.IMPORTANCE_LOW))
    }
    private fun notification(state: UpdateManager.State): Notification {
        val open = PendingIntent.getActivity(this, 9801, Intent(this, UpdateDownloadActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, "app_updates").setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(if (state.phase == UpdateManager.Phase.READY) "更新已下载，点击安装" else "正在更新卡皮巴拉教务助手")
            .setContentText(state.message).setContentIntent(open).setOnlyAlertOnce(true).setOngoing(state.active)
        if (state.active) {
            builder.setProgress(100, state.progress, state.phase != UpdateManager.Phase.DOWNLOADING)
            val pause = PendingIntent.getService(this, 9802, Intent(this, UpdateDownloadService::class.java).setAction("pause"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(0, "暂停", pause)
        } else builder.setAutoCancel(true)
        return builder.build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(9801, notification(manager.state.value))
        if (intent?.action == "pause") { manager.pauseDownload(); return START_NOT_STICKY }
        manager.runDownload()
        scope.coroutineContext.cancelChildren()
        scope.launch {
            manager.state.distinctUntilChangedBy { Triple(it.phase, it.progress, it.message) }.collect { state ->
                runCatching { notifications.notify(9801, notification(state)) }
                if (!state.active) { stopForeground(STOP_FOREGROUND_DETACH); stopSelf() }
            }
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { scope.cancel(); manager.serviceStopped(); super.onDestroy() }
}

/** Notification entry: never starts installation or a download without a user action. */
class UpdateDownloadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = UpdateManager.getInstance(this)
        setContent { CourseSelectorTheme { GlassWindowHost {
            UpdateDialog(manager = manager, onDismiss = { manager.dismiss(); finish() })
        } } }
    }
}
