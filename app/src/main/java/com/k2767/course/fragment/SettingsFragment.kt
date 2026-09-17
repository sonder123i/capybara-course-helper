package com.k2767.course.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import com.k2767.course.LoginActivity
import com.k2767.course.manager.UserManager
import com.k2767.course.model.SchoolConfig
import com.k2767.course.ui.screen.SettingsScreen

class SettingsFragment : Fragment() {

    // Reactive state for Compose
    private var studentName by mutableStateOf("")
    private var studentId by mutableStateOf("")
    private var schoolName by mutableStateOf("")
    private var isSuper by mutableStateOf(false)
    private var quotaInfo by mutableStateOf("")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return ComposeView(requireContext()).apply {
            setContent {
                SettingsScreen(
                    studentName = studentName,
                    studentId = studentId,
                    schoolName = schoolName,
                    onSchoolSelect = { showSchoolSelector() },
                    onCookieConfig = { handleCookieConfig() },
                    onClearCache = { handleClearCache() },
                    onCheckUpdate = { /* Not used in fragment, handled by SettingsRoute */ },
                    onAbout = { handleAbout() },
                    onCredits = { handleCredits() },
                    onLogout = { handleLogout() },
                    onQuotaClick = { showQuotaDetails() },
                    isSuper = isSuper,
                    quotaInfo = quotaInfo
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUserInfo()
    }

    private fun updateUserInfo() {
        val name = UserManager.getInstance().studentName
        val id = UserManager.getInstance().studentId
        val school = UserManager.getInstance().currentSchool

        studentName = name ?: "同学"
        studentId = if (id.isNullOrEmpty()) "" else id
        schoolName = school?.name ?: "未选择"
        
        // 获取配额信息 - 使用多种方式尝试获取 Context
        val ctx = context ?: activity?.applicationContext ?: view?.context
        if (ctx != null) {
            try {
                val maxStudents = com.k2767.course.activation.ActivationManager.getMaxStudents(ctx)
                isSuper = maxStudents <= 0
                if (isSuper) {
                    quotaInfo = "无限制"
                } else {
                    val usedCount = com.k2767.course.manager.StudentLimitManager.getUsedCount(ctx)
                    quotaInfo = "$usedCount / $maxStudents"
                }
                Log.d("SettingsFragment", "配额加载成功: isSuper=$isSuper, quota=$quotaInfo, maxStudents=$maxStudents")
            } catch (e: Exception) {
                Log.e("SettingsFragment", "配额加载失败: ${e.message}")
                quotaInfo = "加载失败"
            }
        } else {
            Log.w("SettingsFragment", "Context 为空，无法加载配额")
            quotaInfo = "0 / 2" // 默认配额
        }
        
        Log.d("SettingsFragment", "Updated UI: name=$studentName, id=$studentId, isSuper=$isSuper, quota=$quotaInfo")
    }

    private fun showQuotaDetails() {
        val ctx = context ?: activity ?: view?.context
        if (ctx == null) {
            Log.e("SettingsFragment", "showQuotaDetails: 无法获取 Context")
            return
        }
        
        val maxStudents = com.k2767.course.activation.ActivationManager.getMaxStudents(ctx)
        val usedNames = com.k2767.course.manager.StudentLimitManager.getUsedStudentNames(ctx)
        val usedCount = com.k2767.course.manager.StudentLimitManager.getUsedCount(ctx)
        val isSuperUser = maxStudents <= 0
        
        val message = buildString {
            append("📊 设备绑定详情\n\n")
            if (isSuperUser) {
                append("✨ 身份：超级用户\n")
                append("📈 配额：无限制\n")
            } else {
                append("📈 配额：$usedCount / $maxStudents（所有学校合计）\n")
            }
            append("━━━━━━━━━━━━━━━\n")
            if (usedNames.isNotEmpty()) {
                append("👥 已绑定账号：\n")
                usedNames.forEachIndexed { index, name ->
                    append("${index + 1}. $name\n")
                }
            } else {
                append("ℹ️ 暂未绑定任何账号\n")
            }
            append("━━━━━━━━━━━━━━━\n\n")
            append("💡 说明：激活名额一旦绑定无法自行解绑。如需更换请联系管理员。")
        }

        AlertDialog.Builder(ctx)
            .setTitle("当前账号配额")
            .setMessage(message)
            .setPositiveButton("我知道了", null)
            .show()
    }

    private fun handleCookieConfig() {
        // 清除登录状态和保存的 Cookie
        UserManager.getInstance().clearLoginState()
        val intent = Intent(context, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun handleClearCache() {
        AlertDialog.Builder(context)
            .setTitle("清除缓存")
            .setMessage("确定要清除所有本地缓存数据吗？")
            .setPositiveButton("确定") { _, _ ->
                Toast.makeText(context, "缓存已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun handleAbout() {
        val message = buildString {
            append("更新日志\n\n")
            append("• 2026-09-17: 新增桌面小组件「今日课程」(1.1.0)\n")
            append("• 2026-09-17: 日/周视图切换动画，顶栏保持不动 (1.0.8)\n")
            append("• 2026-09-17: 内置学校库（1777 所，搜索 + A–Z + 收藏）；隐藏学校适配入口，关闭匿名统计界面入口 (1.0.7)\n")
            append("• 2026-09-17: 新增日视图与日/周切换；地点完整显示、一屏八节；单双周过滤 (1.0.3 – 1.0.6)\n")
            append("• 2026-09-17: 课程页如实透传教务原文提示，不再误报 (1.0.2)\n")
            append("• 2026-09-17: 更名换标、包名迁移，接入自建官网与更新服务 (1.0.0 / 1.0.1)\n\n")
            append("本应用仅供学习交流使用")
        }
        AlertDialog.Builder(context)
            .setTitle("更新历史")
            .setMessage(message)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun handleCredits() {
        AlertDialog.Builder(context)
            .setTitle("致谢与关于")
            .setMessage(
                "特别致谢\n" +
                "本应用基于多项优秀的开源技术构建，衷心感谢以下开源项目及社区的支持：\n" +
                "• Jetpack Compose & Kotlin\n" +
                "• OkHttp3 & Gson\n" +
                "• Jsoup (HTML 解析库)\n" +
                "• Material Design 3\n" +
                "• AndroidLiquidGlass 动效库\n\n" +
                "关于项目\n" +
                "• 本应用基于开源项目「教务助手」二次开发\n" +
                "• 原作者：znjhahaha（GPL-3.0）\n" +
                "• 上游项目：https://github.com/znjhahaha/zhengfang-apk\n" +
                "• 本项目：https://github.com/sonder123i/capybara-course-helper\n\n" +
                "本软件为开源免费项目，仅供个人学习与技术交流使用，严禁用于任何商业目的与倒卖。"
            )
            .setPositiveButton("我知道了", null)
            .show()
    }

    private fun handleLogout() {
        AlertDialog.Builder(context)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                // 清除登录状态和保存的 Cookie
                UserManager.getInstance().clearLoginState()

                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showSchoolSelector() {
        val schools = UserManager.getInstance().selectableSchools
        val schoolNames = schools.map { it.name }.toTypedArray()

        AlertDialog.Builder(context)
            .setTitle("选择学校")
            .setItems(schoolNames) { _, which ->
                val selected = schools[which]
                // 切换学校需要重新登录
                UserManager.getInstance().clearLoginState()
                UserManager.getInstance().currentSchool = selected
                Toast.makeText(context, "已切换到: ${selected.name}，请重新登录", Toast.LENGTH_SHORT).show()
                
                // 跳转到登录页面
                val intent = Intent(context, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            .show()
    }
}
