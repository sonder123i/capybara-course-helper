package com.k2767.course.ui.route

import com.k2767.course.ui.system.GlassToaster
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import com.k2767.course.schedule.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.fragment.app.FragmentActivity
import com.k2767.course.demo.DemoData
import com.k2767.course.academic.AcademicGatewayFactory
import com.k2767.course.academic.AcademicStudyBridge
import com.k2767.course.manager.ScheduleSettingsManager
import com.k2767.course.manager.UserManager
import com.k2767.course.network.CourseApiClient
import com.k2767.course.ui.screen.PeriodTimeUi
import com.k2767.course.ui.screen.ScheduleCourseUi
import com.k2767.course.ui.screen.ScheduleScreen
import com.k2767.course.ui.screen.ScheduleSettingsScreen
import com.k2767.course.ui.system.DisablePlatformDialogDim
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.ui.system.LocalSemesterAnchorNotice
import com.k2767.course.ui.system.SystemSecondaryButton
import com.k2767.course.ui.system.SystemPrimaryButton
import com.k2767.course.ui.theme.MotionDuration
import com.k2767.course.ui.theme.MotionEasing
import com.k2767.course.ui.theme.MotionSpring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Calendar
import com.k2767.course.utils.ICalExporter

private const val ScheduleRouteSnapshotMaxAgeMs = 5 * 60 * 1000L

/** 保留课表数据状态，但页面 Composition 与玻璃图层仍在切走时立即释放。 */
private data class ScheduleRouteSnapshot(
    val savedAtMs: Long,
    val currentWeek: Int,
    val courses: List<ScheduleCourseUi>,
    val periodTimes: List<PeriodTimeUi>,
    val periodCount: Int,
    val isNextSemester: Boolean,
    val termId: String = "",
    val appliedCalendar: String? = null
)

private object ScheduleRouteMemoryCache {
    private val snapshots = mutableMapOf<String, ScheduleRouteSnapshot>()

    fun get(accountKey: String): ScheduleRouteSnapshot? = snapshots[accountKey]?.takeIf {
        System.currentTimeMillis() - it.savedAtMs <= ScheduleRouteSnapshotMaxAgeMs
    }

    fun put(accountKey: String, snapshot: ScheduleRouteSnapshot) {
        snapshots[accountKey] = snapshot
    }
}

