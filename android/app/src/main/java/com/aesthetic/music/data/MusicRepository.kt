package com.aesthetic.music.data

import android.util.Log
import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.AlbumDetailData
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.AppVersionDto
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.AuthData
import com.aesthetic.music.data.api.BindEmailBody
import com.aesthetic.music.data.api.CategoriesData
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.DailyData
import com.aesthetic.music.data.api.EmailLoginBody
import com.aesthetic.music.data.api.LoginBody
import com.aesthetic.music.data.api.LyricData
import com.aesthetic.music.data.api.MusicApi
import com.aesthetic.music.data.api.SendCodeBody
import com.aesthetic.music.data.api.NewSongsData
import com.aesthetic.music.data.api.PasswordBody
import com.aesthetic.music.data.api.PlaylistDetailData
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.ProfileBody
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.RegisterBody
import com.aesthetic.music.data.api.SessionDto
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.api.SyncPushResult
import com.aesthetic.music.data.api.UnbindEmailBody
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.platform.PlatformMusicService
import com.aesthetic.music.data.api.platform.RankCatalog
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.source.SourceManager
import kotlinx.coroutines.CancellationException

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

    suspend fun search(keyword: String, limit: Int = 30, platform: String? = null): Result<List<Song>> =
        apiCatching { PlatformMusicService.search(keyword, limit, platform) }

    suspend fun searchPlaylists(keyword: String, limit: Int = 20, platform: String? = null): Result<List<PlaylistItem>> =
        apiCatching { PlatformMusicService.searchPlaylists(keyword, limit, platform) }

    suspend fun searchAlbums(keyword: String, limit: Int = 20, platform: String? = null): Result<List<AlbumItem>> =
        apiCatching { PlatformMusicService.searchAlbums(keyword, limit, platform) }

    suspend fun getAlbum(id: String): Result<AlbumDetailData> = apiCatching {
        PlatformMusicService.getAlbum(id)
    }

    suspend fun searchArtists(keyword: String, limit: Int = 12, platform: String? = null): Result<List<ArtistItem>> =
        apiCatching { PlatformMusicService.searchArtists(keyword, limit, platform) }

    suspend fun getArtist(platform: String?, id: String, limit: Int = 50): Result<ArtistDetailData> = apiCatching {
        PlatformMusicService.getArtist(platform, id, limit)
    }

    suspend fun getArtistAllSongs(platform: String?, id: String, maxCount: Int = 1000): Result<List<Song>> = apiCatching {
        PlatformMusicService.getArtistAllSongs(platform, id, maxCount)
    }

    suspend fun getSongUrl(song: Song, quality: String = "lossless"): Result<SongUrlData> = apiCatching {
        SourceManager.resolveUrl(song, quality)
    }

    suspend fun getLyric(song: Song): Result<LyricData> = apiCatching {
        val lyric = SourceManager.resolveLyric(song)
        if (!lyric.lyric.isNullOrBlank()) lyric else LyricData(platform = song.platform)
    }

    suspend fun getPlaylistDetail(platform: String?, id: String): Result<PlaylistDetailData> = apiCatching {
        PlatformMusicService.getPlaylistDetail(platform, id)
    }

    suspend fun resolveFinalUrl(url: String): Result<String> = apiCatching {
        PlatformMusicService.resolveFinalUrl(url)
    }

    suspend fun getHotPlaylists(limit: Int = 20): Result<List<PlaylistItem>> = apiCatching {
        PlatformMusicService.getHotPlaylists(limit)
    }

    suspend fun getDaily(limit: Int = 8): Result<DailyData> = apiCatching {
        PlatformMusicService.getDaily(limit)
    }

    suspend fun getNewSongs(limit: Int = 20): Result<NewSongsData> = apiCatching {
        PlatformMusicService.getNewSongs(limit)
    }

    suspend fun getNewSongsByArea(areaId: Int, limit: Int = 20): Result<List<Song>> = apiCatching {
        PlatformMusicService.newSongsByArea(areaId, limit)
    }

    suspend fun getSimilarSongs(songId: String, limit: Int = 20): Result<List<Song>> = apiCatching {
        PlatformMusicService.similarSongs(songId, limit)
    }

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

    fun sheetTags(): List<String> = PlatformMusicService.defaultSheetTags

    suspend fun sheetPlaylists(cat: String, order: String, limit: Int = 40): Result<List<PlaylistItem>> =
        apiCatching {
            PlatformMusicService.sheetPlaylists(cat, order, limit)
        }

    suspend fun fmSeedSongs(tag: String, limit: Int = 120): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.fmSeedSongs(tag, limit)
        }

    suspend fun artistTopSongs(artistId: String, limit: Int = 50): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.artistTopSongs(artistId, limit)
        }

    suspend fun searchSongs(query: String, limit: Int = 20): Result<List<Song>> =
        apiCatching {
            PlatformMusicService.searchSongs(query, limit)
        }

    suspend fun register(
        username: String,
        password: String,
        email: String,
        code: String,
        nickname: String = "",
        deviceName: String = "Android"
    ): Result<AuthData> = apiCatching {
        val resp = api.register(RegisterBody(
            username = username,
            password = password,
            email = email,
            code = code,
            nickname = nickname.ifBlank { null },
            deviceName = deviceName
        ))
        if (resp.code == 200) resp.data ?: throw Exception("注册返回为空") else throw Exception(resp.msg ?: "注册失败")
    }

    suspend fun bindEmail(email: String, code: String): Result<UserInfo> = apiCatching {
        val resp = api.bindEmail(BindEmailBody(email = email, code = code))
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "绑定邮箱失败")
    }

    suspend fun unbindEmail(code: String): Result<UserInfo> = apiCatching {
        val resp = api.unbindEmail(UnbindEmailBody(code = code))
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "解绑邮箱失败")
    }

    suspend fun updatePassword(oldPassword: String, newPassword: String): Result<Unit> = apiCatching {
        val resp = api.updatePassword(PasswordBody(oldPassword = oldPassword, newPassword = newPassword))
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "修改密码失败")
    }

    suspend fun login(
        username: String,
        password: String,
        deviceName: String = "Android"
    ): Result<AuthData> = apiCatching {
        val resp = api.login(LoginBody(username = username, password = password, deviceName = deviceName))
        if (resp.code == 200) resp.data ?: throw Exception("登录返回为空") else throw Exception(resp.msg ?: "登录失败")
    }

    suspend fun sendCode(email: String, scene: String = "login"): Result<Unit> = apiCatching {
        val resp = api.sendCode(SendCodeBody(email = email, scene = scene))
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "发送验证码失败")
    }

    suspend fun emailLogin(
        email: String,
        code: String,
        deviceName: String = "Android"
    ): Result<AuthData> = apiCatching {
        val resp = api.emailLogin(EmailLoginBody(email = email, code = code, deviceName = deviceName))
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

    suspend fun getSessions(): Result<List<SessionDto>> = apiCatching {
        val resp = api.sessions()
        if (resp.code == 200) resp.data ?: emptyList() else throw Exception(resp.msg ?: "获取设备列表失败")
    }

    suspend fun deleteSession(sessionId: String): Result<Unit> = apiCatching {
        val resp = api.deleteSession(sessionId)
        if (resp.code == 200) Unit else throw Exception(resp.msg ?: "撤销会话失败")
    }

    suspend fun updateProfile(
        nickname: String? = null,
        avatar: String? = null,
        background: String? = null,
        email: String? = null,
    ): Result<UserInfo> = apiCatching {
        val resp = api.updateProfile(ProfileBody(nickname = nickname, avatar = avatar, background = background, email = email))
        if (resp.code == 200 && resp.data != null) resp.data else throw Exception(resp.msg ?: "更新资料失败")
    }

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

    suspend fun getLatestVersion(): Result<AppVersionDto?> = apiCatching {
        val resp = api.latestVersion()
        if (resp.code == 200) resp.data else throw Exception(resp.msg ?: "检查更新失败")
    }
}
