package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * **设备级**同步配置：只保存"自己的服务器地址"。
 *
 * ## 账号与密钥的分工（本次重写）
 * 旧版本这里是「服务器地址 + 同步密钥」：一个手填的共享密钥代表"我有权访问这份数据"。
 * 现在后端只有一个职责——保存某个**账号**的数据，因此鉴权交给账号登录态
 * （`UserStore` 里的 token，见 `ApiClient`），这里不再有"同步密钥"这个概念。
 *
 * 服务器地址保留在设备上而不是账号里：地址是"我这台手机连哪个后端"的设备事实，
 * 换账号登录不该把地址清掉或跟着账号跑（自托管用户可能同时有多个后端）。
 *
 * 账号级的东西（同步游标、基线指纹、待删标记）在
 * [com.aesthetic.music.data.sync.SyncStateStore]，按 userId 隔离。
 */
object SyncServerStore {

    private const val PREFS = "aesthetic_music_sync_server"
    private const val KEY_SERVER_URL = "server_url"

    /** 旧版本遗留键（同步密钥 / 全局游标 / 全局状态）：一次性清理，见 [init] */
    private const val LEGACY_KEY_SYNC_TOKEN = "sync_token"
    private const val LEGACY_KEY_LAST_SYNCED_AT = "last_synced_at"
    private const val LEGACY_KEY_LAST_SYNC_STATUS = "last_sync_status"
    private const val LEGACY_KEY_LAST_SYNC_VERSION = "last_sync_version"
    private const val LEGACY_KEY_LAST_EVENT_SINCE = "last_event_since"

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 旧键清理：同步密钥已被账号登录态取代；全局游标/状态已改为按账号存储（SyncStateStore）。
        // 留着它们只会让人以为"还有一个密钥在起作用"，也可能被后续代码误读到过期的游标。
        prefs?.edit()
            ?.remove(LEGACY_KEY_SYNC_TOKEN)
            ?.remove(LEGACY_KEY_LAST_SYNCED_AT)
            ?.remove(LEGACY_KEY_LAST_SYNC_STATUS)
            ?.remove(LEGACY_KEY_LAST_SYNC_VERSION)
            ?.remove(LEGACY_KEY_LAST_EVENT_SINCE)
            ?.apply()
    }

    fun saveServerUrl(serverUrl: String) {
        prefs?.edit()?.putString(KEY_SERVER_URL, normalize(serverUrl))?.apply()
    }

    fun getServerUrl(): String = normalize(prefs?.getString(KEY_SERVER_URL, "").orEmpty())

    /** 是否已填服务器地址（**不代表已登录**：能不能同步还取决于账号登录态） */
    fun isConfigured(): Boolean = getServerUrl().isNotBlank()

    fun clear() {
        prefs?.edit()?.clear()?.apply()
    }

    /** 地址归一化：去掉尾部斜杠与首尾空格（Retrofit/OkHttp 拼接路径时最容易被它坑） */
    private fun normalize(url: String): String = url.trim().trimEnd('/')
}
