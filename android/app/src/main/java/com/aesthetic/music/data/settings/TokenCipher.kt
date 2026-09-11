package com.aesthetic.music.data.settings

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 登录态 / 同步密钥的本地加密存储（AndroidKeyStore + AES-256-GCM）。
 *
 * 为什么不用 `EncryptedSharedPreferences`：`androidx.security:security-crypto`
 * 已于 1.1.0-alpha07（2025-04）起废弃全部 API，官方说明是
 * "Deprecated all APIs in favour of existing platform APIs and direct use of Android Keystore"，
 * 且未提供迁移指南。因此这里直接使用平台 Keystore，不引入已废弃依赖。
 *
 * 密钥存放在 AndroidKeyStore 中，**不可导出、不随云备份迁移**，
 * 与 `backup_rules.xml` 排除登录态的策略一致：换机后 token 本来就不该跟过去，
 * 解不开即视为未登录，要求重新登录即可。
 *
 * 解密结果带内存缓存：OkHttp 拦截器会在**每个请求**上读取 token，
 * 若每次都走 Keystore（单次数十毫秒）会显著拖慢网络层。
 */
object TokenCipher {

    private const val TAG = "TokenCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "melos_sensitive_store_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12          // GCM 推荐 96 bit
    private const val TAG_LENGTH_BITS = 128

    /**
     * 存储值 → 明文 的缓存。
     * 键是完整的存储值（含前缀），因此重新登录写入新 token 时天然失效，无需手动清理。
     * 条目数量级为个位数（登录 token + 同步 token），无需考虑容量。
     */
    private val plainCache = ConcurrentHashMap<String, String>()

    // ---------------- 对外接口 ----------------

    /**
     * 加密并包装。加密失败时返回 null，由调用方决定降级策略
     * （当前策略：退回明文存储以保证功能可用，仅记录警告）。
     */
    fun encryptToStored(plain: String): String? {
        val payload = encrypt(plain) ?: return null
        return TokenFormat.wrap(payload)
    }

    /**
     * 读取存储值并还原为明文。
     *
     * - 加密值：解密成功返回明文；解密失败返回 null（视为登录态失效）
     * - 历史明文值：原样返回，保证老用户升级后不掉登录
     */
    fun unwrap(stored: String): String? {
        if (stored.isBlank()) return null

        val payload = TokenFormat.payloadOf(stored) ?: return stored  // 历史明文，直接放行

        plainCache[stored]?.let { return it }

        val plain = decrypt(payload) ?: run {
            Log.w(TAG, "token 解密失败，视为登录态失效（需重新登录）")
            return null
        }
        plainCache[stored] = plain
        return plain
    }

    // ---------------- 内部实现 ----------------

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String? = try {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val cipherText = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        Base64.encodeToString(iv + cipherText, Base64.NO_WRAP)
    } catch (e: Exception) {
        Log.w(TAG, "token 加密失败", e)
        null
    }

    private fun decrypt(payload: String): String? = try {
        val raw = Base64.decode(payload, Base64.NO_WRAP)
        if (raw.size <= IV_LENGTH) {
            null
        } else {
            val iv = raw.copyOfRange(0, IV_LENGTH)
            val cipherText = raw.copyOfRange(IV_LENGTH, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_LENGTH_BITS, iv))
            String(cipher.doFinal(cipherText), Charsets.UTF_8)
        }
    } catch (e: Exception) {
        // GCM 校验失败（密钥被清除、密文被篡改、换机后密钥不存在）都会走到这里
        null
    }
}
