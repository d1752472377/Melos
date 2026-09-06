package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import java.io.File

/**
 * 音源脚本目录（目录表）：缓存私有云下发的**全部**音源脚本，供用户在
 * 「设置音源」里本地一键切换，无需再回到服务端改激活项。
 *
 * - 脚本 JS 源码按 id 存到 filesDir/scripts/<id>.js（原文保存，便于 QuickJS 重载）
 * - 目录摘要（不含 content）存 SharedPreferences，启动即时可列出
 */
data class ScriptCatalogEntry(
    val id: String = "",
    val name: String = "自定义音源",
    val version: String = "1.0.0",
    val description: String = "",
    val isActive: Boolean = false,
    val status: String = "",
    val statusMessage: String = "",
    val updatedAt: Long = 0L,
    val content: String = "",
)

object ScriptCatalogStore {

    private const val PREFS = "aesthetic_music_script_catalog"
    private const val KEY_CATALOG = "catalog"
    private const val DIR_NAME = "scripts"

    private var prefs: SharedPreferences? = null
    private var scriptDir: File? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            scriptDir = File(context.applicationContext.filesDir, DIR_NAME).apply { mkdirs() }
        }
    }

    /** 覆盖写目录（内容落盘到独立文件，摘要写入 prefs） */
    fun saveAll(entries: List<ScriptCatalogEntry>) {
        val dir = scriptDir ?: return
        entries.forEach { entry ->
            if (entry.id.isBlank() || entry.content.isBlank()) return@forEach
            try {
                File(dir, "${entry.id}.js").writeText(entry.content, Charsets.UTF_8)
            } catch (e: Exception) {
                android.util.Log.e("ScriptCatalogStore", "save content failed: ${e.message}")
            }
        }
        val summaries = entries.map { it.copy(content = "") }
        prefs?.edit()?.putString(KEY_CATALOG, gson.toJson(summaries))?.apply()
    }

    fun list(): List<ScriptCatalogEntry> {
        val raw = prefs?.getString(KEY_CATALOG, null) ?: return emptyList()
        return try {
            val arr = gson.fromJson(raw, Array<ScriptCatalogEntry>::class.java)
            arr?.toList() ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getContent(id: String): String? {
        val dir = scriptDir ?: return null
        val f = File(dir, "${id}.js")
        return if (f.exists()) {
            try { f.readText(Charsets.UTF_8) } catch (_: Exception) { null }
        } else null
    }

    fun clear() {
        prefs?.edit()?.remove(KEY_CATALOG)?.apply()
        scriptDir?.listFiles()?.forEach { it.delete() }
    }
}