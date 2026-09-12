package com.aesthetic.music.data.settings

import android.content.Context
import android.content.SharedPreferences
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.UserInfo

/**
 * 用户登录态持久化：token + 用户信息。
 * OkHttp 拦截器从这里读取 token，给用户接口请求加 Authorization。
 */
object UserStore {

    private const val TAG = "UserStore"
    private const val PREFS = "aesthetic_music_user"
    private const val KEY_TOKEN = "token"
    private const val KEY_USER = "user"

    private var prefs: SharedPreferences? = null
    private val gson = AppJson.gson

    fun init(context: Context) {
        if (prefs == null) {
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }
    }

    fun save(token: String, user: UserInfo) {
        try {
            prefs?.edit()
                // 加密失败时退回明文，保证功能可用（加密失败属设备级异常，此时优先不让用户掉登录）
                ?.putString(KEY_TOKEN, TokenCipher.encryptToStored(token) ?: token)
                ?.putString(KEY_USER, gson.toJson(user))
                ?.apply()
        } catch (_: Exception) {
        }
    }

    fun updateUser(user: UserInfo) {
        try {
            prefs?.edit()?.putString(KEY_USER, gson.toJson(user))?.apply()
        } catch (_: Exception) {
        }
    }

    /**
     * 读取登录 token。
     *
     * 兼容历史明文值：老版本直接把 token 明文写入 SharedPreferences，
     * 这里在读到明文时顺带就地升级为密文（一次性，之后 `isEncrypted` 即为 true），
     * 避免老用户永远停留在明文状态。解密失败返回 null，等价于登录态失效。
     */
    fun token(): String? = try {
        val stored = prefs?.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() } ?: return null
        val plain = TokenCipher.unwrap(stored)?.takeIf { it.isNotBlank() } ?: return null
        if (!TokenFormat.isEncrypted(stored)) {
            TokenCipher.encryptToStored(plain)?.let {
                prefs?.edit()?.putString(KEY_TOKEN, it)?.apply()
            }
        }
        plain
    } catch (_: Exception) {
        null
    }

    fun user(): UserInfo? = try {
        val raw = prefs?.getString(KEY_USER, null) ?: return null
        gson.fromJson(raw, UserInfo::class.java)
    } catch (_: Exception) {
        null
    }

    fun isLoggedIn(): Boolean = token() != null && user() != null

    private const val KEY_CUSTOM_AVATAR = "custom_avatar"
    private const val KEY_CUSTOM_BG = "custom_bg"
    private const val KEY_BG_BLUR = "bg_blur"
    private const val KEY_CUSTOM_NICKNAME = "custom_nickname"
    private const val KEY_CUSTOM_BIO = "custom_bio"
    private const val KEY_CUSTOM_GENDER = "custom_gender"
    private const val KEY_CUSTOM_BIRTHDAY = "custom_birthday"
    private const val KEY_CUSTOM_ID = "custom_id"

    fun logout() {
        try {
            prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_USER)?.apply()
        } catch (_: Exception) {
        }
    }

    // ===== 个人中心自定义字段 =====
    fun getAvatarPath(): String? = try {
        prefs?.getString(KEY_CUSTOM_AVATAR, null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun setAvatarPath(path: String?) = bestEffort(TAG, "保存头像路径") {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_AVATAR)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_AVATAR, path)?.apply()
            }
    }

    fun getBackgroundPath(): String? = try {
        prefs?.getString(KEY_CUSTOM_BG, null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun setBackgroundPath(path: String?) = bestEffort(TAG, "保存背景图路径") {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_BG)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_BG, path)?.apply()
            }
    }

    fun isBackgroundBlur(): Boolean = try {
        prefs?.getBoolean(KEY_BG_BLUR, false) ?: false
    } catch (_: Exception) {
        false
    }

    fun setBackgroundBlur(blur: Boolean) = bestEffort(TAG, "保存背景模糊开关") {
            prefs?.edit()?.putBoolean(KEY_BG_BLUR, blur)?.apply()
    }

    fun getNickname(): String = try {
        prefs?.getString(KEY_CUSTOM_NICKNAME, null)?.takeIf { it.isNotBlank() }
            ?: user()?.nickname?.takeIf { it.isNotBlank() }
            ?: user()?.username?.takeIf { it.isNotBlank() }
            ?: "Melos 音乐人"
    } catch (_: Exception) {
        "Melos 音乐人"
    }

    fun setNickname(nickname: String) = bestEffort(TAG, "保存昵称") {
            prefs?.edit()?.putString(KEY_CUSTOM_NICKNAME, nickname)?.apply()
    }

    fun getBio(): String = try {
        prefs?.getString(KEY_CUSTOM_BIO, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setBio(bio: String) = bestEffort(TAG, "保存个性签名") {
            prefs?.edit()?.putString(KEY_CUSTOM_BIO, bio)?.apply()
    }

    // 性别 / 出生日期已随「个人资料页去掉这两项」一起移除：它们只存在本地、服务端没有对应字段，
    // 填了也不会跟着账号走（换设备就丢），属于看起来正式但其实是假的字段。
    // 旧版本写入的键仍在 restoreProfileDefaults 里清理，避免残留脏数据。

    fun getUserId(): String = try {
        user()?.id?.takeIf { it > 0 }?.toString()
            ?: prefs?.getString(KEY_CUSTOM_ID, null)?.takeIf { it.isNotBlank() }
            ?: "10001"
    } catch (_: Exception) {
        "10001"
    }

    fun setUserId(id: String) = bestEffort(TAG, "保存用户 ID") {
            prefs?.edit()?.putString(KEY_CUSTOM_ID, id)?.apply()
    }

    /**
     * 恢复默认背景、模糊开关与个性化偏好。
     *
     * **头像刻意保留**：用户实测反馈「恢复默认不该顺手把头像清掉」——头像属于账号身份的一部分，
     * 重选一次的成本远高于切一次背景。因此这里只删掉背景图文件（`background_` 前缀），
     * 头像文件（`avatar_` 前缀）与 `KEY_CUSTOM_AVATAR` 都留着。
     *
     * 用户名/注册日期这些来自服务端账号的字段本来就不在这里，无需处理。
     */
    fun restoreProfileDefaults(context: Context) = bestEffort(TAG, "恢复个人资料默认值") {
        val dir = java.io.File(context.filesDir, "profile")
        // 只删背景：以前是 dir.deleteRecursively()，会把头像一起删掉
        dir.listFiles()?.forEach { file ->
            if (file.name.startsWith(BG_FILE_PREFIX)) file.delete()
        }
        prefs?.edit()
            ?.remove(KEY_CUSTOM_BG)
            ?.remove(KEY_BG_BLUR)
            ?.remove(KEY_CUSTOM_NICKNAME)
            ?.remove(KEY_CUSTOM_BIO)
            ?.remove(KEY_CUSTOM_GENDER)
            ?.remove(KEY_CUSTOM_BIRTHDAY)
            ?.apply()
    }

    /** 背景图文件前缀（见 ProfileUtils.saveImageToInternal 的 prefix 参数；头像用 "avatar"，不删） */
    private const val BG_FILE_PREFIX = "background"
}
