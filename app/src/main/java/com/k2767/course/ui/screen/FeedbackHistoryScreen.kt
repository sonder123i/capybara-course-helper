package com.k2767.course.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k2767.course.network.FeedbackManager
import com.k2767.course.network.SchoolAdaptationManager
import com.k2767.course.ui.system.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FeedbackHistoryScreen(onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedbacks by remember { mutableStateOf<List<FeedbackManager.FeedbackItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun loadFeedback() {
        isLoading = true
        errorMessage = null
        FeedbackManager.getMyFeedbacks(context)
            .onSuccess { feedbacks = it.filterNot { item -> SchoolAdaptationManager.isAdaptationFeedback(item.content) } }
            .onFailure { errorMessage = "暂时无法加载反馈，请稍后重试" }
        isLoading = false
    }
    LaunchedEffect(Unit) { loadFeedback() }

    GlassPageScaffold(title = "我的反馈", subtitle = "反馈记录与回复", onBack = onNavigateBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                isLoading -> SystemLoadingState("正在加载反馈…", Modifier.align(Alignment.Center))
                errorMessage != null -> SystemEmptyState(
                    title = "反馈加载失败", message = errorMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.Center).padding(PagePadding)
                ) {
                    SystemSecondaryButton("重新加载", { scope.launch { loadFeedback() } })
                }
                feedbacks.isEmpty() -> SystemEmptyState(
                    title = "还没有反馈记录", message = "在设置中提交反馈后，可在这里查看回复。",
                    modifier = Modifier.align(Alignment.Center).padding(PagePadding)
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = PagePadding, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(feedbacks, key = { it.id }) { FeedbackCard(it) }
                }
            }
        }
    }
}

@Composable
private fun FeedbackCard(feedback: FeedbackManager.FeedbackItem) {
    var expanded by remember(feedback.id) { mutableStateOf(false) }
    SystemCard(modifier = Modifier.fillMaxWidth(), onClick = { expanded = !expanded }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatDateTime(feedback.createdAt), modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SystemStatusBadge(if (feedback.reply != null) "已回复" else "待回复",
                tone = if (feedback.reply != null) SystemTone.Success else SystemTone.Neutral)
        }
        Text(feedback.content, style = MaterialTheme.typography.bodyLarge,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface)
        if (feedback.content.length > 100) {
            Text(if (expanded) "收起内容" else "点击展开完整内容", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
        }
        feedback.reply?.let { reply ->
            SystemDivider()
            Text("开发者回复", style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(reply.content, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
            Text(formatDateTime(reply.repliedAt), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDateTime(isoString: String): String {
    if (isoString.isBlank()) return ""
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val date = inputFormat.parse(isoString.replace("Z", "").split(".")[0])
        date?.let { outputFormat.format(it) } ?: isoString
    } catch (e: Exception) {
        isoString.take(16).replace("T", " ")
    }
}
