package com.aesthetic.music.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 敏感值存储格式的单元测试。
 *
 * 这组断言守着一条容易被忽略的兼容线：老版本把 token 明文写进 SharedPreferences，
 * 新版本改成密文。判定逻辑写错的两个方向都很糟——
 * 把明文当密文去解密 → 老用户全部掉登录；
 * 把密文当明文直接用 → 把 `v1:xxxx` 当 token 发给服务端，鉴权必然失败且极难排查。
 *
 * 真正的加解密依赖 AndroidKeyStore，无法在 JVM 上验证，因此这里只锁格式判定。
 */
class TokenFormatTest {

    @Test
    fun `wrap 加上版本前缀`() {
        assertEquals("v1:abc", TokenFormat.wrap("abc"))
    }

    @Test
    fun `带前缀的值被识别为加密格式`() {
        assertTrue(TokenFormat.isEncrypted("v1:abc"))
    }

    @Test
    fun `历史明文值不被误判为加密格式`() {
        assertFalse(TokenFormat.isEncrypted("eyJhbGciOiJIUzI1NiJ9.plain.token"))
        assertFalse(TokenFormat.isEncrypted(""))
        // 前缀必须完整匹配，"v1" 或 "v1" 少了冒号都不算
        assertFalse(TokenFormat.isEncrypted("v1abc"))
        assertFalse(TokenFormat.isEncrypted("v2:abc"))
    }

    @Test
    fun `payloadOf 对加密值返回去掉前缀的载荷`() {
        assertEquals("abc", TokenFormat.payloadOf("v1:abc"))
    }

    @Test
    fun `payloadOf 对历史明文值返回 null`() {
        assertNull(TokenFormat.payloadOf("plain-token"))
        assertNull(TokenFormat.payloadOf(""))
    }

    @Test
    fun `payloadOf 对空载荷返回 null 而不是空串`() {
        // 前缀后什么都没有属于损坏数据，必须判为无效，避免把空 token 当成有效值
        assertNull(TokenFormat.payloadOf("v1:"))
        assertNull(TokenFormat.payloadOf("v1:   "))
    }

    @Test
    fun `wrap 与 payloadOf 互为逆运算`() {
        val payload = "YWJjZGVmZ2hpamtsbW5vcA=="
        assertEquals(payload, TokenFormat.payloadOf(TokenFormat.wrap(payload)))
    }
}
