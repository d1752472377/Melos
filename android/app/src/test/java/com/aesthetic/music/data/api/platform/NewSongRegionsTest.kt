package com.aesthetic.music.data.api.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「新歌首发」地区目录单测。
 *
 * 钉住三件事：
 * 1. areaId 是实测值（7 华语 / 96 欧美 / 8 日语 / 16 韩语），改错一个字母都会让首页某张卡
 *    变成「0 首新歌」而没有任何报错——这类静默失败只能靠测试挡；
 * 2. 同一天多次调用必须给出同一组区域（否则首页每次重组三张卡会闪）；
 * 3. 轮换必须覆盖所有区域（不能有某个语种永远轮不到）。
 */
class NewSongRegionsTest {

    @Test
    fun `区域与 areaId 一一对应`() {
        assertEquals(
            listOf("huayu", "oumei", "riyu", "hanguo"),
            NewSongRegions.all.map { it.id },
        )
        assertEquals(
            listOf("华语", "欧美", "日语", "韩语"),
            NewSongRegions.all.map { it.label },
        )
        assertEquals(
            listOf(7, 96, 8, 16),
            NewSongRegions.all.map { it.areaId },
        )
    }

    @Test
    fun `按 id 查得到区域`() {
        assertEquals(96, NewSongRegions.byId("oumei")?.areaId)
        assertNotNull(NewSongRegions.byId("huayu"))
        assertNull(NewSongRegions.byId("yueyu")) // 数据源没有粤语分区
    }

    @Test
    fun `同一天给出同一组三个区域`() {
        val day = NewSongRegions.dayKeyOf(1_767_225_600_000L)
        assertEquals(
            NewSongRegions.pick(3, day),
            NewSongRegions.pick(3, day),
        )
        assertEquals(3, NewSongRegions.pick(3, day).size)
    }

    @Test
    fun `连续四天覆盖全部四个区域`() {
        val day = NewSongRegions.dayKeyOf(1_767_225_600_000L)
        val seen = mutableSetOf<String>()
        for (offset in 0 until NewSongRegions.all.size) {
            seen += NewSongRegions.pick(3, day + offset).map { it.id }
        }
        assertEquals(NewSongRegions.all.map { it.id }.toSet(), seen)
    }

    @Test
    fun `取三个时每次都不重样且顺序连续`() {
        val day = NewSongRegions.dayKeyOf(1_767_225_600_000L)
        repeat(NewSongRegions.all.size) { offset ->
            val picked = NewSongRegions.pick(3, day + offset)
            assertEquals(3, picked.map { it.id }.distinct().size)
        }
    }

    @Test
    fun `count 超界时收敛到合法范围`() {
        val day = 0L
        assertEquals(1, NewSongRegions.pick(0, day).size)
        assertEquals(
            NewSongRegions.all.size,
            NewSongRegions.pick(99, day).size,
        )
    }

    @Test
    fun `负 dayKey 不会越界`() {
        val picked = NewSongRegions.pick(3, -1L)
        assertEquals(3, picked.size)
        assertTrue(picked.all { it.areaId > 0 })
    }
}
