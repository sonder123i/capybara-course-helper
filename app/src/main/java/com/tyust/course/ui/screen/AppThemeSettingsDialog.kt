package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tyust.course.manager.AppThemeMode
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton

@Composable
fun AppThemeSettingsDialog(onDismiss: () -> Unit) {
    SystemDialog(onDismissRequest = onDismiss, title = { Text("主题") }) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("更改主题会保留当前背景设置。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppThemeMode.entries.forEach { mode ->
                val select = { AppearanceSettingsManager.updateThemeMode(mode) }
                if (AppearanceSettingsManager.themeMode == mode) {
                    SystemPrimaryButton(text = "${mode.label} · 已选择", onClick = select, modifier = Modifier.fillMaxWidth())
                } else {
                    SystemSecondaryButton(text = mode.label, onClick = select, modifier = Modifier.fillMaxWidth())
                }
            }
            SystemSecondaryButton(text = "完成", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
