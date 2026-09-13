package com.aesthetic.music.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.aesthetic.music.data.AppJson
import java.util.UUID

/**
 * 同步状态持久化：**按账号隔离**的基线、游标、以及设备标识。
 *
 * ## 为什么必须按账号隔离
 * 「上次同步到的版本号 / 每条记录的基线指纹 / 待删除标记」都是"这份本地数据与某个云端账号的关系"。
 * 如果只存一份：
 *  - A 账号同步到 version=42 后退出、B 账号登录，B 会拿着 42 去拉自己的数据 → 少了 0~42 号变更，
 *    表现为"换账号后云端数据只回来一半"；
 *  - 基线还会让 B 的推送判定错误（A 的记录被当成 B 已同步过的内容而不推）。
 * 因此所有键都带 `u<userId>.` 前缀；退出登录**不**清这些数据（同一个账号再登录可继续增量同步），
 * 只有"退出并清空本地数据"才调 [clearUser]。
 *
 * ## 与 [com.aesthetic.music.data.settings.SyncServerStore] 的分工
 * 那边只存**设备级**的东西（服务器地址、设备 id）；账号级的东西全在这里。
 */
object SyncStateStore {

    private const val PREFS = "aesthetic_music_sync_state"

    private const val KEY_DEVICE_ID = "device_id"

    /** 无 SharedPreferences（JVM 单测）时的内存镜像：让 store 的行为仍可被真实测到 */
    private val mem = mutableMapOf<String, Any?>()

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    // ==================== 设备标识 ====================

    /**
     * 本机安装的唯一标识（首次调用时生成并持久化）。
     *
     * 用途：下载记录的主键之一——"这首歌在这台设备上有没有文件"是设备级事实。
     * 不用 ANDROID_ID：它在不同 ROM/恢复出厂后行为不一，且属于更敏感的设备标识；
     * 这里只需要一个"本机安装自增的随机串"，UUID 足够且不涉及隐私。
     */
    fun deviceId(): String {
        val cached = mem[KEY_DEVICE_ID] as? String
        if (!cached.isNullOrBlank()) return cached
        val stored = prefs?.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
        if (stored != null) {
            mem[KEY_DEVICE_ID] = stored
            return stored
        }
        val fresh = UUID.randomUUID().toString()
        mem[KEY_DEVICE_ID] = fresh
        prefs?.edit()?.putString(KEY_DEVICE_ID, fresh)?.apply()
        return fresh
    }

    /** 设备名（登录时上报，用于"登录设备列表"里认出是哪台手机） */
    fun deviceName(): String {
        val brand = Build.BRAND.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" }
    }

    // ==================== 基线 ====================

    fun snapshot(userId: Long, collection: String): SyncSnapshot = SyncSnapshot(
        fingerprints = readMap(fpKey(userId, collection)),
        pendingDeletes = readSet(delKey(userId, collection)),
    )

    fun saveSnapshot(userId: Long, collection: String, snapshot: SyncSnapshot) {
        writeMap(fpKey(userId, collection), snapshot.fingerprints)
        writeSet(delKey(userId, collection), snapshot.pendingDeletes)
    }

    /**
     * 记录一条用户**显式删除**。
     *
     * 只有用户主动删除（取消收藏 / 删歌单 / 从歌单移除 / 删脚本 / 删下载）才会走到这里；
     * "本地因为数量上限被裁掉"绝不能调用本方法，否则一台设备的上限会把云端数据一起删掉。
     */
    fun recordDelete(userId: Long, collection: String, key: String) {
        saveSnapshot(userId, collection, SyncPlan.recordDelete(snapshot(userId, collection), key))
    }

    fun recordDeletes(userId: Long, collection: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        var snap = snapshot(userId, collection)
        keys.forEach { snap = SyncPlan.recordDelete(snap, it) }
        saveSnapshot(userId, collection, snap)
    }

    /** 本地重新添加了一条曾经删除的记录：撤销待删标记（否则"加了又没"） */
    fun recordAdd(userId: Long, collection: String, key: String) {
        saveSnapshot(userId, collection, SyncPlan.recordAdd(snapshot(userId, collection), key))
    }

    // ==================== 便捷入口（按当前登录账号） ====================

