package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.google.gson.reflect.TypeToken

/**
 * 「猜你喜欢」已推荐过的歌曲记忆。
 *
 * ## 为什么需要它
 * GZ 的 `GuessYouLikeRepo`（`defpackage/y33.java:662`）在每批结果产出后会调用
 * `mp6Var.a(list)` 把本批歌曲写进「已推荐身份」表，下一批挑选时该表参与排除
 * （`yc2Var2.l(mp6Var.b())`，`y33.java:616`）。没有这张表，用户点「换一批」时
 * 因为种子没变、相似歌曲接口返回的又是同一批结果，看到的会是与上一批高度重合的列表——
 * 点了像没点。
 *
 * ## 存的是什么
 * `platform:songId` 形式的字符串集合（与全工程 `SyncKey.song` / `favorites` 的键口径一致），
 * 只记键不记歌名：一首歌改标题不该让它「重新变成新歌」。
 *
 * ## 容量
 * 最多保留 [MAX_KEYS] 条，超出后按写入顺序丢最旧的（[LinkedHashSet] 的插入序）。
 * 300 条 ≈ 10 批推荐，足够覆盖「连续点几次换一批」的场景，又不会让偏好文件无限膨胀。
 */
object GuessHistoryStore {

    private const val PREFS = "aesthetic_music_guess"
    private const val KEY_RECOMMENDED = "recommended_keys"
    private const val MAX_KEYS = 300

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    /** 单线程后台写入器：与收藏/历史一致，落盘不占用调用线程 */
    private val writer = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    /** 已推荐过的键（解析失败按空处理，绝不让推荐页因为脏数据打不开） */
    fun load(): Set<String> = try {
        val raw = prefs?.getString(KEY_RECOMMENDED, null) ?: return emptySet()
        val type = object : TypeToken<List<String>>() {}.type
        val list = gson.fromJson<List<String>>(raw, type) ?: emptyList()
        list.filter { it.isNotBlank() }.toSet()
    } catch (_: Exception) {
        emptySet()
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
        writer.execute {
            try {
                prefs?.edit()?.putString(KEY_RECOMMENDED, gson.toJson(trimmed))?.apply()
            } catch (_: Exception) {
            }
        }
    }

    /** 清空（设置里「清空本机数据」/调试用） */
    fun clear() {
        writer.execute {
            try {
                prefs?.edit()?.remove(KEY_RECOMMENDED)?.apply()
            } catch (_: Exception) {
            }
        }
    }
}
