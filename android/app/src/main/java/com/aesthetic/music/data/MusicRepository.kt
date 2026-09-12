package com.aesthetic.music.data

import android.util.Log
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.AlbumDetailData
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.AuthData
import com.aesthetic.music.data.api.CategoriesData
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.DailyData
import com.aesthetic.music.data.api.LoginBody
import com.aesthetic.music.data.api.LyricData
import com.aesthetic.music.data.api.MusicApi
import com.aesthetic.music.data.api.NewSongsData
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.ProfileBody
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.RegisterBody
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.SyncPushResult
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.platform.PlatformMusicService
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.userapi.UserApiEngine
import kotlinx.coroutines.CancellationException

/**
 * 音乐数据仓库：
 * 1. 核心音乐能力（搜索、榜单、广场、电台、直链、歌词）：完全在客户端独立运行（直连平台公开协议 + 本地 QuickJS 音源脚本）；
 * 2. 账号与同步能力：连接用户私有后端，支持用户认证与多端增量 LWW 数据同步。
 */
class MusicRepository(
    private val api: MusicApi = ApiClient.api,
) {

    private companion object {
        const val TAG = "MusicRepository"
    }

    private suspend fun <T> apiCatching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        // 协程取消不是业务失败，必须原样抛出，否则会吞掉取消信号
        throw e
    } catch (e: Exception) {
        Result.failure(friendlyError(e))
    }

    // ==================== 音乐搜索（四平台直连） ====================
    /** 单曲搜索（多平台） */
    suspend fun search(keyword: String, limit: Int = 30, platform: String? = null): Result<List<Song>> =
        apiCatching { PlatformMusicService.search(keyword, limit, platform) }

    /** 热搜词（搜索页热门搜索：直连官方热搜） */
    suspend fun hotSearchWords(): Result<List<String>> = apiCatching {
        PlatformMusicService.hotSearchWords()
    }

    /** 歌单搜索（多平台：网易云 / 酷狗 / 酷我；QQ 暂不支持） */
    suspend fun searchPlaylists(keyword: String, limit: Int = 20, platform: String? = null): Result<List<PlaylistItem>> =
        apiCatching { PlatformMusicService.searchPlaylists(keyword, limit, platform) }

    /** 专辑搜索（多平台：网易云 / 酷狗 / 酷我；QQ 暂不支持） */
    suspend fun searchAlbums(keyword: String, limit: Int = 20, platform: String? = null): Result<List<AlbumItem>> =
        apiCatching { PlatformMusicService.searchAlbums(keyword, limit, platform) }



    /** 专辑详情 + 曲目列表 */
    suspend fun getAlbum(id: String): Result<AlbumDetailData> = apiCatching {
        PlatformMusicService.getAlbum(id)
    }

    /** 歌手搜索（多平台：网易云 / 酷狗 / 酷我；QQ 暂不支持） */
    suspend fun searchArtists(keyword: String, limit: Int = 12, platform: String? = null): Result<List<ArtistItem>> =
        apiCatching { PlatformMusicService.searchArtists(keyword, limit, platform) }


    suspend fun getArtist(platform: String?, id: String, limit: Int = 50): Result<ArtistDetailData> = apiCatching {
        PlatformMusicService.getArtist(platform, id, limit)
    }

    suspend fun getSongUrl(song: Song, quality: String = "lossless"): Result<SongUrlData> = apiCatching {
        // 1. 优先使用本地 QuickJS 音源脚本解析直链（完全在客户端沙箱独立执行）
        if (UserApiEngine.isAvailable()) {
            val scriptResult = UserApiEngine.resolveMusicUrl(song.platform, song, quality)
            if (scriptResult.isSuccess) {
                val resolvedUrl = scriptResult.getOrNull()
                if (!resolvedUrl.isNullOrBlank()) {
                    return@apiCatching SongUrlData(
                        url = resolvedUrl,
                        platform = song.platform,
                        sourceName = UserApiEngine.getScriptName()
                    )
                }
            }
        }

        // 2. 音源脚本未安装或未解析成功时，使用平台公开外链协议直连兜底
        try {
            val direct = PlatformMusicService.getDirectSongUrl(song, quality)
            if (!direct.url.isNullOrBlank()) {
                return@apiCatching direct
            }
        } catch (e: Exception) {
            // 平台直连兜底也失败。这里仍会抛"请导入音源脚本"，但记录原因才能区分
            // "平台接口变更"与"网络问题"——此前静默吞掉，线上只能看到一句通用提示。
            Log.w(TAG, "平台直连兜底失败 ${song.platform}:${song.id}", e)
        }

        throw Exception("未获取到播放直链，请在「设置」中导入音源脚本以获得高品质音源")
    }

    suspend fun getLyric(song: Song): Result<LyricData> = apiCatching {
        // 1. 优先使用本地音源脚本解析歌词
        if (UserApiEngine.isAvailable()) {
            val scriptResult = UserApiEngine.resolveLyric(song.platform, song)
            if (scriptResult.isSuccess) {
                val lrc = scriptResult.getOrNull()
                if (!lrc.isNullOrBlank()) {
                    return@apiCatching LyricData(
                        lyric = lrc,
                        platform = song.platform,
                        sourceName = UserApiEngine.getScriptName()
                    )
                }
            }
        }

        // 2. 直连公共平台歌词接口
        val directLyric = PlatformMusicService.getLyric(song)
        if (!directLyric.lyric.isNullOrBlank()) {
            return@apiCatching directLyric
        }

        LyricData(platform = song.platform)
    }

    suspend fun getPlaylist(id: String): Result<List<Song>> = apiCatching {
        PlatformMusicService.getPlaylist(id)
    }

    suspend fun getHotPlaylists(limit: Int = 20): Result<List<PlaylistItem>> = apiCatching {
        PlatformMusicService.getHotPlaylists(limit)
    }

    // ==================== 内容发现（直连公共平台协议） ====================

    suspend fun getDaily(limit: Int = 8): Result<DailyData> = apiCatching {
        PlatformMusicService.getDaily(limit)
    }

    suspend fun getNewSongs(limit: Int = 20): Result<NewSongsData> = apiCatching {
        PlatformMusicService.getNewSongs(limit)
    }

    suspend fun getRanks(): Result<RanksData> = apiCatching {
        PlatformMusicService.getRanks()
    }

    suspend fun getRankSongs(id: String): Result<List<Song>> = apiCatching {
        PlatformMusicService.getRankSongs(id)
    }

    suspend fun getCategories(): Result<CategoriesData> = apiCatching {
        PlatformMusicService.getCategories()
    }

    // ==================== 歌单广场 ====================

    /** 歌单广场标签（左侧抽屉） */
    fun sheetTags(): List<String> = PlatformMusicService.defaultSheetTags

    /** 歌单广场列表：最新/最热 + 标签过滤 */
    suspend fun sheetPlaylists(cat: String, order: String, limit: Int = 40): Result<List<PlaylistItem>> =
        apiCatching {
            PlatformMusicService.sheetPlaylists(cat, order, limit)
        }

    // ==================== FM 电台（免登录会话级推荐） ====================

    /** FM 风格种子池：推荐新歌或按标签拉精品歌单曲目 */
    suspend fun fmSeedSongs(tag: String, limit: Int = 120): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.fmSeedSongs(tag, limit)
        }

    /** 歌手热歌（FM 漂移：点赞/听完 → 歌手 ID） */
    suspend fun artistTopSongs(artistId: String, limit: Int = 50): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.artistTopSongs(artistId, limit)
        }

    /** 按歌手名/歌名搜索扩充（FM 漂移补充） */
    suspend fun searchSongs(query: String, limit: Int = 20): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.searchSongs(query, limit)
        }

    // ==================== 用户体系 (Auth) ====================

    suspend fun register(
        username: String,
        password: String,
        nickname: String = "",
        deviceName: String = "Android"
    ): Result<AuthData> = apiCatching {
        val resp = api.register(RegisterBody(username = username, password = password, nickname = nickname.ifBlank { null }, deviceName = deviceName))
        if (resp.code == 200) resp.data ?: throw Exception("注册返回为空") else throw Exception(resp.msg ?: "注册失败")
    }

    suspend fun login(
        username: String,
        password: String,
        deviceName: String = "Android"
    ): Result<AuthData> = apiCatching {
        val resp = api.login(LoginBody(username = username, password = password, deviceName = deviceName))
        if (resp.code == 200) resp.data ?: throw Exception("登录返回为空") else throw Exception(resp.msg ?: "登录失败")
    }

    suspend fun logout(): Result<Unit> = apiCatching {
        val resp = api.logout()
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "登出失败")
    }

    suspend fun logoutAll(): Result<Unit> = apiCatching {
        val resp = api.logoutAll()
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "登出失败")
    }

    suspend fun getMe(): Result<UserInfo> = apiCatching {
        val resp = api.me()
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "获取个人信息失败")
    }

    suspend fun updateProfile(nickname: String? = null, avatar: String? = null): Result<UserInfo> = apiCatching {
        val resp = api.updateProfile(ProfileBody(nickname = nickname, avatar = avatar))
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "更新资料失败")
    }

    // ==================== 私有云同步 (Sync LWW) ====================

    suspend fun testConnection(): Result<Boolean> = apiCatching {
        val token = SyncServerStore.getSyncToken().ifBlank { UserStore.token().orEmpty() }
        if (token.isNotBlank()) {
            val resp = api.me()
            if (resp.code == 200) return@apiCatching true
            throw Exception(resp.msg ?: "HTTP ${resp.code}")
        }
        val pullResp = api.syncPull(0, 0)
        if (pullResp.code == 200) true else throw Exception(pullResp.msg ?: "连接异常")
    }

    suspend fun syncPull(since: Long = 0L, eventSince: Long = 0L): Result<CloudSyncPullData> = apiCatching {
        val resp = api.syncPull(since = since, eventSince = eventSince)
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "数据拉取失败")
    }

    suspend fun syncPush(payload: SyncPushPayload): Result<SyncPushResult> = apiCatching {
        val resp = api.syncPush(payload)
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "同步上传失败")
    }
}