@Composable
fun ScheduleRoute() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isDemoMode = remember { UserManager.getInstance().isDemoMode }
    val routeAccountKey = remember { UserManager.getInstance().currentAccountStorageKey }
    val sessions = UserManager.getInstance().sessionState
    val session by sessions.state.collectAsState()
    val requests = remember { com.k2767.course.manager.SessionRequestGate(sessions) }
    DisposableEffect(requests) { onDispose { requests.cancelAll() } }
    val restoredSnapshot = remember(routeAccountKey) {
        ScheduleRouteMemoryCache.get(routeAccountKey)
    }
    var hasInitializedRoute by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot != null)
    }
    var skipFirstSemesterLoad by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot != null)
    }
    
    // State
    var currentWeek by rememberSaveable(routeAccountKey) {
        mutableIntStateOf(restoredSnapshot?.currentWeek ?: 1)
    }
    var courses by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot?.courses ?: emptyList())
    }
    var isLoading by remember { mutableStateOf(false) }
    var loadError by remember(routeAccountKey) { mutableStateOf("") }
    var studyLoadJob by remember(routeAccountKey) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var studyGeneration by remember(routeAccountKey) { mutableIntStateOf(0) }
    var periodTimes by remember(routeAccountKey) {
        mutableStateOf(restoredSnapshot?.periodTimes ?: emptyList())
    }
    var periodCount by remember(routeAccountKey) {
        mutableIntStateOf(restoredSnapshot?.periodCount ?: 12)
    }
    var isNextSemester by rememberSaveable(routeAccountKey) {
        mutableStateOf(restoredSnapshot?.isNextSemester ?: false)
    }
    
    // Dialog State
    var showSettingsDialog by rememberSaveable { mutableStateOf(false) }
    var detailId by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var detailSourceBounds by remember(routeAccountKey) { mutableStateOf<androidx.compose.ui.geometry.Rect?>(null) }
    var editingId by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var resolvedTermId by rememberSaveable(routeAccountKey) { mutableStateOf(restoredSnapshot?.termId.orEmpty()) }
    var appliedCalendar by rememberSaveable(routeAccountKey) { mutableStateOf(restoredSnapshot?.appliedCalendar) }
    var notificationCourseJson by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var settingsTermOverride by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var deletedCourseJson by rememberSaveable(routeAccountKey) { mutableStateOf<String?>(null) }
    var deletedRemindersJson by rememberSaveable(routeAccountKey) { mutableStateOf("[]") }
    var undoDeadline by rememberSaveable(routeAccountKey) { mutableLongStateOf(0L) }
    
    // Managers
    val settingsManager = remember { ScheduleSettingsManager.getInstance().apply { init(context) } }
    val scheduleCache = remember(context) {
        ScheduleCacheStore(context.getSharedPreferences("schedule_cache", android.content.Context.MODE_PRIVATE))
    }
    val reminderScheduler = remember(context) { ScheduleReminderScheduler.get(context) }
    val customRevision = settingsManager.revision
    val customCourses = remember(customRevision, routeAccountKey) { settingsManager.getCustomCourses(routeAccountKey) }
    val remindersRevision = reminderScheduler.revision
    val settingsTerm = settingsTermOverride ?: resolvedTermId
    val termTimeBase = remember(settingsTerm, remindersRevision) { reminderScheduler.timeBase(routeAccountKey, settingsTerm) }
    val displayedTimeBase = remember(resolvedTermId, remindersRevision) { reminderScheduler.timeBase(routeAccountKey, resolvedTermId) }
    fun periodTimesFor(base: ScheduleTimeBase?): List<ScheduleSettingsManager.PeriodTime> = settingsManager.getPeriodTimes().map {
        it.copy(startTime = base?.periodStarts?.get(it.period) ?: it.startTime, endTime = base?.periodEnds?.get(it.period) ?: it.endTime)
    }
    val focusRegistry = remember { com.k2767.course.ui.screen.ScheduleFocusRegistry() }
    val reminderRequest = CourseReminderNavigation.requestedId
    LaunchedEffect(reminderRequest) {
        if (reminderRequest != null) {
            detailSourceBounds = null
            reminderScheduler.findById(reminderRequest)?.let {
                notificationCourseJson = ReminderJson.reminder(it).toString()
                detailId = it.course.id
            }
            CourseReminderNavigation.consume()
        }
    }

    val snapshotForCache = ScheduleRouteSnapshot(
        savedAtMs = System.currentTimeMillis(),
        currentWeek = currentWeek,
        courses = courses,
        periodTimes = periodTimes,
        periodCount = periodCount,
        isNextSemester = isNextSemester,
        termId = resolvedTermId,
        appliedCalendar = appliedCalendar
    )
    val latestSnapshotForCache by rememberUpdatedState(snapshotForCache)
    val canCacheSnapshot by rememberUpdatedState(hasInitializedRoute && !isLoading && loadError.isBlank())
    DisposableEffect(routeAccountKey) {
        onDispose {
            if (canCacheSnapshot) {
                ScheduleRouteMemoryCache.put(routeAccountKey, latestSnapshotForCache)
            }
        }
    }
    
    // Colors
    val courseColors = remember {
        listOf(
            Color(0xFF5C6BC0), Color(0xFF42A5F5), Color(0xFF66BB6A), Color(0xFFFFA726),
            Color(0xFFAB47BC), Color(0xFFEF5350), Color(0xFF26C6DA), Color(0xFF8D6E63)
        )
    }

    // 桌面小组件（今日课程）：课表数据变化时同步一份快照，并让组件立即重绘。
    // 组件自己按日期与周次算「今天上什么」，这里只负责给原始数据。
    LaunchedEffect(courses, periodTimes, displayedTimeBase?.firstWeekDate, routeAccountKey) {
        if (courses.isEmpty()) return@LaunchedEffect
        // 演示模式没有真实开学日期，用「本周一」兜底——否则组件算不出第几周，会一个课都不显示
        val widgetTimeBase = displayedTimeBase ?: if (UserManager.getInstance().isDemoMode) {
            com.k2767.course.schedule.ScheduleTimeBase(
                firstWeekDate = com.k2767.course.schedule.ScheduleTimeBase.dateFromMillis(
                    com.k2767.course.schedule.ScheduleDates.mondayOfWeek(System.currentTimeMillis()).timeInMillis
                )
            )
        } else null
        com.k2767.course.widget.WidgetSnapshotWriter.write(
            context = context,
            schoolName = UserManager.getInstance().currentSchool?.name.orEmpty(),
            timeBase = widgetTimeBase,
            periods = periodTimes.map {
                com.k2767.course.widget.WidgetPeriodTime(it.period, it.startTime, it.endTime)
            },
            courses = courses.map { it.record() }
        )
    }

    // 「还没设开学日期」的两层提示。弹窗每个账号只弹一次（手滑关掉也不该反复拦人），
    // 内联条则一直挂到填好为止——它是可右滑划掉的，划掉只压这一把键，换学期还会再来。
    val semesterNotice = LocalSemesterAnchorNotice.current
    val semesterKey = "$routeAccountKey|$resolvedTermId"
    val semesterMissing = !isDemoMode && !isLoading && courses.isNotEmpty() &&
        displayedTimeBase?.firstWeekDate.isNullOrBlank()
    fun openScheduleSettings() {
        settingsTermOverride = null
        showSettingsDialog = true
    }
    LaunchedEffect(semesterKey, semesterMissing) {
        semesterNotice.report(semesterKey, semesterMissing) { openScheduleSettings() }
    }
    DisposableEffect(semesterKey) {
        // 离开课表页就把提示收掉：通知宿主挂在根节点，不主动清会一路跟着用户跑
        onDispose { semesterNotice.report(semesterKey, missing = false) {} }
    }
    var showSemesterHint by remember(routeAccountKey) { mutableStateOf(false) }
    LaunchedEffect(semesterMissing, routeAccountKey) {
        if (semesterMissing && !settingsManager.semesterNoticeShown) showSemesterHint = true
    }
    if (showSemesterHint) {
        SystemDialog(
            onDismissRequest = {
                showSemesterHint = false
                settingsManager.semesterNoticeShown = true
            },
            title = {
                Text(
                    text = "设一下开学日期",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            dismissButton = {
                SystemSecondaryButton(
                    text = "先不用",
                    onClick = {
                        showSemesterHint = false
                        settingsManager.semesterNoticeShown = true
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SystemPrimaryButton(
                    text = "去设置",
                    onClick = {
                        showSemesterHint = false
                        settingsManager.semesterNoticeShown = true
                        openScheduleSettings()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Text(
                text = "不填第一周从哪天开始，课表只会显示第 1 周：双周的课会被当成不属于本周而藏起来，" +
                    "「今天」的标记和桌面小组件也都算不出来。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    fun parseSchedule(json: String): List<ScheduleCourseUi>? = ScheduleJson.parse(json)?.map { entry ->
        val c = entry.course
        ScheduleCourseUi(c.name, c.teacher, c.location, c.day, c.startPeriod, c.endPeriod, c.weeks,
            courseColors[ScheduleIdentity.colorIndex(c.id, courseColors.size)], sourceId = entry.sourceId, id = c.id)
    }

    fun reloadCustomCourses(currentList: List<ScheduleCourseUi>): List<ScheduleCourseUi> {
        val customCourses = settingsManager.getCustomCourses(routeAccountKey)
        val customUi = customCourses.map { cc ->
            ScheduleCourseUi(
                name = cc.name, teacher = cc.teacher, location = cc.location, day = cc.day,
                startPeriod = cc.startPeriod, endPeriod = cc.endPeriod, weeks = cc.weeks,
                color = courseColors[ScheduleIdentity.colorIndex("custom:${cc.id}", courseColors.size)], isCustom = true, customId = cc.id
            )
        }
        val nonCustom = currentList.filter { !it.isCustom }
        return nonCustom + customUi
    }

    // Load Schedule Function
    val loadSchedule = remember(isNextSemester, session.token) {
        fun(forceRefresh: Boolean) {
            if (isDemoMode) {
                resolvedTermId = (if (isNextSemester) DemoData.currentTerm.next() else DemoData.currentTerm).id
                courses = reloadCustomCourses(DemoData.scheduleCourses())
                isLoading = false
                return
            }
            val school = UserManager.getInstance().currentSchool
            if (school == null) return
            val ticket = requests.begin("schedule")
            studyLoadJob?.cancel()
            val generation = ++studyGeneration
            val account = UserManager.getInstance().currentAccountStorageKey
            val currentTerm = scheduleCache.currentTerm(account, school.id)
            val requestedTerm = if (isNextSemester) currentTerm.next() else currentTerm
            val cached = scheduleCache.selected(account, school.id, isNextSemester)
            loadError = ""
            if (!forceRefresh && cached != null) {
                courses = reloadCustomCourses(requireNotNull(parseSchedule(cached.json)))
                resolvedTermId = cached.term.id
                isLoading = false
                reminderScheduler.updateSnapshot(routeAccountKey, cached.term.id, courses.map { it.record() })
                return
            }
            // Refresh in place. Only a different semester must discard the previous rows.
            if (resolvedTermId != requestedTerm.id) courses = reloadCustomCourses(emptyList())
            val hasRetainedSchedule = cached != null || courses.isNotEmpty()
            isLoading = true

            if (AcademicGatewayFactory.supports(school)) {
                studyLoadJob = scope.launch {
                    try {
                        val loaded = withContext(Dispatchers.IO) {
                            scheduleCache.load(account, school.id, isNextSemester, forceRefresh) {
                                AcademicStudyBridge.reader(school, account, ticket.session)
                            }
                        }
                        if (!requests.isCurrent(ticket) || studyGeneration != generation) return@launch
                        scheduleCache.save(account, school.id, loaded)
                        courses = reloadCustomCourses(requireNotNull(parseSchedule(loaded.json)))
                        resolvedTermId = loaded.term.id
                        reminderScheduler.updateSnapshot(routeAccountKey, loaded.term.id, courses.map { it.record() })
                        if (forceRefresh) GlassToaster.show("已同步课表")
                    } catch (e: CancellationException) { throw e }
                    catch (e: Exception) {
                        if (requests.isCurrent(ticket) && studyGeneration == generation) {
                            val message = e.message ?: "课表同步失败，请重试"
                            if (hasRetainedSchedule) GlassToaster.show("同步失败，已保留本地课表：$message")
                            else loadError = message
                        }
                    } finally {
                        if (requests.isCurrent(ticket) && studyGeneration == generation) isLoading = false
                    }
                }
                return
            }
            
            val xnm = requestedTerm.year.toString()
            val xqm = if (requestedTerm.semester == 1) "3" else "12"
            fun isRequestAccountActive(): Boolean {
                return requests.isCurrent(ticket)
            }
            val requestTermId = requestedTerm.id
            resolvedTermId = requestTermId
            CourseApiClient.getInstance().fetchSchedule(school, "xnm=$xnm&xqm=$xqm", object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    scope.launch(Dispatchers.Main) {
                        if (!isRequestAccountActive()) return@launch
                        isLoading = false
                        if (hasRetainedSchedule) GlassToaster.show("同步失败，已保留本地课表")
                        else loadError = "加载失败：${e.message}"
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val json = response.body?.string() ?: ""
                    if (json.contains("用户登录")) {
                         scope.launch(Dispatchers.Main) { 
                             if (!isRequestAccountActive()) return@launch
                             isLoading = false
                             if (!hasRetainedSchedule) loadError = "请先登录"
                             GlassToaster.show("请先登录") 
                         }
                        return
                    }
                    val parsed = if (response.isSuccessful) parseSchedule(json) else null
                    scope.launch(Dispatchers.Main) {
                        if (!isRequestAccountActive()) return@launch
                        isLoading = false
                        if (parsed != null) {
                            scheduleCache.save(account, school.id, CachedSchedule(currentTerm, requestedTerm, json, false))
                            courses = reloadCustomCourses(parsed)
                            reminderScheduler.updateSnapshot(routeAccountKey, requestTermId, courses.map { it.record() })
                            if (forceRefresh) GlassToaster.show("已刷新")
                        } else {
                            val message = "课表响应无效，请重试"
                            if (!hasRetainedSchedule) loadError = message
                            GlassToaster.show(message)
                        }
                    }
                }
            })
        }
    }

    // Init Effect
    LaunchedEffect(routeAccountKey) {
        if (restoredSnapshot == null) {
            periodCount = settingsManager.periodCount
            periodTimes = settingsManager.getPeriodTimes().map {
                PeriodTimeUi(it.period, it.startTime, it.endTime)
            }
        }
    }
    
    // 监听学期切换并重新加载
    LaunchedEffect(isNextSemester, session.token) {
        if (skipFirstSemesterLoad) {
            skipFirstSemesterLoad = false
            return@LaunchedEffect
        }
        hasInitializedRoute = true
        loadSchedule(false)
    }

    // Refresh custom courses when dialogs close
    LaunchedEffect(customRevision, routeAccountKey) {
        courses = reloadCustomCourses(courses)
        periodCount = settingsManager.periodCount
    }
    LaunchedEffect(resolvedTermId) {
        if (resolvedTermId.isNotBlank() && !isNextSemester) {
            reminderScheduler.migrateLegacyTimeBase(routeAccountKey, resolvedTermId, ScheduleTimeBase(
                ScheduleTimeBase.dateFromMillis(settingsManager.semesterStartDate), periodTimes.associate { it.period to it.startTime },
                periodTimes.associate { it.period to it.endTime }))
        }
    }
    LaunchedEffect(displayedTimeBase) {
        periodTimes = periodTimesFor(displayedTimeBase).map { PeriodTimeUi(it.period, it.startTime, it.endTime) }
    }
    LaunchedEffect(resolvedTermId, displayedTimeBase?.firstWeekDate) {
        if (resolvedTermId.isBlank()) return@LaunchedEffect
        val calendar = "$resolvedTermId|${displayedTimeBase?.firstWeekDate.orEmpty()}"
        // Apply date changes when saved, including system-back dismissal. Retain a
        // browsed week across page restoration and unrelated reminder/time changes.
        if (appliedCalendar != calendar) {
            appliedCalendar = calendar
            currentWeek = ScheduleDates.weekAt(displayedTimeBase?.firstWeekDate, System.currentTimeMillis()) ?: 1
        }
    }
    LaunchedEffect(undoDeadline) {
        if (undoDeadline > 0) {
            kotlinx.coroutines.delay((undoDeadline - System.currentTimeMillis()).coerceAtLeast(0))
            deletedCourseJson = null
            deletedRemindersJson = "[]"
        }
    }
    com.k2767.course.ui.system.ReportPageContent(courses.isNotEmpty())
    Box(Modifier.fillMaxSize()) {
    CompositionLocalProvider(com.k2767.course.ui.screen.LocalScheduleFocus provides focusRegistry) {
    ScheduleScreen(
        currentWeek = currentWeek,
        courses = courses,
        isLoading = isLoading,
        errorMessage = loadError,
        onRetry = { loadSchedule(true) },
        periodTimes = periodTimes,
        periodCount = periodCount,
        firstWeekDate = displayedTimeBase?.firstWeekDate,
        weekRequestKey = appliedCalendar.orEmpty(),
        onWeekChange = { currentWeek = it },
        onCourseClick = {
            notificationCourseJson = null
            // Freeze the tapped card before pager neighbours or sheet layout update their bounds.
            detailSourceBounds = focusRegistry.bounds(it.id)
            detailId = it.id
        },
        onSettingsClick = { settingsTermOverride = null; showSettingsDialog = true },
        onExportClick = {
            if (courses.isEmpty()) {
                GlassToaster.show("课表为空，无法导出")
            } else {
                try {
                    val semesterStart = ScheduleDates.firstMonday(displayedTimeBase?.firstWeekDate)
                    if (semesterStart == null) {
                        GlassToaster.show("请先设置这个学期的第一周周一日期")
                        settingsTermOverride = null
                        showSettingsDialog = true
                    } else {
                    ICalExporter.exportAndShare(
                        context = context,
                        courses = courses,
                        semesterStartDate = semesterStart,
                        totalWeeks = ScheduleMaxWeeks,
                        periodTimes = periodTimes.associate { it.period to (it.startTime to it.endTime) }
                    )
                    GlassToaster.show("课表已导出，可导入到系统日历中查看")
                    }
                } catch (e: Exception) {
                    GlassToaster.show("导出失败：${e.message}")
                }
            }
        },
        isNextSemester = isNextSemester,
        onToggleSemester = { isNextSemester = !isNextSemester }
    )
    }
    if (deletedCourseJson != null) {
        androidx.compose.material3.Snackbar(
            modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 16.dp)
                .padding(bottom = com.k2767.course.ui.system.LocalAppOverlayBottomInset.current + 12.dp),
            action = {
                androidx.compose.material3.TextButton(onClick = {
                    if (System.currentTimeMillis() < undoDeadline) {
                        val record = deletedCourseJson?.let { ReminderJson.course(JSONObject(it)) }
                        if (record != null) {
                            settingsManager.updateCustomCourse(ScheduleSettingsManager.CustomCourse(record.id.removePrefix("custom:"), record.name,
                                record.location, record.teacher, record.day, record.startPeriod, record.endPeriod, record.weeks), routeAccountKey)
                            reminderScheduler.restoreUndo(deletedRemindersJson)
                        }
                    }
                    deletedCourseJson = null
                }) { Text("撤销") }
            }
        ) { Text("已删除课程") }
    }
    }
    
    if (showSettingsDialog) {
        com.k2767.course.ui.system.GlassSubpage(onDismiss = { showSettingsDialog = false; settingsTermOverride = null }) { close ->
            ScheduleSettingsScreen(
                manager = settingsManager,
                periodTimesOverride = periodTimesFor(termTimeBase),
                semesterStartOverride = termTimeBase?.firstWeekDate?.takeIf { it.isNotBlank() }?.let {
                    runCatching { java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).parse(it)?.time }.getOrNull()
                } ?: 0L,
                onSemesterStartChange = { millis ->
                    if (!isNextSemester && settingsTermOverride == null) settingsManager.semesterStartDate = millis
                    val times = periodTimesFor(termTimeBase)
                    reminderScheduler.updateTimeBase(routeAccountKey, settingsTerm, ScheduleTimeBase(
                        ScheduleTimeBase.dateFromMillis(millis), times.associate { it.period to it.startTime }, times.associate { it.period to it.endTime }))
                },
                onPeriodTimesChange = { times ->
                    if (!isNextSemester && settingsTermOverride == null) settingsManager.savePeriodTimes(times)
                    reminderScheduler.updateTimeBase(routeAccountKey, settingsTerm,
                        (reminderScheduler.timeBase(routeAccountKey, settingsTerm) ?: ScheduleTimeBase()).copy(
                            periodStarts = times.associate { it.period to it.startTime }, periodEnds = times.associate { it.period to it.endTime }))
                },
                customCourses = customCourses,
                onAddCustomCourse = { editingId = java.util.UUID.randomUUID().toString() },
                onEditCustomCourse = { editingId = it },
                onSyncSchedule = {
                    close()
                    loadSchedule(true)
                },
                onClose = {
                    periodCount = settingsManager.periodCount
                    periodTimes = periodTimesFor(displayedTimeBase).map { PeriodTimeUi(it.period, it.startTime, it.endTime) }
                    close()
                }
            )
        }
    }
    
    val notificationCourse = notificationCourseJson?.let { runCatching { ReminderJson.reminder(JSONObject(it)) }.getOrNull() }
    val detailTerm = notificationCourse?.key?.term ?: resolvedTermId
    val selectedDetail = courses.firstOrNull { it.id == detailId && detailTerm == resolvedTermId } ?: notificationCourse?.course?.let {
        ScheduleCourseUi(it.name, it.teacher, it.location, it.day, it.startPeriod, it.endPeriod, it.weeks,
            courseColors[ScheduleIdentity.colorIndex(it.id, courseColors.size)], it.custom, if (it.custom) it.id.removePrefix("custom:") else "", id = it.id)
    }
    selectedDetail?.let { course ->
        com.k2767.course.ui.screen.ScheduleCourseSheet(course, routeAccountKey, detailTerm, if (detailTerm == resolvedTermId) courses else listOf(course),
            sourceCenterX = detailSourceBounds?.center?.x,
            sourceBounds = detailSourceBounds,
            onDismiss = { detailId = null; detailSourceBounds = null; notificationCourseJson = null; focusRegistry.restore(course.id) },
            onEdit = { editingId = course.customId },
            onConfigureTime = { settingsTermOverride = detailTerm; showSettingsDialog = true },
            onDelete = {
                deletedCourseJson = ReminderJson.course(course.record()).toString()
                deletedRemindersJson = reminderScheduler.encodeUndo(reminderScheduler.removeCourse(routeAccountKey, course.id))
                undoDeadline = System.currentTimeMillis() + 5000L
                settingsManager.removeCustomCourse(course.customId, routeAccountKey)
                detailId = null
                detailSourceBounds = null
                notificationCourseJson = null
                focusRegistry.restore(course.id)
            })
    }
    editingId?.let { id ->
        val initial = customCourses.firstOrNull { it.id == id } ?: ScheduleSettingsManager.CustomCourse(
            id, "", "", "", 1, 1, 2, "1-16周")
        com.k2767.course.ui.system.GlassSubpage(onDismiss = { editingId = null }) { close ->
            com.k2767.course.ui.screen.ScheduleCourseEditor(initial, courses, periodCount,
                isNew = customCourses.none { it.id == id }, onClose = close, onSave = { saved ->
                    settingsManager.updateCustomCourse(saved, routeAccountKey)
                    reminderScheduler.updateCustomCourse(routeAccountKey, ScheduleCourseRecord("custom:${saved.id}", saved.name,
                        saved.teacher, saved.location, saved.day, saved.startPeriod, saved.endPeriod, saved.weeks, true))
                    notificationCourse?.key?.storageId?.let { reminderScheduler.findById(it) }?.let {
                        notificationCourseJson = ReminderJson.reminder(it).toString()
                    }
                    close()
                })
        }
    }
}
