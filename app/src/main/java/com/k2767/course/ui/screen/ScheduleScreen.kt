package com.k2767.course.ui.screen

import com.k2767.course.ui.theme.moduleEntrance

import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.ViewDay
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2767.course.ui.system.GlassLoadingState
import com.k2767.course.ui.system.PagePadding
import com.k2767.course.ui.system.SystemCard
import com.k2767.course.ui.system.SystemCompactSegmentedControl
import com.k2767.course.ui.system.SystemEmptyState
import com.k2767.course.ui.system.SystemStatusBadge
import com.k2767.course.ui.system.SystemTone
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.k2767.course.ui.system.GlassMaterialRole
import com.k2767.course.ui.system.HeaderGlassSlab
import com.k2767.course.ui.system.StatusBarFrost
import com.k2767.course.ui.system.lerpDp
import com.k2767.course.ui.system.rememberScreenMetrics
import com.k2767.course.ui.system.lerpSp
import com.k2767.course.ui.system.GlassMaterials
import com.k2767.course.ui.system.GlassRecipe
import com.k2767.course.ui.system.LocalAppBackdrop
import com.k2767.course.ui.system.LocalControlBackdrop
import com.k2767.course.ui.system.isBackdropSupported
import com.k2767.course.ui.system.glass.LiquidActionGroup
import com.k2767.course.ui.system.glass.glassRim
import com.k2767.course.ui.system.glass.resolvePhysicalLens
import com.k2767.course.ui.system.rememberGlassAccessibilityMode
import com.k2767.course.ui.system.reportNoticeAnchor

import com.k2767.course.ui.theme.MotionSpring
import com.k2767.course.ui.theme.NeuDivider
import com.k2767.course.ui.theme.NeuPrimary
import com.k2767.course.ui.theme.NeuSurface

import java.util.Calendar
import kotlin.math.roundToInt
import kotlin.math.ceil
import kotlinx.coroutines.launch

private val ScheduleTimeColumnWidth = 36.dp
private val ScheduleTimeColumnShadowWidth = 8.dp
/**
 * 每节课的高度下限。内容多的课（长课程名、多行地点）会被内容撑高，这里只是下限。
 *
 * 从 84 收到 72 是为了「一屏看全 8 节」：8 × 84 = 672dp 会顶穿可视区（顶部展开态还要
 * 134dp，另计状态栏与底部导航栏），收窄后通常能完整落进一屏。真实高度仍由
 * rememberCoursePeriodHeight 按内容测量后取 max，所以内容满的课不会被压扁。
 */
private val SchedulePeriodHeight = 72.dp

/** 窄屏收窄的时间列与网格左右留白，把省下的宽度全给七个日列。 */
private val ScheduleTimeColumnWidthTight = 28.dp
private val SchedulePeriodHeightTight = 68.dp
private val ScheduleGridPaddingTight = 10.dp

// ── 顶栏折叠几何 ────────────────────────────────────────────────
// 展开态与折叠态的高度【差】必须等于折叠行程（travel）：
// 手指走 60dp -> 内容上移 60dp -> 顶栏下缘也上移 60dp，两者间距恒定。
// 行程和高度差一旦脱钩，网格顶端就会与收缩中的顶栏彼此追赶——所以 travel
// 定义成差值而不是另一个常量（与成绩页的 GradesHeaderMetrics 同一套写法）。
private val HeaderTopPadExpanded = 10.dp
private val HeaderTopPadCollapsed = 6.dp
/** 标题 + 分段控件 + 芯片同一行：34(标题) + 6 + 36(分段) = 76。 */
private val HeaderActionRowExpanded = 76.dp
private val HeaderActionRowCollapsed = 48.dp
private val HeaderTitleGap = 6.dp
/** 分段控件自身高度。它内部写死 36dp，容器给不足就会被压扁而不是被裁。 */
private val HeaderSegmentHeight = 36.dp
private val HeaderWeekRowExpanded = 34.dp
private val HeaderWeekRowCollapsed = 28.dp
private val HeaderBottomPadExpanded = 8.dp
private val HeaderBottomPadCollapsed = 4.dp

/**
 * 顶栏两态高度。**短屏只压展开态的空白**——折叠态、分段控件高度与玻璃条几何
 * 一律不动，那几个值同时是胶囊圆角与折射行程的依据。
 */
private class ScheduleHeaderMetrics(
    val expanded: Dp,
    val collapsed: Dp,
    val topPadExpanded: Dp,
    val titleGap: Dp,
    val weekRowExpanded: Dp,
    val weekRowCollapsed: Dp,
    val actionRowExpanded: Dp,
    val actionRowCollapsed: Dp,
    val stackedActions: Boolean,
    val semesterWidth: Dp,
    val prefixFontSize: TextUnit
) {
    /** 折叠行程。定义成差值，于是不可能与两态高度脱钩。 */
    val travel: Dp get() = expanded - collapsed
}

@Composable
private fun rememberScheduleHeaderMetrics(availableWidth: Dp): ScheduleHeaderMetrics {
    val screen = rememberScreenMetrics()
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.bodyLarge
    val segmentStyle = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.ExtraBold)
    return remember(screen, availableWidth, density, measurer, titleStyle, segmentStyle) {
        fun textSize(text: String, style: TextStyle) = measurer.measure(text, style, maxLines = 1)
        fun textWidth(text: String, style: TextStyle) = with(density) { textSize(text, style).size.width.toDp() }
        fun textHeight(text: String, style: TextStyle) = with(density) { textSize(text, style).size.height.toDp() }
        val topPad = screen.tall(HeaderTopPadExpanded, 6.dp)
        val titleGap = screen.tall(HeaderTitleGap, 4.dp)
        // 星期条是「星期 + 日期」两行。定高必须实测，不能写死：Column 逐个测量子项时会把
        // 剩余高度传给下一个，行高一旦小于两行文字，日期就被测成 0 高、整行什么都不画——
        // 系统字体放大时必然踩到，因为文字按 fontScale 长，写死的行高不长。
        val weekdayStyle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        val weekdayDateStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Medium)
        val weekdayCompactStyle = TextStyle(fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
        val weekdayDateCompactStyle = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Medium)
        val weekRow = maxOf(
            screen.tall(HeaderWeekRowExpanded, 30.dp),
            textHeight("一", weekdayStyle) + textHeight("18", weekdayDateStyle) + 3.dp
        )
        val bottomPad = screen.tall(HeaderBottomPadExpanded, 4.dp)
        // Four 48dp targets keep their original row. Fit the text to the actual parent,
        // rather than stacking every device below an arbitrary screen-width breakpoint.
        val titleAvailable = (availableWidth - PagePadding * 2 - 204.dp - 8.dp).coerceAtLeast(0.dp)
        val prefixSize = if (availableWidth < 380.dp) 12.sp else 14.sp
        val expandedStyle = titleStyle.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.sp)
        val collapsedStyle = expandedStyle.copy(fontSize = 17.sp)
        val prefixStyle = titleStyle.copy(fontSize = prefixSize, fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp)
        val segmentMinimum = textWidth("本学期", segmentStyle) * 2 + 30.dp
        val titleMinimum = maxOf(textWidth("第 25 周", expandedStyle),
            textWidth("第 25 周", collapsedStyle) + textWidth("下学期 · ", prefixStyle), segmentMinimum)
        val stacked = titleAvailable < titleMinimum
        val semesterWidth = if (stacked) maxOf(144.dp, segmentMinimum) else
            maxOf(segmentMinimum, minOf(144.dp * density.fontScale.coerceAtLeast(1f), titleAvailable))
        val titleExpanded = textHeight("第 25 周", expandedStyle) + titleGap + HeaderSegmentHeight
        val titleCollapsed = maxOf(textHeight("第 25 周", collapsedStyle), textHeight("下学期 · ", prefixStyle))
        val actionExpanded = if (stacked) titleExpanded + 6.dp + 48.dp else maxOf(HeaderActionRowExpanded, titleExpanded)
        val actionCollapsed = if (stacked) titleCollapsed + 6.dp + 48.dp else maxOf(HeaderActionRowCollapsed, titleCollapsed)
        val collapsedWeekRow = maxOf(
            HeaderWeekRowCollapsed,
            textHeight("一", weekdayCompactStyle) + textHeight("18", weekdayDateCompactStyle) + 2.dp
        )
        ScheduleHeaderMetrics(
            // 展开态：上留白 + 标题行 + 标题间距 + 周次行 + 下留白
            expanded = topPad + actionExpanded + titleGap + weekRow + bottomPad,
            collapsed = HeaderTopPadCollapsed + actionCollapsed + collapsedWeekRow + HeaderBottomPadCollapsed,
            topPadExpanded = topPad,
            titleGap = titleGap,
            weekRowExpanded = weekRow,
            weekRowCollapsed = collapsedWeekRow,
            actionRowExpanded = actionExpanded,
            actionRowCollapsed = actionCollapsed,
            stackedActions = stacked,
            semesterWidth = semesterWidth,
            prefixFontSize = prefixSize
        )
    }
}

