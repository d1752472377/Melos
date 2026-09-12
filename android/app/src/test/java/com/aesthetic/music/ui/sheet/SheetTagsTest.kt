package com.aesthetic.music.ui.sheet

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 首页分类卡片名 → 歌单广场标签 的归一化测试。
 *
 * 这条规则直接决定"从首页点分类卡片进去看到的是什么"：
 * 映射错了就是请求一个平台不认识的分类 → 空列表，用户看到"该分类下暂无歌单"。
 */
class SheetTagsTest {

    private val tags = listOf(
        "全部", "华语", "欧美", "日语", "韩语", "粤语", "流行",
        "电子", "民谣", "摇滚", "说唱", "轻音乐", "影视原声", "古风", "治愈",
    )

    @Test
    fun `名字能对上时原样使用`() {
        assertEquals("流行", SheetTags.normalize("流行", tags))
        assertEquals("摇滚", SheetTags.normalize("摇滚", tags))
        assertEquals("华语", SheetTags.normalize("华语", tags))
    }

    @Test
    fun `卡片叫电音 广场标签是电子`() {
        // 这是真实存在的不一致：getCategories() 把 edm 显示成"电音"，而标签里是"电子"
        assertEquals("电子", SheetTags.normalize("电音", tags))
    }

    @Test
    fun `其它别名同样归一`() {
        assertEquals("影视原声", SheetTags.normalize("原声", tags))
        assertEquals("影视原声", SheetTags.normalize("影视", tags))
        assertEquals("轻音乐", SheetTags.normalize("纯音乐", tags))
        assertEquals("古风", SheetTags.normalize("国风", tags))
    }

    @Test
    fun `对不上的名字退回全部 而不是请求一个不存在的分类`() {
        assertEquals("全部", SheetTags.normalize("不存在的分类", tags))
        assertEquals("全部", SheetTags.normalize(null, tags))
        assertEquals("全部", SheetTags.normalize("   ", tags))
    }

    @Test
    fun `两边空格不影响匹配`() {
        assertEquals("民谣", SheetTags.normalize(" 民谣 ", tags))
    }

    @Test
    fun `标签列表里没有全部时用第一项兜底`() {
        // 兜底逻辑不依赖"全部"这个字面值存在
        assertEquals("流行", SheetTags.normalize("不存在的分类", listOf("流行", "电子")))
        assertEquals(SheetTags.ALL, SheetTags.normalize("x", emptyList()))
    }

    @Test
    fun `别名目标不在标签列表里时也退回全部`() {
        assertEquals("全部", SheetTags.normalize("电音", listOf("全部", "流行")))
    }
}
