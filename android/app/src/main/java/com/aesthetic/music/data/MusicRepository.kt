package com.aesthetic.music.data

import com.aesthetic.music.data.api.ApiClient
import com.aesthetic.music.data.api.AuthData
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

/**
 * 音乐数据仓库：封装后端 API 调用，统一返回 Result。
 */
class MusicRepository(
    private val api: MusicApi = ApiClient.api,
) {

    // ==================== 音乐 ====================

    suspend fun search(keyword: String, limit: Int = 20): Result<List<Song>> = runCatching {
        val resp = api.search(keyword, limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "搜索失败")
    }

    suspend fun getSongUrl(song: Song, quality: String = "lossless"): Result<SongUrlData> = runCatching {
        val resp = api.songUrl(song.platform, song.id, song.title, quality)
        if (resp.code == 200) {
            val d = resp.data
            if (d?.url.isNullOrBlank()) throw Exception("未获取到播放地址")
            d
        } else {
            throw Exception(resp.msg ?: "取直链失败")
        }
    }

    suspend fun getLyric(song: Song): Result<LyricData> = runCatching {
        val resp = api.lyric(song.platform, song.id, song.title)
        if (resp.code == 200) resp.data ?: LyricData() else throw Exception(resp.msg ?: "取歌词失败")
    }

    suspend fun getPlaylist(id: String): Result<List<Song>> = runCatching {
        val resp = api.playlistDetail(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取歌单失败")
    }

    suspend fun getHotPlaylists(limit: Int = 20): Result<List<PlaylistItem>> = runCatching {
        val resp = api.hotPlaylists(limit)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取热门歌单失败")
    }

    // ==================== 内容 ====================

    suspend fun getDaily(limit: Int = 8): Result<DailyData> = runCatching {
        val resp = api.daily(limit)
        if (resp.code == 200) resp.data ?: DailyData() else throw Exception(resp.msg ?: "获取每日推荐失败")
    }

    suspend fun getNewSongs(limit: Int = 20): Result<NewSongsData> = runCatching {
        val resp = api.newSongs(limit)
        if (resp.code == 200) resp.data ?: NewSongsData() else throw Exception(resp.msg ?: "获取新歌速递失败")
    }

    suspend fun getRanks(): Result<RanksData> = runCatching {
        val resp = api.ranks()
        if (resp.code == 200) resp.data ?: RanksData() else throw Exception(resp.msg ?: "获取排行榜失败")
    }

    suspend fun getRankSongs(id: String): Result<List<Song>> = runCatching {
        val resp = api.rankDetail(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "获取榜单歌曲失败")
    }

    // ==================== 用户 ====================

    suspend fun register(username: String, password: String, nickname: String): Result<AuthData> = runCatching {
        val resp = api.register(RegisterBody(username = username, password = password, nickname = nickname))
        if (resp.code == 200) resp.data ?: throw Exception("注册返回为空") else throw Exception(resp.msg ?: "注册失败")
    }

    suspend fun login(username: String, password: String): Result<AuthData> = runCatching {
        val resp = api.login(LoginBody(username = username, password = password))
        if (resp.code == 200) resp.data ?: throw Exception("登录返回为空") else throw Exception(resp.msg ?: "登录失败")
    }

    suspend fun updateProfile(nickname: String, avatar: String = ""): Result<UserInfo> = runCatching {
        val resp = api.updateProfile(ProfileBody(nickname = nickname, avatar = avatar))
        if (resp.code == 200) resp.data ?: throw Exception("更新失败") else throw Exception(resp.msg ?: "更新失败")
    }

    suspend fun cloudFavorites(): Result<List<Song>> = runCatching {
        val resp = api.cloudFavorites()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushFavorites(songs: List<Song>): Result<Unit> = runCatching {
        val resp = api.putFavorites(SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun cloudHistory(): Result<List<Song>> = runCatching {
        val resp = api.cloudHistory()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushHistory(songs: List<Song>): Result<Unit> = runCatching {
        val resp = api.putHistory(SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun cloudPlaylists(): Result<List<UserPlaylistDto>> = runCatching {
        val resp = api.cloudPlaylists()
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun createCloudPlaylist(name: String, coverUrl: String?): Result<UserPlaylistDto> = runCatching {
        val resp = api.createPlaylist(PlaylistMetaBody(name = name, coverUrl = coverUrl))
        if (resp.code == 200) resp.data ?: throw Exception("创建失败") else throw Exception(resp.msg ?: "创建失败")
    }

    suspend fun renameCloudPlaylist(id: String, name: String): Result<UserPlaylistDto> = runCatching {
        val resp = api.updatePlaylist(id, PlaylistMetaBody(name = name))
        if (resp.code == 200) resp.data ?: throw Exception("重命名失败") else throw Exception(resp.msg ?: "重命名失败")
    }

    suspend fun deleteCloudPlaylist(id: String): Result<Unit> = runCatching {
        val resp = api.deletePlaylist(id)
        if (resp.code != 200) throw Exception(resp.msg ?: "删除失败")
    }

    suspend fun cloudPlaylistSongs(id: String): Result<List<Song>> = runCatching {
        val resp = api.cloudPlaylistSongs(id)
        if (resp.code == 200) resp.data.orEmpty() else throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun pushPlaylistSongs(id: String, songs: List<Song>): Result<Unit> = runCatching {
        val resp = api.putPlaylistSongs(id, SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun addCloudPlaylistSongs(id: String, songs: List<Song>): Result<Unit> = runCatching {
        val resp = api.addPlaylistSongs(id, SongsBody(songs = songs))
        if (resp.code != 200) throw Exception(resp.msg ?: "同步失败")
    }

    suspend fun reportStats(events: List<PlayEventDto>): Result<Unit> = runCatching {
        val resp = api.reportStats(StatsReportBody(events = events))
        if (resp.code != 200) throw Exception(resp.msg ?: "上报失败")
    }

    suspend fun userStats(): Result<UserStatsDto> = runCatching {
        val resp = api.userStats()
        if (resp.code == 200) resp.data ?: UserStatsDto() else throw Exception(resp.msg ?: "获取统计失败")
    }
}