/** 悬浮玻璃条相对屏幕边缘的内缩。前景内容不在条里，所以这个值不影响任何对齐。 */
private val HeaderSlabInset = 12.dp
/** 与状态栏磨砂之间留一道透明缝：网格清晰穿过，条的上缘才看得出折射。 */
private val HeaderSlabTopGap = 6.dp
private val HeaderSlabBottomGap = 4.dp
/**
 * 固定 29dp = 折叠态条高(58dp)的一半，于是折叠态正好是一枚胶囊。
 * 不用 percent=50：那样半可见的中途会是一枚很大的软药片，观感突兀。
 */
private val HeaderSlabCorner = 29.dp

/**
 * 网格的横向几何。**星期条与网格必须调用同一对函数**——星期标签靠
 * 「相同的左右留白 + 相同宽度的时间列占位」才和日期列对齐（见 `WeekHeaderCompact`
 * 里那条注释），两边取不同的值就会整体错位。
 */
@Composable
private fun scheduleGridPadding(): Dp =
    rememberScreenMetrics().wide(PagePadding, ScheduleGridPaddingTight)

@Composable
private fun scheduleTimeColumnWidth(): Dp =
    rememberScreenMetrics().wide(ScheduleTimeColumnWidth, ScheduleTimeColumnWidthTight)

/** 单节课的行高。短屏收到 68dp，一屏能多看一节多。 */
@Composable
private fun schedulePeriodHeight(): Dp =
    rememberScreenMetrics().tall(SchedulePeriodHeight, SchedulePeriodHeightTight)

data class ScheduleCourseUi(
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val color: Color,
    val isCustom: Boolean = false,
    val customId: String = "",
    val sourceId: String = "",
    val id: String = if (isCustom) "custom:$customId" else com.k2767.course.schedule.ScheduleIdentity.network(
        sourceId, name, teacher, day, startPeriod, endPeriod, weeks, location
    ),
    val hasConflict: Boolean = false,
    val isCurrent: Boolean = false
) {
    fun record() = com.k2767.course.schedule.ScheduleCourseRecord(id, name, teacher, location, day, startPeriod, endPeriod, weeks, isCustom)
}

