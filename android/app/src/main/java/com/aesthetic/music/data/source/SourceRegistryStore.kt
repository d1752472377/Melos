package com.aesthetic.music.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.aesthetic.music.data.userapi.ScriptMetadata
import com.aesthetic.music.data.userapi.UserApiStore
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID

/**
 * 音源插件注册表（多插件）。
 *
 * 与旧的 `UserApiStore`（只保存"当前唯一脚本"）的区别：这里能**同时保留多个插件**，
 * 每个插件的 JS 源码各存一份文件（`filesDir/sources/<id>.js`），
 * 记录表（名称/平台/启用状态/声明源）存 SharedPreferences，启动即可列出。
 *
 * 为什么需要多插件：只有能并存多个音源，"失败换源 / 智能切换 / 自动最快"才有意义——
 * 单一脚本只能"有或没有"，谈不上挑选。
 *
 * 兼容：升级上来时若注册表为空而旧的单一脚本存在，会自动把它登记为第一个插件（[migrateLegacy]）。
 *
 * JSON 手写而非 Gson 反射：Gson 反序列化会绕过 Kotlin 构造器，缺字段会留下 null（旧 `ScriptCatalogStore`
 * 就有这个隐患），手写读取可以逐字段回落默认值，脏数据只丢一条记录。
 */
object SourceRegistryStore {

    private const val TAG = "SourceRegistryStore"
    private const val PREFS = "aesthetic_music_sources"
    private const val KEY_PLUGINS = "plugins_v1"
    private const val KEY_ACTIVE = "active_plugin_id"
    private const val DIR_NAME = "sources"

    private var prefs: SharedPreferences? = null
    private var dir: File? = null

    /**
     * 内存缓存。
     *
     * 两个作用：一是解析一次共享给所有调用方（插件列表在每次播放解析时都会被读到，
     * 每次都反序列化一遍 JSON 是纯浪费）；二是 JVM 单测里没有 SharedPreferences 时，
     * 写入仍然可见，store 的行为可以被真实测到，而不是"没落盘就全丢"。
     */
    private var cache: List<SourcePlugin>? = null

