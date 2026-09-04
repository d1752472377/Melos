package com.aesthetic.music.data.api

import com.aesthetic.music.data.model.Song
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/** 后端统一响应包装 */
data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null,
)

/** /api/song/url 返回的直链数据 */
data class SongUrlData(
    val url: String? = null,
    val format: String? = null,
    val bitrate: String? = null,
    val duration: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
)

/** /api/lyric 返回的歌词数据 */
data class LyricData(
    val lyric: String? = null,
    val translated: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
)

/** 热门歌单条目（也用于排行榜） */
data class PlaylistItem(
    val id: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val playCount: Long? = null,
    val trackCount: Long? = null,
    val platform: String? = null,
    /** 排行榜语义：hot / new / rising / original / other；非榜单为 null */
    val kind: String? = null,
)

/** 登录用户 */
data class UserInfo(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
)

/** 注册/登录返回 */
data class AuthData(
    val token: String = "",
    val user: UserInfo = UserInfo(),
)

/** 云端我的歌单 */
data class UserPlaylistDto(
    val id: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val songCount: Int = 0,
)

/** 统计聚合 */
data class UserStatsDto(
    val totalPlayMs: Long = 0,
    val totalPlays: Long = 0,
    val todayPlayMs: Long = 0,
    val weekPlayMs: Long = 0,
    val topSongs: List<TopSong> = emptyList(),
    val topArtists: List<TopArtist> = emptyList(),
    val weekChart: List<WeekPoint> = emptyList(),
)

data class TopSong(
    val platform: String = "",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val listenedMs: Long = 0,
    val plays: Long = 0,
)

data class TopArtist(
    val artist: String = "",
    val listenedMs: Long = 0,
    val plays: Long = 0,
)

data class WeekPoint(
    val day: String = "",
    val listenedMs: Long = 0,
)

/** 听歌明细上报条目 */
data class PlayEventDto(
    val platform: String = "",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val listenedMs: Long = 0,
    val durationMs: Long = 0,
)

/** 内容聚合包（每日推荐 / 新歌速递 / 排行榜） */
data class ContentData<T>(
    val title: String = "",
    val data: T? = null,
)

/** 歌手条目（/api/artist/search 与歌手详情 info 共用） */
data class ArtistItem(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val alias: String? = null,
    val platform: String? = null,
    /** 歌手专辑数（仅详情接口返回） */
    val albumSize: Long? = null,
    /** 歌手歌曲数（仅详情接口返回） */
    val musicSize: Long? = null,
)

/** /api/artist 返回的详情 + 热门歌曲 */
data class ArtistDetailData(
    val info: ArtistItem? = null,
    val songs: List<Song> = emptyList(),
)

/** 专辑搜索条目（也用于专辑详情 info） */
data class AlbumItem(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    /** 专辑曲目数（搜索与详情均可能返回） */
    val size: Long? = null,
    val platform: String? = null,
)

/** /api/album 返回的专辑详情 + 全部曲目 */
data class AlbumDetailData(
    val info: AlbumItem? = null,
    val songs: List<Song> = emptyList(),
)

/** 热搜词（/api/search/hot） */
data class HotWordDto(
    val word: String = "",
)

/** 请求体：注册 */
data class RegisterBody(
    val username: String,
    val password: String,
    val nickname: String = "",
)

/** 请求体：登录 */
data class LoginBody(
    val username: String,
    val password: String,
)

/** 请求体：修改资料 */
data class ProfileBody(
    val nickname: String? = null,
    val avatar: String? = null,
)

/** 请求体：同步歌曲列表 */
data class SongsBody(
    val songs: List<Song>,
)

/** 请求体：创建/重命名歌单 */
data class PlaylistMetaBody(
    val name: String,
    val coverUrl: String? = null,
)

/** 请求体：统计上报 */
data class StatsReportBody(
    val events: List<PlayEventDto>,
)

interface MusicApi {

    // ==================== 音乐 ====================

    @GET("api/search")
    suspend fun search(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 30,
        /** 可选：只搜指定平台（netease/kuwo/kugou/qq...），null = 聚合全部健康源 */
        @Query("platform") platform: String? = null,
    ): ApiResponse<List<Song>>

    /** 热搜词（搜索页"热门搜索"区） */
    @GET("api/search/hot")
    suspend fun hotSearchWords(): ApiResponse<List<HotWordDto>>

