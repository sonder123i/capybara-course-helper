package com.k2767.course.ui.route

import com.k2767.course.ui.system.GlassToaster
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.ui.system.SystemConfirmDialog
import com.k2767.course.ui.system.SystemSecondaryButton
import com.k2767.course.ui.system.SystemPrimaryButton
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.k2767.course.LoginActivity
import com.k2767.course.login.PasswordLoginCallback
import com.k2767.course.login.PasswordLoginGatewayFactory
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.manager.StartupPagePreferences
import com.k2767.course.manager.UserManager
import com.k2767.course.network.CourseApiClient
import com.k2767.course.ui.screen.SettingsScreen
import com.k2767.course.ui.screen.SchoolAdaptationFlow
import com.k2767.course.update.UpdateManager
import com.k2767.course.update.UpdateDialog
import com.k2767.course.activation.ActivationManager
import com.k2767.course.manager.StudentLimitManager
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.LinearProgressIndicator
import com.k2767.course.ui.system.SystemDivider
import com.k2767.course.ui.system.SystemStatusBadge
import com.k2767.course.ui.system.SystemTone
import com.k2767.course.ui.theme.NeuPrimary
import com.k2767.course.ui.theme.Neutral500
import com.k2767.course.ui.theme.SemanticSuccess
import com.k2767.course.ui.theme.SemanticWarning

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onAccountChanged: () -> Unit = {},
    onSurveyCenter: () -> Unit = {},
    surveyUnreadCount: Int = 0
) {
    val context = LocalContext.current
    val isDemoMode = remember { UserManager.getInstance().isDemoMode }
    
    var studentName by remember { mutableStateOf("") }
    var deviceId by remember { mutableStateOf("") }
    var schoolName by remember { mutableStateOf("") }
    
    // UI States
    var showSchoolDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showClearCacheDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showAcademicSupport by remember { mutableStateOf(false) }
    var showCreditsDialog by remember { mutableStateOf(false) }
    var showQuotaDialog by remember { mutableStateOf(false) }
    var showAccountManagerDialog by remember { mutableStateOf(false) }
    var pendingPasswordDelete by remember { mutableStateOf<UserManager.AccountRecord?>(null) }
    var pendingAccountDelete by remember { mutableStateOf<UserManager.AccountRecord?>(null) }
    var showSchoolAdaptation by remember { mutableStateOf(false) }
    var showWallpaperDialog by remember { mutableStateOf(false) }
    var showThemeDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var showStartupPageDialog by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val startupPagePreferences = remember(context) { StartupPagePreferences.from(context) }
    var startupPage by remember(startupPagePreferences) { mutableStateOf(startupPagePreferences.read()) }
    val currentWallpaperName = com.k2767.course.manager.AppearanceSettingsManager.currentWallpaperName
    
    // Quota States
    var isSuper by remember { mutableStateOf(false) }
    var quotaInfo by remember { mutableStateOf("") }
    var quotaUsedCount by remember { mutableIntStateOf(0) }
    var quotaMaxCount by remember { mutableIntStateOf(0) }
    var quotaBoundNames by remember { mutableStateOf<List<String>>(emptyList()) }
    var quotaAccounts by remember { mutableStateOf<List<UserManager.AccountRecord>>(emptyList()) }
    // 账号管理走全量列表（跨学校）：这里是"管理"，不该被当前学校过滤掉
    var allAccounts by remember { mutableStateOf<List<UserManager.AccountRecord>>(emptyList()) }
    var accountsWithPassword by remember { mutableStateOf<Set<String>>(emptySet()) }
    var currentAccountKey by remember { mutableStateOf("") }
    var canRefreshCookie by remember { mutableStateOf(false) }
    val session by UserManager.getInstance().sessionState.state.collectAsState()
    var cookieUpdateFeedback by remember {
        mutableStateOf<Pair<com.k2767.course.manager.SessionToken, com.k2767.course.ui.system.SymbolResult>?>(null)
    }
    val cookieUpdateResult = cookieUpdateFeedback?.takeIf { it.first == session.token }?.second
        ?: com.k2767.course.ui.system.SymbolResult.None
    val recovery by com.k2767.course.utils.SessionRenewer.state.collectAsState()
    val isRefreshingCookie = recovery.token == session.token &&
        recovery.phase == com.k2767.course.utils.RecoveryPhase.Restoring
    val relogin = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { }
    
    // Update States
    val updateManager = remember { UpdateManager.getInstance(context) }
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var isCheckingUpdate by remember { mutableStateOf(false) }
    val currentVersion = remember { updateManager.getCurrentVersionName() }


    fun refreshAccountUiState() {
        val userManager = UserManager.getInstance()
        val name = userManager.studentName
        val school = userManager.currentSchool

        if (isDemoMode) {
            studentName = name ?: "演示用户"
            deviceId = "LOCAL-DEMO"
            schoolName = school?.name ?: "正方演示大学（演示数据）"
            isSuper = true
            quotaUsedCount = 0
            quotaMaxCount = 0
            quotaBoundNames = emptyList()
            quotaAccounts = emptyList()
            allAccounts = emptyList()
            accountsWithPassword = emptySet()
            currentAccountKey = userManager.currentAccountKey
            canRefreshCookie = false
            quotaInfo = "本地演示"
            return
        }

        studentName = name ?: "同学"
        deviceId = ActivationManager.getSavedDeviceId(context)
        schoolName = school?.name ?: "未选择"

        val maxStudents = ActivationManager.getMaxStudents(context)
        val usedNames = StudentLimitManager.getUsedStudentNames(context)
        val usedCount = StudentLimitManager.getUsedCount(context)
        isSuper = maxStudents <= 0
        quotaUsedCount = usedCount
        quotaMaxCount = maxStudents
        quotaBoundNames = usedNames.toList()
        quotaAccounts = userManager.accountsForCurrentSchool
        allAccounts = userManager.savedAccounts
        accountsWithPassword = allAccounts
            .filter { userManager.hasSavedPassword(it.key) }
            .map { it.key }
            .toSet()
        currentAccountKey = userManager.currentAccountKey
        canRefreshCookie = userManager.loginMode == "password"
        quotaInfo = if (isSuper) {
            "无限制"
        } else {
            "$usedCount / $maxStudents"
        }
    }

    LaunchedEffect(session.token) {
        refreshAccountUiState()
    }
    
    fun performLogout() {
        UserManager.getInstance().clearLoginState()
        val intent = Intent(context, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        context.startActivity(intent)
        // If context is not activity, clean task might need validation but usually safe
    }

    fun switchAccount(accountKey: String, onSwitched: () -> Unit) {
        if (accountKey == currentAccountKey) return
        if (UserManager.getInstance().switchToAccount(accountKey)) {
            refreshAccountUiState()
            onSwitched()
            onAccountChanged()
            GlassToaster.show("已切换账号")
        } else {
            GlassToaster.show("账号切换失败，请重新登录")
        }
    }

    /** 只删密码：账号还在，但不再自动续期，下次失效需要手动登录。 */
    fun deleteAccountPassword(record: UserManager.AccountRecord) {
        UserManager.getInstance().deletePassword(record.key)
        refreshAccountUiState()
        GlassToaster.show("已删除该账号保存的密码")
    }

    /** 彻底删号：账号记录 + 已存密码 + 运行期 Cookie + 本地课程缓存。 */
    fun deleteAccountEntirely(record: UserManager.AccountRecord) {
        val userManager = UserManager.getInstance()
        val isCurrent = record.key == userManager.currentAccountKey
        val storageKey = userManager.deleteAccount(record.key)
        if (storageKey.isNotEmpty()) {
            com.k2767.course.manager.CourseCacheManager.clearAccountCache(context, storageKey)
        }
        refreshAccountUiState()
        if (isCurrent) {
            // 当前账号被删掉，会话已经没有依据了，直接回登录页
            GlassToaster.show("账号已删除，请重新登录")
            performLogout()
        } else {
            GlassToaster.show("账号已删除")
        }
    }
    
    fun checkForUpdate() {
        if (isDemoMode) {
            GlassToaster.show("本地演示模式不执行更新检查")
            return
        }
        isCheckingUpdate = true
        GlassToaster.show("正在检查更新…")
        
        updateManager.checkForUpdate { info ->
            isCheckingUpdate = false
            if (info != null) {
                updateInfo = info
                showUpdateDialog = true
            } else {
                GlassToaster.show("已是最新版本")
            }
        }
    }
    
    fun startDownload() {
        if (isDemoMode) return
        val info = updateInfo ?: return
        isDownloading = true
        downloadProgress = 0
        
        updateManager.downloadApk(
            downloadUrl = info.downloadUrl,
            onProgress = { progress ->
                downloadProgress = progress
            },
            onComplete = { file ->
                isDownloading = false
                if (file != null && file.exists()) {
                    updateManager.installApk(file)
                    showUpdateDialog = false
                }
                // 失败时 DownloadManager 一定先走 onFailure（原因已提示），这里不再重复报错
            },
            onFailure = { message -> GlassToaster.show(message) }
        )
    }

    fun refreshCookieManually() {
        if (isDemoMode || isRefreshingCookie) return
        cookieUpdateFeedback = null
        val user = UserManager.getInstance()
        val expected = user.sessionState.token
        com.k2767.course.utils.SessionRenewer.request(expected, manual = true) { result ->
            when (result) {
                is com.k2767.course.utils.SessionRecoveryResult.Recovered -> {
                    if (user.sessionState.isCurrent(result.token)) {
                        cookieUpdateFeedback = result.token to com.k2767.course.ui.system.SymbolResult.Success
                        refreshAccountUiState()
                        GlassToaster.show("登录状态已更新")
                    }
                }
                is com.k2767.course.utils.SessionRecoveryResult.NeedsLogin -> {
                    if (user.sessionState.isCurrent(expected)) {
                        cookieUpdateFeedback = expected to com.k2767.course.ui.system.SymbolResult.Failure
                        GlassToaster.show(
                        when (result.reason) {
                            com.k2767.course.utils.RecoveryFailure.Network -> "暂时无法连接，请稍后重试"
                            com.k2767.course.utils.RecoveryFailure.Storage -> "保存失败，请重试"
                            else -> "需要重新登录以更新登录状态"
                        }
                        )
                    }
                }
                com.k2767.course.utils.SessionRecoveryResult.Superseded -> Unit
            }
        }
    }
    
    if (showSchoolAdaptation) {
        com.k2767.course.ui.system.GlassSubpage(onDismiss = { showSchoolAdaptation = false }) { close ->
            SchoolAdaptationFlow(onNavigateBack = close)
        }
    }

    // Update Dialog
    if (showUpdateDialog && updateInfo != null && !session.expired) {
        UpdateDialog(
            updateInfo = updateInfo!!,
            currentVersion = currentVersion,
            onDismiss = { 
                showUpdateDialog = false 
                updateInfo = null
            },
            onUpdate = { startDownload() },
            downloadProgress = downloadProgress,
            isDownloading = isDownloading
        )
    }
    
    val usagePreferences by com.k2767.course.usage.UsageStatsManager.preferences.collectAsState()
    SettingsScreen(
        studentName = studentName,
        studentId = deviceId,
        schoolName = schoolName,
        currentVersion = currentVersion,
        onSchoolSelect = {
            if (isDemoMode) GlassToaster.show("演示学校固定为本地数据源") else showSchoolDialog = true
        },
        onCookieConfig = {
            relogin.launch(Intent(context, LoginActivity::class.java).apply {
                putExtra("force_relogin", true)
                putExtra(LoginActivity.EXTRA_RETURN_TO_CALLER, true)
            })
        },
        onAccountManage = {
            if (isDemoMode) GlassToaster.show("本地演示模式不读取真实账号") else showAccountManagerDialog = true
        },
        savedAccountCount = allAccounts.size,
        onClearCache = { showClearCacheDialog = true },
        onCheckUpdate = { checkForUpdate() },
        onAbout = { showAboutDialog = true },
        onCredits = { showCreditsDialog = true },
        onLogout = { showLogoutDialog = true },
        onQuotaClick = { showQuotaDialog = true },
        onRefreshCookieClick = { refreshCookieManually() },
        onLogExport = { com.k2767.course.utils.LogUtils.exportLogs(context) },
        onSchoolAdaptation = {
            if (isDemoMode) GlassToaster.show("本地演示模式不连接学校适配服务") else showSchoolAdaptation = true
        },
        onSurveyCenter = onSurveyCenter,
        surveyUnreadCount = surveyUnreadCount,
        onWallpaperSelect = { showWallpaperDialog = true },
        wallpaperName = currentWallpaperName,
        themeName = AppearanceSettingsManager.themeMode.label,
        onThemeSelect = { showThemeDialog = true },
        startupPageName = startupPage.label,
        onStartupPageSelect = { showStartupPageDialog = true },
        glassEffectEnabled = AppearanceSettingsManager.glassEffectEnabled,
        onGlassEffectChange = { AppearanceSettingsManager.updateGlassEffect(it) },
        usageEnabled = usagePreferences.enabled,
        onUsageEnabledChange = com.k2767.course.usage.UsageStatsManager::setEnabled,
        isSuper = isSuper,
        quotaInfo = quotaInfo,
        canRefreshCookie = canRefreshCookie,
        isRefreshingCookie = isRefreshingCookie,
        academicSystemName = com.k2767.course.academic.AcademicCapabilities.name(UserManager.getInstance().currentSchool?.academicSystem),
        onAcademicSupport = { showAcademicSupport = true }
    )
    if (showAcademicSupport) com.k2767.course.ui.screen.AcademicSupportDialog(
        UserManager.getInstance().currentSchool?.academicSystem, onDismiss = { showAcademicSupport = false })
    
    if (showThemeDialog) {
        com.k2767.course.ui.screen.AppThemeSettingsDialog { showThemeDialog = false }
    }
    if (showStartupPageDialog) {
        com.k2767.course.ui.screen.StartupPageSettingsDialog(
            page = startupPage,
            onPageChange = {
                startupPagePreferences.write(it)
                startupPage = it
            },
            onDismiss = { showStartupPageDialog = false }
        )
    }
    if (showWallpaperDialog) {
        com.k2767.course.ui.screen.WallpaperSettingsDialog(
            onDismiss = { showWallpaperDialog = false }
        )
    }

    // Dialogs
    if (showLogoutDialog) {
        SimpleConfirmDialog(
            title = "退出登录",
            text = "确定要退出登录吗？",
            onConfirm = { 
                performLogout() 
                showLogoutDialog = false
            },
            onDismiss = { showLogoutDialog = false }
        )
    }
    
    if (showClearCacheDialog) {
        SimpleConfirmDialog(
            title = "清除缓存",
            text = "确定要清除所有本地缓存数据吗？",
            onConfirm = { 
                GlassToaster.show("缓存已清除")
                showClearCacheDialog = false
            },
            onDismiss = { showClearCacheDialog = false }
        )
    }
    
    if (showAboutDialog) {
        SystemDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Text(
                    text = "更新历史",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "关闭",
                    onClick = { showAboutDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 本项目的更新历史，按天归纳（上游作者的历史记录不再列出；仓库里的 CHANGELOG 仍保留作存档）
                val updates = listOf(
                    "2026-09-18" to "周视图星期条下面的日期修好了：系统字体放大时它会被行高约束压成 0 高度，只剩星期不见日期；切周时的淡出也不再被残留偏移乘成全透明。星期条左侧加上当周月份。没设开学日期时会给提示——首次进课表弹一次，之后顶栏下方常驻一条可右滑划掉的提示，点一下直达设置；课表设置新增「现在是第几周」，不记得开学哪天也能反推。桌面小组件也不再对没设开学日期的课表说「今天没有课啦」。（1.1.5）",
                    "2026-09-18" to "换上新头像：桌面图标、启动页与官网图标整体换成新形象（水豚趴在课表前泡在水里）。「今日课程」「今日与明日」小组件在桌面的组件选择面板里终于显示自己的样子，不再只是一个应用头像。（1.1.4）",
                    "2026-09-18" to "周课表组件拉高后底部不再留一大片空白（显示几节改由组件真实高度决定，行高按行数反推）；默认宽度加到 5 格，周一到周日不再挤在一起。演示模式没填开学日期时组件按本周兜底，不再一节课都不显示。修复应用内更新：GitHub 分流下载不给总大小时不再被误判成失败，下载迟迟未开始最长等 2 分钟且超时后清掉通知栏残留条目。（1.1.3）",
                    "2026-09-17" to "小组件三处改进：教室与时间各占一行（不再被校区名挤掉，校区前缀自动裁掉）；上完的课自动让位给后面的课（下课那一刻组件自动刷新，全上完显示「今天的课都上完啦」）；演示模式下也能用小组件。（1.1.2）",
                    "2026-09-17" to "新增「今日与明日」双栏小组件与「周课表」网格小组件；小组件行数改为按真实尺寸计算（之前拉大也只会显示三行）；修复检查更新时下载进度一直停在 0% 的问题（改滚动进度条，下载迟迟未开始会在 45 秒后明确报错）。（1.1.1）",
                    "2026-09-17" to "新增桌面小组件「今日课程」：桌面直接看今天的课（课名、地点、时间与配色条），支持三种尺寸，点击打开课表；组件自己按日期与周次计算当天课程，无需应用常驻后台。（1.1.0）",
                    "2026-09-17" to "日 / 周视图之间加入切换动画：旧视图收起淡出、新视图展开淡入，顶栏保持不动；跟随系统「减少动态效果」设置。（1.0.8）",
                    "2026-09-17" to "新增内置学校库：1777 所高校可搜，支持中文 / 拼音 / 首字母搜索、A–Z 索引与收藏；登录页与设置页共用同一套选择界面。同时隐藏学校适配入口、关闭匿名统计的界面入口。（1.0.7）",
                    "2026-09-17" to "课表面貌改进：新增日视图与「日 / 周」切换；上课地点完整显示不再被截断，一屏装得下八节课；日视图按周过滤单双周课程。（1.0.3 – 1.0.6）",
                    "2026-09-17" to "修复课程页误报：教务尚未开放选课时如实透传原文提示，不再显示「无法识别的列表」。（1.0.2）",
                    "2026-09-17" to "更名换标并完成独立性改造：应用名、图标与源码包名全部换成自己的，切断与原作者服务的全部往来，接入自建官网与更新服务。（1.0.0 / 1.0.1）"
                )

                updates.forEach { (date, desc) ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(com.k2767.course.ui.theme.NeuPrimary, CircleShape)
                            )
                            Text(
                                text = date,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = desc,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            }
        }
    }

    if (showCreditsDialog) {
        SystemDialog(
            onDismissRequest = { showCreditsDialog = false },
            title = {
                Text(
                    text = "致谢与关于",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "我知道了",
                    onClick = { showCreditsDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "特别致谢",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "本应用基于多项优秀的开源技术构建，衷心感谢以下开源项目及社区的支持：\n" +
                            "• Jetpack Compose & Kotlin\n" +
                            "• OkHttp3 & Gson\n" +
                            "• Jsoup (HTML 解析库)\n" +
                            "• Material Design 3\n" +
                            "• AndroidLiquidGlass 动效库",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                com.k2767.course.ui.system.SystemDivider()

                Text(
                    text = "关于项目",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "本应用基于开源项目「教务助手」二次开发\n" +
                            "原作者：znjhahaha · GPL-3.0\n" +
                            "上游项目：https://github.com/znjhahaha/zhengfang-apk\n" +
                            "本项目：https://github.com/sonder123i/capybara-course-helper",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )

                com.k2767.course.ui.system.SystemDivider()

                Text(
                    text = "本软件为开源免费项目，仅供个人学习与技术交流使用，严禁用于任何商业目的与倒卖。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    lineHeight = 16.sp
                )
            }
        }
    }
    
    if (showQuotaDialog) {
        QuotaStatusDialog(
            deviceId = deviceId,
            isSuper = isSuper,
            usedCount = quotaUsedCount,
            maxCount = quotaMaxCount,
            boundNames = quotaBoundNames,
            accounts = quotaAccounts,
            currentAccountKey = currentAccountKey,
            onSwitchAccount = { accountKey ->
                switchAccount(accountKey) { showQuotaDialog = false }
            },
            onDismiss = { showQuotaDialog = false }
        )
    }

    if (showAccountManagerDialog) {
        AccountManagerDialog(
            accounts = allAccounts,
            currentAccountKey = currentAccountKey,
            accountsWithPassword = accountsWithPassword,
            onSwitchAccount = { accountKey ->
                switchAccount(accountKey) { showAccountManagerDialog = false }
            },
            onDeletePassword = { pendingPasswordDelete = it },
            onDeleteAccount = { pendingAccountDelete = it },
            onDismiss = { showAccountManagerDialog = false }
        )
    }

    pendingPasswordDelete?.let { record ->
        SimpleConfirmDialog(
            title = "删除已保存的密码",
            text = "删除后「${record.displayName}」将无法在登录状态失效时自动续期，" +
                "需要你手动重新登录。账号本身与本地数据不会被删除。",
            confirmText = "删除密码",
            onConfirm = {
                deleteAccountPassword(record)
                pendingPasswordDelete = null
            },
            onDismiss = { pendingPasswordDelete = null }
        )
    }

    pendingAccountDelete?.let { record ->
        SimpleConfirmDialog(
            title = "删除账号",
            text = "将删除「${record.displayName}」的账号记录、已保存的密码、登录状态与本地课程缓存，" +
                "此操作不可恢复。设备绑定名额不会因此释放。",
            confirmText = "删除账号",
            onConfirm = {
                deleteAccountEntirely(record)
                pendingAccountDelete = null
                showAccountManagerDialog = false
            },
            onDismiss = { pendingAccountDelete = null }
        )
    }


    
    if (showSchoolDialog) {
        // 学校选择改为学校库（搜索 + A–Z + 收藏 + 手动添加），与登录页同一套界面
        com.k2767.course.ui.system.GlassSubpage(onDismiss = { showSchoolDialog = false }) {
            var schoolFavorites by remember {
                mutableStateOf(com.k2767.course.model.SchoolCatalogFavorites.load(context))
            }
            com.k2767.course.ui.screen.SchoolPickerScreen(
                favorites = schoolFavorites,
                onToggleFavorite = { id ->
                    schoolFavorites = com.k2767.course.model.SchoolCatalogFavorites.toggle(context, id)
                },
                onSelect = { school ->
                    UserManager.getInstance().clearLoginState()
                    UserManager.getInstance().currentSchool = school
                    GlassToaster.show("已切换到：${school.name}")
                    performLogout()
                    showSchoolDialog = false
                },
                onBack = { showSchoolDialog = false }
            )
        }
    }
}

@Composable
private fun QuotaStatusDialog(
    deviceId: String,
    isSuper: Boolean,
    usedCount: Int,
    maxCount: Int,
    boundNames: List<String>,
    accounts: List<UserManager.AccountRecord>,
    currentAccountKey: String,
    onSwitchAccount: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val safeMax = maxCount.coerceAtLeast(0)
    val safeUsed = usedCount.coerceAtLeast(0)
    val usageRatio = if (!isSuper && safeMax > 0) {
        (safeUsed.toFloat() / safeMax.toFloat()).coerceIn(0f, 1f)
    } else {
        1f
    }
    val statusText = if (isSuper) "超级用户" else "普通用户"
    val quotaText = if (isSuper) "无限制" else "$safeUsed / $safeMax"

    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "当前账号配额",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "设备绑定与名额使用情况",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "知道了",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuotaInfoRow(label = "身份", value = statusText)
                    QuotaInfoRow(label = "配额", value = quotaText)
                    if (!isSuper) {
                        LinearProgressIndicator(
                            progress = { usageRatio },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp)),
                            color = if (usageRatio >= 1f) SemanticWarning else NeuPrimary,
                            trackColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            SystemDivider(alpha = 0.5f)

            Text("设备 ID：${deviceId.ifBlank { "未获取" }}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "已绑定账号",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (boundNames.isEmpty()) {
                    Text(
                        text = "当前设备尚未绑定账号。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        boundNames.forEachIndexed { index, name ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        modifier = Modifier.size(24.dp),
                                        color = NeuPrimary.copy(alpha = 0.12f),
                                        shape = CircleShape
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "${index + 1}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = NeuPrimary,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (accounts.isNotEmpty()) {
                SystemDivider(alpha = 0.5f)

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "切换账号",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    accounts.forEach { account ->
                        val isCurrent = account.key == currentAccountKey
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isCurrent) { onSwitchAccount(account.key) },
                            color = if (isCurrent) NeuPrimary.copy(alpha = 0.12f)
                                else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${account.accountIdText} · ${if (account.loginMode == "password") "密码登录" else "Cookie 登录"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SystemStatusBadge(
                                    text = if (isCurrent) "当前" else "切换",
                                    tone = if (isCurrent) SystemTone.Info else SystemTone.Neutral
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "说明：同一设备可绑定不同学校的学生账号，所有学校合计最多 3 个；切换账号会同步切换 Cookie 与本地账号上下文。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun AccountManagerDialog(
    accounts: List<UserManager.AccountRecord>,
    currentAccountKey: String,
    accountsWithPassword: Set<String>,
    onSwitchAccount: (String) -> Unit,
    onDeletePassword: (UserManager.AccountRecord) -> Unit,
    onDeleteAccount: (UserManager.AccountRecord) -> Unit,
    onDismiss: () -> Unit
) {
    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "账号管理",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "切换账号、管理已保存的密码",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "完成",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (accounts.isEmpty()) {
                Text(
                    text = "本机还没有保存任何账号。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                accounts.forEach { account ->
                    val isCurrent = account.key == currentAccountKey
                    val hasPassword = accountsWithPassword.contains(account.key)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isCurrent) NeuPrimary.copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = account.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${account.accountIdText} · " +
                                            if (account.loginMode == "password") "密码登录" else "Cookie 登录",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = account.schoolName.ifBlank { "未记录学校" },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                SystemStatusBadge(
                                    text = if (hasPassword) "已存密码" else "未存密码",
                                    tone = if (hasPassword) SystemTone.Success else SystemTone.Neutral
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isCurrent) {
                                    SystemStatusBadge(text = "当前账号", tone = SystemTone.Info)
                                } else {
                                    AccountActionButton(
                                        text = "切换",
                                        onClick = { onSwitchAccount(account.key) }
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                if (hasPassword) {
                                    AccountActionButton(
                                        text = "删除密码",
                                        onClick = { onDeletePassword(account) }
                                    )
                                }
                                AccountActionButton(
                                    text = "删除账号",
                                    tint = com.k2767.course.ui.theme.SemanticDanger,
                                    onClick = { onDeleteAccount(account) }
                                )
                            }
                        }
                    }
                }
            }

            Text(
                text = "密码经系统密钥库加密后仅保存在本机，用于登录状态失效时自动续期；" +
                    "退出登录不会删除它。删除账号不会释放设备绑定名额。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

/** 账号卡里的小动作按钮：轻量胶囊，避免三个实心按钮在一行里互相抢注意力。 */
@Composable
private fun AccountActionButton(
    text: String,
    tint: Color = NeuPrimary,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}

@Composable
private fun QuotaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun SimpleConfirmDialog(
    title: String, 
    text: String, 
    onConfirm: () -> Unit, 
    onDismiss: () -> Unit,
    confirmText: String = "确定",
    showCancel: Boolean = true
) {
    SystemConfirmDialog(
        title = title,
        text = text,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        confirmText = confirmText,
        showCancel = showCancel
    )
}


