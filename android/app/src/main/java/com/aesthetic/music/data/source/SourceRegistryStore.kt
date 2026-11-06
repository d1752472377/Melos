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

object SourceRegistryStore {

    private const val TAG = "SourceRegistryStore"
    private const val PREFS = "aesthetic_music_sources"
    private const val KEY_PLUGINS = "plugins_v1"
    private const val KEY_ACTIVE = "active_plugin_id"
    private const val DIR_NAME = "sources"

    private var prefs: SharedPreferences? = null
    private var dir: File? = null

    private var cache: List<SourcePlugin>? = null

    private var activeCache: String? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
        cache = null
        activeCache = null
        migrateLegacy()
    }

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
        com.aesthetic.music.data.sync.SyncStateStore.recordDeleteForCurrentUser(
            com.aesthetic.music.data.sync.SyncCollections.SCRIPTS,
            com.aesthetic.music.data.sync.SyncKey.script(id),
        )
    }

    fun clear() {
        val removedIds = list().map { it.id }.filter { it.isNotBlank() }
        cache = emptyList()
        activeCache = null
        prefs?.edit()?.remove(KEY_PLUGINS)?.remove(KEY_ACTIVE)?.apply()
        dir?.listFiles()?.forEach { it.delete() }
        com.aesthetic.music.data.sync.SyncStateStore.recordDeletesForCurrentUser(
            com.aesthetic.music.data.sync.SyncCollections.SCRIPTS,
            removedIds.map { com.aesthetic.music.data.sync.SyncKey.script(it) },
        )
    }

    private fun writeContent(id: String, content: String) {
        try {
            File(dir ?: return, "$id.js").writeText(content, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "插件源码写盘失败[$id]: ${e.message}")
        }
    }

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
            targetPlatform = "",
            declaredSources = DEFAULT_DECLARED,
            enable = true,
            importedAt = meta.updatedAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        )
        upsert(legacy, content)
        setActiveId(legacy.id)
        Log.i(TAG, "已把旧版音源脚本迁移为插件: [${legacy.name}]")
    }

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

    private val DEFAULT_DECLARED = listOf("wy", "tx", "kg", "kw", "mg")

    private const val LEGACY_ID = "legacy-active"
}