data class PeriodTimeUi(val period: Int, val startTime: String, val endTime: String)

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ScheduleScreen(
    currentWeek: Int,
    courses: List<ScheduleCourseUi>,
    isLoading: Boolean,
    periodTimes: List<PeriodTimeUi> = emptyList(),
    periodCount: Int = 12,
    onWeekChange: (Int) -> Unit,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    isNextSemester: Boolean = false,
    onToggleSemester: () -> Unit = {},
    errorMessage: String = "",
    onRetry: () -> Unit = {},
    firstWeekDate: String? = null,
    weekRequestKey: String? = null
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val coroutineScope = rememberCoroutineScope()
    val maxWeeks = com.k2767.course.schedule.ScheduleMaxWeeks
    val reducedMotion = com.k2767.course.ui.system.rememberGlassAccessibilityMode().reduceMotion
    val minuteClock by androidx.compose.runtime.produceState(System.currentTimeMillis()) {
        while (true) {
            kotlinx.coroutines.delay(60_000L - System.currentTimeMillis() % 60_000L)
            value = System.currentTimeMillis()
        }
    }
    val actualWeek = com.k2767.course.schedule.ScheduleDates.weekAt(firstWeekDate, minuteClock)
    val conflictIds = remember(courses) {
        val records = courses.map { it.record() }
        records.filter { com.k2767.course.schedule.scheduleConflicts(it, records).isNotEmpty() }.map { it.id }.toSet()
    }
    val liveIds = remember(courses, periodTimes, minuteClock) {
        val clock = Calendar.getInstance().apply { timeInMillis = minuteClock }
        val today = (clock.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
        val nowMinutes = clock.get(Calendar.HOUR_OF_DAY) * 60 + clock.get(Calendar.MINUTE)
        fun minutes(text: String?): Int? {
            val parts = text?.split(':')?.map { it.toIntOrNull() } ?: return null
            return if (parts.size == 2 && parts[0] != null && parts[1] != null) parts[0]!! * 60 + parts[1]!! else null
        }
        courses.filter { course ->
            val start = minutes(periodTimes.firstOrNull { it.period == course.startPeriod }?.startTime)
            val end = minutes(periodTimes.firstOrNull { it.period == course.endPeriod }?.endTime)
            course.day == today && start != null && end != null && nowMinutes in start until end
        }.map { it.id }.toSet()
    }
    val pagerState = rememberPagerState(
        initialPage = (currentWeek - 1).coerceIn(0, maxWeeks - 1),
        pageCount = { maxWeeks }
    )

    val latestWeekChange by rememberUpdatedState(onWeekChange)
    val latestRequestedWeek by rememberUpdatedState(currentWeek)
    val latestWeekRequestKey by rememberUpdatedState(weekRequestKey)
    val weekSync = remember(pagerState) {
        com.k2767.course.schedule.ScheduleWeekPagerSync(currentWeek, weekRequestKey)
    }

    var arrowJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var requestedPage by remember { mutableIntStateOf(pagerState.currentPage) }
    val userDragging by pagerState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(userDragging) {
        if (userDragging) { arrowJob?.cancel(); requestedPage = pagerState.currentPage }
    }
    fun moveWeek(delta: Int) {
        requestedPage = ((if (arrowJob?.isActive == true) requestedPage else pagerState.currentPage) + delta).coerceIn(0, maxWeeks - 1)
        arrowJob?.cancel()
        arrowJob = coroutineScope.launch {
            if (reducedMotion) pagerState.scrollToPage(requestedPage)
            else pagerState.animateScrollToPage(requestedPage, animationSpec = com.k2767.course.ui.theme.MotionProfile.pagerSpring())
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow {
            Triple(latestRequestedWeek to latestWeekRequestKey, pagerState.isScrollInProgress, pagerState.settledPage)
        }.collect { (request, scrolling, page) ->
            val targetPage = weekSync.requestPage(request.first, request.second)
            if (targetPage != null) {
                arrowJob?.cancel()
                pagerState.scrollToPage(targetPage)
                weekSync.settledWeek(targetPage)
            } else if (!scrolling) {
                weekSync.settledWeek(page)?.let(latestWeekChange)
            }
        }
    }

    // 所有 pager 页共用一个滚动位置：顶栏折叠进度要跟着它推导，
    // 而且左右切周时纵向位置不该跳回顶部。
    val gridScrollState = rememberScrollState()
    // 周视图 / 日视图开关。用 rememberSaveable 跨配置变更（旋转、深浅色切换）保留；
    // 进程重启后回到周视图——它仍是信息密度最高、最常用的那一屏。
    var dayViewEnabled by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    // 日 / 周视图切换动画。
    // 分两拍：旧视图收起 → 换视图并回到顶部 → 新视图展开。之所以不做交叉淡入淡出，
    // 是因为两个视图同时在场会让顶栏玻璃的捕获层（contentBackdrop）被两个节点同帧写入，
    // 顶栏会闪；而且周视图是 3 页 pager + 网格，同帧双份布局会掉帧。
    // 收起/展开都不落到全透明，避免中间出现“整屏空一帧”。
    val renderedDayView = remember { mutableStateOf(dayViewEnabled) }
    val viewSwitch = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(dayViewEnabled) {
        if (dayViewEnabled == renderedDayView.value) return@LaunchedEffect
        if (reducedMotion) {
            renderedDayView.value = dayViewEnabled
            viewSwitch.snapTo(1f)
            gridScrollState.scrollTo(0)
            return@LaunchedEffect
        }
        // 收起：140ms 加速淡出，向自己那一侧轻移
        viewSwitch.animateTo(
            0f,
            androidx.compose.animation.core.tween(140, easing = com.k2767.course.ui.theme.MotionEasing.Accelerate)
        )
        renderedDayView.value = dayViewEnabled
        // 换视图之后才回顶部：若在切之前回，旧视图会在淡出过程中先跳一下
        gridScrollState.scrollTo(0)
        // 展开：220ms 减速淡入，从对侧回位
        viewSwitch.animateTo(
            1f,
            androidx.compose.animation.core.tween(220, easing = com.k2767.course.ui.theme.MotionEasing.FastOutSlowIn)
        )
    }
    // 位移很小（28dp）——传达“换了一屏”，但不做整屏横移，避免与 pager 的左右切周手势混淆。
    val viewSwitchLayer = Modifier.graphicsLayer {
        val progress = viewSwitch.value
        val direction = if (renderedDayView.value) 1f else -1f
        alpha = 0.25f + 0.75f * progress
        translationX = (1f - progress) * direction * 28.dp.toPx()
    }
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val headerMetrics = rememberScheduleHeaderMetrics(maxWidth)
    // 折叠行程 = 顶栏高度差，于是收缩与滚动 1:1 对消，全程跟手。
    val travelPx = with(LocalDensity.current) { headerMetrics.travel.toPx() }
    val headerCollapse by remember(travelPx) {
        derivedStateOf { (gridScrollState.value / travelPx).coerceIn(0f, 1f) }
    }
    // 课表内容的捕获层。顶栏玻璃采样「壁纸 + 这一层」，于是网格从顶栏底下
    // 穿过时会被折射；顶栏本身不在这一层内，不构成自采样。
    val wallpaperBackdrop = LocalAppBackdrop.current
    val contentBackdrop = if (wallpaperBackdrop != null && isBackdropSupported()) {
        rememberLayerBackdrop()
    } else {
        null
    }
    val headerSampleBackdrop = if (wallpaperBackdrop != null && contentBackdrop != null) {
        rememberCombinedBackdrop(wallpaperBackdrop, contentBackdrop)
    } else {
        null
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            // topBar slot 只测量单个子项，两个兄弟节点会叠放并让通知条压到状态栏，
            // 因此顶栏与内联通知必须在同一个 Column 里纵向排布。
            Column(modifier = Modifier.moduleEntrance(0).reportNoticeAnchor()) {
                WeekHeaderCompact(
                    currentWeek = pagerState.currentPage + 1,
                    weekOffset = if (reducedMotion) 0f else pagerState.currentPageOffsetFraction,
                    firstWeekDate = firstWeekDate,
                    actualWeek = actualWeek,
                    onPrevClick = {
                        moveWeek(-1)
                    },
                    onNextClick = {
                        moveWeek(1)
                    },
                    onSettingsClick = onSettingsClick,
                    onExportClick = onExportClick,
                    isNextSemester = isNextSemester,
                    onToggleSemester = onToggleSemester,
                    dayViewEnabled = dayViewEnabled,
                    onToggleDayView = { dayViewEnabled = !dayViewEnabled },
                    collapseFraction = headerCollapse,
                    sampleBackdrop = headerSampleBackdrop
                )
            }
        }
    ) { paddingValues ->
        when {
            isLoading && courses.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .moduleEntrance(1)
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    GlassLoadingState(text = "正在同步课表…")
                }
            }

            errorMessage.isNotBlank() -> {
                Box(Modifier.fillMaxSize().moduleEntrance(1).padding(paddingValues).padding(24.dp), contentAlignment = Alignment.Center) {
                    com.k2767.course.ui.system.SystemEmptyState(title = "课表同步失败", message = errorMessage) {
                        com.k2767.course.ui.system.SystemSecondaryButton(text = "重新同步", onClick = onRetry)
                    }
                }
            }

            renderedDayView.value -> {
                val today = ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
                val dayWeekNumber = pagerState.currentPage + 1
                // 与周视图同一套派生：冲突 / 正在上课的标记来源保持一致，
                // 否则同一门课在两个视图里的状态会不一样。
                val dayCourses = remember(courses, conflictIds, liveIds, dayWeekNumber, actualWeek, isNextSemester) {
                    courses.map {
                        it.copy(
                            hasConflict = it.id in conflictIds,
                            isCurrent = !isNextSemester && dayWeekNumber == actualWeek && it.id in liveIds
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .moduleEntrance(1)
                        .then(
                            if (contentBackdrop != null) Modifier.layerBackdrop(contentBackdrop) else Modifier
                        )
                        .then(viewSwitchLayer)
                ) {
                    ScheduleDayView(
                        courses = dayCourses,
                        dayOfWeek = today,
                        currentWeek = dayWeekNumber,
                        periodTimes = periodTimes,
                        firstWeekDate = firstWeekDate,
                        onCourseClick = onCourseClick,
                        scrollState = gridScrollState,
                        topInset = statusBarHeight + headerMetrics.expanded
                    )
                }
            }

            else -> {
                HorizontalPager(
                    state = pagerState,
                    flingBehavior = androidx.compose.foundation.pager.PagerDefaults.flingBehavior(
                        state = pagerState, snapAnimationSpec = com.k2767.course.ui.theme.MotionProfile.pagerSpring()),
                    modifier = Modifier
                        .testTag("schedule-pager")
                        .fillMaxSize()
                        .moduleEntrance(1)
                        // 内容捕获层挂在 pager 这个稳定节点上（不要挂进每一页）：
                        // 顶栏玻璃与芯片采样它，才能折射滚动中的网格与课程卡片。
                        .then(
                            if (contentBackdrop != null) {
                                Modifier.layerBackdrop(contentBackdrop)
                            } else {
                                Modifier
                            }
                        )
                        .then(viewSwitchLayer),
                    beyondViewportPageCount = 0,
                    pageSpacing = 0.dp
                ) { page ->
                    val weekNumber = page + 1
                    val displayedCourses = remember(courses, conflictIds, liveIds, weekNumber, actualWeek, isNextSemester) {
                        courses.map { it.copy(hasConflict = it.id in conflictIds,
                            isCurrent = !isNextSemester && weekNumber == actualWeek && it.id in liveIds) }
                    }
                    Box(Modifier.fillMaxSize().graphicsLayer {
                        val offset = pagerState.currentPage + pagerState.currentPageOffsetFraction - page
                        scaleX = if (reducedMotion) 1f else com.k2767.course.ui.theme.SchedulePagerMotion.scale(offset)
                        scaleY = scaleX
                        alpha = if (reducedMotion) 1f else com.k2767.course.ui.theme.SchedulePagerMotion.alpha(offset)
                    }) {
                    ScheduleGrid(
                        courses = displayedCourses,
                        currentWeek = weekNumber,
                        periodTimes = periodTimes,
                        periodCount = periodCount,
                        onCourseClick = onCourseClick,
                        scrollState = gridScrollState,
                        // 【常量】而不是 paddingValues.calculateTopPadding()：后者随顶栏
                        // 一起收缩，而它施加在 verticalScroll 内部，于是顶栏每缩 1dp
                        // 内容就被额外上提 1dp——手指走 60dp、内容走 120dp。
                        topInset = statusBarHeight + headerMetrics.expanded
                    )
                    }
                }
            }
        }
    }
    }
}

