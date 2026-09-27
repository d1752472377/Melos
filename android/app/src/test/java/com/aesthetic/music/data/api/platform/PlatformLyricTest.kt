package com.aesthetic.music.data.api.platform

import com.aesthetic.music.player.parseLrc
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class PlatformLyricTest {

    @Test
    fun `unescapeLyric 能够正确转义 HTML 与 XML 实体`() {
        val input = "[00&#58;12&#46;34]Don&#39;t leave me &apos;cause I&amp;you&#10;[00&#58;15&#46;00]&quot;Hello&quot;"
        val result = PlatformMusicService.unescapeLyric(input)
        val expected = "[00:12.34]Don't leave me 'cause I&you\n[00:15.00]\"Hello\""
        assertEquals(expected, result)
    }

    @Test
    fun `decodeBase64OrPlain 针对普通明文 LRC 直接保留`() {
        val plain = "[00:01.00]第一行歌词\n[00:05.50]第二行歌词"
        val decoded = PlatformMusicService.decodeBase64OrPlain(plain)
        assertEquals(plain, decoded)
    }

    @Test
    fun `decodeBase64OrPlain 能够正确解码 Base64 编码的歌词`() {
        val original = "[00:01.00]晴天 - 周杰伦\n[00:04.20]故事的小黄花"
        val encoded = Base64.getEncoder().encodeToString(original.toByteArray(Charsets.UTF_8))
        val decoded = PlatformMusicService.decodeBase64OrPlain(encoded)
        assertEquals(original, decoded)
    }

    @Test
    fun `decodeBase64OrPlain 针对空串或 null 安全返回 null`() {
        assertNull(PlatformMusicService.decodeBase64OrPlain(null))
        assertNull(PlatformMusicService.decodeBase64OrPlain(""))
        assertNull(PlatformMusicService.decodeBase64OrPlain("   "))
    }

    @Test
    fun `decodeBase64OrPlain 能够解码带 XML 实体的 Base64 QQ 歌词`() {
        val rawLrc = "[00&#58;02&#46;50]QQ音乐歌词&#10;[00&#58;06&#46;00]第二句"
        val encoded = Base64.getEncoder().encodeToString(rawLrc.toByteArray(Charsets.UTF_8))
        val decoded = PlatformMusicService.decodeBase64OrPlain(encoded)
        assertEquals("[00:02.50]QQ音乐歌词\n[00:06.00]第二句", decoded)
    }

    @Test
    fun `formatKuwoLrcList 能够将酷我 JSON 歌词列表转换为标准 LRC 格式`() {
        val list = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("time", "0.0")
                addProperty("lineLyric", "青花瓷 - 周杰伦")
            })
            add(JsonObject().apply {
                addProperty("time", "18.35")
                addProperty("lineLyric", "素胚勾勒出青花笔锋浓转淡")
            })
            add(JsonObject().apply {
                addProperty("time", "75.12")
                addProperty("lineLyric", "天青色等烟雨 而我在等你")
            })
        }

        val lrc = PlatformMusicService.formatKuwoLrcList(list)
        val expected = "[00:00.00]青花瓷 - 周杰伦\n[00:18.35]素胚勾勒出青花笔锋浓转淡\n[01:15.12]天青色等烟雨 而我在等你"
        assertEquals(expected, lrc)

        // 验证生成的 LRC 能被播放器的 parseLrc 准确解析且时间戳正确
        val lines = parseLrc(lrc)
        assertEquals(3, lines.size)
        assertEquals(0L, lines[0].timeMs)
        assertEquals("青花瓷 - 周杰伦", lines[0].text)
        assertEquals(18350L, lines[1].timeMs)
        assertEquals(75120L, lines[2].timeMs)
    }
}
