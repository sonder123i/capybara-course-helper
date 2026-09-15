package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyust.course.manager.StartupPage
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPicker
import com.tyust.course.ui.system.SystemPrimaryButton

@Composable
fun StartupPageSettingsDialog(page: StartupPage, onPageChange: (StartupPage) -> Unit, onDismiss: () -> Unit) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = { Text("启动首屏") },
        confirmButton = { SystemPrimaryButton("完成", onDismiss, Modifier.fillMaxWidth()) }
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("下次启动应用时，直接进入选择的页面。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            SystemPicker(
                options = StartupPage.entries.map { it.label },
                selectedIndex = StartupPage.entries.indexOf(page),
                onSelect = { onPageChange(StartupPage.entries[it]) },
                label = "首屏页面",
                modifier = Modifier.fillMaxWidth().testTag("startup-page-picker")
            )
        }
    }
}
