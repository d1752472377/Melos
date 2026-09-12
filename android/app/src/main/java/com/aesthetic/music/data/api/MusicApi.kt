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

/** 直链数据（供本地音源解析引擎与公开外链直连协议使用） */
data class SongUrlData(
    val url: String? = null,
    val format: String? = null,
    val bitrate: String? = null,
    val duration: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
)

/** 歌词数据（供本地解析与官方直连接口使用） */
data class LyricData(
    val lyric: String? = null,
    val translated: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
)

/** 歌单条目模型（用于排行榜、热门歌单、歌单广场等） */
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
    val createdAt: Long = 0L,
)

/** 注册/登录返回数据 */
data class AuthData(
    val token: String = "",
    val expiresAt: Long = 0L,
    val user: UserInfo = UserInfo(),
)

/** 会话设备条目 */
data class SessionDto(
    val id: String = "",
    val deviceName: String = "",
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val isCurrent: Boolean = false,
)

/** 听歌明细上报条目 */
data class PlayEventDto(
    val eventId: String = "",
    val platform: String = "",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val listenedMs: Long = 0,
    val durationMs: Long = 0,
    val listenedAtMs: Long = 0,
)

/** 歌手条目（歌手搜索与歌手详情共用） */
data class ArtistItem(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val alias: String? = null,
    val platform: String? = null,
    val albumSize: Long? = null,
    val musicSize: Long? = null,
)

/** 歌手详情 + 热门歌曲 */
data class ArtistDetailData(
    val info: ArtistItem? = null,
    val songs: List<Song> = emptyList(),
)

/** 专辑搜索条目 */
data class AlbumItem(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val size: Long? = null,
    val platform: String? = null,
)

/** 专辑详情 + 全部曲目 */
data class AlbumDetailData(
    val info: AlbumItem? = null,
    val songs: List<Song> = emptyList(),
)

/** 歌单详情 + 全部曲目（导入外部平台歌单时，歌单名与封面也一并带回作为原始记录） */
data class PlaylistDetailData(
    val info: PlaylistItem? = null,
    val songs: List<Song> = emptyList(),
)

/** 热搜词 */
data class HotWordDto(
    val word: String = "",
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

// ==================== 请求体 ====================

/** 请求体：注册 */
data class RegisterBody(
    val username: String,
    val password: String,
    val nickname: String? = null,
    val deviceName: String? = null,
)

/** 请求体：登录 */
data class LoginBody(
    val username: String,
    val password: String,
    val deviceName: String? = null,
)

/** 请求体：修改资料 */
data class ProfileBody(
    val nickname: String? = null,
    val avatar: String? = null,
)

// ==================== 多端增量同步模型 (LWW + 墓碑) ====================

/**
 * 增量拉取数据包（GET /api/sync/pull?since={version}&eventSince={eventSince}）
 */
data class CloudSyncPullData(
    val version: Long = 0L,
    val nextEventId: Long = 0L,
    val serverTime: Long = 0L,
    val favorites: List<SyncFavoriteItem> = emptyList(),
    val history: List<SyncHistoryItem> = emptyList(),
    val playlists: List<SyncPlaylistItem> = emptyList(),
    val playlistSongs: List<SyncPlaylistSongItem> = emptyList(),
    val playEvents: List<SyncPlayEventItem> = emptyList(),
)

data class SyncFavoriteItem(
    val platform: String = "",
    val id: String = "",
    val song: Song? = null,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncHistoryItem(
    val platform: String = "",
    val id: String = "",
    val song: Song? = null,
    val playedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncPlaylistItem(
    val id: String = "",
    val name: String = "",
    val coverUrl: String = "",
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncPlaylistSongItem(
    val playlistId: String = "",
    val position: Int = 0,
    val platform: String = "",
    val songId: String = "",
    val song: Song? = null,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncPlayEventItem(
    val eventId: String = "",
    val platform: String = "",
    val songId: String = "",
    val title: String = "",
    val artist: String = "",
    val listenedMs: Long = 0L,
    val durationMs: Long = 0L,
    val listenedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
)

/**
 * 增量推送变更包（POST /api/sync/push）
 */
data class SyncPushPayload(
    val favorites: SyncCollection<Song, SyncSongKey>? = null,
    val history: SyncCollection<SyncHistoryUpsert, SyncSongKey>? = null,
    val playlists: SyncCollection<SyncPlaylistUpsert, SyncPlaylistKey>? = null,
    val playlistSongs: SyncCollection<SyncPlaylistSongUpsert, SyncPlaylistSongKey>? = null,
    val playEvents: List<PlayEventDto>? = null,
)

data class SyncCollection<U, D>(
    val upsert: List<U> = emptyList(),
    val delete: List<D> = emptyList(),
)

data class SyncSongKey(
    val platform: String,
    val id: String,
)

data class SyncHistoryUpsert(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artwork: String? = null,
    val platform: String = "netease",
    val playedAtMs: Long = System.currentTimeMillis(),
)

data class SyncPlaylistUpsert(
    val id: String,
    val name: String,
    val coverUrl: String? = null,
)

data class SyncPlaylistKey(
    val id: String,
)

data class SyncPlaylistSongUpsert(
    val playlistId: String,
    val position: Int,
    val song: Song,
)

data class SyncPlaylistSongKey(
    val playlistId: String,
    val platform: String,
    val songId: String,
)

data class SyncPushResult(
    val version: Long = 0L,
    val acceptedEvents: Int = 0,
    val serverTime: Long = 0L,
)

// ==================== Retrofit 接口 ====================

interface MusicApi {

    // ==================== 用户体系 (Auth) ====================

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): ApiResponse<AuthData>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginBody): ApiResponse<AuthData>

    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Any>

    @POST("api/auth/logout-all")
    suspend fun logoutAll(): ApiResponse<Any>

    @GET("api/me")
    suspend fun me(): ApiResponse<UserInfo>

    @PUT("api/me")
    suspend fun updateProfile(@Body body: ProfileBody): ApiResponse<UserInfo>

    @GET("api/me/sessions")
    suspend fun sessions(): ApiResponse<List<SessionDto>>

    @DELETE("api/me/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String): ApiResponse<Any>

    // ==================== 数据同步 (Sync) ====================

    @GET("api/sync/pull")
    suspend fun syncPull(
        @Query("since") since: Long = 0L,
        @Query("eventSince") eventSince: Long = 0L,
    ): ApiResponse<CloudSyncPullData>

    @POST("api/sync/push")
    suspend fun syncPush(@Body body: SyncPushPayload): ApiResponse<SyncPushResult>
}
