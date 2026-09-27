package com.k2767.course.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 课表页向根节点上报「这次同步动了几处」。
 *
 * 宿主 [FloatingNoticeHost] 挂在 MainActivity，`LocalFloatingNotice` 只能往下传，
 * 所以页面交出条数与「知道了」的动作，由根节点决定它和登录类提示、开学日期提示谁优先。
 */
@Stable
class ScheduleChangeNotice {
    var count by mutableIntStateOf(0)
        private set

    var acknowledge by mutableStateOf<(() -> Unit)?>(null)
        private set

    fun report(count: Int, acknowledge: () -> Unit) {
        this.count = count
        this.acknowledge = acknowledge
    }

    fun clear() {
        count = 0
        acknowledge = null
    }

    /**
     * 「知道了」就是已读：基线只在这里前进。点课程卡看明细不算已读——
     * 那样一开门就会把别处还没看到的调整抹掉。
     */
    fun notice(): FloatingNotice? {
        if (count <= 0) return null
        val action = acknowledge ?: return null
        return FloatingNotice(
            message = "课表有 $count 处调整，点开课程可看从→到",
            actionLabel = "知道了",
            onClick = action
        )
    }
}

val LocalScheduleChangeNotice = staticCompositionLocalOf { ScheduleChangeNotice() }

@Composable
fun rememberScheduleChangeNotice(): ScheduleChangeNotice = remember { ScheduleChangeNotice() }
