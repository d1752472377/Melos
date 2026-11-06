package com.aesthetic.music.data.userapi

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import java.io.File

data class ScriptMetadata(
    val id: String = "",
    val name: String = "自定义音源",
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = "",
    val homepage: String = "",
    val supportedSources: List<String> = listOf("kw", "kg", "tx", "wy", "mg"),
    val updatedAt: Long = System.currentTimeMillis()
)

object UserApiStore {

    private const val PREFS = "aesthetic_music_user_api"
    private const val KEY_META = "active_script_meta"
    private const val SCRIPT_FILE_NAME = "active_user_api.js"

    private var prefs: SharedPreferences? = null
    private var scriptFile: File? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            scriptFile = File(context.applicationContext.filesDir, SCRIPT_FILE_NAME)
        }
    }

    fun getScriptContent(): String? {
        return try {
            if (scriptFile?.exists() == true) {
                scriptFile?.readText(Charsets.UTF_8)
            } else {
                null
            }
        } catch (e: Exception) {
            android.util.Log.e("UserApiStore", "Failed to read script content: ${e.message}")
            null
        }
    }

    fun getMetadata(): ScriptMetadata? {
        val raw = prefs?.getString(KEY_META, null) ?: return null
        return try {
            gson.fromJson(raw, ScriptMetadata::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun parseScriptHeader(content: String): ScriptMetadata {
        val headerRegex = Regex("""/\*!([\s\S]*?)\*/""")
        val match = headerRegex.find(content)
        var name = "自定义音源"
        var version = "1.0.0"
        var description = "本地音源脚本"
        var author = ""
        var homepage = ""

        if (match != null) {
            val text = match.groupValues[1]
            val lineRegex = Regex("""\*\s*@([a-zA-Z0-9_-]+)\s*(.*)""")
            for (line in text.lines()) {
                val lineMatch = lineRegex.find(line)
                if (lineMatch != null) {
                    val key = lineMatch.groupValues[1].trim()
                    val value = lineMatch.groupValues[2].trim()
                    when (key.lowercase()) {
                        "name" -> if (value.isNotEmpty()) name = value
                        "version" -> if (value.isNotEmpty()) version = value
                        "description" -> if (value.isNotEmpty()) description = value
                        "author" -> if (value.isNotEmpty()) author = value
                        "homepage" -> if (value.isNotEmpty()) homepage = value
                    }
                }
            }
        }

        return ScriptMetadata(
            name = name,
            version = version,
            description = description,
            author = author,
            homepage = homepage
        )
    }
}
