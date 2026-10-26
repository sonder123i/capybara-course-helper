package com.tyust.course.ui

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.update.UpdateManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 线上更新清单的真实链路验证：不解压、不 mock，直接读 SelfHostConfig 指向的双源。
 *
 * 这个用例只在 debug 变体跑（BuildConfig.APPLICATION_ID 必须与清单的 packageName
 * 一致，uiPreview 的 .uipreview 后缀会被清单校验拒绝），发布门禁不含它——
 * 它会依赖公网，放进门禁会让发版被网络波动卡住。
 */
@RunWith(AndroidJUnit4::class)
class UpdateFeedDeviceTest {
    @Test fun liveManifestVerifiesAndReportsThisBuild() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = UpdateManager(context)
        try {
            manager.checkForUpdate(manual = true)
            val deadline = System.currentTimeMillis() + 25_000
            var state = manager.state.value
            while (System.currentTimeMillis() < deadline) {
                state = manager.state.value
                if (state.check != UpdateManager.Check.IDLE && state.check != UpdateManager.Check.CHECKING) break
                Thread.sleep(250)
            }
            val manifest = requireNotNull(state.manifest) {
                "没有解析出清单：${state.check} / ${state.message} / ${state.errorCode}"
            }
            // 签名、packageName、版本字段都由 UpdateManifestVerifier 校验过才会走到这里。
            assertEquals(20001, manifest.versionCode)
            assertEquals("2.0.1", manifest.versionName)
            assertEquals("com.k2767.course", manifest.packageName)
            assertTrue("清单里至少要有可下载的线路", manifest.mirrors.isNotEmpty())
            // 测试包本身就是 20001，所以应当判定为已是最新，而不是又提示一次更新。
            assertEquals("当前构建已是 2.0.1，应判定为最新：${state.message}",
                UpdateManager.Check.UP_TO_DATE, state.check)
        } finally {
            manager.close()
        }
    }
}
