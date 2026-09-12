package com.aesthetic.music.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 音源检测结果与速度样本。
 *
 * 存在的意义：插件页上的"82ms · 正常"、以及"自动按照最快的进行使用"都必须有**实测依据**。
 * 这里只存测量结果，不参与解析逻辑；测量来源有两处：
 * 1. 用户点「检测」时对每个源发起的真实请求（[SourceProbe.latencyMs]）；
 * 2. 播放解析时的实际耗时（[SourceProbe.observedMs]，指数滑动平均）。
 *
 * 记录条数有上限：源的数量本来就不多（内置 4 个 + 若干插件的若干平台），
 * 但插件反复导入会留下孤儿记录，因此按时间裁剪，避免这个文件只增不减。
 */
object SourceStatsStore {

    private const val TAG = "SourceStatsStore"
    private const val PREFS = "aesthetic_music_source_stats"
    private const val KEY_PROBES = "probes_v1"

    /** 最多保留的记录条数（超出时丢最旧的） */
    private const val MAX_ENTRIES = 80

    /** 播放实测的滑动平均权重：新样本占比（越小越平滑，避免一次抖动就换源） */
    private const val EMA_ALPHA = 0.35

    private var prefs: SharedPreferences? = null

    /**
     * 内存态：播放解析每次都读写这里，落盘只是"顺手备份"。
     * 同时让 JVM 单测（没有 SharedPreferences）也能验证滑动平均与状态纠正逻辑。
     */
    private var cache: Map<String, SourceProbe>? = null

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            cache = null
        }
    }

    fun all(): Map<String, SourceProbe> {
        cache?.let { return it }
        val loaded = readFromPrefs()
        cache = loaded
        return loaded
    }

    private fun readFromPrefs(): Map<String, SourceProbe> {
        val raw = prefs?.getString(KEY_PROBES, null) ?: return emptyMap()
        return try {
            val root = JsonParser.parseString(raw)
            if (!root.isJsonObject) return emptyMap()
            val obj = root.asJsonObject
            obj.keySet().associateWith { key -> parse(obj.getAsJsonObject(key), key) }
        } catch (e: Exception) {
            Log.w(TAG, "检测结果解析失败: ${e.message}")
            emptyMap()
        }
    }

    fun get(target: String): SourceProbe? = all()[target]

    fun clear() {
        cache = emptyMap()
        prefs?.edit()?.remove(KEY_PROBES)?.apply()
    }

    /** 写入一次检测结论（缺检测时间时补上"现在"，裁剪与展示都依赖这个时间戳） */
    fun save(probes: Collection<SourceProbe>) {
        if (probes.isEmpty()) return
        val now = System.currentTimeMillis()
        val merged = all().toMutableMap()
        probes.forEach { merged[it.target] = if (it.checkedAt > 0) it else it.copy(checkedAt = now) }
        persist(merged)
    }

    /**
     * 记录一次播放解析的实测耗时（指数滑动平均）。
     *
     * 解析失败时只累计失败信息：把失败样本混进耗时均值会让"自动最快"误判一个坏源很快。
     */
    fun recordObserved(target: String, latencyMs: Long, ok: Boolean, failReason: String? = null) {
        if (latencyMs < 0) return
        val current = all().toMutableMap()
        val old = current[target] ?: SourceProbe(target = target)
        val next = if (!ok) {
            old.copy(
                target = target,
                status = ProbeStatus.FAIL,
                detail = failReason?.takeIf { it.isNotBlank() } ?: old.detail.ifBlank { "解析失败" },
                checkedAt = System.currentTimeMillis(),
            )
        } else {
            val sample = if (old.observedMs < 0) latencyMs else {
                (latencyMs * EMA_ALPHA + old.observedMs * (1 - EMA_ALPHA)).toLong()
            }
            old.copy(
                target = target,
                observedMs = sample,
                observedCount = old.observedCount + 1,
                // 只有"从未检测过"或"上次检测是失败"时才顺带纠正状态，避免覆盖用户的检测结论
                status = if (old.checkedAt <= 0 || old.status == ProbeStatus.FAIL || old.status == ProbeStatus.UNKNOWN) {
                    SourceProbe.statusOf(sample, ok = true)
                } else {
                    old.status
                },
            )
        }
        current[target] = next
        persist(current)
    }

    private fun persist(map: Map<String, SourceProbe>) {
        val trimmed = map.values
            .sortedByDescending { it.checkedAt }
            .take(MAX_ENTRIES)
        cache = trimmed.associateBy { it.target }
        try {
            val root = JsonObject()
            trimmed.forEach { root.add(it.target, probeJson(it)) }
            prefs?.edit()?.putString(KEY_PROBES, root.toString())?.apply()
        } catch (e: Exception) {
            Log.w(TAG, "检测结果保存失败: ${e.message}")
        }
    }

    private fun probeJson(p: SourceProbe): JsonObject = JsonObject().apply {
        addProperty("s", p.status.name)
        addProperty("ms", p.latencyMs)
        addProperty("obs", p.observedMs)
        addProperty("n", p.observedCount)
        addProperty("d", p.detail)
        addProperty("at", p.checkedAt)
    }

    private fun parse(o: JsonObject?, target: String): SourceProbe {
        if (o == null) return SourceProbe(target = target)
        val status = runCatching { ProbeStatus.valueOf(o.str("s")) }.getOrNull() ?: ProbeStatus.UNKNOWN
        return SourceProbe(
            target = target,
            status = status,
            latencyMs = o.num("ms", -1L),
            observedMs = o.num("obs", -1L),
            observedCount = o.num("n", 0L).toInt(),
            detail = o.str("d"),
            checkedAt = o.num("at", 0L),
        )
    }

    private fun JsonObject.str(key: String): String {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asString }.getOrDefault("") else ""
    }

    private fun JsonObject.num(key: String, def: Long): Long {
        val el = if (has(key)) get(key) else null
        return if (el != null && el.isJsonPrimitive) runCatching { el.asLong }.getOrDefault(def) else def
    }
}
