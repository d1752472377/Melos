package com.aesthetic.music.data.settings

/**
 * 敏感值在 SharedPreferences 中的存储格式。
 *
 * 纯逻辑、无 Android 依赖，便于单元测试——加解密本身依赖 AndroidKeyStore
 * 无法在 JVM 上跑，但"这个值是密文还是历史明文"的判定必须可测，
 * 因为判错会导致老用户直接掉登录态。
 *
 * 格式约定：
 * - 加密值：`v1:<base64(iv || ciphertext)>`
 * - 历史明文值：无前缀，原样保留
 *
 * 用显式版本前缀而不是"解密失败就当明文"，是为了避免语义歧义：
 * 一个真正损坏的 v1 值必须被判为无效（要求重新登录），而不是被当成明文 token 发出去。
 */
object TokenFormat {

    const val PREFIX = "v1:"

    /** 把密文载荷包装成带版本前缀的存储值 */
    fun wrap(payload: String): String = PREFIX + payload

    /** 该存储值是否为加密格式 */
    fun isEncrypted(stored: String): Boolean = stored.startsWith(PREFIX)

    /**
     * 取出密文载荷；若为历史明文值则返回 null。
     * 调用方据此决定"去解密"还是"原样使用"。
     */
    fun payloadOf(stored: String): String? =
        if (isEncrypted(stored)) stored.removePrefix(PREFIX).takeIf { it.isNotBlank() } else null
}
