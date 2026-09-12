package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyust.course.academic.AcademicCapabilities
import com.tyust.course.academic.AcademicSystem
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemDivider
import com.tyust.course.ui.system.SystemPrimaryButton

@Composable
fun AcademicSupportDialog(currentSystem: String?, onDismiss: () -> Unit) {
    val currentSupport = AcademicCapabilities.support(currentSystem)
    SystemDialog(onDismissRequest = onDismiss, title = { Text(currentSupport?.let { it.name + "支持与限制" } ?: "四类教务支持与限制") },
        confirmButton = { SystemPrimaryButton(text = "关闭", onClick = onDismiss, modifier = Modifier.fillMaxWidth()) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("${AcademicCapabilities.FOUR_SYSTEMS}。选课、退课、批量选课、目标监控、队列和定时任务共用应用入口；课表、成绩、考试及导出按学校实际返回的数据展示。",
                style = MaterialTheme.typography.bodyMedium)
            Text(AcademicCapabilities.ACCOUNT_LIMIT, style = MaterialTheme.typography.bodySmall)
            (currentSupport?.let(::listOf) ?: AcademicCapabilities.systems).forEach { support ->
                SystemDivider()
                Text(support.name + if (support.system == AcademicCapabilities.system(currentSystem)) " · 当前学校" else "",
                    style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(support.login, style = MaterialTheme.typography.bodySmall)
                Text(support.limits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            SystemDivider()
            Text(AcademicCapabilities.SCHOOL_LIMIT, style = MaterialTheme.typography.bodySmall)
            Text("学校要求验证码、选课声明或二次确认时需人工完成。账密保存在本机时可尝试续期；需要验证码的会话不能静默续期。定时任务需要通知和精确闹钟权限，启动时账号仍需可用。",
                style = MaterialTheme.typography.bodySmall)
            Text("容量、成绩分项和历史学期以学校公布内容为准。定制页面尚未适配与学校未开放权限会分别提示。",
                style = MaterialTheme.typography.bodySmall)
            if (currentSupport == null || currentSupport.system == AcademicSystem.ZF_OLD) {
                Text("旧正方没有历年汇总入口时，逐学期查询学校开放的成绩；未能完整读取时会提示错误。",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
