package com.tyust.course.ui.route

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tyust.course.BuildConfig
import com.tyust.course.SurveyWebViewActivity
import com.tyust.course.manager.UserManager
import com.tyust.course.survey.Survey
import com.tyust.course.survey.SurveyRepository
import com.tyust.course.ui.screen.SurveyCenterScreen
import com.tyust.course.ui.screen.SurveyDetailScreen
import com.tyust.course.ui.system.*
import com.tyust.course.usage.UsageStatsManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun SurveyCenterRoute(repository: SurveyRepository, onBack: () -> Unit, initialSurveyId: String? = null) {
    val context = LocalContext.current
    val state by repository.state.collectAsState()
    val scope = rememberCoroutineScope()
    val accountKey = remember(repository) { UserManager.getInstance().sessionState.token.accountStorageKey }
    var selectedId by rememberSaveable { mutableStateOf(initialSurveyId) }
    var showPreferences by rememberSaveable { mutableStateOf(false) }
    var showClearHistory by rememberSaveable { mutableStateOf(false) }
    var opening by remember { mutableStateOf(false) }
    var openError by remember { mutableStateOf<String?>(null) }

    fun update(block: suspend () -> Unit) { scope.launch {
        try { block() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { GlassToaster.show("记录暂未保存，请稍后重试") }
    } }
    fun favorite(survey: Survey) = update {
        val wasFavorite = repository.state.value.local(survey.id).favorite
        repository.toggleFavorite(survey)
        GlassToaster.show(if (wasFavorite) "已取消收藏" else "已收藏")
    }
    LaunchedEffect(repository) { repository.refresh(force = true) }
    LaunchedEffect(repository, state.saved.surveys) {
        if (state.saved.surveys.isNotEmpty()) repository.markSeen(state.saved.surveys.map { it.id })
    }
    LaunchedEffect(selectedId, repository, state.loading) {
        openError = null
        selectedId?.let(state::survey)?.let { repository.viewed(it) }
    }

    SurveyCenterScreen(state, onBack, onRefresh = { scope.launch { repository.refresh(force = true) } },
        onDetail = { selectedId = it.id }, onFavorite = ::favorite, onSettings = { showPreferences = true })

    val survey = selectedId?.let(state::survey)
    if (selectedId != null && survey != null) GlassSubpage(onDismiss = { selectedId = null }) { close ->
        SurveyDetailScreen(survey, state.local(survey.id), survey.isEnded(state.serverNow(System.currentTimeMillis())),
            available = state.saved.surveys.any { it.id == survey.id }, opening = opening, error = openError,
            onBack = close, onFavorite = { favorite(survey) },
            onCompleted = { update {
                val completed = repository.state.value.local(survey.id).completedAt == null
                repository.setCompleted(survey, completed)
                GlassToaster.show(if (completed) "已记录为填写完成" else "已撤销填写标记")
            } },
            onOpen = {
                scope.launch {
                    opening = true; openError = null
                    try {
                        val verified = repository.prepareOpen(survey.id)
                        if (UserManager.getInstance().sessionState.token.accountStorageKey != accountKey) return@launch
                        context.startActivity(Intent(context, SurveyWebViewActivity::class.java)
                            .putExtra(SurveyWebViewActivity.EXTRA_URL, verified.url)
                            .putExtra(SurveyWebViewActivity.EXTRA_TITLE, verified.title))
                        repository.reportClick(verified.id, UUID.randomUUID().toString()) {
                            val preferences = UsageStatsManager.preferences.value
                            preferences.enabled && preferences.noticeSeen && !BuildConfig.DEBUG && !BuildConfig.UI_PREVIEW && !UserManager.getInstance().isDemoMode
                        }
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (error: Exception) { openError = error.message ?: "问卷暂时无法打开，请重试" }
                    finally { opening = false }
                }
            })
    }

    if (showPreferences) SystemDialog(onDismissRequest = { showPreferences = false }, title = { Text("问卷偏好") },
        confirmButton = { SystemPrimaryButton("完成", { showPreferences = false }, Modifier.fillMaxWidth()) }) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            InsetGroupedRow(title = "新问卷轻提醒", subtitle = "重要问卷每份提醒一次，每次进入应用最多一份", showDivider = false,
                trailing = { LiquidSwitch(state.saved.remindersEnabled, { enabled -> update { repository.setRemindersEnabled(enabled) } }) })
            Text("关闭轻提醒后，问卷中心入口仍会显示未读红点。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SystemSecondaryButton("清空浏览历史", { showClearHistory = true }, Modifier.fillMaxWidth(),
                enabled = state.saved.local.values.any { it.lastViewedAt != null })
            Text("仅清空当前账号在本机的浏览记录，收藏和填写标记会保留。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (showClearHistory) SystemDialog(onDismissRequest = { showClearHistory = false }, title = { Text("清空浏览历史？") },
        dismissButton = { SystemSecondaryButton("取消", { showClearHistory = false }, Modifier.fillMaxWidth()) },
        confirmButton = { SystemPrimaryButton("清空历史", { update { repository.clearHistory(); showClearHistory = false; GlassToaster.show("浏览历史已清空") } }, Modifier.fillMaxWidth()) }) {
        Text("当前账号的浏览记录将从本机移除。收藏和填写标记会保留。")
    }
}
