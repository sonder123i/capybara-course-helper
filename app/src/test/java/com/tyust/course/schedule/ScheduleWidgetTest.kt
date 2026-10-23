package com.tyust.course.schedule

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.Chronometer
import android.widget.FrameLayout
import android.widget.TextView
import com.tyust.course.R
import com.tyust.course.academic.AcademicStudyReader
import com.tyust.course.manager.AppThemeCoordinator
import com.tyust.course.manager.ScheduleSettingsManager
import com.tyust.course.manager.UserManager
import com.tyust.course.model.SchoolConfig
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.text.SimpleDateFormat
import java.util.TimeZone
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [24, 33], application = Application::class)
class ScheduleWidgetTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var previousZone: TimeZone
    private val now get() = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 08:10")!!.time
    private val base = ScheduleTimeBase("2026-09-07", mapOf(1 to "08:00", 2 to "10:00"), mapOf(1 to "08:45", 2 to "10:45"))
    private val course = ScheduleCourseRecord("network:fixture", "大学体育", "教师", "体育馆", 1, 1, 1, "1-16周")
    private fun snapshot(account: String = "a") = ScheduleSnapshot(account, "school", "2026-2027-1", listOf(course), base, 1, true)

    /** Vendor launchers (ColorOS and friends) enumerate providers themselves and skip non-exported receivers. */
    @Test fun widgetReceiversStayExportedSoVendorLaunchersCanListThem() {
        val pm = context.packageManager
        ScheduleWidgetStyle.entries.forEach { style ->
            val info = pm.getReceiverInfo(android.content.ComponentName(context, style.provider), 0)
            assertTrue("${style.title} 的接收器必须是 exported", info.exported)
        }
    }

    @Before fun setup() { previousZone = TimeZone.getDefault(); TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai")) }
    @After fun teardown() { TimeZone.setDefault(previousZone); ScheduleWidgetNavigation.consume() }

    @Test
    @Config(sdk = [33])
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun doubleWidgetAdaptsItsFieldsAndClickTargetsToAvailableSpace() {
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(course, course.copy(id = "next", startPeriod = 2, endPeriod = 2))), now)
        data class SizeCase(
            val width: Int, val height: Int, val time: String, val nextTime: String,
            val roomVisible: Boolean = true, val statusVisible: Boolean = false,
            val courseTargets: Boolean = true
        )
        // Even the minimum size keeps each course's name, start time, room and own click target.
        val cases = listOf(
            SizeCase(130, 56, "08:00", "10:00"),
            SizeCase(150, 110, "08:00", "10:00"),
            SizeCase(250, 110, "08:00", "10:00"),
            SizeCase(280, 110, "08:00–08:45", "10:00–10:45"),
            SizeCase(280, 128, "08:00–08:45", "10:00–10:45", statusVisible = true),
            SizeCase(280, 180, "08:00–08:45", "10:00–10:45", statusVisible = true),
            SizeCase(360, 240, "08:00–08:45", "10:00–10:45", statusVisible = true)
        )
        for (case in cases) {
            val width = case.width
            val height = case.height
            val view = ScheduleWidgetRenderer.views(context, state, width, height, ScheduleWidgetStyle.Double).apply(context, FrameLayout(context))
            val density = context.resources.displayMetrics.density
            view.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            view.layout(0, 0, view.measuredWidth, view.measuredHeight)
            assertEquals("大学体育", view.findViewById<TextView>(R.id.widget_name).text.toString())
            assertEquals("正在上课", view.findViewById<TextView>(R.id.widget_status).text.toString())
            assertTrue("${width}x$height: root=${view.measuredHeight}, body=${view.findViewById<View>(R.id.widget_course).measuredHeight}, density=$density " +
                listOf(R.id.widget_heading, R.id.widget_name, R.id.widget_location, R.id.widget_time).joinToString { id ->
                    val text = view.findViewById<TextView>(id); "$id:height=${text.measuredHeight},min=${text.minHeight},font=${text.textSize},padding=${text.paddingTop}+${text.paddingBottom},line=${text.lineHeight}" },
                view.findViewById<TextView>(R.id.widget_name).measuredHeight > 0)
            assertEquals("体育馆", view.findViewById<TextView>(R.id.widget_location).text.toString())
            assertEquals(case.time, view.findViewById<TextView>(R.id.widget_time).text.toString())
            assertEquals(case.nextTime, view.findViewById<TextView>(R.id.widget_next_time).text.toString())
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_next).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_separator).visibility)
            assertEquals("大学体育", view.findViewById<TextView>(R.id.widget_next_name).text.toString())
            assertEquals("体育馆", view.findViewById<TextView>(R.id.widget_next_location).text.toString())
            for (id in listOf(R.id.widget_location, R.id.widget_next_location)) {
                assertEquals(if (case.roomVisible) View.VISIBLE else View.GONE, view.findViewById<View>(id).visibility)
            }
            for (id in listOf(R.id.widget_status, R.id.widget_next_status)) {
                assertEquals("$width x $height status visibility", if (case.statusVisible) View.VISIBLE else View.GONE, view.findViewById<View>(id).visibility)
            }
            val fields = listOf(R.id.widget_name, R.id.widget_time, R.id.widget_next_name, R.id.widget_next_time) +
                (if (case.roomVisible) listOf(R.id.widget_location, R.id.widget_next_location) else emptyList()) +
                (if (case.statusVisible) listOf(R.id.widget_status, R.id.widget_next_status) else emptyList())
            for (id in fields) {
                val text = view.findViewById<TextView>(id)
                assertEquals(View.VISIBLE, text.visibility)
                assertTrue("$width x $height clipped ${context.resources.getResourceEntryName(id)}: " +
                    "height=${text.height}, line=${text.lineHeight}, layout=${text.layout?.height}, " +
                    "bottom=${text.bottom}, parent=${(text.parent as View).height}",
                    text.height >= text.lineHeight && text.top >= 0 && text.bottom <= (text.parent as View).height)
            }
            assertTrue(view.findViewById<View>(R.id.widget_course).performClick())
            val intent = shadowOf(context as Application).nextStartedActivity
            ScheduleWidgetNavigation.accept(intent)
            assertEquals(if (case.courseTargets) course.id else null, ScheduleWidgetNavigation.requested?.course)
            assertTrue(ScheduleWidgetNavigation.requested!!.matches("a", "school"))
            assertFalse(ScheduleWidgetNavigation.requested!!.matches("b", "school"))
            assertEquals(if (case.courseTargets) ScheduleWidgetAction.Course else ScheduleWidgetAction.Today, ScheduleWidgetNavigation.requested?.action)
            assertTrue(view.findViewById<View>(R.id.widget_next).performClick())
            ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
            assertEquals(if (case.courseTargets) "next" else null, ScheduleWidgetNavigation.requested?.course)
            assertEquals(if (case.courseTargets) ScheduleWidgetAction.Course else ScheduleWidgetAction.Today, ScheduleWidgetNavigation.requested?.action)
            assertTrue(view.findViewById<View>(R.id.widget_course).contentDescription.toString().contains("08:00–08:45"))
            assertTrue(view.findViewById<View>(R.id.widget_next).contentDescription.toString().contains("10:00–10:45"))
            view.findViewById<View>(R.id.widget_header).performClick()
            ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
            assertNull(ScheduleWidgetNavigation.requested?.course)
        }
    }

    @Test fun oldWidgetClicksCannotOpenADifferentAccountsCourseAndThemeChangesKeepReadableText() {
        val old = ScheduleWidgetRenderer.views(context, ScheduleWidgetState.from(snapshot("a"), now)).apply(context, FrameLayout(context))
        context.getSharedPreferences(AppThemeCoordinator.PREFS, 0).edit().putString(AppThemeCoordinator.KEY, "dark").apply()
        val current = ScheduleWidgetRenderer.views(context, ScheduleWidgetState.from(snapshot("b"), now)).apply(context, FrameLayout(context))
        assertNotEquals(old.findViewById<TextView>(R.id.widget_name).currentTextColor, current.findViewById<TextView>(R.id.widget_name).currentTextColor)
        old.findViewById<View>(R.id.widget_course).performClick()
        ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
        assertFalse(ScheduleWidgetNavigation.requested!!.matches("b", "school"))
    }

    private fun assertCompleteLabel(root: ViewGroup, id: Int, label: String) {
        val text = root.findViewById<TextView>(id)
        assertEquals(label, text.text.toString())
        assertEquals(View.VISIBLE, text.visibility)
        val layout = text.layout
        assertNotNull(layout)
        assertEquals("End of $label was hidden", label.length, layout.getLineEnd(layout.lineCount - 1))
        assertTrue("$label was ellipsized", (0 until layout.lineCount).all { layout.getEllipsisCount(it) == 0 })
        assertTrue("$label text layout was clipped: ${layout.height} > ${text.height}", layout.height <= text.height)
        val bounds = Rect(); text.getDrawingRect(bounds); root.offsetDescendantRectToMyCoords(text, bounds)
        assertTrue("$label outside widget: $bounds in ${root.width}x${root.height}", bounds.top >= 0 && bounds.bottom <= root.height && bounds.left >= 0 && bounds.right <= root.width)
    }

    @Test @Config(sdk = [28, 33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun compactWidgetsShowTheWholeCourseAndRoomAtDifferentFontScales() {
        val primary = course.copy(name = "习近平新时代中国特色社会主义思想概论", location = "河东校区电教楼A座502多媒体教室")
        val secondary = course.copy(id = "next", name = "高等数学 A1（理工类）", location = "河西校区综合实验楼B座1208教室", startPeriod = 2, endPeriod = 2)
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(primary, secondary)), now)
        for (fontScale in listOf(1f, 1.3f, 1.8f)) {
            val config = Configuration(context.resources.configuration).apply { this.fontScale = fontScale }
            val scaled = context.createConfigurationContext(config)
            for (style in ScheduleWidgetStyle.entries) for ((width, height) in listOf(150 to 110, 250 to 110, 280 to 128)) {
                val root = ScheduleWidgetRenderer.views(scaled, state, width, height, style).apply(scaled, FrameLayout(scaled)) as ViewGroup
                val density = scaled.resources.displayMetrics.density
                root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                assertCompleteLabel(root, R.id.widget_name, primary.name)
                assertCompleteLabel(root, R.id.widget_location, primary.location)
                if (root.findViewById<View>(R.id.widget_next)?.visibility == View.VISIBLE) {
                    assertCompleteLabel(root, R.id.widget_next_name, secondary.name)
                    assertCompleteLabel(root, R.id.widget_next_location, secondary.location)
                }
                if (fontScale == 1f && style != ScheduleWidgetStyle.Timeline && width != 280) {
                    val file = File("build/reports/widget-layout/${style.name.lowercase()}-${width}x$height.png")
                    file.parentFile.mkdirs()
                    val image = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
                    root.draw(Canvas(image)); file.outputStream().use { image.compress(Bitmap.CompressFormat.PNG, 100, it) }; image.recycle()
                }
            }
        }
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun timelineFitsCompleteRowsAndRetainsTheCurrentCourse() {
        val primary = course.copy(name = "中国特色社会主义理论与实践研究", location = "河东校区电教楼A座502多媒体教室")
        val secondary = course.copy(id = "next", name = "高等数学 A1（理工类）", location = "河西校区综合实验楼B座1208教室", startPeriod = 2, endPeriod = 2)
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(primary, secondary)), now)
        for ((width, height) in listOf(200 to 170, 250 to 220, 360 to 240)) {
            val root = ScheduleWidgetRenderer.views(context, state, width, height, ScheduleWidgetStyle.Timeline).apply(context, FrameLayout(context)) as ViewGroup
            val density = context.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val rows = root.findViewById<ViewGroup>(R.id.widget_timeline_rows)
            assertTrue("Current class must stay visible", rows.childCount > 0)
            assertCompleteLabel(root, R.id.widget_name, primary.name)
            assertCompleteLabel(root, R.id.widget_location, primary.location)
            for (index in 0 until rows.childCount) {
                val row = rows.getChildAt(index) as ViewGroup
                assertTrue("Timeline row clipped", row.bottom <= rows.height)
                for (id in listOf(R.id.widget_name, R.id.widget_location)) assertCompleteLabel(row, id, row.findViewById<TextView>(id).text.toString())
            }
        }
    }

    @Test fun offlineEmptyMissingCalendarAndLoggedOutStatesAreDistinct() {
        assertEquals("登录后查看课表", ScheduleWidgetState.from(null, now).title)
        assertEquals("还没有本地课表", ScheduleWidgetState.from(snapshot().copy(hasCache = false, courses = emptyList()), now).title)
        assertEquals("请设置开学日期", ScheduleWidgetState.from(snapshot().copy(timeBase = ScheduleTimeBase()), now).title)
        assertEquals("今天没有课程", ScheduleWidgetState.from(snapshot().copy(courses = emptyList()), now).title)
        assertEquals(course.id, ScheduleWidgetState.from(snapshot(), now).primary?.course?.id)
    }

    @Test fun endedDayDoesNotAdvanceWidgetsToAFutureTeachingDay() {
        val idle = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 11:00")!!.time
        val next = course.copy(id = "tomorrow", day = 2)
        val later = course.copy(id = "later", day = 4)
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(course, next, later)), idle)
        assertNull(state.primary)
        assertNull(state.secondary)
        assertEquals("今日课程已结束", state.message)
        assertEquals(listOf(course.id), ScheduleWidgetRenderer.timelineItems(state, 6).map { it.course.id })
        val unknown = next.copy(weeks = "待安排")
        assertNull(ScheduleWidgetState.from(snapshot().copy(courses = listOf(unknown, later)), idle).primary)
        assertNull(ScheduleWidgetState.from(snapshot().copy(courses = listOf(unknown)), idle).primary)
    }

    @Test fun emptyStateButtonsCarryOneSpecificActionAndTheAccountBoundary() {
        val cases = listOf(null to ScheduleWidgetAction.Login,
            snapshot().copy(hasCache = false, courses = emptyList()) to ScheduleWidgetAction.Sync,
            snapshot().copy(timeBase = ScheduleTimeBase()) to ScheduleWidgetAction.Calendar,
            snapshot().copy(courses = emptyList()) to ScheduleWidgetAction.Today)
        for ((snapshot, expected) in cases) {
            val state = ScheduleWidgetState.from(snapshot, now)
            assertNotNull(state.message)
            val view = ScheduleWidgetRenderer.views(context, state).apply(context, FrameLayout(context))
            assertEquals(View.GONE, view.findViewById<View>(R.id.widget_courses).visibility)
            assertEquals(View.VISIBLE, view.findViewById<View>(R.id.widget_empty).visibility)
            view.findViewById<View>(R.id.widget_action).performClick()
            ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
            assertEquals(expected, ScheduleWidgetNavigation.requested?.action)
            assertTrue(ScheduleWidgetNavigation.requested!!.matches("a", "school"))
            if (snapshot != null) assertFalse(ScheduleWidgetNavigation.requested!!.matches("b", "school"))
        }
    }

    @Test fun repositoryReadsOnlyTheChosenAccountAndMigratesLegacyCalendarToCurrentTerm() {
        val term = AcademicStudyReader.calendarTerm()
        val prefs = context.getSharedPreferences("schedule_cache", 0)
        val cache = ScheduleCacheStore(prefs)
        val json = """{"kbList":[{"kcmc":"大学体育","xqj":1,"jcs":"1-2","zcd":"1-16周"}]}"""
        cache.save("a", "school", CachedSchedule(term, term, json, false))
        val settingsPrefs = context.getSharedPreferences("schedule_settings", 0)
        settingsPrefs.edit().putLong("semester_start_a", now).apply()
        val settings = ScheduleSettingsManager(settingsPrefs)
        settings.addCustomCourse(ScheduleSettingsManager.CustomCourse("mine", "自习", "", "", 2, 1, 2, "1-16周"), "a")
        val repo = ScheduleRepository(context, settings)
        val first = repo.snapshot("a", "school")
        assertTrue(first.hasCache)
        assertEquals(2, first.courses.size)
        assertEquals("2026-09-07", first.timeBase.firstWeekDate)
        assertFalse(repo.snapshot("b", "school").hasCache)
        assertTrue(repo.snapshot("b", "school").courses.isEmpty())
        assertEquals("", repo.snapshot("a", "school", term.next().id).timeBase.firstWeekDate)
    }

    @Test fun fourWidgetStylesKeepIndependentProvidersAndNeverFillTodayWithTomorrow() {
        assertEquals(4, ScheduleWidgetStyle.entries.map { it.provider }.distinct().size)
        val today = course.copy(id = "today", startPeriod = 2, endPeriod = 2)
        val tomorrow = today.copy(id = "tomorrow", day = 2)
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(course, today, tomorrow)), now)
        assertEquals(listOf(course.id, today.id), ScheduleWidgetRenderer.timelineItems(state, 6).map { it.course.id })
        val noClassesToday = ScheduleWidgetState.from(snapshot().copy(courses = listOf(tomorrow)), now)
        assertTrue(ScheduleWidgetRenderer.timelineItems(noClassesToday, 6).isEmpty())
        assertNull(noClassesToday.primary)
        assertNull(noClassesToday.secondary)
        assertEquals("今天没有课程", noClassesToday.message)
    }

    @Test fun countdownTargetsTheNextBoundaryAndFollowsTheClassState() {
        val before = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 07:40")!!.time
        val upcoming = ScheduleWidgetState.from(snapshot(), before)
        assertEquals("距上课", upcoming.countdownLabel)
        assertEquals(upcoming.primary!!.occurrence.startsAt, upcoming.countdownAt)
        val during = ScheduleWidgetState.from(snapshot(), now)
        assertEquals("正在上课", during.primary?.status)
        assertEquals("距下课", during.countdownLabel)
        assertEquals(during.primary!!.occurrence.endsAt, during.countdownAt)
        val idle = ScheduleWidgetState.from(snapshot().copy(courses = emptyList()), now)
        assertNull(idle.primary)
        assertNull(idle.countdownAt)
        assertNull(ScheduleWidgetState.from(null, now).countdownAt)
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun countdownStyleRendersASelfUpdatingTimerBesideTheCourse() {
        // 固定夹具时间（2026-09）早于真实时钟，直接渲染会进入“已越过目标→钳制 00:00”分支；
        // 这里显式给一个还在未来的目标，验证仍然正确换算到开机时间轴。
        val state = ScheduleWidgetState.from(snapshot(), now)
            .copy(countdownAt = System.currentTimeMillis() + 45 * 60_000L, countdownLabel = "距下课")
        for ((width, height) in listOf(130 to 56, 150 to 110, 280 to 128, 360 to 240)) {
            val root = ScheduleWidgetRenderer.views(context, state, width, height, ScheduleWidgetStyle.Countdown)
                .apply(context, FrameLayout(context)) as ViewGroup
            val density = context.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val size = "$width x $height"
            assertCompleteLabel(root, R.id.widget_name, course.name)
            assertEquals("$size timer box", View.VISIBLE, root.findViewById<View>(R.id.widget_countdown_box).visibility)
            assertEquals("$size timer label", "距下课", root.findViewById<TextView>(R.id.widget_countdown_label).text.toString())
            // The class details may yield their row on the smallest widget, but never render clipped.
            val details = mapOf(R.id.widget_time to requireNotNull(state.primary).time, R.id.widget_location to course.location)
            details.forEach { (id, label) ->
                if (root.findViewById<View>(id).visibility == View.VISIBLE) assertCompleteLabel(root, id, label)
            }
            val timer = requireNotNull(root.findViewById<Chronometer>(R.id.widget_countdown))
            assertTrue("$size timer must count down", timer.isCountDown)
            // Chronometer 的 base 必须落在开机时间轴上：剩余时间要等于墙上时钟的剩余时间。
            val remainingOnWidget = timer.base - android.os.SystemClock.elapsedRealtime()
            val remainingOnClock = requireNotNull(state.countdownAt) - System.currentTimeMillis()
            assertTrue("$size countdown must use the uptime timebase: $remainingOnWidget vs $remainingOnClock",
                kotlin.math.abs(remainingOnWidget - remainingOnClock) < 60_000L)
            assertTrue("$size header", root.findViewById<View>(R.id.widget_header).isClickable)
            assertTrue(root.findViewById<View>(R.id.widget_course).performClick())
            ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
            assertEquals(course.id, ScheduleWidgetNavigation.requested?.course)
        }
    }

    @Test @Config(sdk = [33])
    fun countdownStyleShowsTheSameEmptyStatesAsTheOtherWidgets() {
        for (snapshot in listOf(null, snapshot().copy(hasCache = false, courses = emptyList()),
            snapshot().copy(timeBase = ScheduleTimeBase()), snapshot().copy(courses = emptyList()))) {
            val state = ScheduleWidgetState.from(snapshot, now)
            val root = ScheduleWidgetRenderer.views(context, state, 130, 56, ScheduleWidgetStyle.Countdown)
                .apply(context, FrameLayout(context)) as ViewGroup
            assertEquals(View.GONE, root.findViewById<View>(R.id.widget_courses).visibility)
            assertEquals(View.GONE, root.findViewById<View>(R.id.widget_countdown_box).visibility)
            assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_empty).visibility)
            assertEquals(state.message, root.findViewById<TextView>(R.id.widget_message).text.toString())
            assertEquals(state.actionLabel, root.findViewById<TextView>(R.id.widget_action).text.toString())
        }
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun countdownRendersZeroInsteadOfNegativeNumbersWhenTheTargetAlreadyPassed() {
        // 边界广播被系统推迟、目标时间已经过去时才重新渲染：不能再启动宿主计时器，
        // 否则框架的 Chronometer 会显示 -00:01；此时必须直接静态渲染 00:00。
        val state = ScheduleWidgetState.from(snapshot(), now)
            .copy(countdownAt = System.currentTimeMillis() - 5_000L, countdownLabel = "距下课")
        val root = ScheduleWidgetRenderer.views(context, state, 280, 128, ScheduleWidgetStyle.Countdown)
            .apply(context, FrameLayout(context)) as ViewGroup
        val timer = requireNotNull(root.findViewById<TextView>(R.id.widget_countdown))
        assertEquals("00:00", timer.text.toString())
        assertFalse("past countdown must not render a negative number", timer.text.toString().startsWith("-"))
        assertEquals(View.VISIBLE, root.findViewById<View>(R.id.widget_countdown_box).visibility)
    }

    /**
     * 桌面小组件在 Launcher 进程 inflate RemoteViews 布局。这里把宿主 ClassLoader 限制成看不到
     * com.tyust.course.*（模拟 Launcher 只加载系统类），四个组件布局都必须能正常 inflate。
     * 之前倒计时布局用了 App 自定义的 Chronometer 子类，在真实桌面会 InflateException。
     */
    @Test fun widgetLayoutsInflateUnderAnIsolatedLauncherClassLoader() {
        val hostLoader = object : ClassLoader(ScheduleWidgetTest::class.java.classLoader) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                if (name.startsWith("com.tyust.course."))
                    throw ClassNotFoundException("launcher cannot load provider code: $name")
                return super.loadClass(name, resolve)
            }
        }
        val isolated = object : android.content.ContextWrapper(context) {
            override fun getClassLoader(): ClassLoader = hostLoader
        }
        val inflater = android.view.LayoutInflater.from(context).cloneInContext(isolated)
        val layouts = mapOf(
            "single" to R.layout.schedule_widget_single,
            "double" to R.layout.schedule_widget,
            "timeline" to R.layout.schedule_widget_timeline,
            "countdown" to R.layout.schedule_widget_countdown
        )
        for ((name, layoutId) in layouts) {
            val root = requireNotNull(inflater.inflate(layoutId, FrameLayout(isolated), false)) as ViewGroup
            assertTrue("$name widget layout must inflate with only platform views", root.childCount > 0)
        }
    }

    @Test fun segmentedCountdownSplitsALongClassAtTheBreakButLabelsItAsABreak() {
        val base = ScheduleTimeBase("2026-09-07", mapOf(1 to "08:00", 2 to "10:00"), mapOf(1 to "08:45", 2 to "10:45"))
        val long = course.copy(endPeriod = 2)
        val data = snapshot().copy(courses = listOf(long), timeBase = base)
        fun at(clock: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 $clock")!!.time
        fun stateAt(clock: String) = ScheduleWidgetState.from(data, at(clock))
        fun resolve(clock: String, segmented: Boolean = true) = requireNotNull(ScheduleWidgetCountdown.resolve(stateAt(clock), segmented))

        // 大课第一小节内：倒计时到课间，并标记为“距课间”。
        val firstHalf = resolve("08:10")
        assertEquals("距课间", firstHalf.label)
        assertEquals(at("08:45"), firstHalf.at)
        assertEquals(at("08:00"), firstHalf.segmentStart)
        assertNotNull(ScheduleWidgetCountdown.progress(stateAt("08:10"), firstHalf))
        // 课间：倒计时到下一小节开始，明确告诉用户这是课间而不是整节课结束。
        val inBreak = resolve("09:00")
        assertEquals("课间休息 · 距上课", inBreak.label)
        assertEquals(true, inBreak.inBreak)
        assertEquals(at("10:00"), inBreak.at)
        assertNull(ScheduleWidgetCountdown.progress(stateAt("09:00"), inBreak))
        // 第二小节内：倒计时到整节课结束。
        val secondHalf = resolve("10:10")
        assertEquals("距下课", secondHalf.label)
        assertEquals(at("10:45"), secondHalf.at)
        assertNotNull(ScheduleWidgetCountdown.progress(stateAt("10:10"), secondHalf))
        // 进度条按剩余时间从满格走到 0：越接近小节结束，剩余比例越小。
        val earlyProgress = requireNotNull(ScheduleWidgetCountdown.progress(stateAt("08:10"), firstHalf))
        val lateProgress = requireNotNull(ScheduleWidgetCountdown.progress(stateAt("08:40"), resolve("08:40")))
        assertTrue("progress must start near 100%: $earlyProgress", earlyProgress > 0.6f)
        assertTrue("progress must shrink towards 0: $lateProgress", lateProgress < 0.2f)
        // 关闭选项：整门课只倒计时到下课，不拆分。
        val whole = resolve("08:10", segmented = false)
        assertEquals("距下课", whole.label)
        assertEquals(at("10:45"), whole.at)
    }

    @Test fun segmentedCountdownFallsBackWhenALaterSegmentTimeIsMissingOrReversed() {
        fun at(clock: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 $clock")!!.time
        val long = course.copy(endPeriod = 3)
        // 第二小节缺结束时间：即使当前落在第一小节里，也必须先校验后面的节次，不能给出分段倒计时。
        val missing = ScheduleTimeBase("2026-09-07",
            mapOf(1 to "08:00", 2 to "08:50", 3 to "09:40"),
            mapOf(1 to "08:45", 3 to "10:25"))
        val missingState = ScheduleWidgetState.from(
            snapshot().copy(courses = listOf(long), timeBase = missing), at("08:10"))
        val missingTarget = requireNotNull(ScheduleWidgetCountdown.resolve(missingState, segmented = true))
        assertEquals("距下课", missingTarget.label)
        assertEquals(at("10:25"), missingTarget.at)

        // 第二小节的结束时间早于开始时间：同样不能按分段显示。
        val reversed = ScheduleTimeBase("2026-09-07",
            mapOf(1 to "08:00", 2 to "08:50", 3 to "09:40"),
            mapOf(1 to "08:45", 2 to "08:20", 3 to "10:25"))
        val reversedState = ScheduleWidgetState.from(
            snapshot().copy(courses = listOf(long), timeBase = reversed), at("08:10"))
        val reversedTarget = requireNotNull(ScheduleWidgetCountdown.resolve(reversedState, segmented = true))
        assertEquals("距下课", reversedTarget.label)
        assertEquals(at("10:25"), reversedTarget.at)
    }

    @Test fun segmentedCountdownSchedulesTheBreakBoundaryInsteadOfOnlyTheCourseEnd() {
        val base = ScheduleTimeBase("2026-09-07", mapOf(1 to "08:00", 2 to "10:00"), mapOf(1 to "08:45", 2 to "10:45"))
        val data = snapshot().copy(courses = listOf(course.copy(endPeriod = 2)), timeBase = base)
        fun at(clock: String) = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 $clock")!!.time

        // 整门课边界是 10:45；分段模式必须同时把 08:45 的小节边界排进边界闹钟，
        // 否则闹钟跳过课间，Chronometer 越过 0 后会一直显示负数。
        val firstHalf = ScheduleWidgetState.from(data, at("08:10"))
        assertEquals(at("08:45"), ScheduleWidgetCountdown.nextRefreshAt(firstHalf, segmented = true))
        assertEquals(at("10:45"), ScheduleWidgetCountdown.nextRefreshAt(firstHalf, segmented = false))

        // 课间里下一次刷新必须是 10:00（下一小节上课），而不是等到 10:45。
        val inBreak = ScheduleWidgetState.from(data, at("09:00"))
        assertEquals(at("10:00"), ScheduleWidgetCountdown.nextRefreshAt(inBreak, segmented = true))
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun breakLabelIsNotEllipsizedOnANarrowCountdownWidget() {
        val base = ScheduleTimeBase("2026-09-07", mapOf(1 to "08:00", 2 to "10:00"), mapOf(1 to "08:45", 2 to "10:45"))
        val data = snapshot().copy(courses = listOf(course.copy(endPeriod = 2)), timeBase = base)
        val state = ScheduleWidgetState.from(data, SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 09:00")!!.time)
        val config = android.content.res.Configuration(context.resources.configuration).apply { fontScale = 1.12f }
        val scaled = context.createConfigurationContext(config)
        for ((width, height) in listOf(143 to 100, 180 to 120, 280 to 128)) {
            val root = ScheduleWidgetRenderer.views(scaled, state, width, height, ScheduleWidgetStyle.Countdown,
                ScheduleWidgetAppearance(segmented = true)).apply(scaled, FrameLayout(scaled)) as ViewGroup
            val density = scaled.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val label = requireNotNull(root.findViewById<TextView>(R.id.widget_countdown_label))
            assertEquals("$width x $height label", View.VISIBLE, label.visibility)
            // 课间文案必须完整可读，不能被倒计时盒子挤成“课间休息…”
            assertEquals("$width x $height label text", "课间休息 · 距上课", label.text.toString())
            assertEquals("$width x $height label must not be ellipsized", 0, requireNotNull(label.layout).getEllipsisCount(0))
        }
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun tomorrowTimelineKeepsItsRowsWholeOnASmallWidgetWithLargeFont() {
        val tomorrow = course.copy(id = "tomorrow", day = 2, name = "高等数学")
        val data = snapshot().copy(courses = listOf(course, tomorrow))
        val evening = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 23:00")!!.time
        val state = ScheduleWidgetState.from(data, evening)
        val config = Configuration(context.resources.configuration).apply { fontScale = 1.3f }
        val scaled = context.createConfigurationContext(config)
        for ((width, height) in listOf(130 to 130, 130 to 170, 176 to 176)) {
            val root = ScheduleWidgetRenderer.views(scaled, state, width, height, ScheduleWidgetStyle.Timeline,
                ScheduleWidgetAppearance(tomorrow = true)).apply(scaled, FrameLayout(scaled)) as ViewGroup
            val density = scaled.resources.displayMetrics.density
            root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
            root.layout(0, 0, root.measuredWidth, root.measuredHeight)
            val visible = mutableListOf<TextView>()
            fun collect(view: View) {
                if (view.visibility != View.VISIBLE) return
                if (view is TextView && view.text.isNotEmpty()) visible += view
                if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
            }
            collect(root)
            for (text in visible) {
                val rect = Rect(0, 0, text.width, text.height)
                root.offsetDescendantRectToMyCoords(text, rect)
                assertTrue("$width x $height clipped ${scaled.resources.getResourceEntryName(text.id)}: $rect",
                    text.height >= text.lineHeight && rect.top >= 0 && rect.bottom <= root.height)
                val layout = requireNotNull(text.layout)
                assertEquals("$width x $height truncated ${text.text}", 0, layout.getEllipsisCount(layout.lineCount - 1))
            }
        }
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun tomorrowOptionShowsTheNextDayOnceTodaysClassesAreOver() {
        val tomorrow = course.copy(id = "tomorrow", day = 2, name = "高等数学")
        val data = snapshot().copy(courses = listOf(course, tomorrow))
        val evening = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 23:00")!!.time
        val state = ScheduleWidgetState.from(data, evening)
        assertEquals("今日课程已结束", state.message)
        assertEquals(listOf("tomorrow"), state.tomorrow.map { it.occurrence.course.id })

        // 默认关闭：老组件保持“今日课程已结束”空态，不偷显示明天。
        val legacy = ScheduleWidgetRenderer.views(context, state, 280, 128, ScheduleWidgetStyle.Double)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals(View.GONE, legacy.findViewById<View>(R.id.widget_courses).visibility)
        assertEquals("今日课程已结束", legacy.findViewById<TextView>(R.id.widget_message).text.toString())

        val tomorrowOption = ScheduleWidgetAppearance(tomorrow = true)
        val double = ScheduleWidgetRenderer.views(context, state, 280, 128, ScheduleWidgetStyle.Double, tomorrowOption)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals(View.VISIBLE, double.findViewById<View>(R.id.widget_courses).visibility)
        assertEquals("高等数学", double.findViewById<TextView>(R.id.widget_name).text.toString())

        // 倒计时样式改为“距明日上课”，并显示明日第一节。
        val countdown = ScheduleWidgetRenderer.views(context, state, 280, 128, ScheduleWidgetStyle.Countdown, tomorrowOption)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals("距明日上课", countdown.findViewById<TextView>(R.id.widget_countdown_label).text.toString())
        assertEquals("高等数学", countdown.findViewById<TextView>(R.id.widget_name).text.toString())

        // 时间轴样式显示“今日课程结束” + 明日课表行。
        val timeline = ScheduleWidgetRenderer.views(context, state, 360, 240, ScheduleWidgetStyle.Timeline, tomorrowOption)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals("今日课程结束", timeline.findViewById<TextView>(R.id.widget_heading).text.toString())
        val labels = mutableListOf<String>()
        fun collect(view: View) {
            if (view is TextView) labels += view.text.toString()
            if (view is ViewGroup) for (index in 0 until view.childCount) collect(view.getChildAt(index))
        }
        collect(timeline)
        assertTrue("timeline must mark the section with 今日课程结束", labels.contains("明日课表"))
        assertTrue("timeline must list tomorrow's course", labels.contains("高等数学"))

        // 今天本来没课（只有明天的课）：打开选项后同样显示“今日课程结束” + 明日课表。
        val noClassToday = ScheduleWidgetState.from(snapshot().copy(courses = listOf(tomorrow)),
            SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 10:00")!!.time)
        assertEquals("今天没有课程", noClassToday.message)
        val emptyDay = ScheduleWidgetRenderer.views(context, noClassToday, 280, 128, ScheduleWidgetStyle.Double, tomorrowOption)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals("高等数学", emptyDay.findViewById<TextView>(R.id.widget_name).text.toString())

        // 明日无课：明确显示“明日无课”，不假装有课。
        val emptyTomorrow = ScheduleWidgetState.from(snapshot(), evening)
        val withoutTomorrow = ScheduleWidgetRenderer.views(context, emptyTomorrow, 280, 128, ScheduleWidgetStyle.Double, tomorrowOption)
            .apply(context, FrameLayout(context)) as ViewGroup
        assertEquals("今日课程结束 · 明日无课", withoutTomorrow.findViewById<TextView>(R.id.widget_message).text.toString())
    }

    @Test @Config(sdk = [33])
    fun boundaryRefreshUsesAWakeupAlarmSoTheCountdownCannotGoStaleWhileTheScreenIsOff() {
        val alarmManager = context.getSystemService(android.app.AlarmManager::class.java)
        val pending = android.app.PendingIntent.getBroadcast(context, 0,
            android.content.Intent(context, ScheduleWidgetProvider::class.java)
                .setAction("com.tyust.course.action.REFRESH_SCHEDULE_WIDGET"),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        ScheduleWidgetBoundaryAlarm.schedule(context, System.currentTimeMillis() + 60_000L, pending, wake = true)
        val alarm = shadowOf(alarmManager).scheduledAlarms.last()
        assertEquals("boundary refresh must wake the device so the countdown never stays stale",
            android.app.AlarmManager.RTC_WAKEUP, alarm.type)
    }

    @Test fun singleClassDayDoesNotBorrowTomorrowBeforeDuringOrAfterClass() {
        val tomorrow = course.copy(id = "tomorrow", day = 2)
        val data = snapshot().copy(courses = listOf(course, tomorrow))
        for (clock in listOf("07:00", "08:10", "08:45", "23:59")) {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-07 $clock")!!.time
            val state = ScheduleWidgetState.from(data, time)
            assertNull("$clock must not show tomorrow in the second slot", state.secondary)
            assertEquals(listOf(course.id), ScheduleWidgetRenderer.timelineItems(state, 6).map { it.course.id })
            if (clock < "08:45") assertEquals(course.id, state.primary?.course?.id)
            else {
                assertNull(state.primary)
                assertEquals("今日课程已结束", state.message)
            }
        }
    }

    @Test fun widgetAdvancesToTheNextDayAtLocalMidnight() {
        val tomorrow = course.copy(id = "tomorrow", day = 2)
        val midnight = SimpleDateFormat("yyyy-MM-dd HH:mm").parse("2026-09-08 00:00")!!.time
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(course, tomorrow)), midnight)
        assertEquals("tomorrow", state.primary?.course?.id)
        assertEquals("", state.primary?.dateLabel)
        assertNull(state.secondary)
        assertEquals(listOf("tomorrow"), ScheduleWidgetRenderer.timelineItems(state, 6).map { it.course.id })
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun minimumSizesPrioritizeNameTimeRoomAndLargerSizesAddTeacherAcrossThemes() {
        val longCourse = course.copy(name = "跨学科联合研讨与实验课程", teacher = "张文博、李思远",
            location = "主校区 明理教学楼 B302（实验机房）")
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(longCourse,
            longCourse.copy(id = "next", startPeriod = 2, endPeriod = 2))), now)
        val fields = setOf(R.id.widget_name, R.id.widget_time, R.id.widget_location, R.id.widget_next_name,
            R.id.widget_next_time, R.id.widget_next_location, R.id.widget_timeline_time, R.id.widget_teacher, R.id.widget_next_teacher)
        for (font in listOf(1f, 1.3f, 1.6f, 2f)) for (theme in listOf("light", "dark")) {
            context.getSharedPreferences(AppThemeCoordinator.PREFS, 0).edit().putString(AppThemeCoordinator.KEY, theme).apply()
            val config = android.content.res.Configuration(context.resources.configuration).apply { fontScale = font }
            val sized = context.createConfigurationContext(config)
            for ((style, sizes) in listOf(
                ScheduleWidgetStyle.Single to listOf(56 to 56, 88 to 88, 176 to 176),
                ScheduleWidgetStyle.Double to listOf(130 to 56, 176 to 88, 280 to 128),
                ScheduleWidgetStyle.Timeline to listOf(130 to 130, 176 to 176, 360 to 240))) {
                for ((width, height) in sizes) {
                    val root = ScheduleWidgetRenderer.views(sized, state, width, height, style).apply(sized, FrameLayout(sized)) as android.view.ViewGroup
                    val density = sized.resources.displayMetrics.density
                    root.measure(View.MeasureSpec.makeMeasureSpec((width * density).toInt(), View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec((height * density).toInt(), View.MeasureSpec.EXACTLY))
                    root.layout(0, 0, root.measuredWidth, root.measuredHeight)
                    val visible = mutableListOf<TextView>()
                    fun collect(view: View) {
                        if (view.visibility != View.VISIBLE) return
                        if (view is TextView && view.id in fields) visible += view
                        if (view is android.view.ViewGroup) for (i in 0 until view.childCount) collect(view.getChildAt(i))
                    }
                    collect(root)
                    assertTrue("$style $width/$height font=$font $theme lost required fields", visible.size >= (if (style == ScheduleWidgetStyle.Timeline || height >= 92 && width >= 80) 4 else 3) * (if (style == ScheduleWidgetStyle.Double) 2 else 1))
                    for (text in visible) {
                        val rect = android.graphics.Rect(0, 0, text.width, text.height)
                        root.offsetDescendantRectToMyCoords(text, rect)
                        assertTrue("$style $width/$height font=$font clips ${sized.resources.getResourceEntryName(text.id)}: $rect",
                            text.height >= text.lineHeight && rect.top >= 0 && rect.bottom <= root.height && rect.left >= 0 && rect.right <= root.width)
                        assertTrue(text.text.isNotBlank())
                        val layout = requireNotNull(text.layout)
                        assertTrue("$style $width/$height font=$font clipped ${text.text}", text.height >= layout.height)
                        assertEquals(text.text.length, layout.getLineEnd(layout.lineCount - 1))
                        for (line in 0 until layout.lineCount) {
                            assertEquals("$style ellipsized ${text.text}", 0, layout.getEllipsisCount(line))
                            assertTrue("$style line exceeds width: ${text.text}", layout.getLineWidth(line) <= text.width + 1)
                        }
                        if (text.id in setOf(R.id.widget_name, R.id.widget_next_name)) assertEquals(longCourse.name, text.text.toString())
                        if (text.id in setOf(R.id.widget_teacher, R.id.widget_next_teacher)) assertEquals(longCourse.teacher, text.text.toString())
                        if (text.id in setOf(R.id.widget_location, R.id.widget_next_location)) assertTrue(text.text.contains("B302"))
                    }
                }
            }
        }
        assertEquals("主校区 明理教学楼 B302（实验机房）", longCourse.location)
    }

    @Test @Config(sdk = [33]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun everyStyleRendersAtSeveralSizesAndTimelineCoursesOpenTheirOwnDetails() {
        val state = ScheduleWidgetState.from(snapshot().copy(courses = listOf(course, course.copy(id = "next", startPeriod = 2, endPeriod = 2))), now)
        for (style in ScheduleWidgetStyle.entries) {
            for ((width, height) in listOf(150 to 110, 280 to 180, 360 to 240)) {
                val view = ScheduleWidgetRenderer.views(context, state, width, height, style).apply(context, FrameLayout(context))
                assertTrue(view.findViewById<View>(R.id.widget_header).isClickable)
                assertTrue(view.findViewById<View>(R.id.widget_course).performClick())
                ScheduleWidgetNavigation.accept(shadowOf(context as Application).nextStartedActivity)
                assertEquals(course.id, ScheduleWidgetNavigation.requested?.course)
            }
        }
    }

    @Test fun accountSwitchExpiryRemovalAndLogoutRefreshEligibilityWithoutLoggingIn() {
        val user = UserManager.getInstance().apply { init(context); clearLoginState() }
        val school = SchoolConfig("widget-fixture-school", "测试学校", "fixture.invalid", "https")
        user.addCustomSchool(school)
        user.currentSchool = school
        user.studentId = "fixture-a"
        user.saveCookieLogin("SESSION=fixture-a")
        val accountA = user.currentAccountKey
        val storageA = user.currentAccountStorageKey
        assertEquals(storageA, ScheduleWidgetUpdater.activeSnapshot(context)?.account)
        user.clearLoginState()
        assertNull(ScheduleWidgetUpdater.activeSnapshot(context))
        user.currentSchool = school
        user.studentId = "fixture-b"
        user.saveCookieLogin("SESSION=fixture-b")
        val storageB = user.currentAccountStorageKey
        assertNotEquals(storageA, storageB)
        assertEquals(storageB, ScheduleWidgetUpdater.activeSnapshot(context)?.account)
        user.sessionState.expire(user.sessionState.token)
        assertEquals(storageB, ScheduleWidgetUpdater.activeSnapshot(context)?.account)
        user.deleteAccount(user.currentAccountKey)
        assertNull(ScheduleWidgetUpdater.activeSnapshot(context))
        assertTrue(user.switchToAccount(accountA))
        assertEquals(storageA, ScheduleWidgetUpdater.activeSnapshot(context)?.account)
        user.logout()
        assertNull(ScheduleWidgetUpdater.activeSnapshot(context))
    }
}
