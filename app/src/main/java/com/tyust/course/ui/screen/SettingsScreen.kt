package com.tyust.course.ui.screen

import com.tyust.course.ui.system.WallpaperCaption

import com.tyust.course.ui.theme.moduleEntrance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AssignmentInd
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentPasteSearch
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.InsetGroupedRow
import com.tyust.course.ui.system.InsetGroupedSection
import com.tyust.course.ui.system.LiquidSwitch
import com.tyust.course.ui.system.PagePadding
import com.tyust.course.ui.system.SectionSpacing
import com.tyust.course.ui.system.SystemCard
import com.tyust.course.ui.system.SystemStatusBadge
import com.tyust.course.ui.system.SystemTone
import com.tyust.course.ui.system.SystemTopBar
import com.tyust.course.ui.theme.SemanticDanger

@Composable
fun SettingsScreen(
    studentName: String,
    studentId: String,
    schoolName: String,
    currentVersion: String = "1.0.0",
    onSchoolSelect: () -> Unit,
    onCookieConfig: () -> Unit,
    onAccountManage: () -> Unit = {},
    savedAccountCount: Int = 0,
    onClearCache: () -> Unit,
    onCheckUpdate: () -> Unit,
    onAbout: () -> Unit,
    onCredits: () -> Unit,
    onLogout: () -> Unit,
    isDemoMode: Boolean = false,
    onQuotaClick: () -> Unit = {},
    onRefreshCookieClick: () -> Unit = {},
    onLogExport: () -> Unit = {},
    onErrorReport: () -> Unit = {},
    onSchoolAdaptation: () -> Unit = {},
    onWallpaperSelect: () -> Unit = {},
    wallpaperName: String = "",
    themeName: String = "跟随系统",
    onThemeSelect: () -> Unit = {},
    startupPageName: String = "课程",
    onStartupPageSelect: () -> Unit = {},
    glassEffectEnabled: Boolean = true,
    onGlassEffectChange: (Boolean) -> Unit = {},
    navBarAutoCollapseEnabled: Boolean = true,
    onNavBarAutoCollapseChange: (Boolean) -> Unit = {},
    usageEnabled: Boolean = true,
    onUsageEnabledChange: (Boolean) -> Unit = {},
    classQuietSummary: String = "按课表静音，下课恢复原来的声音模式",
    onClassQuietSettings: () -> Unit = {},
    isSuper: Boolean = false,
    quotaInfo: String = "",
    canRefreshCookie: Boolean = false,
    isRefreshingCookie: Boolean = false,
    academicSystemName: String = "",
    onAcademicSupport: () -> Unit = {},
    onAcademicPlugins: () -> Unit = {},
    onCampusServices: () -> Unit = {},
    onQuickFeedback: () -> Unit = {}
) {
    val scrollState = rememberScrollState()
    // 折叠进度随滚动偏移连续变化（约 96px 行程），全程跟手
    val headerCollapse by remember {
        derivedStateOf { (scrollState.value / 96f).coerceIn(0f, 1f) }
    }
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            Box(Modifier.moduleEntrance(0)) {
            SystemTopBar(
                title = "设置",
                subtitle = "账号、外观与应用偏好",
                collapseFraction = headerCollapse
            )
            }
        }
    ) { padding ->
        // 内容延伸到玻璃顶栏下方，滚动时从顶栏底下穿过（padding 施加在滚动内容内部）
        Column(
            modifier = Modifier
                .fillMaxSize().wrapContentWidth(androidx.compose.ui.Alignment.CenterHorizontally).widthIn(max = 680.dp)
                .verticalScroll(scrollState)
                .padding(
                    start = PagePadding,
                    end = PagePadding,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = com.tyust.course.ui.system.LocalAppOverlayBottomInset.current + 24.dp
                ),
            verticalArrangement = Arrangement.spacedBy(SectionSpacing)
        ) {
            SettingsHeader(
                name = studentName,
                studentId = studentId,
                school = schoolName,
                isSuper = isSuper,
                quotaInfo = quotaInfo,
                canRefreshCookie = canRefreshCookie,
                isRefreshingCookie = isRefreshingCookie,
                onQuotaClick = onQuotaClick,
                onRefreshCookieClick = onRefreshCookieClick
            )

            InsetGroupedSection(Modifier.moduleEntrance(2), header = "账号与教务") {
                SettingsRow(
                    icon = Icons.Outlined.School,
                    iconTint = Color(0xFF0A84FF),
                    title = "学校与教务",
                    subtitle = listOf(schoolName.ifBlank { "未选择学校" }, academicSystemName).filter(String::isNotBlank).joinToString(" · "),
                    onClick = onSchoolSelect
                )
                SettingsRow(
                    icon = Icons.Outlined.AssignmentInd,
                    iconTint = Color(0xFF18796B),
                    title = "插件中心",
                    subtitle = "本校适配与已安装插件",
                    onClick = onAcademicPlugins
                )
                SettingsRow(
                    icon = Icons.Outlined.School,
                    iconTint = Color(0xFF18796B),
                    title = "校园服务",
                    subtitle = "本校服务与可配置页面",
                    onClick = onCampusServices
                )
                SettingsRow(
                    icon = Icons.Outlined.AssignmentInd,
                    iconTint = if (isSuper) Color(0xFF34C759) else Color(0xFF5E5CE6),
                    title = "配额 / 身份",
                    subtitle = if (isSuper) "超级用户 · 无限制" else quotaInfo.ifBlank { "普通用户" },
                    onClick = onQuotaClick
                )
                SettingsRow(
                    icon = Icons.Outlined.ManageAccounts,
                    iconTint = Color(0xFF32ADE6),
                    title = "账号",
                    subtitle = if (savedAccountCount > 0) {
                        "已保存 $savedAccountCount 个账号 · 可切换或删除"
                    } else {
                        "切换账号、删除已存密码与账号"
                    },
                    onClick = onAccountManage, showDivider = false
                )

            }

            InsetGroupedSection {
                SettingsRow(icon = Icons.AutoMirrored.Filled.ExitToApp,
                    iconTint = SemanticDanger,
                    title = if (isDemoMode) "退出预览" else "退出登录",
                    subtitle = if (isDemoMode) "返回登录页，保留真实账号与缓存" else "结束当前登录，保留已存账号",
                    onClick = onLogout, showDivider = false)
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "外观") {
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "主题",
                    subtitle = themeName,
                    onClick = onThemeSelect
                )
                SettingsRow(
                    icon = Icons.Outlined.Palette,
                    iconTint = Color(0xFFBF5AF2),
                    title = "背景",
                    subtitle = wallpaperName.ifBlank { "选择背景色或图片" },
                    onClick = onWallpaperSelect
                )
                InsetGroupedRow(
                    icon = Icons.Outlined.AutoAwesome,
                    iconTint = Color(0xFF5AC8FA),
                    title = "液态玻璃",
                    subtitle = if (glassEffectEnabled) {
                        "折射、色散与跟手形变"
                    } else {
                        "已关闭，改用不透明材质，更省电也更清晰"
                    },
                    trailing = {
                        LiquidSwitch(
                            checked = glassEffectEnabled,
                            onCheckedChange = onGlassEffectChange
                        )
                    }
                )
                InsetGroupedRow(
                    icon = Icons.Outlined.Home,
                    iconTint = MaterialTheme.colorScheme.primary,
                    title = "底部导航栏自动收起",
                    subtitle = if (navBarAutoCollapseEnabled) {
                        "向下浏览时收起，向上浏览时展开"
                    } else {
                        "始终保持展开"
                    },
                    showDivider = false,
                    trailing = {
                        LiquidSwitch(
                            checked = navBarAutoCollapseEnabled,
                            onCheckedChange = onNavBarAutoCollapseChange
                        )
                    }
                )
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "课表自动化") {
                SettingsRow(icon = Icons.Outlined.VolumeOff, iconTint = MaterialTheme.colorScheme.primary,
                    title = "上课自动静音", subtitle = classQuietSummary,
                    onClick = onClassQuietSettings, showDivider = false)
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "应用与支持") {
                SettingsRow(
                    icon = Icons.Outlined.Home,
                    iconTint = Color(0xFF5E5CE6),
                    title = "启动首屏",
                    subtitle = "$startupPageName · 下次启动时显示",
                    onClick = onStartupPageSelect
                )
                SettingsRow(
                    icon = Icons.Outlined.SystemUpdate,
                    iconTint = Color(0xFF34C759),
                    title = "检查更新",
                    subtitle = "当前版本 $currentVersion",
                    onClick = onCheckUpdate
                )
                SettingsRow(
                    icon = Icons.Outlined.ContentPasteSearch,
                    iconTint = Color(0xFF18796B),
                    title = "问题反馈",
                    subtitle = "跳转 GitHub Issue，自动带上版本与机型（需登录）",
                    onClick = onQuickFeedback
                )
                SettingsRow(
                    icon = Icons.Outlined.ContentPasteSearch,
                    iconTint = Color(0xFF64D2FF),
                    title = "导出日志",
                    subtitle = "导出本地运行日志",
                    onClick = onLogExport
                )
                SettingsRow(
                    icon = Icons.Outlined.ContentPasteSearch,
                    iconTint = Color(0xFF64D2FF),
                    title = "最近错误",
                    subtitle = "查看、复制错误报告并反馈",
                    onClick = onErrorReport
                )
                // 「统一登录适配」依赖上游作者后端，本发行版已切断该服务；
                // 入口一并隐藏，避免用户提交申请后等一个不会到来的回复。
                if (com.tyust.course.SelfHostConfig.ENABLE_SCHOOL_SERVICE_BACKEND) {
                    SettingsRow(
                        icon = Icons.Outlined.School,
                        iconTint = Color(0xFF0A84FF),
                        title = "统一登录适配",
                        subtitle = "申请学校支持或查看适配进度",
                        onClick = onSchoolAdaptation
                    )
                }
                SettingsRow(
                    icon = Icons.Outlined.Info,
                    iconTint = Color(0xFF8E8E93),
                    title = "更新历史",
                    subtitle = "应用的更新日志与时间线",
                    onClick = onAbout
                )
                SettingsRow(
                    icon = Icons.Outlined.FavoriteBorder,
                    iconTint = Color(0xFFFF375F),
                    title = "致谢与关于",
                    subtitle = "开源项目致谢与作者声明",
                    onClick = onCredits,
                    showDivider = false
                )
            }

            InsetGroupedSection(Modifier.moduleEntrance(3), header = "数据与安全") {
                // 统计上报已在 SelfHostConfig 切断，开关也就没有存在的意义。
                if (com.tyust.course.SelfHostConfig.ENABLE_ANONYMOUS_USAGE_STATS) {
                    InsetGroupedRow(
                        title = "匿名使用统计",
                        subtitle = "统计每日活跃与问卷入口点击；关闭后停止上报",
                        trailing = { LiquidSwitch(checked = usageEnabled, onCheckedChange = onUsageEnabledChange) }
                    )
                }
                SettingsRow(
                    icon = Icons.Outlined.Delete,
                    iconTint = Color(0xFFFF9F0A),
                    title = "清除缓存",
                    subtitle = "释放本地存储空间",
                    onClick = onClearCache,
                    showDivider = false
                )
            }

            WallpaperCaption(
                text = "卡皮巴拉教务助手 · $currentVersion",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun SettingsHeader(
    name: String,
    studentId: String,
    school: String,
    isSuper: Boolean,
    quotaInfo: String,
    canRefreshCookie: Boolean,
    isRefreshingCookie: Boolean,
    onQuotaClick: () -> Unit,
    onRefreshCookieClick: () -> Unit
) {
    // 裸玻璃容器（不走 Surface，避免 elevation 阴影在半透色下泛白）
    val heroShape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .moduleEntrance(1)
            .clip(heroShape)
            .background(com.tyust.course.ui.system.glassSurfaceColor())
            .border(0.5.dp, com.tyust.course.ui.system.glassBorderColor(), heroShape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier.size(40.dp).background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.take(1).ifBlank { "同" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = name.ifBlank { "同学" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = school.ifBlank { "未选择学校" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isSuper) {
                SystemStatusBadge(
                    text = "超级用户",
                    tone = SystemTone.Success
                )
            }
            if (quotaInfo.isNotBlank()) {
                Box(modifier = Modifier.clickable(onClick = onQuotaClick)) {
                    SystemStatusBadge(
                        text = if (isSuper) "无限制" else "配额 $quotaInfo",
                        tone = if (isSuper) SystemTone.Info else SystemTone.Neutral
                    )
                }
            }
            if (canRefreshCookie) {
                Box(
                    modifier = Modifier.clickable(
                        enabled = !isRefreshingCookie,
                        onClick = onRefreshCookieClick
                    )
                ) {
                    SystemStatusBadge(
                        text = if (isRefreshingCookie) "更新中" else "更新 Cookie",
                        tone = SystemTone.Info
                    )
                }
            }
        }
    }
}

/** 设置行：InsetGroupedRow + 彩色图标 chip + chevron。 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit
) {
    InsetGroupedRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        showDivider = showDivider,
        onClick = onClick,
        trailing = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}
