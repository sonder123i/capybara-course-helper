package com.tyust.course.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyust.course.schedule.ClassQuietManager
import com.tyust.course.ui.system.GlassToaster
import com.tyust.course.ui.system.InsetGroupedRow
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton

@Composable
fun ClassQuietSettingsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val manager = remember(context) { ClassQuietManager.get(context) }
    val status = manager.status
    LaunchedEffect(manager) { manager.reconcile() }
    fun openSettings(intent: Intent, fallback: Intent) {
        if (runCatching { context.startActivity(intent) }.isFailure &&
            runCatching { context.startActivity(fallback) }.isFailure) {
            GlassToaster.show("请在系统设置中为卡皮巴拉教务助手开启相应权限")
        }
    }
    SystemDialog(onDismissRequest = onDismiss, title = { Text("上课自动静音") },
        confirmButton = { SystemPrimaryButton("完成", onDismiss, Modifier.fillMaxWidth()) }) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InsetGroupedRow(title = "上课自动静音", subtitle = status.summary, showDivider = false,
                trailing = { LiquidSwitch(status.enabled, manager::setEnabled,
                    Modifier.testTag("class-quiet-toggle")) })
            Text("按当前账号本学期的课表，在上课时静音来电和通知，下课后恢复上课前的铃声、振动或静音模式。支持自定义课程、单双周及调停课。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("闹钟和媒体音量保持原设置。重叠课程结束后再恢复；课中手动调整声音或勿扰模式时，本次课程以你的设置为准。",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if ((status.enabled || status.restorePending) && !status.policyAccess) {
                Text("需要允许勿扰控制，才能切换静音并恢复声音。")
                SystemSecondaryButton("允许勿扰控制", {
                    openSettings(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS), Intent(Settings.ACTION_SETTINGS))
                }, Modifier.fillMaxWidth())
            }
            if (status.enabled && !status.exactAlarms && Build.VERSION.SDK_INT >= 31) {
                Text("需要允许闹钟和提醒，才能按上下课时间自动切换。")
                SystemSecondaryButton("允许闹钟和提醒", {
                    openSettings(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:${context.packageName}")),
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:${context.packageName}")))
                }, Modifier.fillMaxWidth())
            }
            if (status.enabled) Text("请在系统电池管理中允许应用后台运行。系统强行停止应用后，自动切换会暂停；重新打开应用可恢复调度。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
