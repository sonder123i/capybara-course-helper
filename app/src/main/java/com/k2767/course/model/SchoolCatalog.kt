package com.k2767.course.model

import android.content.Context
import com.k2767.course.academic.AcademicSystem
import org.json.JSONArray
import java.net.URI

/**
 * 内置学校目录条目。
 *
 * 数据来源：开源项目 shangkeschedule（Apache-2.0），见 assets/licenses/shangkeschedule-school-catalog.txt。
 * 目录只提供「校名 + 教务地址 + 教务类型」，选中后交给 [AcademicGatewayFactory.detect] 在登录时
 * 探测具体教务系统与根路径——所以地址是登录页、根目录还是 CAS 入口都能试。
 */
data class CatalogSchool(
    val id: String,
    val name: String,
    val url: String,
    /** 本项目已有适配器的类型 id（zf / qz / qz_old）；空串表示暂未适配。 */
    val adapterSystem: String,
    /** 数据源的原始教务类型，用于展示标签与后续适配。 */
    val kind: String,
    val pinyin: String,
    val initials: String,
    val initial: String
) {
    val adapterReady: Boolean get() = adapterSystem.isNotEmpty()

    /** 教务类型的中文标签，用于列表右侧的灰色小字。 */
    val typeLabel: String
        get() = when (kind) {
            "zhengfang_new" -> "正方"
            "qiangzhi", "qiangzhi_old" -> "强智"
            "kingosoft", "kingosoft_new" -> "青果"
            "wisedu" -> "金智"
            "chaoxing" -> "超星"
            "urp", "urp_new" -> "URP"
            "south_soft" -> "南软"
            else -> "其他"
        }

    /** 搜索匹配：中文名、拼音全拼、拼音首字母。 */
    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        return name.contains(q) || pinyin.contains(q) || initials.startsWith(q)
    }

    /**
     * 转成本项目的学校配置。教务类型置为「自动识别」，由登录流程探测后再落定。
     * 地址无法解析出 http(s) 域名时返回 null。
     */
    fun toSchoolConfig(): SchoolConfig? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null) return null
        val authority = uri.authority?.lowercase()?.takeIf { it.contains('.') } ?: return null
        val path = (uri.path ?: "").trimEnd('/')

        val basePath = when {
            // 正方标准登录页 → 去掉整段
            path.endsWith(LOGIN_PAGE_SUFFIX, ignoreCase = true) -> path.dropLast(LOGIN_PAGE_SUFFIX.length)
            // 末段带后缀名（xxx.html / xxx.aspx / xxx.action）→ 当作页面去掉
            path.substringAfterLast('/').contains('.') -> path.substringBeforeLast('/')
            // 其余（目录、CAS 入口等）原样保留，探测根路径时还会再试 root 与标准路径
            else -> path
        }
        val normalized = basePath.trim('/').let { if (it.isEmpty()) "" else "/$it" }

        return SchoolConfig(id, name, authority, scheme).apply {
            this.basePath = normalized
            academicSystem = AcademicSystem.AUTO.id
            detectionSource = "pending"
            allowedAcademicHosts.add(authority)
        }
    }

    private companion object {
        const val LOGIN_PAGE_SUFFIX = "/xtgl/login_slogin.html"
    }
}

/** 学校目录的加载与查询（首次解析后常驻内存）。 */
object SchoolCatalog {
    private const val ASSET_NAME = "school_catalog.json"

    @Volatile
    private var cache: List<CatalogSchool>? = null

    fun load(context: Context): List<CatalogSchool> {
        cache?.let { return it }
        val text = runCatching {
            context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val parsed = parse(text)
        cache = parsed
        return parsed
    }

    fun byId(context: Context, id: String): CatalogSchool? =
        load(context).firstOrNull { it.id == id }

    internal fun parse(text: String): List<CatalogSchool> {
        if (text.isBlank()) return emptyList()
        val array = runCatching { JSONArray(text) }.getOrNull() ?: return emptyList()
        val out = ArrayList<CatalogSchool>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val url = item.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) continue
            out.add(
                CatalogSchool(
                    id = item.optString("id"),
                    name = name,
                    url = url,
                    adapterSystem = item.optString("sys"),
                    kind = item.optString("kind"),
                    pinyin = item.optString("py"),
                    initials = item.optString("ini"),
                    initial = item.optString("ab").ifBlank { "#" }
                )
            )
        }
        return out
    }
}

/** 学校选择器里的收藏（★），按目录 id 记录。 */
object SchoolCatalogFavorites {
    private const val PREFS_NAME = "school_catalog_prefs"
    private const val KEY_FAVORITES = "favorites"

    fun load(context: Context): Set<String> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getStringSet(KEY_FAVORITES, emptySet())
            ?.toSet()
            ?: emptySet()

    fun toggle(context: Context, id: String): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getStringSet(KEY_FAVORITES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (!current.add(id)) current.remove(id)
        prefs.edit().putStringSet(KEY_FAVORITES, current).apply()
        return current
    }
}