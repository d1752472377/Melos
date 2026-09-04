package com.aesthetic.music.data.userapi

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object CryptoUtils {

    fun aesEncrypt(dataBase64: String, keyBase64: String, ivBase64: String, mode: String): String {
        return try {
            val data = Base64.decode(dataBase64, Base64.DEFAULT)
            val key = Base64.decode(keyBase64, Base64.DEFAULT)
            val cipher = Cipher.getInstance(mode)
            val secretKeySpec = SecretKeySpec(key, "AES")

            if (ivBase64.isNotEmpty()) {
                val iv = Base64.decode(ivBase64, Base64.DEFAULT)
                val finalIvs = ByteArray(16)
                val len = minOf(iv.size, 16)
                System.arraycopy(iv, 0, finalIvs, 0, len)
                val ivps = IvParameterSpec(finalIvs)
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec, ivps)
            } else {
                cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec)
            }

            val encryptedBytes = cipher.doFinal(data)
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("CryptoUtils", "aesEncrypt error: ${e.message}")
            ""
        }
    }

    fun rsaEncrypt(dataBase64: String, publicKeyBase64: String, padding: String): String {
        return try {
            val cleanKey = publicKeyBase64.trim()
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replace("\\s".toRegex(), "")

            val keyBytes = Base64.decode(cleanKey, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(keyBytes)
            val keyFactory = KeyFactory.getInstance("RSA")
            val publicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance(padding)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val data = Base64.decode(dataBase64, Base64.DEFAULT)
            val encryptedBytes = cipher.doFinal(data)
            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            android.util.Log.e("CryptoUtils", "rsaEncrypt error: ${e.message}")
            ""
        }
    }
}
