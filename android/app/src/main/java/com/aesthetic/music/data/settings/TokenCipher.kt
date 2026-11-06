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

object TokenCipher {

    private const val TAG = "TokenCipher"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "melos_sensitive_store_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12
    private const val TAG_LENGTH_BITS = 128

    private val plainCache = ConcurrentHashMap<String, String>()

    fun encryptToStored(plain: String): String? {
        val payload = encrypt(plain) ?: return null
        return TokenFormat.wrap(payload)
    }

    fun unwrap(stored: String): String? {
        if (stored.isBlank()) return null

        val payload = TokenFormat.payloadOf(stored) ?: return stored

        plainCache[stored]?.let { return it }

        val plain = decrypt(payload) ?: run {
            Log.w(TAG, "token 解密失败，视为登录态失效（需重新登录）")
            return null
        }
        plainCache[stored] = plain
        return plain
    }

    @Synchronized
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
        null
    }
}
