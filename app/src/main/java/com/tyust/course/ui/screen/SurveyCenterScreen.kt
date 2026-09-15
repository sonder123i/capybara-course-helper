package com.tyust.course.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tyust.course.survey.*
import com.tyust.course.ui.system.*
import com.tyust.course.ui.theme.MotionDuration
import com.tyust.course.ui.theme.MotionSpring
import com.tyust.course.ui.theme.moduleEntrance
import com.tyust.course.ui.theme.rememberAppWallpaperStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun surveyTime(value: Long?, full: Boolean = false): String = value?.let {
    SimpleDateFormat(if (full) "yyyy-MM-dd HH:mm" else "MM-dd HH:mm", Locale.CHINA).apply {
        timeZone = TimeZone.getTimeZone("Asia/Shanghai")
    }.format(Date(it))
} ?: "不限"

@Composable
fun SurveyCenterScreen(
    state: SurveyFeedState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onDetail: (Survey) -> Unit,
    onFavorite: (Survey) -> Unit,
    onSettings: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf("") }
    var collection by rememberSaveable { mutableStateOf(SurveyCollection.All) }
    var status by rememberSaveable { mutableStateOf(SurveyStatusFilter.Active) }
    val listState = rememberLazyListState()
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val categories = remember(state.saved.surveys, state.saved.records) {
        listOf("") + (state.saved.surveys + state.saved.records.values).map { it.category }.distinct().sorted()
    }
    val now = System.currentTimeMillis()
    val items = remember(state.saved, query, category, collection, status, now / 60000) {
        filterSurveys(state, query, category, status, collection, now)
    }
    Scaffold(containerColor = Color.Transparent, topBar = {
        SystemTopBar("问卷中心", "校园问卷与参与记录",
            collapseFraction = if (listState.firstVisibleItemIndex > 0) 1f else (listState.firstVisibleItemScrollOffset / 96f).coerceIn(0f, 1f),
            navigationIcon = { SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回设置", onBack) },
            actions = {
                SystemIconButton(Icons.Outlined.Refresh, "刷新问卷", onRefresh, enabled = !state.refreshing)
                SystemIconButton(Icons.Outlined.Tune, "问卷偏好", onSettings)
            })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).imePadding()) {
            Column(Modifier.padding(horizontal = PagePadding), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("survey-search"),
                    placeholder = "搜索问卷、发布方或标签", leadingIcon = Icons.Outlined.Search,
                    trailing = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "清除搜索") } })
                SystemPicker(SurveyCollection.entries.map { it.label }, collection.ordinal, { index ->
                    collection = SurveyCollection.entries[index]
                    status = if (collection == SurveyCollection.All) SurveyStatusFilter.Active else SurveyStatusFilter.All
                }, Modifier.fillMaxWidth(), label = "查看")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SystemPicker(SurveyStatusFilter.entries.map { it.label }, status.ordinal, { status = SurveyStatusFilter.entries[it] },
                        Modifier.weight(1f), label = "状态")
                    SystemPicker(categories.map { it.ifBlank { "全部分类" } }, categories.indexOf(category).takeIf { it >= 0 } ?: 0,
                        { category = categories[it] }, Modifier.weight(1f), label = "分类")
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${items.size} 份问卷", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.saved.fetchedAt > 0) Text(
                        (if (state.error == null) "更新于 " else "缓存于 ") + surveyTime(state.saved.fetchedAt),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (state.refreshing) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 10.dp))
            if (state.error != null) {
                SystemCard(Modifier.padding(horizontal = PagePadding).padding(top = 10.dp), onClick = onRefresh) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Column(Modifier.weight(1f)) {
                            Text(if (state.saved.fetchedAt > 0) "正在显示本机缓存" else "暂时无法加载", fontWeight = FontWeight.SemiBold)
                            Text("点击重试；填写前需要联网确认状态", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.Refresh, "重试加载")
                    }
                }
            }
            LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth().testTag("survey-list"),
                contentPadding = PaddingValues(start = PagePadding, end = PagePadding, top = 14.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading || (state.refreshing && state.saved.fetchedAt == 0L)) item("loading") {
                    SystemLoadingState("正在获取问卷…")
                } else if (items.isEmpty()) item("empty") {
                    SystemEmptyState(if (collection == SurveyCollection.All) "这里还没有问卷" else "还没有${collection.label.removePrefix("我的")}",
                        if (query.isNotBlank() || category.isNotBlank() || status != SurveyStatusFilter.Active) "试试调整搜索或筛选条件" else "新问卷发布后会显示在这里",
                        icon = Icons.Outlined.Assignment)
                }
                items(items, key = { it.id }) { survey ->
                    SurveyListCard(survey, state.local(survey.id), survey.isEnded(state.serverNow(now)),
                        onClick = { onDetail(survey) }, onFavorite = { onFavorite(survey) },
                        modifier = Modifier.animateItem(
                            fadeInSpec = tween(if (reduced) 0 else MotionDuration.Medium),
                            placementSpec = if (reduced) snap() else MotionSpring.snappy(),
                            fadeOutSpec = tween(if (reduced) 0 else MotionDuration.Fast)))
                }
                item("local-note") {
                    Text("收藏、历史和“已填写”仅保存在当前账号的本机记录中。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun SurveyListCard(survey: Survey, local: SurveyLocalState, ended: Boolean, onClick: () -> Unit, onFavorite: () -> Unit, modifier: Modifier) {
    SystemCard(modifier.testTag("survey-card-${survey.id}"), onClick = onClick) {
        if (survey.coverUrl.startsWith("https://")) AsyncImage(survey.coverUrl, "${survey.title}封面",
            Modifier.fillMaxWidth().height(128.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (survey.pinned) SystemStatusBadge("推荐", SystemTone.Info)
                    SystemStatusBadge(if (ended) "已结束" else survey.category, if (ended) SystemTone.Neutral else SystemTone.Info)
                    if (local.completedAt != null) SystemStatusBadge("已填写", SystemTone.Success)
                }
                Text(survey.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            }
            SurveyFavoriteButton(local.favorite, onFavorite)
        }
        if (survey.description.isNotBlank()) Text(survey.description, style = MaterialTheme.typography.bodyMedium,
            maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp))
        Text("${survey.publisher.ifBlank { "问卷发布方" }} · 约 ${survey.estimatedMinutes} 分钟",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp))
        if (survey.endsAt != null) Text("截止 ${surveyTime(survey.endsAt)}（北京时间）", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
internal fun SurveyFavoriteButton(favorite: Boolean, onClick: () -> Unit) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val scale by animateFloatAsState(if (favorite) 1.08f else 1f,
        animationSpec = if (reduced) snap() else MotionSpring.liquidTap(), label = "survey-favorite")
    IconButton(onClick, Modifier.scale(scale).testTag("survey-favorite")) {
        Icon(if (favorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
            if (favorite) "取消收藏" else "收藏问卷",
            tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SurveyDetailScreen(survey: Survey, local: SurveyLocalState, ended: Boolean, available: Boolean,
    opening: Boolean, error: String?, onBack: () -> Unit, onFavorite: () -> Unit, onCompleted: () -> Unit, onOpen: () -> Unit) {
    val scroll = rememberLazyListState()
    val wallpaper = rememberAppWallpaperStyle()
    val microTexture = isBackdropSupported()
    // Nested subpages share the window backdrop, so the detail must cover the list itself.
    Scaffold(modifier = Modifier.fillMaxSize().drawBehind { drawWallpaperPattern(wallpaper, microTexture) },
        containerColor = Color.Transparent,
        topBar = { SystemTopBar("问卷详情", navigationIcon = { SystemIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "返回问卷列表", onBack) },
            actions = { SurveyFavoriteButton(local.favorite, onFavorite) }) },
        bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = PagePadding, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                SystemPrimaryButton(when { opening -> "正在确认问卷状态…"; !available -> "问卷暂不可用"; ended -> "问卷已结束"; else -> "开始填写" },
                    onOpen, Modifier.fillMaxWidth().testTag("survey-open"), enabled = !opening && !ended && available,
                    leadingIcon = { Icon(Icons.Outlined.EditNote, null, Modifier.padding(end = 6.dp)) })
                SystemSecondaryButton(if (local.completedAt == null) "我已填写" else "撤销填写标记", onCompleted,
                    Modifier.fillMaxWidth().testTag("survey-completed"))
            }
        }) { padding ->
        LazyColumn(state = scroll, contentPadding = PaddingValues(start = PagePadding, end = PagePadding,
            top = padding.calculateTopPadding() + 12.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
            item {
                SystemCard(Modifier.moduleEntrance(0)) {
                    if (survey.coverUrl.startsWith("https://")) AsyncImage(survey.coverUrl, "问卷封面",
                        Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        SystemStatusBadge(if (ended) "已结束" else "进行中", if (ended) SystemTone.Neutral else SystemTone.Success)
                        SystemStatusBadge(survey.category, SystemTone.Info)
                        survey.tags.forEach { SystemStatusBadge(it, SystemTone.Neutral) }
                    }
                    Text(survey.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(vertical = 16.dp))
                    Text(survey.description.ifBlank { "发布方未填写简介。" }, style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                InsetGroupedSection(header = "参与信息") {
                    InsetGroupedRow(title = "发布方", subtitle = survey.publisher.ifBlank { "未填写" })
                    InsetGroupedRow(title = "预计用时", subtitle = "约 ${survey.estimatedMinutes} 分钟")
                    InsetGroupedRow(title = "截止时间", subtitle = surveyTime(survey.endsAt, true) + if (survey.endsAt != null) "（北京时间）" else "", showDivider = false)
                }
            }
            item {
                SystemCard {
                    Text("填写与记录", fontWeight = FontWeight.SemiBold)
                    Text("将在应用内打开问卷星页面。答案由问卷星接收，提交结果请以问卷页面为准。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                    Text(if (local.completedAt == null) "填写后可手动标记“我已填写”，方便自己查看。" else "已于 ${surveyTime(local.completedAt)} 手动标记。这条记录不代表问卷星已收到答案。",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}