    /** 歌单搜索（netease） */
    @GET("api/search/playlist")
    suspend fun searchPlaylists(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<PlaylistItem>>

    /** 专辑搜索（netease） */
    @GET("api/search/album")
    suspend fun searchAlbums(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<AlbumItem>>

    /** 歌词关键词搜歌（netease type=1006） */
    @GET("api/search/lyric")
    suspend fun searchByLyric(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<Song>>

    /** 专辑详情 + 曲目列表 */
    @GET("api/album")
    suspend fun albumDetail(@Query("id") id: String): ApiResponse<AlbumDetailData>

    @GET("api/artist/search")
    suspend fun artistSearch(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 8,
        @Query("platform") platform: String? = null,
    ): ApiResponse<List<ArtistItem>>

    @GET("api/artist")
    suspend fun artistDetail(
        @Query("platform") platform: String? = null,
        @Query("id") id: String,
        @Query("limit") limit: Int = 50,
    ): ApiResponse<ArtistDetailData>

    @GET("api/song/url")
    suspend fun songUrl(
        @Query("platform") platform: String,
        @Query("id") id: String,
        @Query("title") title: String,
        @Query("quality") quality: String = "lossless",
    ): ApiResponse<SongUrlData>

    @GET("api/lyric")
    suspend fun lyric(
        @Query("platform") platform: String,
        @Query("id") id: String,
        @Query("title") title: String,
    ): ApiResponse<LyricData>

    @GET("api/playlist")
    suspend fun playlistDetail(
        @Query("id") id: String,
    ): ApiResponse<List<Song>>

    @GET("api/playlist/hot")
    suspend fun hotPlaylists(
        @Query("limit") limit: Int = 20,
    ): ApiResponse<List<PlaylistItem>>

    // ==================== 内容 ====================

    @GET("api/daily")
    suspend fun daily(@Query("limit") limit: Int = 8): ApiResponse<DailyData>

    @GET("api/newsongs")
    suspend fun newSongs(@Query("limit") limit: Int = 20): ApiResponse<NewSongsData>

    @GET("api/ranks")
    suspend fun ranks(): ApiResponse<RanksData>

    @GET("api/rank/{id}")
    suspend fun rankDetail(@Path("id") id: String): ApiResponse<List<Song>>

    @GET("api/categories")
    suspend fun categories(): ApiResponse<CategoriesData>

    // ==================== 用户 ====================

    @POST("api/user/register")
    suspend fun register(@Body body: RegisterBody): ApiResponse<AuthData>

    @POST("api/user/login")
    suspend fun login(@Body body: LoginBody): ApiResponse<AuthData>

    @GET("api/user/me")
    suspend fun me(): ApiResponse<UserInfo>

    @PUT("api/user/profile")
    suspend fun updateProfile(@Body body: ProfileBody): ApiResponse<UserInfo>

    @GET("api/user/favorites")
    suspend fun cloudFavorites(): ApiResponse<List<Song>>

    @PUT("api/user/favorites")
    suspend fun putFavorites(@Body body: SongsBody): ApiResponse<List<Song>>

    @GET("api/user/history")
    suspend fun cloudHistory(): ApiResponse<List<Song>>

    @PUT("api/user/history")
    suspend fun putHistory(@Body body: SongsBody): ApiResponse<List<Song>>

    @GET("api/user/playlists")
    suspend fun cloudPlaylists(): ApiResponse<List<UserPlaylistDto>>

    @POST("api/user/playlists")
    suspend fun createPlaylist(@Body body: PlaylistMetaBody): ApiResponse<UserPlaylistDto>

    @PUT("api/user/playlists/{id}")
    suspend fun updatePlaylist(@Path("id") id: String, @Body body: PlaylistMetaBody): ApiResponse<UserPlaylistDto>

    @DELETE("api/user/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: String): ApiResponse<Any>

    @GET("api/user/playlists/{id}/songs")
    suspend fun cloudPlaylistSongs(@Path("id") id: String): ApiResponse<List<Song>>

    @PUT("api/user/playlists/{id}/songs")
    suspend fun putPlaylistSongs(@Path("id") id: String, @Body body: SongsBody): ApiResponse<UserPlaylistDto>

    @POST("api/user/playlists/{id}/songs")
    suspend fun addPlaylistSongs(@Path("id") id: String, @Body body: SongsBody): ApiResponse<UserPlaylistDto>

    @POST("api/user/stats/report")
    suspend fun reportStats(@Body body: StatsReportBody): ApiResponse<Any>

    @GET("api/user/stats")
    suspend fun userStats(): ApiResponse<UserStatsDto>

    // ==================== 私有云同步 & 音源脚本 ====================

    @GET("api/sync/ping")
    suspend fun syncPing(): ApiResponse<SyncPingData>

    @GET("api/sync/pull")
    suspend fun syncPull(): ApiResponse<CloudSyncPullData>

    @POST("api/sync/push")
    suspend fun syncPush(@Body body: SyncPushBody): ApiResponse<Any>

    @GET("api/scripts/active")
    suspend fun getActiveScript(): ApiResponse<CloudScriptDto>
}

/** 私有云同步数据包 */
data class CloudSyncPullData(
    val script: CloudScriptDto? = null,
    val favorites: List<Song> = emptyList(),
    val history: List<Song> = emptyList(),
    val playlists: List<CloudPlaylistDto> = emptyList(),
    val syncedAt: String? = null,
)

data class CloudScriptDto(
    val id: String = "",
    val name: String = "",
    val version: String = "",
    val content: String = "",
    val status: String? = null,
    val statusMessage: String? = null,
)

data class CloudPlaylistDto(
    val id: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val songs: List<Song> = emptyList(),
)

data class SyncPushBody(
    val favorites: List<Song>? = null,
    val history: List<Song>? = null,
    val playlists: List<CloudPlaylistDto>? = null,
)

data class SyncPingData(
    val serverTime: Long = 0L,
    val syncEnabled: Boolean = false,
)

/** 每日推荐 */
data class DailyData(
    val title: String = "每日推荐",
    val playlists: List<PlaylistItem> = emptyList(),
)

/** 新歌速递 */
data class NewSongsData(
    val title: String = "新歌速递",
    val songs: List<Song> = emptyList(),
)

/** 排行榜列表 */
data class RanksData(
    val title: String = "排行榜",
    val ranks: List<PlaylistItem> = emptyList(),
)

/** 首页分类歌单 */
data class CategoriesData(
    val title: String = "分类歌单",
    val categories: List<HomeCategoryDto> = emptyList(),
)

data class HomeCategoryDto(
    val id: String = "",
    val title: String = "",
    val quote: String = "",
    val coverUrl: String? = null,
    val previewSongs: List<String> = emptyList(),
    val playlist: PlaylistItem? = null,
)
