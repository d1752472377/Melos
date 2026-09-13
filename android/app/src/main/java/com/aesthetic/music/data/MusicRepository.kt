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
import com.aesthetic.music.data.api.PlaylistDetailData
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.ProfileBody
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.RegisterBody
import com.aesthetic.music.data.api.SessionDto
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.SyncPushResult
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.platform.PlatformMusicService
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.source.SourceManager
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

    suspend fun getArtistAllSongs(platform: String?, id: String, maxCount: Int = 1000): Result<List<Song>> = apiCatching {
        PlatformMusicService.getArtistAllSongs(platform, id, maxCount)
    }

    /**
     * 解析播放直链。
     *
     * 音源选择（用哪个插件、失败要不要换、要不要按最快挑）统一交给 [SourceManager]：
     * 之前这里写死"有脚本就用脚本，没有就走内置直连"，只支持单一脚本，
     * 也就没有"换源"可言；现在按用户设置的三条策略在多个插件间挑选与兜底。
     */
    suspend fun getSongUrl(song: Song, quality: String = "lossless"): Result<SongUrlData> = apiCatching {
        SourceManager.resolveUrl(song, quality)
    }

    suspend fun getLyric(song: Song): Result<LyricData> = apiCatching {
        val lyric = SourceManager.resolveLyric(song)
        if (!lyric.lyric.isNullOrBlank()) lyric else LyricData(platform = song.platform)
    }

    /**
     * 歌单详情：歌单名 / 封面 / 曲目一次性抓回。
     *
     * 取代了原先只支持网易云的 `getPlaylist(id)`——收藏/导入进来的歌单可能来自任何平台，
     * 一律走这个按平台分发的入口，避免"打开是空白或串歌"。
     */
    suspend fun getPlaylistDetail(platform: String?, id: String): Result<PlaylistDetailData> = apiCatching {
        PlatformMusicService.getPlaylistDetail(platform, id)
    }

    /** 展开跳转链接（分享用的短链）：失败时返回原 URL */
    suspend fun resolveFinalUrl(url: String): Result<String> = apiCatching {
        PlatformMusicService.resolveFinalUrl(url)
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

    /** 榜单列表：平台由 [RankCatalog] 定义（网易云 / QQ / 酷狗 / 酷我），默认网易云 */
    suspend fun getRanks(platform: String = RankCatalog.DEFAULT): Result<RanksData> = apiCatching {
        PlatformMusicService.getRanks(platform)
    }

    /** 榜单曲目：榜单 id 必须与平台配套使用（各平台 id 语义不同，混用会查到别的榜单或空） */
    suspend fun getRankSongs(platform: String, id: String): Result<List<Song>> = apiCatching {
        PlatformMusicService.getRankSongs(platform, id)
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

    /** 当前账号的登录设备列表（服务端按会话记录，用于"退出其它设备"） */
    suspend fun getSessions(): Result<List<SessionDto>> = apiCatching {
        val resp = api.sessions()
        if (resp.code == 200) resp.data ?: emptyList() else throw Exception(resp.msg ?: "获取设备列表失败")
    }

    /** 撤销指定会话（把那台设备踢下线） */
    suspend fun deleteSession(sessionId: String): Result<Unit> = apiCatching {
        val resp = api.deleteSession(sessionId)
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "撤销会话失败")
    }

    /**
     * 更新资料。字段为 null = 不改动该项（后端按「缺省即不改」处理）。
     *
     * [email] 传空串表示解绑；服务端若尚未支持该字段会被忽略，
     * 因此调用方必须以**响应体**为准刷新界面，不能假设本地输入已经生效。
     */
    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        email: String? = null,
    ): Result<UserInfo> = apiCatching {
        val resp = api.updateProfile(ProfileBody(nickname = nickname, avatar = avatar, email = email))
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "更新资料失败")
    }

    // ==================== 云端同步 ====================

    /**
     * 服务器可达性探测（不需要登录）。
     *
     * 以前这里靠 `/api/me` 或 `/api/sync/pull` 判断，未登录时必然拿 401，
     * 于是"地址填错"和"地址对但没登录"在界面上是同一句"连接失败"。
     * 现在改走无需鉴权的 `/api/health`，语义唯一：地址是否指向一个活着的 Melos 后端。
     */
    suspend fun testConnection(): Result<Boolean> = apiCatching {
        val resp = api.health()
        if (resp.code == 200) true else throw Exception(resp.msg ?: "HTTP ${resp.code}")
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
