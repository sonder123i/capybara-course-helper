package com.k2767.course.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 课表页向根节点的浮动通知上报「当前账号 + 学期没设开学日期」。
 *
 * 通知的宿主 [FloatingNoticeHost] 挂在 MainActivity，`LocalFloatingNotice` 只能往下传，
 * 页面自己 provide 宿主看不见；所以页面把事实和「怎么打开课表设置」交到这里，
 * 由根节点决定它和「需要重新登录」谁优先。
 */
@Stable
class SemesterAnchorNotice {
    /** account|term。用户右滑关掉后，同一把键不再提示，换了学期会重新提示。 */
    private var key: String = ""
    private var dismissedKey: String? = null

    var missing by mutableStateOf(false)
        private set

    var openSettings by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun report(key: String, missing: Boolean, openSettings: () -> Unit) {
        this.key = key
        this.missing = missing
        this.openSettings = openSettings
    }

    /** 右滑划掉：只压掉这一把键的提示，不写进设置，下次换学期还会再提醒。 */
    fun dismiss() {
        dismissedKey = key
        missing = false
    }

    fun notice(): FloatingNotice? {
        if (!missing || dismissedKey == key) return null
        val action = openSettings ?: return null
        return FloatingNotice(
            message = "还没设开学日期，课表只会按第 1 周显示",
            actionLabel = "去设置",
            onClick = action
        )
    }
}

val LocalSemesterAnchorNotice = staticCompositionLocalOf { SemesterAnchorNotice() }

@Composable
fun rememberSemesterAnchorNotice(): SemesterAnchorNotice =
    androidx.compose.runtime.remember { SemesterAnchorNotice() }
