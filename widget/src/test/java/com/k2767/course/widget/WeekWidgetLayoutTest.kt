package com.k2767.course.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 周课表网格纵向排布的回归测试。
 *
 * 这类「只在某些尺寸区间才出错」的 bug 靠肉眼测是测不完的：你可以试 2×3 和 4×3，
 * 但试不出 480dp 那一档——而问题恰恰是用户把组件拖到那一档才暴露出来的（下面一大片留白）。
 * 所以这里跑全尺寸区间。
 */
class WeekWidgetLayoutTest {

    private val chrome = WeekChromeHeight

    @Test
    fun `行高乘行数恰好填满可用高度_全尺寸区间`() {
        var height = 60
        while (height <= 720) {
            val available = height.dp - chrome
            if (available > 0.dp) {
                for (used in 1..WeekMaxRows) {
                    val (rows, rowHeight) = weekGridRows(available, used)
                    assertTrue("rows 至少为 1（高 ${height}dp）", rows >= 1)
                    assertTrue("rows 不能超过 $WeekMaxRows", rows <= WeekMaxRows)
                    assertEquals(
                        "高 ${height}dp / 已用 ${used} 节：行高×行数必须等于可用高度，否则就是留白或截断",
                        available.value.toDouble(), (rowHeight.value * rows).toDouble(), 0.01
                    )
                }
            }
            height += 7
        }
    }

    @Test
    fun `组件拉高时应当多显示节次而不是留白`() {
        val small = weekGridRows(214.dp - chrome, 6)
        val large = weekGridRows(482.dp - chrome, 6)

        assertTrue(
            "拉高后行数必须变多，实际 ${small.first} → ${large.first}",
            large.first > small.first
        )
        assertEquals(
            "拉高后必须依然填满（这正是原来的 bug）",
            availableOf(482), (large.second.value * large.first).toDouble(), 0.01
        )
    }

    @Test
    fun `常规尺寸下不该无谓加行`() {
        val (rows, _) = weekGridRows(214.dp - chrome, 6)
        assertEquals("空间刚好够时应当正好 6 行", 6, rows)
    }

    @Test
    fun `空间不足时收窄行数而不是把行压扁`() {
        val (rows, rowHeight) = weekGridRows(70.dp - chrome, 6)
        assertTrue("行数应当收窄，实际 $rows", rows < 6)
        assertTrue(
            "行高不应低于下限，实际 ${rowHeight.value}",
            rowHeight.value >= WeekMinRowHeight.value - 0.01
        )
    }

    @Test
    fun `极端输入不崩溃`() {
        assertEquals(1, weekGridRows(0.dp, 6).first)
        assertEquals(1, weekGridRows((-50).dp, 6).first)
        assertTrue("periodsUsed=0 也要给出合法行数", weekGridRows(200.dp, 0).first >= 1)
        assertTrue("periodsUsed 超大也不越界", weekGridRows(200.dp, 99).first <= WeekMaxRows)
    }

    private fun availableOf(widgetHeight: Int) = (widgetHeight.dp - chrome).value.toDouble()
}
