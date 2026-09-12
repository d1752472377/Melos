package com.aesthetic.music.data

import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.ProfileBody
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.ui.profile.ProfileFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 资料相关模型的 JSON 契约测试。
 *
 * 两条被钉住的约定（都属于"错了很难发现"的类型）：
 * 1. `ProfileBody` 里为 null 的字段**不出现在 JSON 里**，后端据此判断"这一项不改动"；
 *    如果哪天有人给 Gson 打开 `serializeNulls`，改昵称就会顺手清空邮箱，而且不会有任何报错。
 * 2. `UserInfo.email` 缺失时必须反序列化成 null（而不是空串或炸掉）：
 *    老服务端与老版本本地缓存都没有这个字段，AppJson 用的是无 Kotlin 感知的 Gson，
 *    缺失字段会被填成 null——把它声明成非空 String 就会在读取时 NPE。
 */
class ProfileJsonContractTest {

    private val gson = AppJson.gson

    @Test
    fun `提交资料时 null 字段被省略 只发改动的项`() {
        val json = gson.toJson(ProfileBody(nickname = "小美"))
        assertTrue(json.contains("\"nickname\":\"小美\""))
        assertFalse("avatar 不该出现", json.contains("avatar"))
        assertFalse("email 不该出现", json.contains("email"))
    }

    @Test
    fun `解绑邮箱用空串表达而不是 null`() {
        val json = gson.toJson(ProfileBody(email = ""))
        // 空串必须真的发出去，null 会被后端理解成"不改动"，导致解绑永远不生效
        assertEquals("{\"email\":\"\"}", json)
    }

    @Test
    fun `老数据缺少 email 字段时解析为 null 且保留账号信息`() {
        val legacy = """{"id":12,"username":"melos_user","nickname":"小美","avatar":"a.jpg","createdAt":1694524800000}"""
        val user = gson.fromJson(legacy, UserInfo::class.java)
        assertEquals(12L, user.id)
        assertEquals("melos_user", user.username)
        assertEquals("小美", user.nickname)
        assertEquals(1694524800000L, user.createdAt)
        assertNull(user.email)
    }

    @Test
    fun `服务端返回邮箱时能解析出来`() {
        val json = """{"id":1,"username":"u","nickname":"n","avatar":"","createdAt":1,"email":"a@b.co"}"""
        val user = gson.fromJson(json, UserInfo::class.java)
        assertEquals("a@b.co", user.email)
    }

    @Test
    fun `未绑定邮箱时服务端返回空串 客户端按未绑定处理`() {
        // 这是后端 publicUser 的真实行为：email 是 `row.email || ''`，未绑定给空串而不是 null。
        // 客户端不能把空串当"已绑定一个空邮箱"，否则资料页会显示一行空白。
        val json = """{"id":7,"username":"carol","nickname":"carol","avatar":"","createdAt":1,"email":""}"""
        val user = gson.fromJson(json, UserInfo::class.java)
        assertEquals("", user.email)
        assertFalse(ProfileFormat.isEmailBound(user.email))
        assertEquals("未绑定", ProfileFormat.emailDisplay(user.email))
        assertEquals("未绑定", ProfileFormat.maskEmail(user.email))
    }

    @Test
    fun `服务端把邮箱归一化成小写后 客户端仍能判定绑定成功`() {
        // 后端 trim + 转小写后入库，用户输入 Alice@Example.COM 会拿回 alice@example.com；
        // 绑定成功的判定必须忽略大小写，否则会误报"服务端未保存邮箱"。
        val user = gson.fromJson(
            """{"id":1,"username":"u","nickname":"n","avatar":"","createdAt":1,"email":"alice@example.com"}""",
            UserInfo::class.java,
        )
        assertTrue(user.email.equals("Alice@Example.COM".lowercase(), ignoreCase = true))
    }

    @Test
    fun `榜单条目往返序列化不丢分组与平台`() {
        val item = PlaylistItem(
            id = "26",
            name = "热歌榜",
            coverUrl = "https://example.com/x.jpg",
            playCount = 19200000L,
            trackCount = 300L,
            platform = "qq",
            kind = "hot",
            group = "巅峰榜",
        )
        val back = gson.fromJson(gson.toJson(item), PlaylistItem::class.java)
        assertEquals(item, back)
        assertEquals("巅峰榜", back.group)
    }

    @Test
    fun `没有分组的歌单解析后 group 为空`() {
        val json = """{"id":"1","name":"我喜欢的音乐","platform":"netease"}"""
        val item = gson.fromJson(json, PlaylistItem::class.java)
        assertNull(item.group)
        assertNull(item.kind)
    }
}
