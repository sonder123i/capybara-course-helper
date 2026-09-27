package com.k2767.course.schedule

import org.json.JSONArray
import org.json.JSONObject

/**
 * 只在 debug 入口用到的课表道具：从当前课表倒推出一份「上次看过的那份表」。
 *
 * 它只改**输入**——写进 `_seen` 基线，让真正的 diff 引擎去算该报什么；
 * 不直接摆标记或通知，所以验出来的渲染就是真实链路渲染的。
 */
internal object DebugTimetableFixture {
    /** 至少两行才够既改一间教室又删掉一门课；不足或形状不认识就返回 null。 */
    fun previousVersion(json: String): String? {
        val rows = rowsOf(json) ?: return null
        if (rows.length() < 2) return null
        val root = JSONObject(json)
        val forged = JSONArray()
        for (index in 0 until rows.length() - 1) {
            val row = JSONObject(rows.getJSONObject(index).toString())
            // 改第一行的教室：这一行仍然配得上，只是从→到变了。
            if (index == 0) row.put("cdmc", "验证用X999")
            forged.put(row)
        }
        return write(root, forged)
    }

    private fun rowsOf(json: String): JSONArray? = runCatching {
        val root = JSONObject(json)
        val data = root.opt("data")
        root.optJSONArray("kbList") ?: when (data) {
            is JSONObject -> data.optJSONArray("kbList")
            is JSONArray -> data
            else -> null
        }
    }.getOrNull()

    private fun write(root: JSONObject, rows: JSONArray): String {
        when {
            root.has("kbList") -> root.put("kbList", rows)
            root.opt("data") is JSONObject -> root.getJSONObject("data").put("kbList", rows)
            root.opt("data") is JSONArray -> root.put("data", rows)
            else -> root.put("kbList", rows)
        }
        return root.toString()
    }
}
