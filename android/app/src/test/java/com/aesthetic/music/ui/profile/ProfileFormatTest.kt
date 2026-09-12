package com.aesthetic.music.ui.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 个人资料展示规则单测。
 *
 * 这些规则此前散在 Compose 代码里（日期拼接、邮箱判断、昵称兜底），只能靠肉眼看界面验证；
 * 抽到 [ProfileFormat] 之后每条规则都能被钉住，避免以后改界面时把"未知日期显示成 1970 年"
 * 或"未绑定邮箱显示成 null"这类问题重新引入。
 */
class ProfileFormatTest {

    /** 用本地日历构造时间戳，测试不受运行时区影响 */
    private fun ts(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            set(year, month - 1, day, hour, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `注册日期按年月日格式输出`() {
        assertEquals("2023年5月6日", ProfileFormat.registerDate(ts(2023, 5, 6)))
        // 用已过去的日期：未来时间戳会被按"当前时间"处理（见下一个用例）
        assertEquals("2019年12月31日", ProfileFormat.registerDate(ts(2019, 12, 31)))
    }

    @Test
    fun `注册时间缺失或非法返回 null 而不是 1970`() {
        // 老服务端/未登录时 createdAt 为 0：界面显示"未知"，绝不能显示 1970年1月1日
        assertNull(ProfileFormat.registerDate(0L))
        assertNull(ProfileFormat.registerDate(-1L))
        assertNull(ProfileFormat.registerDays(0L))
    }

    @Test
    fun `注册时间在未来时按当前时间处理`() {
        // 服务端时钟快于客户端时不能出现"注册于 2027 年"
        val now = ts(2026, 9, 13)
        assertEquals("2026年9月13日", ProfileFormat.registerDate(ts(2030, 1, 1), now))
    }

    @Test
    fun `注册天数与加入摘要`() {
        val now = ts(2026, 9, 13)
        // 当天注册算 1 天（不是 0 天）
        assertEquals(1L, ProfileFormat.registerDays(now, now))
        assertEquals(2L, ProfileFormat.registerDays(now - 86_400_000L, now))
        assertEquals("已加入 1 天", ProfileFormat.joinedSummary(now, now))
        assertNull(ProfileFormat.joinedSummary(0L, now))
    }

    @Test
    fun `合法邮箱通过校验`() {
        listOf(
            "a@b.co",
            "user.name@example.com",
            "user+tag@sub.domain.com.cn",
            "13800138000@qq.com",
            "  spaced@example.com  ",
        ).forEach { assertTrue("应通过: $it", ProfileFormat.isValidEmail(it)) }
    }

    @Test
    fun `明显不是邮箱的输入被拒绝`() {
        listOf(
            "",
            "abc",
            "abc@",
            "@abc.com",
            "a@@b.com",
            "a b@c.com",
            "a@b",
            "a@b.c",
            "a@b..com",
            "a@.com",
            "a@com.",
            "a@b.c0m",
            "a@" + "b".repeat(130) + ".com",
        ).forEach { assertFalse("应拒绝: $it", ProfileFormat.isValidEmail(it)) }
    }

    @Test
    fun `邮箱展示与脱敏`() {
        assertFalse(ProfileFormat.isEmailBound(null))
        assertFalse(ProfileFormat.isEmailBound("   "))
        assertTrue(ProfileFormat.isEmailBound("a@b.co"))

        // 未绑定统一文案，界面不会出现 null / 空串
        assertEquals("未绑定", ProfileFormat.emailDisplay(null))
        assertEquals("未绑定", ProfileFormat.emailDisplay("  "))
        assertEquals("a@b.co", ProfileFormat.emailDisplay(" a@b.co "))

        // 卡片这类一眼可见的位置用脱敏形式，避免完整地址被旁人看到
        assertEquals("ab***@qq.com", ProfileFormat.maskEmail("abcd@qq.com"))
        assertEquals("未绑定", ProfileFormat.maskEmail(null))
        assertEquals("未绑定", ProfileFormat.maskEmail("没有at符"))
    }

    @Test
    fun `显示名回落顺序为昵称到用户名再到兜底`() {
        assertEquals("小美", ProfileFormat.displayName("小美", "melos_user"))
        assertEquals("melos_user", ProfileFormat.displayName("", "melos_user"))
        assertEquals("melos_user", ProfileFormat.displayName("   ", "melos_user"))
        assertEquals("Melos 音乐人", ProfileFormat.displayName(null, null))
        // 与 UserStore.getNickname() 的兜底文案保持一致，卡片与资料页显示同一个名字
        assertEquals("Melos 音乐人", ProfileFormat.displayName("", ""))
    }

    @Test
    fun `用户名与简介的展示兜底`() {
        assertEquals("melos_user", ProfileFormat.usernameDisplay("melos_user"))
        assertEquals("未登录", ProfileFormat.usernameDisplay(null))
        assertEquals("未登录", ProfileFormat.usernameDisplay("  "))

        assertEquals("爱听老歌", ProfileFormat.bioDisplay("爱听老歌"))
        assertEquals("还没有填写个性签名", ProfileFormat.bioDisplay(""))
        assertEquals("还没有填写个性签名", ProfileFormat.bioDisplay(null))
    }
}
