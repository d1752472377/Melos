package com.aesthetic.music.data.source

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.gson.JsonObject
import com.google.gson.JsonParser

object SourceStatsStore {

    private const val TAG = "SourceStatsStore"
    private const val PREFS = "aesthetic_music_source_stats"
    private const val KEY_PROBES = "probes_v1"

    private const val MAX_ENTRIES = 80

    private const val EMA_ALPHA = 0.35

    private var prefs: SharedPreferences? = null

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

    fun save(probes: Collection<SourceProbe>) {
        if (probes.isEmpty()) return
        val now = System.currentTimeMillis()
        val merged = all().toMutableMap()
        probes.forEach { merged[it.target] = if (it.checkedAt > 0) it else it.copy(checkedAt = now) }
        persist(merged)
    }

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
