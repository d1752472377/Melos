package com.aesthetic.music.data.sync

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import com.aesthetic.music.data.AppJson
import java.util.UUID

object SyncStateStore {

    private const val PREFS = "aesthetic_music_sync_state"

    private const val KEY_DEVICE_ID = "device_id"

    private val mem = mutableMapOf<String, Any?>()

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

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

    fun deviceName(): String {
        val brand = Build.BRAND.orEmpty().replaceFirstChar { it.uppercase() }
        val model = Build.MODEL.orEmpty()
        return listOf(brand, model).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Android" }
    }

    fun snapshot(userId: Long, collection: String): SyncSnapshot = SyncSnapshot(
        fingerprints = readMap(fpKey(userId, collection)),
        pendingDeletes = readSet(delKey(userId, collection)),
    )

    fun saveSnapshot(userId: Long, collection: String, snapshot: SyncSnapshot) {
        writeMap(fpKey(userId, collection), snapshot.fingerprints)
        writeSet(delKey(userId, collection), snapshot.pendingDeletes)
    }

    fun recordDelete(userId: Long, collection: String, key: String) {
        saveSnapshot(userId, collection, SyncPlan.recordDelete(snapshot(userId, collection), key))
    }

    fun recordDeletes(userId: Long, collection: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        var snap = snapshot(userId, collection)
        keys.forEach { snap = SyncPlan.recordDelete(snap, it) }
        saveSnapshot(userId, collection, snap)
    }

    fun recordAdd(userId: Long, collection: String, key: String) {
        saveSnapshot(userId, collection, SyncPlan.recordAdd(snapshot(userId, collection), key))
    }

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

    fun clearUser(userId: Long) {
        val prefix = prefix(userId)
        mem.keys.filter { it.startsWith(prefix) }.forEach { mem.remove(it) }
        val editor = prefs?.edit() ?: return
        prefs?.all?.keys?.filter { it.startsWith(prefix) }?.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun prefix(userId: Long) = "u$userId."
    private fun fpKey(userId: Long, collection: String) = "${prefix(userId)}fp.$collection"
    private fun delKey(userId: Long, collection: String) = "${prefix(userId)}del.$collection"
    private fun verKey(userId: Long) = "${prefix(userId)}version"
    private fun evtKey(userId: Long) = "${prefix(userId)}event_since"
    private fun atKey(userId: Long) = "${prefix(userId)}last_at"
    private fun stKey(userId: Long) = "${prefix(userId)}last_status"

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
