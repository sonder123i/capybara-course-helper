package com.k2767.course.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextAlign

@Composable
fun SessionExpiryPrompt(hasCachedContent: Boolean, onLater: () -> Unit, onLogin: () -> Unit) {
    SystemDialog(
        onDismissRequest = onLater,
        title = { Text("需要重新登录", fontSize = 19.sp, lineHeight = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold) },
        dismissButton = { TextButton(onClick = onLater, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("稍后", fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } },
        confirmButton = { TextButton(onClick = onLogin, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text("重新登录", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
        } }
    ) {
        Column(Modifier.testTag("session-expiry-prompt"), verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
            Text("登录状态已过期，重新登录后可继续查询和选课。", fontSize = 15.sp, lineHeight = 23.sp, textAlign = TextAlign.Center)
            if (hasCachedContent) Text("已加载的内容仍可查看", fontSize = 13.sp, lineHeight = 19.sp, textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
