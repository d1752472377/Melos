package com.aesthetic.music.service

import java.io.File
import java.security.MessageDigest

/**
 * 安装包摘要校验策略。
 *
 * 纯逻辑、无 Android 依赖，便于单元测试——"用哪个算法、跟谁比"的判定出错
 * 会导致要么永远校验通过（形同虚设），要么把正常包判为损坏。
 *
 * 背景：早期 update.json 只提供 `md5`。MD5 已不具备抗碰撞能力，
 * 因此新增 `sha256` 字段并优先采用；同时保留 `md5` 回退，
 * 让仍在读旧格式的历史版本与已发布的 update.json 都能正常工作。
 *
 * 注意：摘要校验只是完整性检查，**不是信任锚点**——两者都经由同一条 TLS 通道下发，
 * 属于"自证"。真正的防伪造依赖 [UpdateService.verifyApkSignature] 的签名一致性校验。
 */
internal data class DigestSpec(
    val algorithm: String,
    val expected: String,
)

/**
 * 选择摘要校验方案：优先 SHA-256，缺失时回退 MD5，两者都没有则不做摘要校验。
 */
internal fun selectDigestSpec(sha256: String?, md5: String?): DigestSpec? = when {
    !sha256.isNullOrBlank() -> DigestSpec("SHA-256", sha256.trim())
    !md5.isNullOrBlank() -> DigestSpec("MD5", md5.trim())
    else -> null
}

/**
 * 流式计算文件摘要并返回小写十六进制串。
 *
 * 必须流式：整包 `readBytes()` 会把几十上百 MB 的 APK 一次性读进内存，低内存设备直接 OOM。
 */
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
