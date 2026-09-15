package com.tyust.course.academic

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyust.course.manager.UserManager
import com.tyust.course.model.SchoolConfig
import com.tyust.course.ui.route.AcademicGrabQueueRoute
import com.tyust.course.ui.system.GlassWindowHost
import com.tyust.course.ui.theme.CourseSelectorTheme
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the real route with isolated preferences; no enrollment or account changes. */
@RunWith(AndroidJUnit4::class)
class AcademicManualQueueDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private lateinit var isolated: Context
    private val generation = mutableIntStateOf(0)
    private val account get() = UserManager.getInstance().currentAccountStorageKey

    @Before fun setUp() {
        val application = object : Application() {
            init { attachBaseContext(compose.activity.applicationContext) }
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("academic_manual_form_test_$name", mode)
        }
        isolated = object : ContextWrapper(compose.activity) {
            override fun getApplicationContext(): Context = application
            override fun getSharedPreferences(name: String, mode: Int) =
                application.getSharedPreferences(name, mode)
        }
        clearTestPreferences()
    }

    @After fun tearDown() = clearTestPreferences()

    private fun clearTestPreferences() {
        if (::isolated.isInitialized) {
            isolated.getSharedPreferences("academic_grab_queue", Context.MODE_PRIVATE).edit().clear().commit()
            isolated.getSharedPreferences("grab_pro_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    private fun showQueue(compact: Boolean = false) {
        val school = SchoolConfig("manual-test", "新正方测试", "school.example", "https").apply {
            academicSystem = AcademicSystem.ZF.id
        }
        compose.setContent {
            val configuration = Configuration(LocalConfiguration.current).apply {
                if (compact) { screenWidthDp = 320; screenHeightDp = 568; smallestScreenWidthDp = 320 }
            }
            val density = LocalDensity.current
            CompositionLocalProvider(LocalContext provides isolated, LocalConfiguration provides configuration,
                LocalDensity provides Density(density.density, if (compact) 1.5f else density.fontScale)) {
                CourseSelectorTheme {
                    Box((if (compact) Modifier.requiredSize(320.dp, 568.dp) else Modifier.fillMaxSize()).testTag("manual-viewport")) {
                        GlassWindowHost { key(generation.intValue) { AcademicGrabQueueRoute(school) } }
                    }
                }
            }
        }
    }

    private fun openAdd(text: String) {
        compose.onNodeWithTag("grab-console-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
        compose.onNodeWithText("教学班（选填）").performScrollTo().assertIsDisplayed()
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(4)
    }

    private fun fillCourse(section: String) {
        compose.onAllNodes(hasSetTextAction())[0].performTextInput("体育（一）")
        compose.onAllNodes(hasSetTextAction())[1].performTextInput(section)
    }

    @Test fun teachingClassesCanBeAddedSeparatelyAndSurviveReopeningTheQueue() {
        showQueue()
        openAdd("手动添加")
        fillCourse("篮球0003")
        capture("new-zf-manual-dialog")
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle {
            val saved = AcademicGrabQueueStore(isolated).items(account).single()
            assertEquals("体育（一）", saved.courseName)
            assertEquals("篮球0003", saved.sectionName)
            assertFalse(saved.useExactMatch)
        }
        openAdd("添加课程")
        fillCourse("足球0003")
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle {
            assertEquals(listOf("篮球0003", "足球0003"), AcademicGrabQueueStore(isolated).items(account).map { it.sectionName })
            generation.intValue++
        }
        for (section in listOf("篮球0003", "足球0003")) {
            compose.onNodeWithTag("grab-console-list").performScrollToNode(hasText(section))
            compose.onNodeWithText(section).assertIsDisplayed()
        }
        capture("new-zf-manual-queue")
    }

    @Test fun teachingClassFormCanScrollAndSubmitOnAShortScreenWithLargeText() {
        showQueue(compact = true)
        openAdd("手动添加")
        fillCourse("篮球0003")
        compose.onAllNodes(hasSetTextAction())[3].performScrollTo().performTextInput("周一")
        compose.onNodeWithText("添加").assertIsDisplayed().assertIsEnabled()
        capture("new-zf-manual-compact")
        compose.onNodeWithText("添加").performClick()
        compose.runOnIdle {
            val saved = AcademicGrabQueueStore(isolated).items(account).single()
            assertEquals("篮球0003", saved.sectionName)
            assertEquals("周一", saved.time)
        }
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "academic-manual-queue").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
