package com.aesthetic.music.data

import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.AlbumDetailData
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.AuthData
import com.aesthetic.music.data.api.CategoriesData
import com.aesthetic.music.data.api.DailyData
import com.aesthetic.music.data.api.LoginBody
import com.aesthetic.music.data.api.LyricData
import com.aesthetic.music.data.api.MusicApi
import com.aesthetic.music.data.api.NewSongsData
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.PlaylistMetaBody
import com.aesthetic.music.data.api.ProfileBody
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.RegisterBody
import com.aesthetic.music.data.api.SongsBody
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.api.StatsReportBody
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.UserPlaylistDto
import com.aesthetic.music.data.api.UserStatsDto
import com.aesthetic.music.data.model.Song
import kotlinx.coroutines.CancellationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 音乐数据仓库：封装后端 API 调用，统一返回 Result。
 *
 * 错误处理约定（apiCatching）：
 * - CancellationException 一律原样重抛：协程取消不是业务失败，
 *   吞掉它会破坏结构化并发（表现为"已取消的请求把错误状态写回 UI"）；
 * - IOException 族（断网/超时/DNS 失败）翻译成统一的友好中文提示，
 *   不再把 UnknownHostException 之类的原始英文信息直出给用户；
 * - 其余异常原样进 Result.failure，服务端业务 msg 由调用方透传。
 */
