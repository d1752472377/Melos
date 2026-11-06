package com.aesthetic.music.service

import java.io.File
import java.security.MessageDigest

internal data class DigestSpec(
    val algorithm: String,
    val expected: String,
)

internal fun selectDigestSpec(sha256: String?, md5: String?): DigestSpec? = when {
    !sha256.isNullOrBlank() -> DigestSpec("SHA-256", sha256.trim())
    !md5.isNullOrBlank() -> DigestSpec("MD5", md5.trim())
    else -> null
}

internal fun computeFileDigest(file: File, algorithm: String): String {
    val digest = MessageDigest.getInstance(algorithm)
    file.inputStream().use { input ->
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
