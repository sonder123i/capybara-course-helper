package com.k2767.course.ui.screen

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
import com.k2767.course.BuildConfig
import com.k2767.course.SelfHostConfig
import com.k2767.course.manager.UserManager
import com.k2767.course.ui.system.InsetGroupedRow
import com.k2767.course.ui.system.LiquidSwitch
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.ui.system.SystemPrimaryButton
import com.k2767.course.ui.system.SystemSecondaryButton
import com.k2767.course.usage.UsageStatsManager

@Composable
fun UsageNotice() {
    val preferences by UsageStatsManager.preferences.collectAsState()
    // 自用改造：统计后端未部署，不再弹知情提示（开关见 SelfHostConfig）。
    // 弹窗本身与 acknowledgeNotice 链路一字未删，将来改回开关即可恢复。
    if (SelfHostConfig.ENABLE_USAGE_STATS_UI &&
        !preferences.noticeSeen && !BuildConfig.UI_PREVIEW && !UserManager.getInstance().isDemoMode
    ) {
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
            Text("帮助开发者了解有多少设备在使用卡皮巴拉教务助手。", style = MaterialTheme.typography.bodyLarge)
            Text("每日活跃统计仅发送随机安装标识和应用版本，每天前台使用时最多成功上报一次。", style = MaterialTheme.typography.bodyMedium)
            Text("点击“开始填写”时，会发送问卷编号、一次性随机请求编号及用于校验投放范围的教务域名。服务端不保存域名，仅保留匿名点击汇总与去重标记，不接收问卷答案、学号、账号、密码或课程内容。入口点击不代表真实提交。", style = MaterialTheme.typography.bodyMedium)
            InsetGroupedRow(
                title = "参与匿名统计", subtitle = "可以随时在设置中关闭", showDivider = false,
                trailing = { LiquidSwitch(checked = enabled, onCheckedChange = { enabled = it }) }
            )
        }
    }
}