@Composable
fun WeekHeaderCompact(
    currentWeek: Int,
    onPrevClick: () -> Unit,
    onNextClick: () -> Unit,
    onSettingsClick: () -> Unit = {},
    onExportClick: () -> Unit = {},
    isNextSemester: Boolean = false,
    onToggleSemester: () -> Unit = {},
    /** 当前是否处于日视图。只影响工具栏第一个按钮的图标与无障碍文案。 */
    dayViewEnabled: Boolean = false,
    onToggleDayView: () -> Unit = {},
    /** 0=未滚动（大标题直接浮在课表上）、1=已上划（收拢成一条悬浮玻璃）。 */
    collapseFraction: Float = 0f,
    /** 「壁纸 + 课表内容」的合成采样源。为空则退回无玻璃顶栏。 */
    sampleBackdrop: Backdrop? = null,
    weekOffset: Float = 0f,
    firstWeekDate: String? = null,
    actualWeek: Int? = null
) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val calendar = Calendar.getInstance()
    val headerFocus = remember { FocusRequester() }
    val focusRegistry = LocalScheduleFocus.current
    DisposableEffect(focusRegistry, headerFocus) {
        focusRegistry?.register("header", headerFocus)
        onDispose { focusRegistry?.remove("header", headerFocus) }
    }
    val currentDayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    // CourseSelectorTheme 已根据系统模式与壁纸明暗选择配色，无需读取 GPU 像素。
    val titleColor = MaterialTheme.colorScheme.onSurface

    val collapse = collapseFraction.coerceIn(0f, 1f)

    // 顶栏玻璃把自己的渲染结果导出到这一层，供板上的芯片二次采样，
    // 于是芯片折射「壁纸 + 滚动网格 + 顶栏玻璃」三者的合成——玻璃叠玻璃。
    val headerBackdrop = rememberLayerBackdrop()
    val chipBackdrop = if (sampleBackdrop != null) {
        rememberCombinedBackdrop(sampleBackdrop, headerBackdrop)
    } else {
        null
    }
    // 与 ScheduleScreen 里那份是同一个纯函数结果，不会算出两套几何
    val headerMetrics = rememberScheduleHeaderMetrics(maxWidth)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // 定高：这是"收起"的来源，也让悬浮条的几何是确定的
            .height(
                statusBarHeight +
                    lerpDp(headerMetrics.expanded, headerMetrics.collapsed, collapse)
            )
            .then(com.k2767.course.ui.system.wallpaperHeaderScrim())
    ) {
        // 玻璃层：【必须是前景内容的兄弟节点，不能是它的父节点】。
        // layerBackdrop 捕获所在节点的整棵子树——挂在包含按钮的父节点上，
        // 按钮就会采样一个含有自己的图层，RenderThread 死循环直接 native 崩溃。
        // 同款写法见 SystemUi.kt 的 SystemDialog。
        //
        // 节点常驻、不用 if(collapse>0) 摘掉：一摘掉这一层就没有内容可导出，
        // 采样它的芯片会拿到空图层。强度全部由 collapse 调制。
        if (sampleBackdrop != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .layerBackdrop(headerBackdrop)
            ) {
                StatusBarFrost(
                    height = statusBarHeight + 1.dp,
                    collapse = collapse,
                    backdrop = sampleBackdrop
                )
                HeaderGlassSlab(
                    // 比 collapse 晚起步：顶栏还高的时候它是一张大卡片，
                    // 提前显形会让人先看到"卡"再看到"条"。
                    strength = ((collapse - 0.35f) / 0.65f).coerceIn(0f, 1f),
                    backdrop = sampleBackdrop,
                    cornerRadius = HeaderSlabCorner,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = statusBarHeight + HeaderSlabTopGap,
                            start = HeaderSlabInset,
                            end = HeaderSlabInset,
                            bottom = HeaderSlabBottomGap
                        )
                        .fillMaxHeight()
                )
            }
        }

        CompositionLocalProvider(LocalControlBackdrop provides chipBackdrop) {
            // 前景横向几何刻意保持不变（padding = PagePadding）：星期条要和网格的
            // 日期列对齐，塞进内缩 12dp 的玻璃条里就得反向补偿，多一处会飘的耦合。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(
                        top = lerpDp(headerMetrics.topPadExpanded, HeaderTopPadCollapsed, collapse)
                    )
            ) {
                ScheduleHeaderActionLayout(
                    stacked = headerMetrics.stackedActions,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            lerpDp(headerMetrics.actionRowExpanded, headerMetrics.actionRowCollapsed, collapse)
                        )
                        .padding(horizontal = PagePadding)
                ) {
                    // 标题与分段控件同属一个纵列、芯片在右侧垂直居中——这是改动前
                    // 的构图。把芯片单独提到标题行会在分段控件右边空出一大块。
                    Column(
                        modifier = Modifier.testTag("schedule-header-title"),
                        verticalArrangement = Arrangement.spacedBy(
                            lerpDp(headerMetrics.titleGap, 0.dp, collapse)
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // 学期信息从分段控件"凝聚"进标题：控件缩掉的同时
                            // 「下学期 ·」从左侧挤出、把标题推向右，标题同时在缩字号。
                            // 一段连续形变，而不是字符串在某一帧突变。
                            SemesterTitlePrefix(
                                text = if (isNextSemester) "下学期 · " else "本学期 · ",
                                fontSize = headerMetrics.prefixFontSize,
                                progress = ((collapse - 0.45f) / 0.55f).coerceIn(0f, 1f)
                            )
                            Text(
                                text = "第 $currentWeek 周",
                                modifier = Modifier.focusRequester(headerFocus).focusable().graphicsLayer {
                                    alpha = (1f - kotlin.math.abs(weekOffset) * 2f).coerceIn(0f, 1f)
                                    translationX = -weekOffset * 12.dp.toPx()
                                },
                                fontSize = lerpSp(26f, 17f, collapse),
                                fontWeight = FontWeight.Bold,
                                color = titleColor,
                                letterSpacing = 0.sp,
                                maxLines = 1
                            )
                        }

                        // 收拢方式：容器高度与内容缩放【同一个系数】，于是绘制尺寸
                        // 永远等于容器高度——既不会被裁出一条平边，也不会被压扁。
                        // 单独缩容器高度是上一版"文字挤出轨道"的成因：分段控件内部
                        // 写死 height(36.dp)，父约束一小它就被压扁。
                        val segmentFraction = (1f - collapse).coerceIn(0f, 1f)
                        Box(modifier = Modifier.height(HeaderSegmentHeight * segmentFraction)) {
                            SemesterCapsuleToggle(
                                isNextSemester = isNextSemester,
                                onClick = onToggleSemester,
                                width = headerMetrics.semesterWidth,
                                modifier = Modifier.graphicsLayer {
                                    alpha = (segmentFraction * 2.2f - 0.2f).coerceIn(0f, 1f)
                                    scaleX = segmentFraction
                                    scaleY = segmentFraction
                                    transformOrigin = TransformOrigin(0f, 0f)
                                }
                            )
                        }
                    }

                    com.k2767.course.ui.system.TopBarActionRail(Modifier.testTag("schedule-header-actions"), spacing = lerpDp(4.dp, 3.dp, collapse)) {
                        val buttonSize = lerpDp(34.dp, 30.dp, collapse)
                        val iconSize = lerpDp(16.dp, 15.dp, collapse)
                        action(
                            index = 0,
                            icon = if (dayViewEnabled) Icons.Default.ViewWeek else Icons.Default.ViewDay,
                            contentDescription = if (dayViewEnabled) "切换到周视图" else "切换到日视图",
                            onClick = onToggleDayView,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 1,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = "上一周",
                            onClick = onPrevClick,
                            enabled = currentWeek > 1,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 2,
                            icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "下一周",
                            onClick = onNextClick,
                            enabled = currentWeek < 25,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 3,
                            icon = Icons.Default.Share,
                            contentDescription = "导出",
                            onClick = onExportClick,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                        action(
                            index = 4,
                            icon = Icons.Default.Settings,
                            contentDescription = "设置",
                            onClick = onSettingsClick,
                            buttonSize = buttonSize,
                            iconSize = iconSize
                        )
                    }
                }

                Spacer(Modifier.height(lerpDp(headerMetrics.titleGap, 0.dp, collapse)))

                // 左侧时间列那一格里放当周周一的月份。没有开学日期就算不出日期，
                // 月份也就留空，不硬造一个「本月」。
                val monthDate = com.k2767.course.schedule.ScheduleDates.date(firstWeekDate, currentWeek, 1)
                val swipeAlpha = weekSwipeAlpha(weekOffset)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            lerpDp(
                                headerMetrics.weekRowExpanded,
                                headerMetrics.weekRowCollapsed,
                                collapse
                            )
                        )
                        .padding(horizontal = scheduleGridPadding()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.width(scheduleTimeColumnWidth() + ScheduleTimeColumnShadowWidth),
                        contentAlignment = Alignment.Center
                    ) {
                        if (monthDate != null) Text(
                            text = "${monthDate.get(Calendar.MONTH) + 1}月",
                            fontSize = lerpSp(9f, 8f, collapse),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = MaterialTheme.colorScheme.onSurfaceVariant.alpha * 0.72f * swipeAlpha
                            ),
                            maxLines = 1
                        )
                    }
                    weekLabels.forEachIndexed { index, day ->
                        val isToday = index + 1 == currentDayOfWeek && currentWeek == actualWeek
                        val date = com.k2767.course.schedule.ScheduleDates.date(firstWeekDate, currentWeek, index + 1)
                        CompactWeekdayLabel(
                            modifier = Modifier.weight(1f).testTag("schedule-weekday-${index + 1}"),
                            day = day,
                            isToday = isToday,
                            collapse = collapse,
                            date = date?.let { "${it.get(Calendar.MONTH) + 1}/${it.get(Calendar.DAY_OF_MONTH)}" },
                            weekOffset = weekOffset
                        )
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun ScheduleHeaderActionLayout(stacked: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val actions = measurables[1].measure(loose.copy(maxHeight = 48.dp.roundToPx()))
        val gap = if (stacked) 6.dp.roundToPx() else 8.dp.roundToPx()
        val title = measurables[0].measure(loose.copy(
            maxWidth = if (stacked) constraints.maxWidth else (constraints.maxWidth - actions.width - gap).coerceAtLeast(0),
            maxHeight = if (stacked) (constraints.maxHeight - actions.height - gap).coerceAtLeast(0) else constraints.maxHeight
        ))
        layout(constraints.maxWidth, constraints.maxHeight) {
            if (stacked) {
                title.placeRelative(0, 0)
                actions.placeRelative(constraints.maxWidth - actions.width, constraints.maxHeight - actions.height)
            } else {
                title.placeRelative(0, (constraints.maxHeight - title.height) / 2)
                actions.placeRelative(constraints.maxWidth - actions.width, (constraints.maxHeight - actions.height) / 2)
            }
        }
    }
}

