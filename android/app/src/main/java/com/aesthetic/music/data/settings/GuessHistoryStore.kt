package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * 「猜你喜欢」已推荐过的歌曲记忆（`platform:songId` 键集合）。
 *
 * 没有这张表，「换一批」会因为种子不变而反复返回同一批结果。
 * 最多保留 [MAX_KEYS] 条，按写入顺序丢最旧的。
 */
object GuessHistoryStore {

    private const val PREFS = "aesthetic_music_guess"
    private const val KEY_RECOMMENDED = "recommended_keys"
    private const val MAX_KEYS = 300

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = PrefsJson.prefs(context, PREFS)
    }

    /** 已推荐过的键（解析失败按空处理，绝不让推荐页因为脏数据打不开） */
    fun load(): Set<String> {
        val list = PrefsJson.load<List<String>>(
            prefs,
            KEY_RECOMMENDED,
            jsonType<List<String>>(),
            namespaced = false,
        ).orEmpty()
        return list.filter { it.isNotBlank() }.toSet()
    }

    /** 追加一批已推荐的键（去重 + 截断到 [MAX_KEYS]，保留最新的） */
    fun record(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val merged = LinkedHashSet(load())
        merged.addAll(keys.filter { it.isNotBlank() })
        val trimmed = if (merged.size > MAX_KEYS) {
            merged.toList().takeLast(MAX_KEYS)
        } else {
            merged.toList()
        }
        PrefsJson.save(prefs, KEY_RECOMMENDED, trimmed, namespaced = false)
    }

    fun clear() = PrefsJson.clear(prefs, KEY_RECOMMENDED, namespaced = false)
}
