package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyust.course.BuildConfig
import com.tyust.course.manager.UserManager
import com.tyust.course.ui.system.InsetGroupedRow
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.usage.UsageStatsManager

@Composable
fun UsageNotice() {
    val preferences by UsageStatsManager.preferences.collectAsState()
    if (!preferences.noticeSeen && !BuildConfig.UI_PREVIEW && !UserManager.getInstance().isDemoMode) {
        UsageNoticeDialog(onContinue = UsageStatsManager::acknowledgeNotice)
    }
}

@Composable
internal fun UsageNoticeDialog(onContinue: (Boolean) -> Unit) {
    var enabled by rememberSaveable { mutableStateOf(true) }
    SystemDialog(
        title = { Text("匿名使用统计") },
        onDismissRequest = { onContinue(false) },
        confirmButton = { SystemPrimaryButton("继续", { onContinue(enabled) }, Modifier.fillMaxWidth().testTag("usage-notice-confirm")) },
        dismissButton = { SystemSecondaryButton("暂不开启", { onContinue(false) }, Modifier.fillMaxWidth()) }
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("帮助开发者了解有多少设备在使用教务助手。", style = MaterialTheme.typography.bodyLarge)
            Text("仅发送随机安装标识和应用版本，不包含学号、学校、账号、密码或课程内容。每天前台使用时最多成功上报一次。", style = MaterialTheme.typography.bodyMedium)
            InsetGroupedRow(
                title = "参与匿名统计", subtitle = "可以随时在设置中关闭", showDivider = false,
                trailing = { LiquidSwitch(checked = enabled, onCheckedChange = { enabled = it }) }
            )
        }
    }
}
