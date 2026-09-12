package com.aesthetic.music.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * [SourceRegistryStore] 的多插件注册表测试。
 *
 * 与其它 Store 测试一样不依赖 SharedPreferences（JVM 下未 init，落盘降级为 no-op），
 * 但 store 内部维护内存态，因此增删改查的行为在单测里是真实可验证的——
 * 这一点很重要：插件记录一旦写坏，用户会直接遇到"插件列表里有、播放却用不了"。
 */
class SourceRegistryStoreTest {

    @Before
    fun setUp() {
        SourceRegistryStore.clear()
    }

    private fun plugin(id: String = "", name: String = "测试音源", platform: String = "") = SourcePlugin(
        id = id,
        name = name,
        version = "2.1.0",
        author = "作者",
        description = "描述",
        homepage = "https://example.com",
        targetPlatform = platform,
        declaredSources = listOf("wy", "tx"),
    )

    @Test
    fun `upsert 会自动分配 id 并保留字段`() {
        val saved = SourceRegistryStore.upsert(plugin())
        assertTrue(saved.id.isNotBlank())
        val loaded = SourceRegistryStore.find(saved.id)!!
        assertEquals("测试音源", loaded.name)
        assertEquals("2.1.0", loaded.version)
        assertEquals("作者", loaded.author)
        assertEquals("https://example.com", loaded.homepage)
        assertEquals(listOf("wy", "tx"), loaded.declaredSources)
        assertTrue(loaded.enable)
    }

    @Test
    fun `同一 id 重复 upsert 是覆盖而不是追加`() {
        val saved = SourceRegistryStore.upsert(plugin(id = "fixed", name = "旧名"))
        assertEquals(1, SourceRegistryStore.list().size)
        SourceRegistryStore.upsert(plugin(id = "fixed", name = "新名"))
        assertEquals(1, SourceRegistryStore.list().size)
        assertEquals("新名", SourceRegistryStore.find("fixed")!!.name)
        // 首次导入时间保留，便于列表按导入顺序稳定排序
        assertEquals(saved.importedAt, SourceRegistryStore.find("fixed")!!.importedAt)
    }

    @Test
    fun `update 可以改启用状态与指定平台`() {
        val saved = SourceRegistryStore.upsert(plugin(id = "p1"))
        SourceRegistryStore.update(saved.id) { it.copy(enable = false) }
        assertFalse(SourceRegistryStore.find("p1")!!.enable)

        SourceRegistryStore.update(saved.id) { it.copy(targetPlatform = "kugou") }
        assertEquals("kugou", SourceRegistryStore.find("p1")!!.targetPlatform)
    }

    @Test
    fun `非法的指定平台会被归一化为通用`() {
        val saved = SourceRegistryStore.upsert(plugin(id = "p1", platform = "bilibili"))
        assertEquals("", SourceRegistryStore.find(saved.id)!!.targetPlatform)
    }

    @Test
    fun `remove 会删除记录`() {
        SourceRegistryStore.upsert(plugin(id = "p1"))
        SourceRegistryStore.upsert(plugin(id = "p2", name = "第二个"))
        SourceRegistryStore.remove("p1")
        assertEquals(listOf("p2"), SourceRegistryStore.list().map { it.id })
    }

    @Test
    fun `删除当前指定插件时会清空指定`() {
        SourceRegistryStore.upsert(plugin(id = "p1"))
        SourceRegistryStore.setActiveId("p1")
        assertEquals("p1", SourceRegistryStore.activeId())
        SourceRegistryStore.remove("p1")
        assertNull(SourceRegistryStore.activeId())
    }

    @Test
    fun `声明源写回后会影响适用平台展示`() {
        SourceRegistryStore.upsert(plugin(id = "p1"))
        SourceRegistryStore.update("p1") { it.copy(declaredSources = listOf("kg", "mg")) }
        val declared = SourceRegistryStore.find("p1")!!.declaredPlatforms
        assertEquals(listOf(SourcePlatform.KUGOU, SourcePlatform.MIGU), declared)
    }

    @Test
    fun `markUsed 只更新时间戳`() {
        SourceRegistryStore.upsert(plugin(id = "p1"))
        assertEquals(0L, SourceRegistryStore.find("p1")!!.lastUsedAt)
        SourceRegistryStore.markUsed("p1")
        assertTrue(SourceRegistryStore.find("p1")!!.lastUsedAt > 0)
    }

    @Test
    fun `未指定平台的插件通用 指定后只对该平台生效`() {
        SourceRegistryStore.upsert(plugin(id = "p1"))
        assertTrue(SourceRegistryStore.find("p1")!!.appliesTo("qq"))
        SourceRegistryStore.update("p1") { it.copy(targetPlatform = "qq") }
        assertTrue(SourceRegistryStore.find("p1")!!.appliesTo("qq"))
        assertFalse(SourceRegistryStore.find("p1")!!.appliesTo("netease"))
    }

    @Test
    fun `update 不存在的 id 返回 null 不误建记录`() {
        assertNull(SourceRegistryStore.update("missing") { it })
        assertTrue(SourceRegistryStore.list().isEmpty())
    }

    @Test
    fun `从元数据构造插件会立刻落库并保留指定平台`() {
        val meta = com.aesthetic.music.data.userapi.ScriptMetadata(
            id = "script-1",
            name = "某音源",
            version = "3.0",
            author = "某人",
        )
        val created = SourceRegistryStore.pluginFromMeta(meta, "netease", "/*! @name 某音源 */")
        assertEquals("某音源", created.name)
        assertEquals("netease", created.targetPlatform)
        assertEquals(1, SourceRegistryStore.list().size)
        assertFalse(created.id.isBlank())
    }
}
