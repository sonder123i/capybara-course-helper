package com.tyust.course.manager

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject

/**
 * 学生数量限制管理器
 * 各版本统一按设备统计学生账号，允许跨学校，合计最多 3 个。
 */
object StudentLimitManager {
    const val MAX_STUDENTS = 3
    private const val TAG = "StudentLimitManager"
    private const val PREFS_NAME = "student_limit_prefs"
    private const val KEY_USED_NAMES = "used_student_names"
    private const val KEY_BOUND_RECORDS = "bound_student_records"

    data class BoundStudentRecord(
        val schoolId: String,
        val schoolName: String,
        val studentName: String,
        val studentId: String
    ) {
        val identity: String
            get() = studentId.ifBlank { studentName }.trim()

        val displayName: String
            get() = when {
                studentName.isNotBlank() && studentId.isNotBlank() -> "$studentName（$studentId）"
                studentName.isNotBlank() -> studentName
                studentId.isNotBlank() -> studentId
                else -> "同学"
            }
    }

    data class BindingCheck(
        val allowed: Boolean,
        val alreadyBound: Boolean,
        val reason: String,
        val usedNames: Set<String>,
        val usedCount: Int
    )

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun currentSchoolId(): String {
        return UserManager.getInstance().currentSchool?.id.orEmpty()
    }

    private fun currentSchoolName(): String {
        return UserManager.getInstance().currentSchool?.name.orEmpty()
    }

    private fun normalizeIdentity(studentName: String, studentId: String): String {
        return studentId.ifBlank { studentName }.trim()
    }

    private fun recordToJson(record: BoundStudentRecord): JSONObject {
        return JSONObject().apply {
            put("schoolId", record.schoolId)
            put("schoolName", record.schoolName)
            put("studentName", record.studentName)
            put("studentId", record.studentId)
        }
    }

    private fun recordFromJson(obj: JSONObject): BoundStudentRecord {
        return BoundStudentRecord(
            schoolId = obj.optString("schoolId", ""),
            schoolName = obj.optString("schoolName", ""),
            studentName = obj.optString("studentName", ""),
            studentId = obj.optString("studentId", "")
        )
    }

    private fun migrateLegacyNamesIfNeeded(context: Context) {
        val prefs = getPrefs(context)
        val existing = prefs.getString(KEY_BOUND_RECORDS, "[]").orEmpty()
        if (existing != "[]" && existing.isNotBlank()) return

        val oldNames = prefs.getStringSet(KEY_USED_NAMES, emptySet()).orEmpty()
            .filter { it.isNotBlank() }
        if (oldNames.isEmpty()) return

        val schoolId = currentSchoolId()
        val schoolName = currentSchoolName()
        if (schoolId.isBlank()) return

        val arr = JSONArray()
        oldNames.forEach { name ->
            arr.put(
                recordToJson(
                    BoundStudentRecord(
                        schoolId = schoolId,
                        schoolName = schoolName,
                        studentName = name,
                        studentId = ""
                    )
                )
            )
        }
        prefs.edit().putString(KEY_BOUND_RECORDS, arr.toString()).apply()
        Log.d(TAG, "已迁移旧版绑定记录到当前学校: $schoolName, count=${oldNames.size}")
    }

