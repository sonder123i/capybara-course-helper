package com.tyust.course.ui.screen

import com.tyust.course.ui.theme.moduleEntrance

import androidx.compose.animation.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import com.tyust.course.ui.system.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyust.course.model.Course
import com.tyust.course.ui.theme.NeuPrimary
import com.tyust.course.ui.theme.SemanticSuccess
import com.tyust.course.ui.theme.SemanticDanger
import com.tyust.course.ui.theme.SemanticWarning
import com.tyust.course.ui.theme.Neutral500
import com.tyust.course.ui.theme.Neutral900
import com.tyust.course.ui.theme.Neutral100
import com.tyust.course.ui.theme.Neutral200
import com.tyust.course.ui.theme.NeuInsetBackground
import com.tyust.course.ui.theme.GlassBorderLight
import com.tyust.course.ui.theme.GlassBorderDark
import com.tyust.course.ui.system.GlassRecipe
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.SystemCard
import com.tyust.course.ui.system.SystemCompactSegmentedControl
import com.tyust.course.ui.system.SystemEmptyState
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.system.glass.applyPressSquash
import com.tyust.course.ui.system.glass.glassChip
import com.tyust.course.ui.system.glass.rememberInteractiveOptics
import com.tyust.course.ui.system.rememberGlassAccessibilityMode
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
/**
 * 抢课队列项状态
 */
enum class GrabQueueItemStatus {
    WAITING,    // 等待中
    GRABBING,   // 抢课中
    SUCCESS,    // 成功
    FAILED      // 失败
}

/**
 * 抢课队列屏幕组件
 */
