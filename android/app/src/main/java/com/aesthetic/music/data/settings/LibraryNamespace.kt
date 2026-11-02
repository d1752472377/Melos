package com.aesthetic.music.data.settings

/**
 * 本地音乐库（收藏 / 历史 / 歌单 / 收藏歌单 / 云端下载镜像）的账号命名空间。
 *
 * ## 为什么需要
 * 这些数据"属于账号"而不是"属于设备"。此前所有账号共用同一份 SharedPreferences 键，
 * 退出登录换账号后，新账号第一次云同步会把上一个账号留在本机的数据当成"自己的新增"全量推上去
 * ——这正是线上观察到的"新账号自动继承旧账号全部数据"的根因。
 *
 * ## 规则
 * - 已登录：键前缀 `u<userId>:`，每个账号一份独立数据，重新登录同一账号数据还在；
 * - 未登录：键前缀 `guest:`，纯本地使用（登录后本地改动会作为该账号的数据走同步）；
 * - 旧版本数据迁移：首次读到旧的无前缀键时，把内容搬进**当前账号**的命名空间并删除旧键。
 *   只搬一次，之后的账号不再看到它——旧键里的数据本来就属于"当时登录的那个人"。
 */
object LibraryNamespace {

    private const val GUEST = "guest"

    /** 当前命名空间前缀（含结尾冒号） */
    fun prefix(): String = when (val uid = currentUserId()) {
        null -> "$GUEST:"
        else -> "u$uid:"
    }

    fun currentUserId(): Long? =
        runCatching { UserStore.user()?.id }.getOrNull()?.takeIf { it > 0 }

    /**
     * 读取 namespaced 键，并做一次性的旧键迁移。
     *
     * 迁移是幂等的：旧键搬走后即删除，不会重复。prefs 为 null（未 init，JVM 单测）时
     * 退化为读旧键，行为与历史版本一致。
     */
    fun read(prefs: android.content.SharedPreferences?, key: String): String? {
        if (prefs == null) return prefsNullLegacy(key)
        val legacy = prefs.getString(key, null)
        if (legacy != null) {
            val p = prefix()
            if (prefs.getString("$p$key", null) == null) {
                prefs.edit().putString("$p$key", legacy).apply()
            }
            prefs.edit().remove(key).apply()
        }
        return prefs.getString(prefix() + key, null)
    }

    private fun prefsNullLegacy(key: String): String? = null

    /** 写 namespaced 键 */
    fun write(prefs: android.content.SharedPreferences?, key: String, value: String) {
        prefs?.edit()?.putString(prefix() + key, value)?.apply()
    }

    /** 清掉当前命名空间的键（"退出并清空本机数据"用） */
    fun clear(prefs: android.content.SharedPreferences?, key: String) {
        prefs?.edit()?.remove(prefix() + key)?.apply()
    }
}
