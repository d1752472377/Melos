package com.aesthetic.music.ui.settings

import com.aesthetic.music.data.settings.DownloadFailurePolicy
import com.aesthetic.music.data.settings.DownloadNaming
import com.aesthetic.music.data.settings.MediaCardButtonPosition
import com.aesthetic.music.data.settings.MediaCardLyricPosition
import com.aesthetic.music.data.settings.StatusBarMode
import com.aesthetic.music.data.settings.UserPrefs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 设置项控制器的行为测试。
 *
 * 守两条容易写错、又不容易在界面上发现的规则：
 * 1. 改一项设置不能把其它项改掉（copy 更新必须保留旧值）；
 * 2. 设置变更必须同步触发 onApplied——播放器与通知卡片依赖它即时生效，
 *    漏掉的话表现是"改了设置要重启 App 才生效"，很难归因。
 *
 * 本测试跑在 JVM 上：SettingsStore 在无 SharedPreferences 时读默认值、写入降级为 no-op，
 * 因此这里断言的是控制器自身的状态流转，不涉及真实落盘。
 */
class SettingsControllerTest {

    @Test
    fun `修改设置会同步更新内存态并通知应用层`() {
        val applied = mutableListOf<UserPrefs>()
        val controller = SettingsController { prefs -> applied.add(prefs) }

        controller.setStatusBarMode(StatusBarMode.HIDDEN)

        assertEquals(StatusBarMode.HIDDEN, controller.prefs.value.statusBarMode)
        assertEquals(1, applied.size)
        assertEquals(StatusBarMode.HIDDEN, applied.first().statusBarMode)
    }

    @Test
    fun `设置相同值时不重复通知`() {
        val applied = mutableListOf<UserPrefs>()
        val controller = SettingsController { prefs -> applied.add(prefs) }
        val current = controller.prefs.value.mediaCardFavorite

        controller.setMediaCardFavorite(current)

        assertTrue("值未变化就不应该触发应用层回调", applied.isEmpty())
        assertEquals(current, controller.prefs.value.mediaCardFavorite)
    }

    @Test
    fun `改动一项不会影响其它设置`() {
        val controller = SettingsController()

        controller.setDownloadWifiOnly(true)
        controller.setMediaCardButtonPosition(MediaCardButtonPosition.LEFT)
        controller.setLyricTranslation(false)

        val prefs = controller.prefs.value
        assertEquals(true, prefs.downloadWifiOnly)
        assertEquals(MediaCardButtonPosition.LEFT, prefs.mediaCardButtonPosition)
        assertEquals(false, prefs.lyricTranslation)
        // 同一次会话里其它字段保持默认，没有被连带改掉
        assertEquals(UserPrefs().preloadNext, prefs.preloadNext)
        assertEquals(UserPrefs().downloadNaming, prefs.downloadNaming)
        assertEquals(UserPrefs().mediaCardLyricPosition, prefs.mediaCardLyricPosition)
    }

    @Test
    fun `每一项都有对应的写入口`() {
        val controller = SettingsController()

        controller.setPreloadNext(false)
        controller.setCrossFade(true)
        controller.setResumeAfterInterrupt(false)
        controller.setMixWithOthers(true)
        controller.setLyricRomanization(true)
        controller.setLyricOnline(false)
        controller.setLyricLocal(true)
        controller.setMediaCardShowLyric(false)
        controller.setMediaCardLyricPosition(MediaCardLyricPosition.ABOVE)
        controller.setDownloadWhilePlaying(false)
        controller.setDownloadCoverLyrics(false)
        controller.setDownloadNaming(DownloadNaming.ARTIST_TITLE)
        controller.setDownloadFailurePolicy(DownloadFailurePolicy.NOTIFY)

        val p = controller.prefs.value
        assertEquals(false, p.preloadNext)
        assertEquals(true, p.crossFade)
        assertEquals(false, p.resumeAfterInterrupt)
        assertEquals(true, p.mixWithOthers)
        assertEquals(true, p.lyricRomanization)
        assertEquals(false, p.lyricOnline)
        assertEquals(true, p.lyricLocal)
        assertEquals(false, p.mediaCardShowLyric)
        assertEquals(MediaCardLyricPosition.ABOVE, p.mediaCardLyricPosition)
        assertEquals(false, p.downloadWhilePlaying)
        assertEquals(false, p.downloadCoverLyrics)
        assertEquals(DownloadNaming.ARTIST_TITLE, p.downloadNaming)
        assertEquals(DownloadFailurePolicy.NOTIFY, p.downloadFailurePolicy)
    }

    @Test
    fun `枚举取值可以还原且未知值回落默认`() {
        // 落盘存的是 value 字符串，升级/脏数据时必须能安全还原
        StatusBarMode.entries.forEach { mode ->
            assertEquals(mode, StatusBarMode.fromValue(mode.value))
        }
        DownloadNaming.entries.forEach { naming ->
            assertEquals(naming, DownloadNaming.fromValue(naming.value))
        }
        MediaCardButtonPosition.entries.forEach { pos ->
            assertEquals(pos, MediaCardButtonPosition.fromValue(pos.value))
        }
        MediaCardLyricPosition.entries.forEach { pos ->
            assertEquals(pos, MediaCardLyricPosition.fromValue(pos.value))
        }
        DownloadFailurePolicy.entries.forEach { policy ->
            assertEquals(policy, DownloadFailurePolicy.fromValue(policy.value))
        }

        // 未知/空值回落到默认项，而不是抛异常或返回 null
        assertEquals(StatusBarMode.ALWAYS, StatusBarMode.fromValue("未来版本的状态栏模式"))
        assertEquals(StatusBarMode.ALWAYS, StatusBarMode.fromValue(null))
        assertEquals(DownloadNaming.TITLE_ARTIST, DownloadNaming.fromValue("不存在"))
        assertEquals(MediaCardButtonPosition.RIGHT, MediaCardButtonPosition.fromValue(""))
        assertEquals(MediaCardLyricPosition.BELOW, MediaCardLyricPosition.fromValue(null))
        assertEquals(DownloadFailurePolicy.RETRY, DownloadFailurePolicy.fromValue("unknown"))
    }

    @Test
    fun `默认值与设置页文案一致`() {
        val d = UserPrefs()
        // 歌词：默认开翻译、在线取词，不开本地（没有下载过的用户本地也取不到）
        assertTrue(d.lyricTranslation)
        assertTrue(d.lyricOnline)
        assertEquals(false, d.lyricLocal)
        // 媒体卡片：默认带收藏按钮、显示歌词，位置在右 / 歌名下方
        assertTrue(d.mediaCardFavorite)
        assertEquals(MediaCardButtonPosition.RIGHT, d.mediaCardButtonPosition)
        assertTrue(d.mediaCardShowLyric)
        assertEquals(MediaCardLyricPosition.BELOW, d.mediaCardLyricPosition)
        // 播放与下载：预加载/边听边下默认开，仅 WiFi 默认关（默认不省流量，由用户显式开启）
        assertTrue(d.preloadNext)
        assertTrue(d.downloadWhilePlaying)
        assertEquals(false, d.downloadWifiOnly)
        assertEquals(DownloadNaming.TITLE_ARTIST, d.downloadNaming)
        assertNotNull(d.statusBarMode)
    }
}