@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.grabQueueItems(
    queue: List<Course>,
    currentIndex: Int,
    itemStatuses: Map<String, GrabQueueItemStatus>,  // 改用课程ID作为key，而不是索引
    isRunning: Boolean,
    isParallelMode: Boolean,
    queueVersion: Int = 0, // 🔧 显式接收版本号
    onMoveItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveItem: (index: Int) -> Unit,
    onAddCourse: () -> Unit,
    onToggleMode: (index: Int) -> Unit = {}, // 🔧 切换精确/智能模式
    showMode: Boolean = true, // 🔧 控制是否显示模式标签和切换
    supportsManualAdd: Boolean = true,
    editable: Boolean = !isRunning
) {
    if (queue.isEmpty()) {
        item {
            Box(Modifier.moduleEntrance(2)) { GrabQueueEmptyState(onAddCourse, supportsManualAdd) }
        }
    } else {
        itemsIndexed(
            items = queue,
            // 🔧 包含 queueVersion 确保任何刷新都会引起重组
            // 🔧 使用对象唯一标识(UUID)作为key，彻底解决动画问题
            key = { _, course -> 
                course.uuid
            }
        ) { index, course ->
            // 显式提取模式值，确保 Compose 追踪此变量
            val currentExactMode = course.useExactMatch
            
            // 用课程名+老师+时间组合获取状态
            val courseKey = course.completeParams["academic_queue_key"]
                ?: course.queueStatusKey
            val status = itemStatuses[courseKey] ?: GrabQueueItemStatus.WAITING
            
            // 用 Box 包裹并应用动画
            Box(modifier = Modifier.animateItem(fadeInSpec = null).moduleEntrance(2)) {
                GrabQueueItem(
                    course = course,
                    index = index,
                    status = status,
                    isActive = index == currentIndex && isRunning,
                    enabled = editable,
                    onRemove = { onRemoveItem(index) },
                    onMoveUp = if (index > 0) {{ onMoveItem(index, index - 1) }} else null,
                    onMoveDown = if (index < queue.size - 1) {{ onMoveItem(index, index + 1) }} else null,
                    onToggleMode = { onToggleMode(index) }, // 🔧 传递模式切换回调
                    showMode = showMode, // 🔧 控制显示模式
                    useExactMatch = currentExactMode // 🔧 显式传递模式，修复刷新问题
                )
            }
        }
        
        if (supportsManualAdd) item {
            // 添加课程按钮
            SystemSecondaryButton(
                text = "添加课程",
                onClick = onAddCourse,
                modifier = Modifier
                    .fillMaxWidth()
                    .moduleEntrance(2)
                    .padding(vertical = 8.dp),
                enabled = editable,
                leadingIcon = {
                    ActionLineIcon(
                        Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
            )
        }
    }
}

@Composable
fun GrabQueueHeader(
    queueSize: Int,
    isParallelMode: Boolean,
    onParallelModeChange: (Boolean) -> Unit,
    onClearQueue: () -> Unit,
    isRunning: Boolean,
    showMode: Boolean = false, // 🔧 是否显示模式切换控制
    isExactModeGlobal: Boolean = true, // 🔧 全局模式状态 (从父组件传入)
    onToggleAllMode: ((Boolean) -> Unit)? = null, // 🔧 一键设置所有模式
    modifier: Modifier = Modifier,
    supportsParallel: Boolean = true
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // 顶部工具栏
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "抢课队列 ($queueSize)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // 🔧 全局模式切换：与全 App 分段选择栏同语言（原自制开关已并入体系）
            if (onToggleAllMode != null && !isRunning && queueSize > 0) {
                SystemCompactSegmentedControl(
                    options = listOf("智能", "精确"),
                    selectedIndex = if (isExactModeGlobal) 1 else 0,
                    onSelect = { onToggleAllMode(it == 1) },
                    modifier = Modifier.width(124.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 清空队列
            val clearEnabled = queueSize > 0 && !isRunning
            GlassQueueIconButton(
                icon = Icons.Default.DeleteSweep,
                contentDescription = "清空队列",
                tint = SemanticDanger,
                enabled = clearEnabled,
                onClick = onClearQueue
            )
        }

        if (supportsParallel && queueSize > 1) {
            SystemCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "并行抢课",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Neutral900
                        )
                        Text(
                            text = "最多同时处理 2 门课程，仅限当前账号；切换账号前请先停止抢课。",
                            style = MaterialTheme.typography.bodySmall,
                            color = Neutral500,
                            lineHeight = 17.sp
                        )
                    }
                    LiquidSwitch(
                        checked = isParallelMode,
                        onCheckedChange = onParallelModeChange,
                        enabled = !isRunning
                    )
                }
            }
        }
    }
}

@Composable
fun GrabQueueEmptyState(onAddCourse: () -> Unit, supportsManualAdd: Boolean = true) {
    SystemCard(modifier = Modifier.fillMaxWidth()) {
        SystemEmptyState(
            title = "队列为空",
            message = if (supportsManualAdd) "在课程列表长按课程添加到队列，或在此手动添加" else "暂无待执行课程"
        ) {
            if (supportsManualAdd) SystemSecondaryButton(
                text = "手动添加",
                onClick = onAddCourse,
                modifier = Modifier.fillMaxWidth(0.62f)
            )
        }
    }
}