    fun getBoundStudents(context: Context): List<BoundStudentRecord> {
        migrateLegacyNamesIfNeeded(context)
        return try {
            val json = getPrefs(context).getString(KEY_BOUND_RECORDS, "[]") ?: "[]"
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val record = recordFromJson(arr.getJSONObject(i))
                    if (record.schoolId.isNotBlank() && record.identity.isNotBlank()) {
                        add(record)
                    }
                }
            }.distinctBy { it.schoolId to it.identity }
        } catch (e: Exception) {
            Log.e(TAG, "读取绑定记录失败: ${e.message}")
            emptyList()
        }
    }

    fun getBoundStudentsForSchool(context: Context, schoolId: String): List<BoundStudentRecord> {
        return getBoundStudents(context)
            .filter { it.schoolId == schoolId }
            .distinctBy { it.identity }
    }

    fun getCurrentSchoolBoundStudents(context: Context): List<BoundStudentRecord> {
        val schoolId = currentSchoolId()
        if (schoolId.isBlank()) return emptyList()
        return getBoundStudentsForSchool(context, schoolId)
    }

    /**
     * 获取当前设备所有学校的绑定记录，学校名称用于区分同名学生。
     */
    fun getUsedStudentNames(context: Context): Set<String> {
        return getBoundStudents(context)
            .map { "${it.schoolName.ifBlank { it.schoolId }} · ${it.displayName}" }
            .toSet()
    }

    fun checkCanUseStudent(
        context: Context,
        schoolId: String,
        studentName: String,
        studentId: String
    ): BindingCheck = evaluateBinding(getBoundStudents(context), schoolId, studentName, studentId)

    internal fun evaluateBinding(
        boundRecords: List<BoundStudentRecord>,
        schoolId: String,
        studentName: String,
        studentId: String
    ): BindingCheck {
        val records = boundRecords.filter { it.schoolId.isNotBlank() && it.identity.isNotBlank() }
            .distinctBy { it.schoolId to it.identity }
        val currentIdentity = normalizeIdentity(studentName, studentId)
        val usedNames = records.map { "${it.schoolName.ifBlank { it.schoolId }} · ${it.displayName}" }.toSet()

        if (schoolId.isBlank() || currentIdentity.isBlank()) {
            return BindingCheck(
                allowed = false,
                alreadyBound = false,
                reason = "无法识别当前账号身份，请重新登录后再试",
                usedNames = usedNames,
                usedCount = records.size
            )
        }

        val alreadyBound = records.any { it.schoolId == schoolId && it.identity == currentIdentity }
        if (alreadyBound) {
            return BindingCheck(
                allowed = true,
                alreadyBound = alreadyBound,
                reason = "",
                usedNames = usedNames,
                usedCount = records.size
            )
        }

        if (records.size >= MAX_STUDENTS) {
            return BindingCheck(
                allowed = false,
                alreadyBound = false,
                reason = "该设备已绑定 ${records.size} 个学生账号，已达上限（所有学校合计最多 $MAX_STUDENTS 个）",
                usedNames = usedNames,
                usedCount = records.size
            )
        }

        return BindingCheck(
            allowed = true,
            alreadyBound = false,
            reason = "",
            usedNames = usedNames,
            usedCount = records.size
        )
    }

    /**
     * 记录当前学校下的新学生身份。
     */
    fun recordStudentName(context: Context, studentName: String) {
        val userManager = UserManager.getInstance()
        val school = userManager.currentSchool ?: return
        recordStudent(
            context = context,
            schoolId = school.id,
            schoolName = school.name,
            studentName = studentName,
            studentId = userManager.studentId.orEmpty()
        )
    }

    fun recordStudent(
        context: Context,
        schoolId: String,
        schoolName: String,
        studentName: String,
        studentId: String
    ): Boolean = synchronized(this) {
        val identity = normalizeIdentity(studentName, studentId)
        val records = getBoundStudents(context).toMutableList()
        if (!evaluateBinding(records, schoolId, studentName, studentId).allowed) return@synchronized false
        val exists = records.any { it.schoolId == schoolId && it.identity == identity }
        if (!exists) {
            records.add(
                BoundStudentRecord(
                    schoolId = schoolId,
                    schoolName = schoolName,
                    studentName = studentName,
                    studentId = studentId
                )
            )
            val arr = JSONArray()
            records.forEach { arr.put(recordToJson(it)) }
            getPrefs(context).edit()
                .putString(KEY_BOUND_RECORDS, arr.toString())
                .putStringSet(KEY_USED_NAMES, records.map { it.displayName }.toSet())
                .apply()
            Log.d(TAG, "已记录新学生: $studentName @ $schoolName, 当前共 ${records.size} 个")
        } else {
            Log.d(TAG, "学生 $studentName 已存在记录中")
        }
        true
    }

    /**
     * 检查是否可以使用新的学生姓名。
     * 保留旧调用入口，按设备总配额判断。
     */
    fun canUseStudent(context: Context, studentName: String): Boolean {
        val school = UserManager.getInstance().currentSchool ?: return false
        val result = checkCanUseStudent(
            context = context,
            schoolId = school.id,
            studentName = studentName,
            studentId = UserManager.getInstance().studentId.orEmpty()
        )
        return result.allowed
    }

    /**
     * 获取当前设备所有学校合计已使用数量。
     */
    fun getUsedCount(context: Context): Int {
        return getBoundStudents(context).size
    }

    /**
     * 清除记录（调试用）
     */
    fun clearRecords(context: Context) {
        getPrefs(context).edit().clear().apply()
        Log.d(TAG, "已清除所有学生记录")
    }
}