    /** 当前指定插件 id 的内存镜像（理由同上） */
    private var activeCache: String? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
        cache = null
        activeCache = null
        migrateLegacy()
    }

    // ==================== 读取 ====================

    fun list(): List<SourcePlugin> {
        cache?.let { return it }
        val loaded = readFromPrefs()
        cache = loaded
        return loaded
    }

    private fun readFromPrefs(): List<SourcePlugin> {
        val raw = prefs?.getString(KEY_PLUGINS, null) ?: return emptyList()
        return try {
            val arr = JsonParser.parseString(raw)
            if (!arr.isJsonArray) return emptyList()
            arr.asJsonArray.mapNotNull { parsePlugin(it) }
        } catch (e: Exception) {
            Log.w(TAG, "插件注册表解析失败: ${e.message}")
            emptyList()
        }
    }

    fun find(id: String): SourcePlugin? = list().firstOrNull { it.id == id }

    /** 当前指定的插件 id（手动选择的"当前音源"；策略开启时会被自动挑选覆盖） */
    fun activeId(): String? = activeCache ?: prefs?.getString(KEY_ACTIVE, null)?.takeIf { it.isNotBlank() }

    fun setActiveId(id: String?) {
        activeCache = id?.takeIf { it.isNotBlank() }
        try {
            val editor = prefs?.edit() ?: return
            if (id.isNullOrBlank()) editor.remove(KEY_ACTIVE) else editor.putString(KEY_ACTIVE, id)
            editor.apply()
        } catch (e: Exception) {
            Log.w(TAG, "保存当前音源失败: ${e.message}")
        }
    }

    fun getContent(id: String): String? {
        val f = File(dir ?: return null, "$id.js")
        return try {
            if (f.exists()) f.readText(Charsets.UTF_8) else null
        } catch (e: Exception) {
            Log.w(TAG, "读取插件源码失败[$id]: ${e.message}")
            null
        }
    }

    // ==================== 写入 ====================

    /** 新增或覆盖一个插件（返回最终记录；id 为空时自动生成） */
    fun upsert(plugin: SourcePlugin, content: String? = null): SourcePlugin {
        val id = plugin.id.ifBlank { UUID.randomUUID().toString() }
        val existing = find(id)
        val now = System.currentTimeMillis()
        val merged = plugin.copy(
            id = id,
            importedAt = existing?.importedAt?.takeIf { it > 0 } ?: plugin.importedAt.takeIf { it > 0 } ?: now,
            lastUsedAt = existing?.lastUsedAt ?: plugin.lastUsedAt,
        )
        val rest = list().filterNot { it.id == id }
        saveAll(rest + merged)
        if (!content.isNullOrBlank()) writeContent(id, content)
        return merged
    }

    fun saveAll(plugins: List<SourcePlugin>) {
        // 归一化放在唯一的落库出口：指定平台只认识内置平台 code，
        // 脏值（比如手改出来的 "bilibili"）在这里统一降级为"全平台通用"，
        // 避免后面按平台过滤时出现"这个插件永远不适用"的幽灵记录。
        val normalized = plugins.map { p ->
            p.copy(targetPlatform = SourcePlatform.of(p.targetPlatform)?.code ?: "")
        }
        cache = normalized
        try {
            val arr = JsonArray()
            normalized.forEach { arr.add(pluginJson(it)) }
            prefs?.edit()?.putString(KEY_PLUGINS, arr.toString())?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "插件注册表保存失败: ${e.message}")
        }
    }

    /** 只更新记录字段（不改源码），用于启用/平台/声明源/最近使用等变更 */
    fun update(id: String, transform: (SourcePlugin) -> SourcePlugin): SourcePlugin? {
        val all = list()
        val target = all.firstOrNull { it.id == id } ?: return null
        val next = transform(target)
        saveAll(all.map { if (it.id == id) next else it })
        return next
    }

    fun remove(id: String) {
        saveAll(list().filterNot { it.id == id })
        try {
            dir?.let { File(it, "$id.js").delete() }
        } catch (_: Exception) {
        }
        if (activeId() == id) setActiveId(null)
    }

    fun clear() {
        cache = emptyList()
        activeCache = null
        prefs?.edit()?.remove(KEY_PLUGINS)?.remove(KEY_ACTIVE)?.apply()
        dir?.listFiles()?.forEach { it.delete() }
    }

    private fun writeContent(id: String, content: String) {
        try {
            File(dir ?: return, "$id.js").writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "插件源码写盘失败[$id]: ${e.message}")
        }
    }

    // ==================== 迁移 ====================

    /**
     * 把旧版本"唯一脚本"登记为第一个插件。
     *
     * 迁移只做一次：注册表非空就跳过，避免用户删掉旧插件后又被重新塞回来。
     */
    private fun migrateLegacy() {
        if (list().isNotEmpty()) return
        val content = UserApiStore.getScriptContent()
        val meta = UserApiStore.getMetadata()
        if (content.isNullOrBlank() || meta == null) return
        val legacy = SourcePlugin(
            id = meta.id.takeIf { it.isNotBlank() } ?: LEGACY_ID,
            name = meta.name,
            version = meta.version,
            author = meta.author,
            description = meta.description,
            homepage = meta.homepage,
            // 旧版本没有"指定平台"的概念，一律按通用处理
            targetPlatform = "",
            declaredSources = DEFAULT_DECLARED,
            enable = true,
            importedAt = meta.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        upsert(legacy, content)
        setActiveId(legacy.id)
        Log.i(TAG, "已把旧版音源脚本迁移为插件: [${legacy.name}]")
    }

    /** 由脚本元数据构造插件记录（导入时的初始值；声明源在沙箱加载后补全） */
    fun pluginFromMeta(
        meta: ScriptMetadata,
        targetPlatform: String,
        content: String,
    ): SourcePlugin = SourcePlugin(
        id = UUID.randomUUID().toString(),
        name = meta.name,
        version = meta.version,
        author = meta.author,
        description = meta.description,
        homepage = meta.homepage,
        targetPlatform = targetPlatform,
        declaredSources = emptyList(),
        enable = true,
        importedAt = System.currentTimeMillis(),
    ).also { upsert(it, content) }

    fun markUsed(id: String) {
        update(id) { it.copy(lastUsedAt = System.currentTimeMillis()) }
    }

    // ==================== JSON ====================

    private fun pluginJson(p: SourcePlugin): JsonObject = JsonObject().apply {
        addProperty("id", p.id)
        addProperty("name", p.name)
        addProperty("ver", p.version)
        addProperty("author", p.author)
        addProperty("desc", p.description)
        addProperty("home", p.homepage)
        addProperty("plat", p.targetPlatform)
        addProperty("enable", p.enable)
        addProperty("importedAt", p.importedAt)
        addProperty("lastUsedAt", p.lastUsedAt)
        add("sources", JsonArray().apply { p.declaredSources.forEach { add(it) } })
    }

    private fun parsePlugin(el: com.google.gson.JsonElement): SourcePlugin? {
        val o = el.takeIf { it.isJsonObject }?.asJsonObject ?: return null
        val id = o.str("id")
        if (id.isBlank()) return null
        return SourcePlugin(
            id = id,
            name = o.str("name").ifBlank { "自定义音源" },
            version = o.str("ver").ifBlank { "1.0.0" },
            author = o.str("author"),
            description = o.str("desc"),
            homepage = o.str("home"),
            targetPlatform = SourcePlatform.of(o.str("plat"))?.code ?: "",
            declaredSources = o.strList("sources"),
            enable = if (o.has("enable")) o.bool("enable") else true,
            importedAt = o.long("importedAt"),
            lastUsedAt = o.long("lastUsedAt"),
        )
    }

    private fun JsonObject.str(key: String): String {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asString }.getOrDefault("") else ""
    }

    private fun JsonObject.long(key: String): Long {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asLong }.getOrDefault(0L) else 0L
    }

    private fun JsonObject.bool(key: String): Boolean {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asBoolean }.getOrDefault(true) else true
    }

    private fun JsonObject.strList(key: String): List<String> {
        val el = if (has(key)) get(key) else null
        if (el == null || !el.isJsonArray) return emptyList()
        return el.asJsonArray.mapNotNull { item ->
            if (item.isJsonPrimitive) runCatching { item.asString }.getOrNull() else null
        }
    }

    /** 旧脚本的默认声明源（与脚本规范内置的五源一致；加载沙箱后会被真实声明覆盖） */
    private val DEFAULT_DECLARED = listOf("wy", "tx", "kg", "kw", "mg")

    private const val LEGACY_ID = "legacy-active"
}
