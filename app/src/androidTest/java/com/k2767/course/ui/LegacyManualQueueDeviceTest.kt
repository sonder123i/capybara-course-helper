package com.k2767.course.ui

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.manager.SmartSelector
import com.k2767.course.manager.UserManager
import com.k2767.course.model.SchoolConfig
import com.k2767.course.service.GrabService
import com.k2767.course.ui.route.GrabProRoute
import com.k2767.course.ui.system.GlassWindowHost
import com.k2767.course.ui.theme.CourseSelectorTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Covers the default Zhengfang route shown in the reported screenshot, without enrollment. */
@RunWith(AndroidJUnit4::class)
class LegacyManualQueueDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var isolated: Context
    private val generation = mutableIntStateOf(0)
    private val visible = mutableStateOf(true)
    private val account get() = UserManager.getInstance().currentAccountStorageKey

    @Before fun setUp() {
        val application = object : Application() {
            init { attachBaseContext(compose.activity.applicationContext) }
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("legacy_manual_test_$name", mode)
        }
        isolated = object : ContextWrapper(compose.activity) {
            override fun getApplicationContext(): Context = application
            override fun getSharedPreferences(name: String, mode: Int) = application.getSharedPreferences(name, mode)
        }
        clearPreferences()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            UserManager.getInstance().init(isolated)
            UserManager.getInstance().currentSchool = SchoolConfig("legacy-test", "新正方测试", "unused.invalid", "https")
            SmartSelector.getInstance().init(isolated)
        }
    }

    @After fun tearDown() {
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            UserManager.getInstance().init(compose.activity.applicationContext)
            SmartSelector.getInstance().init(compose.activity.applicationContext)
        }
        clearPreferences()
    }

    private fun clearPreferences() {
        if (::isolated.isInitialized) listOf("smart_selector_prefs", "grab_pro_prefs", "course_selector_prefs").forEach {
            isolated.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun showQueue(compact: Boolean = false) {
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (compact) { screenWidthDp = 320; screenHeightDp = 568; smallestScreenWidthDp = 320 }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides isolated, LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, if (compact) 1.5f else density.fontScale)) {
                CourseSelectorTheme {
                    Box(if (compact) Modifier.requiredSize(320.dp, 568.dp) else Modifier.fillMaxSize()) {
                        if (visible.value) GlassWindowHost { key(generation.intValue) { GrabProRoute() } }
                    }
                }
            }
        }
    }

    private fun openAdd(text: String = "手动添加") {
        compose.onNodeWithTag("grab-console-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText("教学班（选填）").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(3)
    }

    private fun fill(section: String) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("体育（一）")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(section)
    }

    @Test fun teachingClassesPersistSeparatelyAndDoNotShareStatus() {
        showQueue()
        openAdd()
        fill("篮球0003")
        capture("legacy-manual-dialog")
        compose.onNodeWithText("添加").performClick()
        openAdd("添加课程")
        fill("足球0003")
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle {
            val selector = SmartSelector.getInstance()
            val saved = selector.queue
            assertEquals(listOf("篮球0003", "足球0003"), saved.map { it.teachingClassFilter })
            assertFalse(selector.addToQueue(saved.first().copy()))
            selector.reloadForCurrentAccount()
            assertEquals(saved.map { it.queueStatusKey }, selector.queue.map { it.queueStatusKey })
            assertEquals(listOf("篮球0003", "足球0003"), selector.queue.map { it.jxbmc })
            generation.intValue++
        }
        for (section in listOf("篮球0003", "足球0003")) {
            compose.onNodeWithTag("grab-console-list").performScrollToNode(hasText(section))
            compose.onNodeWithText(section).assertIsDisplayed()
        }
        compose.runOnIdle {
            val basketball = SmartSelector.getInstance().queue.first()
            isolated.sendBroadcast(Intent(GrabService.BROADCAST_UPDATE).apply {
                setPackage(isolated.packageName)
                putExtra(GrabService.EXTRA_QUEUE_UPDATED, true)
                putExtra(GrabService.EXTRA_QUEUE_STATUS_KEY, basketball.queueStatusKey)
                putExtra(GrabService.EXTRA_COURSE_NAME_STATUS, basketball.name)
                putExtra(GrabService.EXTRA_COURSE_STATUS, "failed")
                putExtra(GrabService.EXTRA_IS_RUNNING, false)
            })
        }
        compose.waitUntil(5_000) {
            isolated.getSharedPreferences("grab_pro_prefs", Context.MODE_PRIVATE)
                .getString("queue_item_statuses_$account", "").orEmpty().contains("FAILED")
        }
        compose.runOnIdle {
            val queue = SmartSelector.getInstance().queue
            val statuses = isolated.getSharedPreferences("grab_pro_prefs", Context.MODE_PRIVATE)
                .getString("queue_item_statuses_$account", "").orEmpty()
            assertEquals("${queue.first().queueStatusKey}=FAILED", statuses)
        }
        capture("legacy-manual-queue")
    }

    @Test fun shortScreenWithLargeTextCanScrollTheWholeForm() {
        showQueue(compact = true)
        openAdd()
        fill("篮球0003")
        compose.onAllNodes(hasSetTextAction())[2].performScrollTo().performTextInput("李老师")
        compose.onNodeWithText("上课时间（选填）").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("添加").assertIsDisplayed().assertIsEnabled()
        capture("legacy-manual-compact")
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle {
            val saved = SmartSelector.getInstance().queue.single()
            assertEquals("篮球0003", saved.teachingClassFilter)
            assertEquals("李老师", saved.teacher)
        }
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "legacy-manual-queue").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