class MusicRepository(
    private val api: MusicApi = ApiClient.api,
) {

    private suspend fun <T> apiCatching(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(friendly(e))
    }

    private fun friendly(e: Exception): Exception {
        val networkError = e is IOException || e is ConnectException ||
            e is SocketTimeoutException || e is UnknownHostException ||
            e.message?.contains("Unable to resolve host") == true
        return if (networkError) Exception("网络连接失败，请检查网络后重试") else e
    }

    // ==================== 音乐 ====================

    suspend fun search(keyword: String, limit: Int = 30, platform: String? = null): Result<List<Song>> = apiCatching {
        val resp = api.search(keyword, limit, platform)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "搜索失败")
    }

    /** 热搜词（搜索页热门搜索；后端无 netease 源时返回空列表，调用方用本地兜底） */
    suspend fun hotSearchWords(): Result<List<String>> = apiCatching {
        val resp = api.hotSearchWords()
        if (resp.code == 200) resp.data.orEmpty().map { it.word }.filter { it.isNotBlank() }
        else throw Exception(resp.msg ?: "获取热搜失败")
    }

    /** 歌单搜索（netease 官方接口） */
    suspend fun searchPlaylists(keyword: String, limit: Int = 20): Result<List<PlaylistItem>> = apiCatching {
        val resp = api.searchPlaylists(keyword, limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "歌单搜索失败")
    }

    /** 专辑搜索（netease 官方接口） */
    suspend fun searchAlbums(keyword: String, limit: Int = 20): Result<List<AlbumItem>> = apiCatching {
        val resp = api.searchAlbums(keyword, limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "专辑搜索失败")
    }

    /** 歌词关键词搜歌（netease type=1006） */
    suspend fun searchByLyric(keyword: String, limit: Int = 20): Result<List<Song>> = apiCatching {
        val resp = api.searchByLyric(keyword, limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "歌词搜索失败")
    }

    /** 专辑详情 + 曲目列表 */
    suspend fun getAlbum(id: String): Result<AlbumDetailData> = apiCatching {
        val resp = api.albumDetail(id)
        if (resp.code == 200) resp.data ?: throw Exception("获取专辑失败") else throw Exception(resp.msg ?: "获取专辑失败")
    }

    /** 歌手搜索（后端无支持音源时返回失败，调用方静默降级即可） */
    suspend fun searchArtists(keyword: String, limit: Int = 8, platform: String? = null): Result<List<ArtistItem>> = apiCatching {
        val resp = api.artistSearch(keyword, limit, platform)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "歌手搜索失败")
    }

    suspend fun getArtist(platform: String?, id: String, limit: Int = 50): Result<ArtistDetailData> = apiCatching {
        val resp = api.artistDetail(platform, id, limit)
        if (resp.code == 200) resp.data ?: throw Exception("获取歌手失败") else throw Exception(resp.msg ?: "获取歌手失败")
    }

    suspend fun getSongUrl(song: Song, quality: String = "lossless"): Result<SongUrlData> = apiCatching {
        val resp = api.songUrl(song.platform, song.id, song.title, quality)
        if (resp.code == 200) {
            val d = resp.data
            if (d?.url.isNullOrBlank()) throw Exception("未获取到播放地址")
            d
        } else {
            throw Exception(resp.msg ?: "取直链失败")
        }
    }

    suspend fun getLyric(song: Song): Result<LyricData> = apiCatching {
        val resp = api.lyric(song.platform, song.id, song.title)
        if (resp.code == 200) resp.data ?: LyricData() else throw Exception(resp.msg ?: "取歌词失败")
    }

    suspend fun getPlaylist(id: String): Result<List<Song>> = apiCatching {
        val resp = api.playlistDetail(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取歌单失败")
    }

    suspend fun getHotPlaylists(limit: Int = 20): Result<List<PlaylistItem>> = apiCatching {
        val resp = api.hotPlaylists(limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取热门歌单失败")
    }

    // ==================== 内容 ====================

    suspend fun getDaily(limit: Int = 8): Result<DailyData> = apiCatching {
        val resp = api.daily(limit)
        if (resp.code == 200) resp.data ?: DailyData() else throw Exception(resp.msg ?: "获取每日推荐失败")
    }

    suspend fun getNewSongs(limit: Int = 20): Result<NewSongsData> = apiCatching {
        val resp = api.newSongs(limit)
        if (resp.code == 200) resp.data ?: NewSongsData() else throw Exception(resp.msg ?: "获取新歌速递失败")
    }

    suspend fun getRanks(): Result<RanksData> = apiCatching {
        val resp = api.ranks()
        if (resp.code == 200) resp.data ?: RanksData() else throw Exception(resp.msg ?: "获取排行榜失败")
    }

    suspend fun getRankSongs(id: String): Result<List<Song>> = apiCatching {
        val resp = api.rankDetail(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取榜单歌曲失败")
    }

    suspend fun getCategories(): Result<CategoriesData> = apiCatching {
        val resp = api.categories()
        if (resp.code == 200) resp.data ?: CategoriesData() else throw Exception(resp.msg ?: "获取分类歌单失败")
    }

    // ==================== 用户 ====================

    suspend fun register(username: String, password: String, nickname: String): Result<AuthData> = apiCatching {
        val resp = api.register(RegisterBody(username = username, password = password, nickname = nickname))
        if (resp.code == 200) resp.data ?: throw Exception("注册返回为空") else throw Exception(resp.msg ?: "注册失败")
    }

    suspend fun login(username: String, password: String): Result<AuthData> = apiCatching {
        val resp = api.login(LoginBody(username = username, password = password))
        if (resp.code == 200) resp.data ?: throw Exception("登录返回为空") else throw Exception(resp.msg ?: "登录失败")
    }

    suspend fun updateProfile(nickname: String, avatar: String = ""): Result<UserInfo> = apiCatching {
        val resp = api.updateProfile(ProfileBody(nickname = nickname, avatar = avatar))
        if (resp.code == 200) resp.data ?: throw Exception("更新失败") else throw Exception(resp.msg ?: "更新失败")
    }

    suspend fun cloudFavorites(): Result<List<Song>> = apiCatching {
        val resp = api.cloudFavorites()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushFavorites(songs: List<Song>): Result<Unit> = apiCatching {
        val resp = api.putFavorites(SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun cloudHistory(): Result<List<Song>> = apiCatching {
        val resp = api.cloudHistory()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushHistory(songs: List<Song>): Result<Unit> = apiCatching {
        val resp = api.putHistory(SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun cloudPlaylists(): Result<List<UserPlaylistDto>> = apiCatching {
        val resp = api.cloudPlaylists()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun createCloudPlaylist(name: String, coverUrl: String?): Result<UserPlaylistDto> = apiCatching {
        val resp = api.createPlaylist(PlaylistMetaBody(name = name, coverUrl = coverUrl))
        if (resp.code == 200) resp.data ?: throw Exception("创建失败") else throw Exception(resp.msg ?: "创建失败")
    }

    suspend fun renameCloudPlaylist(id: String, name: String): Result<UserPlaylistDto> = apiCatching {
        val resp = api.updatePlaylist(id, PlaylistMetaBody(name = name))
        if (resp.code == 200) resp.data ?: throw Exception("重命名失败") else throw Exception(resp.msg ?: "重命名失败")
    }

    suspend fun deleteCloudPlaylist(id: String): Result<Unit> = apiCatching {
        val resp = api.deletePlaylist(id)
        if (resp.code != 200) throw Exception(resp.msg ?: "删除失败")
    }

    suspend fun cloudPlaylistSongs(id: String): Result<List<Song>> = apiCatching {
        val resp = api.cloudPlaylistSongs(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushPlaylistSongs(id: String, songs: List<Song>): Result<Unit> = apiCatching {
        val resp = api.putPlaylistSongs(id, SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun addCloudPlaylistSongs(id: String, songs: List<Song>): Result<Unit> = apiCatching {
        val resp = api.addPlaylistSongs(id, SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun reportStats(events: List<PlayEventDto>): Result<Unit> = apiCatching {
        val resp = api.reportStats(StatsReportBody(events = events))
        if (resp.code != 200) throw Exception(resp.msg ?: "上报失败")
    }

    suspend fun userStats(): Result<UserStatsDto> = apiCatching {
        val resp = api.userStats()
        if (resp.code == 200) resp.data ?: UserStatsDto() else throw Exception(resp.msg ?: "获取统计失败")
    }
}