/**
 * 标题左侧的学期前缀。宽度按 progress 缩放，于是它是"挤出来"的而不是"闪出来"的。
 * 用 Modifier.layout 改上报宽度，而不是 animateContentSize：后者需要先测到目标宽度
 * 再补一帧动画，跟手的折叠里会慢半拍。
 */
@Composable
private fun SemesterTitlePrefix(
    text: String,
    fontSize: TextUnit,
    progress: Float
) {
    if (progress <= 0.001f) return
    Box(
        modifier = Modifier
            .graphicsLayer { alpha = progress }
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val width = (placeable.width * progress).roundToInt()
                layout(width, placeable.height) { placeable.place(0, 0) }
            }
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.sp,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * 切周途中把日期淡出。留一段死区：pager 静止时 `currentPageOffsetFraction` 未必精确归零
 * （切周动画被别的目标位置打断就会留小数），而 `1 - |offset| × 2` 在偏移 0.5 时就是全透明，
 * 于是日期整行看不见、只有星期还在——看起来像"这个版本没有日期"。
 */
private fun weekSwipeAlpha(offset: Float): Float =
    (1f - (kotlin.math.abs(offset) - 0.15f).coerceAtLeast(0f) / 0.5f).coerceIn(0f, 1f)

@Composable
private fun CompactWeekdayLabel(
    modifier: Modifier = Modifier,
    day: String,
    isToday: Boolean,
    collapse: Float = 0f,
    date: String? = null,
    weekOffset: Float = 0f
) {
    val textColor by animateColorAsState(
        targetValue = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "weekdayColor"
    )
    val dotScale by animateFloatAsState(
        targetValue = if (isToday) 1f else 0.65f,
        animationSpec = MotionSpring.gentle(),
        label = "weekdayDotScale"
    )
    // 折叠后只留今天那一点——"只留必要信息"落到最小的一处
    val dotColor = if (isToday) {
        NeuPrimary
    } else {
        NeuDivider.copy(alpha = NeuDivider.alpha * (1f - collapse))
    }

    Column(
        modifier = modifier.semantics(mergeDescendants = true) { if (isToday) stateDescription = "今天" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(lerpDp(3.dp, 2.dp, collapse))
    ) {
        Text(
            text = day,
            fontSize = lerpSp(13f, 11.5f, collapse),
            fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Medium,
            color = textColor,
            maxLines = 1
        )
        if (date != null) Text(date, fontSize = lerpSp(9f, 8f, collapse), color = textColor,
            modifier = Modifier.graphicsLayer { alpha = weekSwipeAlpha(weekOffset) }, maxLines = 1)
        else Box(
            modifier = Modifier
                .scale(dotScale)
                .size(lerpDp(5.dp, 4.dp, collapse))
                .background(color = dotColor, shape = CircleShape)
        )
    }
}

@Composable
private fun SemesterCapsuleToggle(
    isNextSemester: Boolean,
    onClick: () -> Unit,
    width: Dp,
    modifier: Modifier = Modifier
) {
    SystemCompactSegmentedControl(
        options = listOf("本学期", "下学期"),
        selectedIndex = if (isNextSemester) 1 else 0,
        onSelect = { selectedIndex ->
            val nextSemesterSelected = selectedIndex == 1
            if (nextSemesterSelected != isNextSemester) {
                onClick()
            }
        },
        // requiredHeight 无视父约束：容器在收拢过程中比 36dp 矮，
        // 普通 height 会被钳成压扁，轨道里的文字随之挤出。
        modifier = modifier
            .width(width)
            .requiredHeight(HeaderSegmentHeight)
    )
}


@Composable
fun ScheduleGrid(
    courses: List<ScheduleCourseUi>,
    currentWeek: Int,
    periodTimes: List<PeriodTimeUi> = emptyList(),
    periodCount: Int = 12,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    /**
     * scrollState 由调用方持有：顶栏的玻璃浓度要跟着它推导，而且所有 pager 页
     * 共用一个，左右切周时纵向位置不会跳回顶部。
     */
    scrollState: ScrollState = rememberScrollState(),
    /**
     * 顶栏高度。**施加在 verticalScroll 内部**——容器保持全出血，于是滚动位置 0
     * 时内容起始于顶栏之下，滚起来则从顶栏底下穿过，顶栏芯片才有东西可折射。
     * 加在容器上（`Modifier.padding(paddingValues)`）就成了"内容被推到顶栏下方"，
     * 芯片背后永远是空的。
     */
    topInset: Dp = 0.dp
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val weeklyCourses = remember(courses, currentWeek) {
        courses.filter { isInWeek(it.weeks, currentWeek) }
    }
    val timeColumnWidth = scheduleTimeColumnWidth()
    val gridPadding = scheduleGridPadding()
    val dayColumnWidthPx = with(LocalDensity.current) {
        ((constraints.maxWidth - gridPadding.roundToPx() * 2 - timeColumnWidth.roundToPx() -
            ScheduleTimeColumnShadowWidth.roundToPx()) / 7f).roundToInt()
    }
    val periodHeight = rememberCoursePeriodHeight(courses, dayColumnWidthPx, schedulePeriodHeight())
    val totalHeight = periodHeight * periodCount
    val darkGrid = com.k2767.course.ui.system.rememberGlassDarkTheme()
    val gridTint = MaterialTheme.colorScheme.surface.copy(alpha =
        if (darkGrid || com.k2767.course.manager.AppearanceSettingsManager.mode != com.k2767.course.manager.WallpaperMode.Preset) 0.86f else 0.18f)
    val timeColumnTint = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (darkGrid) 0.4f else 0.28f)

    Column(
        modifier = Modifier
            .testTag("schedule-grid-$currentWeek")
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(
                start = gridPadding,
                end = gridPadding,
                top = topInset + 8.dp,
                bottom = com.k2767.course.ui.system.LocalAppOverlayBottomInset.current + 24.dp
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 去卡片化：轻玻璃衬底压到很低，让壁纸渐变与网格线透上来。
        // 这个 alpha 是"顶栏芯片能不能看出折射"的直接开关——衬底一厚，
        // 白芯片压在白板上，折射再准也是白压白。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(gridTint)
                .border(
                    0.5.dp,
                    Color.White.copy(alpha = 0.34f),
                    RoundedCornerShape(24.dp)
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalHeight)
            ) {
                Column(
                    modifier = Modifier
                        .width(timeColumnWidth)
                        .fillMaxHeight()
                        .background(timeColumnTint),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    for (i in 1..periodCount) {
                        val periodTime = periodTimes.find { it.period == i }
                        Box(
                            modifier = Modifier
                                .height(periodHeight)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            Column(
                                modifier = Modifier.padding(top = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = i.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (periodTime != null) {
                                    Text(
                                        text = periodTime.startTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = periodTime.endTime,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }

                // 时间列右侧阴影，柔和过渡
                Box(
                    modifier = Modifier
                        .width(ScheduleTimeColumnShadowWidth)
                        .fillMaxHeight()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    NeuDivider.copy(alpha = 0.10f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    TimetableBackground(
                        periodCount = periodCount,
                        periodHeight = periodHeight
                    )
                    TimetableLayout(
                        courses = weeklyCourses,
                        periodCount = periodCount,
                        periodHeight = periodHeight,
                        onCourseClick = onCourseClick
                    )

                    if (weeklyCourses.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            SystemEmptyState(
                                title = "本周暂无课程",
                                message = "可以切换周次、学期，或在设置中管理自定义课程。",
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }

        if (weeklyCourses.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SystemStatusBadge(
                    text = "本周 ${weeklyCourses.size} 门课程",
                    tone = SystemTone.Info
                )
                if (weeklyCourses.any { it.isCustom }) {
                    SystemStatusBadge(
                        text = "含自定义课程",
                        tone = SystemTone.Warning
                    )
                }
            }
        }
    }
    }
}

private fun courseNameStyle(base: TextStyle, duration: Int): TextStyle = base.copy(
    fontWeight = FontWeight.Bold,
    fontSize = when (duration) { 1 -> 10.5.sp; 2 -> 11.sp; else -> 11.5.sp },
    lineHeight = when (duration) { 1 -> 12.sp; 2 -> 12.5.sp; else -> 13.sp },
    letterSpacing = (-0.2).sp
)

private fun courseLocationStyle(base: TextStyle, duration: Int): TextStyle = base.copy(
    fontWeight = FontWeight.Normal,
    fontSize = if (duration <= 2) 9.sp else 9.5.sp,
    lineHeight = if (duration <= 2) 10.5.sp else 11.sp
)

private fun ScheduleCourseUi.hasCardStatus() = hasConflict || isCurrent || isCustom ||
    !com.k2767.course.schedule.ScheduleWeeks.parse(weeks).valid

/** The same measured row height drives the time rail, grid lines and course spans. */
@Composable
private fun rememberCoursePeriodHeight(courses: List<ScheduleCourseUi>, columnWidthPx: Int, minimum: Dp): Dp {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 128)
    val style = MaterialTheme.typography.labelSmall
    return remember(courses, columnWidthPx, minimum, density, measurer, style) {
        with(density) {
            val contentWidth = (columnWidthPx - 2 * 1.dp.roundToPx() - 5.dp.roundToPx() - 2.dp.roundToPx()).coerceAtLeast(1)
            var rowHeight = minimum.toPx()
            for (course in courses) {
                val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
                // 行数分配：课程名最多 3 行、地点最多 2 行。
                // 七列均分后每列内容宽约 42dp，「至善楼406」一行装不下；与其截成「至善…」，
                // 不如让它折到第二行完整显示。课程名从 4 行收到 3 行腾出这段高度，配合地点
                // 行高收紧，每节总高反而略降（3×13 + 2×10.5 = 60 < 4×13 + 1×11 = 63）。
                // 渲染端 CourseCard 必须用同样的 maxLines，否则测量与布局会脱节。
                val name = measurer.measure(course.name, courseNameStyle(style, duration),
                    maxLines = 3, overflow = TextOverflow.Ellipsis, constraints = Constraints(maxWidth = contentWidth))
                val hasStatus = course.hasCardStatus()
                val locationHeight = if (course.location.isNotBlank()) measurer.measure(
                    compactLocation(course.location), courseLocationStyle(style, duration),
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                    constraints = Constraints(maxWidth = contentWidth)
                ).size.height else 0
                val informationHeight = locationHeight + if (hasStatus) 11.dp.roundToPx() else 0
                // Includes card insets, content padding, inter-line gap and rounding slack.
                val required = name.size.height + informationHeight + 9.dp.toPx()
                rowHeight = maxOf(rowHeight, ceil(required / duration))
            }
            ceil(rowHeight).toDp()
        }
    }
}

@Composable
private fun TimetableBackground(
    periodCount: Int,
    periodHeight: Dp
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // 去斑马纹：透明列 + 极淡分隔线，壁纸从网格间透出
        Row(modifier = Modifier.fillMaxSize()) {
            repeat(7) { dayIndex ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    if (dayIndex < 6) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                                .width(1.dp)
                                .background(NeuDivider.copy(alpha = 0.18f))
                        )
                    }
                }
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            repeat(periodCount) { rowIndex ->
                Box(
                    modifier = Modifier
                        .height(periodHeight)
                        .fillMaxWidth()
                ) {
                    if (rowIndex < periodCount - 1) {
                        HorizontalDivider(
                            modifier = Modifier.align(Alignment.BottomCenter),
                            color = NeuDivider.copy(alpha = 0.15f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimetableLayout(
    courses: List<ScheduleCourseUi>,
    periodCount: Int = 12,
    periodHeight: Dp = SchedulePeriodHeight,
    modifier: Modifier = Modifier,
    onCourseClick: (ScheduleCourseUi) -> Unit
) {
    Layout(
        modifier = modifier.fillMaxSize(),
        content = {
            courses.forEach { course ->
                androidx.compose.runtime.key(course.id) { CourseCard(course = course, onClick = { onCourseClick(course) }) }
            }
        }
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val columnWidth = width / 7f
        val cardInset = 1.dp.roundToPx()
        val pxPerPeriod = periodHeight.toPx()

        val placeables = measurables.mapIndexed { index, measurable ->
            val course = courses[index]
            val duration = course.endPeriod - course.startPeriod + 1
            val height = (duration * pxPerPeriod).roundToInt()
            val cardWidth = (columnWidth.roundToInt() - cardInset * 2).coerceAtLeast(1)
            val cardHeight = (height - cardInset * 2).coerceAtLeast(1)

            measurable.measure(
                constraints.copy(
                    minWidth = cardWidth,
                    maxWidth = cardWidth,
                    minHeight = cardHeight,
                    maxHeight = cardHeight
                )
            )
        }

        layout(width, (periodCount * pxPerPeriod).roundToInt()) {
            placeables.forEachIndexed { index, placeable ->
                val course = courses[index]
                val dayIndex = (course.day - 1).coerceIn(0, 6)
                val startPeriodIndex = (course.startPeriod - 1).coerceIn(0, periodCount - 1)

                val x = (dayIndex * columnWidth).roundToInt() + cardInset
                val y = (startPeriodIndex * pxPerPeriod).roundToInt() + cardInset

                placeable.place(x, y)
            }
        }
    }
}

@Composable
fun CourseCard(course: ScheduleCourseUi, onClick: () -> Unit) {
    val darkCard = com.k2767.course.ui.system.rememberGlassDarkTheme()
    val focusRequester = remember { FocusRequester() }
    val focusRegistry = LocalScheduleFocus.current
    var cardBounds by remember { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    val unknownWeeks = remember(course.weeks) { !com.k2767.course.schedule.ScheduleWeeks.parse(course.weeks).valid }
    DisposableEffect(course.id, focusRegistry) {
        focusRegistry?.register(course.id, focusRequester)
        onDispose { focusRegistry?.remove(course.id, focusRequester) }
    }
    val duration = (course.endPeriod - course.startPeriod + 1).coerceAtLeast(1)
    // 彩色半透玻璃 tile：加深填充保证壁纸上可读，边缘细亮线似透镜
    val containerColor = androidx.compose.ui.graphics.lerp(MaterialTheme.colorScheme.surface, course.color, if (course.isCustom) 0.30f else 0.22f)
    val borderColor = course.color.copy(alpha = 0.50f)
    val accentColor = course.color.copy(alpha = 0.85f)

    // 只显示楼栋 + 教室：校区前缀每门课都重复一遍，裁掉后真正的楼栋与教室才放得下。
    val displayLocation = compactLocation(course.location)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !com.k2767.course.ui.system.rememberGlassAccessibilityMode().reduceMotion) 0.97f else 1f,
        animationSpec = com.k2767.course.ui.theme.MotionProfile.iconSpring(),
        label = "courseCardScale"
    )

    Surface(
        modifier = Modifier
            .testTag("schedule-course-${course.id}")
            .focusRequester(focusRequester)
            .onGloballyPositioned {
                cardBounds = it.boundsInWindow()
                focusRegistry?.place(course.id, it.boundsInWindow())
            }
            .semantics {
                stateDescription = listOfNotNull(if (course.hasConflict) "时间冲突" else null,
                    if (course.isCurrent) "正在上课" else null, if (course.isCustom) "自定义课程" else null,
                    if (unknownWeeks) "周次待核对" else null).joinToString("，")
            }
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    // A course can also exist in the pager's adjacent week. The clicked copy
                    // owns the opening origin and the focus returned after dismissal.
                    focusRegistry?.register(course.id, focusRequester)
                    cardBounds?.let { focusRegistry?.place(course.id, it) }
                    onClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(0.6.dp, borderColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 顶部玻璃高光渐变：模拟光源照射的反射
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) { (duration * 20).dp })
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = if (darkCard) 0.08f else 0.35f),
                                Color.White.copy(alpha = if (darkCard) 0.02f else 0.05f),
                                Color.Transparent
                            )
                        )
                    )
            )
            // 左侧彩色指示条：课程颜色标识
            Box(
                modifier = Modifier
                    .width((3f + (1f - scale) * 50f).dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accentColor,
                                accentColor.copy(alpha = 0.3f)
                            )
                        )
                    )
            )
            // 内容区域
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 5.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                Text(
                    text = course.name,
                    style = courseNameStyle(MaterialTheme.typography.labelSmall, duration),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                if (displayLocation.isNotBlank()) {
                    Text(
                        text = displayLocation,
                        modifier = Modifier.fillMaxWidth().testTag("schedule-location-${course.id}"),
                        style = courseLocationStyle(MaterialTheme.typography.labelSmall, duration),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        // 与 rememberCoursePeriodHeight 的测量保持一致：最多两行。
                        // 列宽只有约 42dp，「至善楼406」一行放不下——折到第二行完整显示，
                        // 比截成「至善…」有用得多。
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                // A status symbol must not reserve a column beside every line of a long address.
                if (course.hasConflict || course.isCurrent || course.isCustom || unknownWeeks) Icon(
                        imageVector = when { course.hasConflict || unknownWeeks -> Icons.Default.Warning
                            course.isCurrent -> Icons.Default.PlayArrow
                            else -> Icons.Default.Edit },
                        contentDescription = null,
                        modifier = Modifier.size(10.dp),
                        tint = if (course.hasConflict || unknownWeeks) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
            }
        }
    }
}

internal fun isInWeek(weeks: String?, week: Int): Boolean {
    return com.k2767.course.schedule.ScheduleWeeks.parse(weeks).visibleIn(week)
}

/**
 * 课表卡片上的地点只保留「楼栋 + 教室」，裁掉开头的「XX校区」。
 *
 * 校区名几乎每门课都重复一遍，而卡片里最宝贵的就是那一行——裁掉之后
 * 「至善楼406」这类真正管用的信息才显示得下。校区信息在课程详情里仍然完整。
 * 只有校区、没有楼栋时不裁剪，避免把地点变成空串。
 */
internal fun compactLocation(location: String): String {
    if (location.isBlank()) return location
    val stripped = location.replace(Regex("^[^\\s]*校区[\\s·・,，、]*"), "").trim()
    return stripped.ifBlank { location }
}

/**
 * 日视图：把当天的课按时序纵向列出来，一屏能装下的信息比网格多（教室、教师都能完整显示）。
 *
 * 与周视图**共用同一个 scrollState**：顶栏折叠行程是由 gridScrollState 推导的
 * （见 ScheduleScreen 里的 collapse），共用才能保证切换视图时顶栏不会跳一下。
 *
 * [topInset] 同样传「状态栏 + 顶栏展开高」这个常量，而不是
 * paddingValues.calculateTopPadding()——后者随顶栏收缩而变小，而它作用在
 * verticalScroll 内部，会让内容被多提一份。
 */
@Composable
private fun ScheduleDayView(
    courses: List<ScheduleCourseUi>,
    dayOfWeek: Int,
    currentWeek: Int,
    periodTimes: List<PeriodTimeUi>,
    firstWeekDate: String?,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    scrollState: ScrollState,
    topInset: Dp
) {
    // 必须按当前周过滤：否则单双周的课会在同一时段一起冒出来，
    // 「共 N 节」也会把不属于本周的算进去。周视图正是用 isInWeek 决定显示哪些。
    val dayCourses = remember(courses, dayOfWeek, currentWeek) {
        courses.filter { it.day == dayOfWeek && isInWeek(it.weeks, currentWeek) }
            .sortedBy { it.startPeriod }
    }
    val date = remember(firstWeekDate, currentWeek, dayOfWeek) {
        com.k2767.course.schedule.ScheduleDates.date(firstWeekDate, currentWeek, dayOfWeek)
    }
    val dayLabel = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")
        .getOrElse(dayOfWeek - 1) { "" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(top = topInset)
            .padding(horizontal = PagePadding)
    ) {
        val title = if (date != null) {
            "${date.get(Calendar.MONTH) + 1}月${date.get(Calendar.DAY_OF_MONTH)}日 · $dayLabel"
        } else {
            dayLabel
        }
        Text(
            text = title,
            modifier = Modifier.testTag("schedule-day-title").padding(top = 16.dp, bottom = 2.dp),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = if (dayCourses.isEmpty()) "这天没有课" else "共 ${dayCourses.size} 节",
            modifier = Modifier.padding(bottom = 6.dp),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (dayCourses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "换一天看看，或切回周视图",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            dayCourses.forEach { course ->
                DayCourseRow(
                    course = course,
                    periodTimes = periodTimes,
                    onClick = { onCourseClick(course) }
                )
            }
        }
        Spacer(
            Modifier.height(
                com.k2767.course.ui.system.LocalAppOverlayBottomInset.current + 32.dp
            )
        )
    }
}

/**
 * 日视图里的一行课：左侧是上课时段（起止时间 + 中间一段连接竖线），右侧是课程卡片。
 * 卡片配色与网格卡片同一套来源（course.color），切视图时同一门课颜色不变。
 */
@Composable
private fun DayCourseRow(
    course: ScheduleCourseUi,
    periodTimes: List<PeriodTimeUi>,
    onClick: () -> Unit
) {
    val startTime = periodTimes.firstOrNull { it.period == course.startPeriod }?.startTime.orEmpty()
    val endTime = periodTimes.firstOrNull { it.period == course.endPeriod }?.endTime.orEmpty()
    val unknownWeeks = remember(course.weeks) {
        !com.k2767.course.schedule.ScheduleWeeks.parse(course.weeks).valid
    }
    val containerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        course.color,
        if (course.isCustom) 0.30f else 0.22f
    )
    val hasStatus = course.hasConflict || course.isCurrent || course.isCustom || unknownWeeks

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(56.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = startTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text(
                text = endTime,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .testTag("schedule-day-course-${course.id}")
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(0.6.dp, course.color.copy(alpha = 0.50f))
        ) {
            Row(modifier = Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(course.color.copy(alpha = 0.85f))
                )
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "第${course.startPeriod}-${course.endPeriod}节",
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasStatus) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = when {
                                    course.hasConflict || unknownWeeks -> Icons.Default.Warning
                                    course.isCustom -> Icons.Default.Edit
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = if (course.hasConflict || unknownWeeks) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (course.location.isNotBlank() || course.teacher.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (course.location.isNotBlank()) {
                                Text(
                                    text = compactLocation(course.location),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (course.teacher.isNotBlank()) Text(
                                text = course.teacher,
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