    /**
     * 数据层的删除入口：只有登录时才记录。
     *
     * 数据层（脚本注册表、音频缓存）不知道也不该知道"账号"这回事，
     * 但"用户删了东西"这个事实必须记下来才能同步出去，因此这里用当前登录态兜住：
     * 未登录时静默跳过（本地删除照常生效，只是没有云端要通知）。
     */
    fun recordDeleteForCurrentUser(collection: String, key: String) {
        currentUserId()?.let { recordDelete(it, collection, key) }
    }

    fun recordDeletesForCurrentUser(collection: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        currentUserId()?.let { recordDeletes(it, collection, keys) }
    }

    fun recordAddForCurrentUser(collection: String, key: String) {
        currentUserId()?.let { recordAdd(it, collection, key) }
    }

    private fun currentUserId(): Long? =
        runCatching { com.aesthetic.music.data.settings.UserStore.user()?.id }.getOrNull()?.takeIf { it > 0 }

    // ==================== 游标与状态 ====================

    fun version(userId: Long): Long = prefs?.getLong(verKey(userId), 0L) ?: (mem[verKey(userId)] as? Long ?: 0L)

    fun setVersion(userId: Long, version: Long) {
        mem[verKey(userId)] = version
        prefs?.edit()?.putLong(verKey(userId), version)?.apply()
    }

    fun eventSince(userId: Long): Long =
        prefs?.getLong(evtKey(userId), 0L) ?: (mem[evtKey(userId)] as? Long ?: 0L)

    fun setEventSince(userId: Long, eventId: Long) {
        mem[evtKey(userId)] = eventId
        prefs?.edit()?.putLong(evtKey(userId), eventId)?.apply()
    }

    fun lastSyncedAt(userId: Long): Long =
        prefs?.getLong(atKey(userId), 0L) ?: (mem[atKey(userId)] as? Long ?: 0L)

    fun lastSyncStatus(userId: Long): String? =
        prefs?.getString(stKey(userId), null) ?: (mem[stKey(userId)] as? String)

    fun updateSyncStatus(userId: Long, status: String, timeMs: Long = System.currentTimeMillis()) {
        mem[atKey(userId)] = timeMs
        mem[stKey(userId)] = status
        prefs?.edit()?.putLong(atKey(userId), timeMs)?.putString(stKey(userId), status)?.apply()
    }

    /**
     * 清空某个账号的全部同步状态（用于"退出登录并清空本地数据"）。
     * 游标归零意味着下次登录会做一次全量拉取；基线清空意味着本地现有数据会被视为"新增"推上去。
     */
    fun clearUser(userId: Long) {
        val prefix = prefix(userId)
        mem.keys.filter { it.startsWith(prefix) }.forEach { mem.remove(it) }
        val editor = prefs?.edit() ?: return
        prefs?.all?.keys?.filter { it.startsWith(prefix) }?.forEach { editor.remove(it) }
        editor.apply()
    }

    // ==================== 内部 ====================

    private fun prefix(userId: Long) = "u$userId."
    private fun fpKey(userId: Long, collection: String) = "${prefix(userId)}fp.$collection"
    private fun delKey(userId: Long, collection: String) = "${prefix(userId)}del.$collection"
    private fun verKey(userId: Long) = "${prefix(userId)}version"
    private fun evtKey(userId: Long) = "${prefix(userId)}event_since"
    private fun atKey(userId: Long) = "${prefix(userId)}last_at"
    private fun stKey(userId: Long) = "${prefix(userId)}last_status"

    /**
     * 基线读取：JSON 解析失败一律视为"没有基线"（下次同步退化为全量推送）。
     * 宁可多推一次，也不能因为脏数据直接抛异常让同步整块失效。
     */
    private fun readMap(key: String): Map<String, String> {
        val raw = prefs?.getString(key, null) ?: (mem[key] as? String)
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            (gson.fromJson<Map<String, String>>(raw, type) ?: emptyMap())
                .filterKeys { it.isNotBlank() }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun writeMap(key: String, value: Map<String, String>) {
        val json = gson.toJson(value)
        mem[key] = json
        prefs?.edit()?.putString(key, json)?.apply()
    }

    private fun readSet(key: String): Set<String> {
        val raw = prefs?.getString(key, null) ?: (mem[key] as? String)
        if (raw.isNullOrBlank()) return emptySet()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
            (gson.fromJson<List<String>>(raw, type) ?: emptyList()).filter { it.isNotBlank() }.toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    private fun writeSet(key: String, value: Set<String>) {
        val json = gson.toJson(value.toList())
        mem[key] = json
        prefs?.edit()?.putString(key, json)?.apply()
    }
}
