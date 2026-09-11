package com.aesthetic.music.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 安装包摘要校验的单元测试。
 *
 * 覆盖两件事：
 * 1. 算法选择——必须优先 SHA-256，仅在缺失时回退 MD5（老服务端 / 历史 update.json）；
 * 2. 流式摘要实现——`computeFileDigest` 用了手写缓冲区循环，必须与标准算法结果逐字节一致。
 *
 * 若第 1 条写反（优先 MD5），等于放弃了这次升级的意义；
 * 若第 2 条有偏差（例如漏掉最后一次不满缓冲区的数据），会造成"正常包被判损坏"的下载失败。
 */
class ApkIntegrityTest {

    // ---------------- 算法选择 ----------------

    @Test
    fun `同时存在时优先 SHA-256`() {
        val spec = selectDigestSpec(sha256 = "abc123", md5 = "def456")
        assertEquals("SHA-256", spec?.algorithm)
        assertEquals("abc123", spec?.expected)
    }

    @Test
    fun `仅有 md5 时回退 MD5`() {
        val spec = selectDigestSpec(sha256 = null, md5 = "def456")
        assertEquals("MD5", spec?.algorithm)
        assertEquals("def456", spec?.expected)
    }

    @Test
    fun `sha256 为空串时同样回退 MD5`() {
        // 服务端字段存在但为空，不能因此跳过校验
        val spec = selectDigestSpec(sha256 = "", md5 = "def456")
        assertEquals("MD5", spec?.algorithm)
    }

    @Test
    fun `sha256 为空白串时回退 MD5`() {
        val spec = selectDigestSpec(sha256 = "   ", md5 = "def456")
        assertEquals("MD5", spec?.algorithm)
    }

    @Test
    fun `两者都缺失时不返回校验方案`() {
        assertNull(selectDigestSpec(sha256 = null, md5 = null))
        assertNull(selectDigestSpec(sha256 = "", md5 = ""))
    }

    @Test
    fun `期望值两端空白被去除`() {
        val spec = selectDigestSpec(sha256 = "  abc123\n", md5 = null)
        assertEquals("abc123", spec?.expected)
    }

    // ---------------- 流式摘要实现 ----------------

    @Test
    fun `空文件的 SHA-256 与标准值一致`() {
        withTempFile("") { file ->
            assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                computeFileDigest(file, "SHA-256"),
            )
        }
    }

    @Test
    fun `空文件的 MD5 与标准值一致`() {
        withTempFile("") { file ->
            assertEquals("d41d8cd98f00b204e9800998ecf8427e", computeFileDigest(file, "MD5"))
        }
    }

    @Test
    fun `短内容的摘要与标准值一致`() {
        withTempFile("hello") { file ->
            assertEquals(
                "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824",
                computeFileDigest(file, "SHA-256"),
            )
            assertEquals("5d41402abc4b2a76b9719d911017c592", computeFileDigest(file, "MD5"))
        }
    }

    @Test
    fun `超过缓冲区大小的内容摘要仍正确`() {
        // 缓冲区为 64KB；用 64KB + 7 字节验证"最后一次不满缓冲区"的数据没有被丢掉
        val content = ByteArray(64 * 1024 + 7) { (it % 251).toByte() }
        withTempFileBytes(content) { file ->
            val expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(content)
                .joinToString("") { "%02x".format(it) }

            assertEquals(expected, computeFileDigest(file, "SHA-256"))
        }
    }

    @Test
    fun `同一文件重复计算结果稳定`() {
        withTempFile("deterministic") { file ->
            assertEquals(computeFileDigest(file, "SHA-256"), computeFileDigest(file, "SHA-256"))
        }
    }

    // ---------------- helpers ----------------

    private fun withTempFile(content: String, block: (File) -> Unit) =
        withTempFileBytes(content.toByteArray(Charsets.UTF_8), block)

    private fun withTempFileBytes(content: ByteArray, block: (File) -> Unit) {
        val file = File.createTempFile("apk-integrity-test", ".bin")
        try {
            file.writeBytes(content)
            block(file)
        } finally {
            file.delete()
        }
    }
}
