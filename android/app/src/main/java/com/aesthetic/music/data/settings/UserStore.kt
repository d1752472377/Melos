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

    fun setAvatarPath(path: String?) {
        try {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_AVATAR)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_AVATAR, path)?.apply()
            }
        } catch (_: Exception) {}
    }

    fun getBackgroundPath(): String? = try {
        prefs?.getString(KEY_CUSTOM_BG, null)?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }

    fun setBackgroundPath(path: String?) {
        try {
            if (path == null) {
                prefs?.edit()?.remove(KEY_CUSTOM_BG)?.apply()
            } else {
                prefs?.edit()?.putString(KEY_CUSTOM_BG, path)?.apply()
            }
        } catch (_: Exception) {}
    }

    fun isBackgroundBlur(): Boolean = try {
        prefs?.getBoolean(KEY_BG_BLUR, false) ?: false
    } catch (_: Exception) {
        false
    }

    fun setBackgroundBlur(blur: Boolean) {
        try {
            prefs?.edit()?.putBoolean(KEY_BG_BLUR, blur)?.apply()
        } catch (_: Exception) {}
    }

    fun getNickname(): String = try {
        prefs?.getString(KEY_CUSTOM_NICKNAME, null)?.takeIf { it.isNotBlank() }
            ?: user()?.nickname?.takeIf { it.isNotBlank() }
            ?: user()?.username?.takeIf { it.isNotBlank() }
            ?: "Melos 音乐人"
    } catch (_: Exception) {
        "Melos 音乐人"
    }

    fun setNickname(nickname: String) {
        try {
            prefs?.edit()?.putString(KEY_CUSTOM_NICKNAME, nickname)?.apply()
        } catch (_: Exception) {}
    }

    fun getBio(): String = try {
        prefs?.getString(KEY_CUSTOM_BIO, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setBio(bio: String) {
        try {
            prefs?.edit()?.putString(KEY_CUSTOM_BIO, bio)?.apply()
        } catch (_: Exception) {}
    }

    fun getGender(): String = try {
        prefs?.getString(KEY_CUSTOM_GENDER, "保密") ?: "保密"
    } catch (_: Exception) {
        "保密"
    }

    fun setGender(gender: String) {
        try {
            prefs?.edit()?.putString(KEY_CUSTOM_GENDER, gender)?.apply()
        } catch (_: Exception) {}
    }

    fun getBirthday(): String = try {
        prefs?.getString(KEY_CUSTOM_BIRTHDAY, "") ?: ""
    } catch (_: Exception) {
        ""
    }

    fun setBirthday(birthday: String) {
        try {
            prefs?.edit()?.putString(KEY_CUSTOM_BIRTHDAY, birthday)?.apply()
        } catch (_: Exception) {}
    }

    fun getUserId(): String = try {
        user()?.id?.takeIf { it > 0 }?.toString()
            ?: prefs?.getString(KEY_CUSTOM_ID, null)?.takeIf { it.isNotBlank() }
            ?: "10001"
    } catch (_: Exception) {
        "10001"
    }

    fun setUserId(id: String) {
        try {
            prefs?.edit()?.putString(KEY_CUSTOM_ID, id)?.apply()
        } catch (_: Exception) {}
    }

    /** 恢复默认头像、背景、模糊开关及个性化偏好 */
    fun restoreProfileDefaults(context: Context) {
        try {
            val dir = java.io.File(context.filesDir, "profile")
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            prefs?.edit()
                ?.remove(KEY_CUSTOM_AVATAR)
                ?.remove(KEY_CUSTOM_BG)
                ?.remove(KEY_BG_BLUR)
                ?.remove(KEY_CUSTOM_NICKNAME)
                ?.remove(KEY_CUSTOM_BIO)
                ?.remove(KEY_CUSTOM_GENDER)
                ?.remove(KEY_CUSTOM_BIRTHDAY)
                ?.apply()
        } catch (_: Exception) {}
    }
}