@Composable
fun GrabQueueItem(
    course: Course, index: Int, status: GrabQueueItemStatus, isActive: Boolean,
    enabled: Boolean, onRemove: () -> Unit, onMoveUp: (() -> Unit)?, onMoveDown: (() -> Unit)?,
    onToggleMode: () -> Unit = {}, showMode: Boolean = true, useExactMatch: Boolean = false,
    modifier: Modifier = Modifier
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val colors = MaterialTheme.colorScheme
    val canToggleMode = showMode && !course.classId.isNullOrEmpty()
    val statusColor by animateColorAsState(
        when (status) {
            GrabQueueItemStatus.WAITING -> colors.onSurfaceVariant
            GrabQueueItemStatus.GRABBING -> colors.primary
            GrabQueueItemStatus.SUCCESS -> SemanticSuccess
            GrabQueueItemStatus.FAILED -> colors.error
        }, animationSpec = if (reduced) androidx.compose.animation.core.snap()
            else androidx.compose.animation.core.tween(com.tyust.course.ui.theme.MotionProfile.IconMillis),
        label = "queue-status-color")
    val result = when (status) {
        GrabQueueItemStatus.SUCCESS -> SymbolResult.Success
        GrabQueueItemStatus.FAILED -> SymbolResult.Failure
        else -> SymbolResult.None
    }
    val statusLabel = when (status) {
        GrabQueueItemStatus.WAITING -> "待命"
        GrabQueueItemStatus.GRABBING -> "执行中"
        GrabQueueItemStatus.SUCCESS -> "已成功"
        GrabQueueItemStatus.FAILED -> "未成功"
    }
    Box(modifier.fillMaxWidth().testTag("queue-row-" + course.uuid)) {
        LiquidTaskSurface(
            modifier = Modifier.fillMaxWidth().testTag("queue-item-" + course.uuid)
                .semantics { stateDescription = statusLabel },
            accent = statusColor, emphasized = isActive,
            contentPadding = PaddingValues(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            cornerRadius = 18.dp
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(6.dp).background(statusColor.copy(alpha = if (status == GrabQueueItemStatus.WAITING) 0.25f else 0.8f), CircleShape))
                Spacer(Modifier.width(6.dp))
                QueueStateSymbol(index, status == GrabQueueItemStatus.GRABBING, result, Modifier.size(20.dp), statusColor)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f).heightIn(min = 48.dp)
                    .then(if (canToggleMode) Modifier.clickable(
                        enabled = enabled, role = Role.Button,
                        onClickLabel = if (useExactMatch) "切换为智能匹配" else "切换为精确匹配",
                        onClick = onToggleMode
                    ).semantics { stateDescription = if (useExactMatch) "精确匹配" else "智能匹配" }
                    else Modifier), verticalArrangement = Arrangement.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(course.name ?: "未命名课程", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium, color = colors.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (canToggleMode) Text(if (useExactMatch) "精确" else "智能",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.primary.copy(alpha = if (enabled) 1f else 0.38f))
                    }
                    Text(listOfNotNull(course.jxbmc?.takeIf { it.isNotBlank() }, course.teacher?.takeIf { it.isNotBlank() },
                        course.time?.takeIf { it.isNotBlank() }).joinToString(" | "),
                        style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (onMoveUp != null) GlassQueueIconButton(Icons.Default.KeyboardArrowUp, "上移", colors.onSurfaceVariant,
                    enabled, Modifier.testTag("queue-up-" + course.uuid), onMoveUp)
                else Spacer(Modifier.width(48.dp))
                if (onMoveDown != null) GlassQueueIconButton(Icons.Default.KeyboardArrowDown, "下移", colors.onSurfaceVariant,
                    enabled, Modifier.testTag("queue-down-" + course.uuid), onMoveDown)
                else Spacer(Modifier.width(48.dp))
                GlassQueueIconButton(Icons.Default.Delete, "删除", colors.error,
                    enabled, Modifier.testTag("queue-delete-" + course.uuid), onRemove)
            }
        }
    }
}
/**
 * 队列行内的无轮廓玻璃图标钮：保留轻微表面层，不绘制外轮廓——
 * 它们待在 SystemCard 半透面板【里面】，逐枚采样等于玻璃叠玻璃
 * （同 CourseFilterPanel 的 GlassFilterChip：玻璃感由面板承担）。
 * 交互能力与 adaptiveGlassChip 回退分支等价：按压挤压 + optics 手势。
 */
@Composable
private fun GlassQueueIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AnimatedIconButton(onClick = onClick, icon = icon, contentDescription = contentDescription, modifier = modifier,
        enabled = enabled, buttonSize = 48.dp, iconSize = 18.dp, tint = tint, chip = false)
}
