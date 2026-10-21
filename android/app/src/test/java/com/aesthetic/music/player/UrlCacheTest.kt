package com.aesthetic.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 直链缓存单测。
 *
 * 这个缓存是「息屏连播」的地基：键拼错 = 永远不命中（等于没做），
 * 过期不淘汰 = 播放器拿到过期签名直链（403，比没有缓存更糟）。
 * 两者都不会崩，只会让"息屏放一两首就停"这个老问题悄悄回来，所以钉死。
 */
class UrlCacheTest {

    @Test
    fun `存进去能取出来`() {
        UrlCache.clear()
        val key = UrlCache.key("netease", "123", "lossless")
        UrlCache.put(key, "https://example.com/a.flac")
        assertEquals("https://example.com/a.flac", UrlCache.get(key))
        UrlCache.clear()
    }

    @Test
    fun `键包含音质，不同音质互不覆盖`() {
        UrlCache.clear()
        val lossless = UrlCache.key("netease", "123", "lossless")
        val high = UrlCache.key("netease", "123", "high")
        assertNotEquals(lossless, high)
        UrlCache.put(lossless, "flac-url")
        UrlCache.put(high, "320-url")
        assertEquals("flac-url", UrlCache.get(lossless))
        assertEquals("320-url", UrlCache.get(high))
        UrlCache.clear()
    }

    @Test
    fun `键对平台大小写与空白不敏感`() {
        assertEquals(
            UrlCache.key("Netease", " 123 ", "high"),
            UrlCache.key("netease", "123", "high"),
        )
    }

    @Test
    fun `空直链不入缓存`() {
        UrlCache.clear()
        val key = UrlCache.key("qq", "abc", "high")
        UrlCache.put(key, "")
        assertNull(UrlCache.get(key))
        UrlCache.put(key, "   ")
        assertNull(UrlCache.get(key))
    }

    @Test
    fun `容量上限淘汰最久未用的一条`() {
        UrlCache.clear()
        // 上限 40：塞 41 条，最早那条应被淘汰
        for (i in 0 until 41) {
            UrlCache.put(UrlCache.key("netease", "$i", "high"), "url-$i")
        }
        assertEquals(40, UrlCache.size())
        assertNull(UrlCache.get(UrlCache.key("netease", "0", "high")))
        assertEquals("url-40", UrlCache.get(UrlCache.key("netease", "40", "high")))
        UrlCache.clear()
    }

    @Test
    fun `remove 与 clear 生效`() {
        UrlCache.clear()
        val a = UrlCache.key("netease", "1", "high")
        val b = UrlCache.key("netease", "2", "high")
        UrlCache.put(a, "ua")
        UrlCache.put(b, "ub")
        UrlCache.remove(a)
        assertNull(UrlCache.get(a))
        assertEquals("ub", UrlCache.get(b))
        UrlCache.clear()
        assertEquals(0, UrlCache.size())
    }
}
