package com.aesthetic.music.data.api

import com.aesthetic.music.data.model.Song
import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

data class ApiResponse<T>(
    val code: Int = 0,
    val msg: String? = null,
    val data: T? = null,
)

data class SongUrlData(
    val url: String? = null,
    val format: String? = null,
    val bitrate: String? = null,
    val duration: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
)

data class LyricData(
    val lyric: String? = null,
    val translated: String? = null,
    val platform: String? = null,
    val sourceName: String? = null,
    val wordByWord: String? = null,
)

data class HealthData(
    val ok: Boolean = true,
    val time: Long = 0L,
    val version: String? = null,
)

data class PlaylistItem(
    val id: String = "",
    val name: String = "",
    val coverUrl: String? = null,
    val playCount: Long? = null,
    val trackCount: Long? = null,
    val platform: String? = null,
    val kind: String? = null,
    val group: String? = null,
    val description: String? = null,
)

data class UserInfo(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val background: String = "",
    val createdAt: Long = 0L,
    val email: String? = null,
)

data class AuthData(
    val token: String = "",
    val expiresAt: Long = 0L,
    val user: UserInfo = UserInfo(),
)

data class SessionDto(
    val id: String = "",
    val deviceName: String = "",
    val createdAt: Long = 0L,
    val lastSeenAt: Long = 0L,
    val isCurrent: Boolean = false,
)

data class PlayEventDto(
    val eventId: String = "",
    val platform: String = "",
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val listenedMs: Long = 0,
    val durationMs: Long = 0,
    val listenedAtMs: Long = 0,
    val deviceId: String = "",
)

data class ArtistItem(
    val id: String = "",
    val name: String = "",
    val avatar: String? = null,
    val alias: String? = null,
    val platform: String? = null,
    val albumSize: Long? = null,
    val musicSize: Long? = null,
)

data class ArtistDetailData(
    val info: ArtistItem? = null,
    val songs: List<Song> = emptyList(),
)

data class AlbumItem(
    val id: String = "",
    val name: String = "",
    val artist: String = "",
    val coverUrl: String? = null,
    val size: Long? = null,
    val platform: String? = null,
)

data class AlbumDetailData(
    val info: AlbumItem? = null,
    val songs: List<Song> = emptyList(),
)

data class PlaylistDetailData(
    val info: PlaylistItem? = null,
    val songs: List<Song> = emptyList(),
)

data class DailyData(
    val title: String = "每日推荐",
    val playlists: List<PlaylistItem> = emptyList(),
)

data class NewSongsData(
    val title: String = "新歌速递",
    val songs: List<Song> = emptyList(),
)

data class RanksData(
    val title: String = "排行榜",
    val ranks: List<PlaylistItem> = emptyList(),
)

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

data class RegisterBody(
    val username: String,
    val password: String,
    val email: String,
    val code: String,
    val nickname: String? = null,
    val deviceName: String? = null,
)

/** 请求体：安全绑定/更换邮箱 */
data class BindEmailBody(
    val email: String,
    val code: String,
)

/** 请求体：安全解绑邮箱 */
data class UnbindEmailBody(
    val code: String,
)

data class PasswordBody(
    val oldPassword: String,
    val newPassword: String,
)

data class LoginBody(
    val username: String,
    val password: String,
    val deviceName: String? = null,
)

data class ProfileBody(
    val nickname: String? = null,
    val avatar: String? = null,
    val background: String? = null,
    val email: String? = null,
)

data class SendCodeBody(
    val email: String,
    val scene: String = "login",
)

data class EmailLoginBody(
    val email: String,
    val code: String,
    val deviceName: String? = null,
)

data class CloudSyncPullData(
    val version: Long = 0L,
    val nextEventId: Long = 0L,
    val serverTime: Long = 0L,
    val favorites: List<SyncFavoriteItem> = emptyList(),
    val history: List<SyncHistoryItem> = emptyList(),
    val playlists: List<SyncPlaylistItem> = emptyList(),
    val playlistSongs: List<SyncPlaylistSongItem> = emptyList(),
    val playEvents: List<SyncPlayEventItem> = emptyList(),
    val scripts: List<SyncScriptItem> = emptyList(),
    val downloads: List<SyncDownloadItem> = emptyList(),
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
    /** 歌单类型：`mine`=自建歌单 / `fav`=收藏的网络歌单（缺省按 mine 处理，兼容旧数据） */
    val kind: String = "mine",
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
    val deviceId: String = "",
    val updatedAtMs: Long = 0L,
)

data class SyncScriptItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val code: String = "",
    val enabled: Boolean = true,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncDownloadItem(
    val platform: String = "",
    val id: String = "",
    val deviceId: String = "",
    val deviceName: String = "",
    val title: String = "",
    val artist: String = "",
    val sizeBytes: Long = 0L,
    val downloadedAtMs: Long = 0L,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

data class SyncPushPayload(
    val favorites: SyncCollection<Song, SyncSongKey>? = null,
    val history: SyncCollection<SyncHistoryUpsert, SyncSongKey>? = null,
    val playlists: SyncCollection<SyncPlaylistUpsert, SyncPlaylistKey>? = null,
    val playlistSongs: SyncCollection<SyncPlaylistSongUpsert, SyncPlaylistSongKey>? = null,
    val playEvents: List<PlayEventDto>? = null,
    val scripts: SyncCollection<SyncScriptUpsert, SyncScriptKey>? = null,
    val downloads: SyncCollection<SyncDownloadUpsert, SyncDownloadKey>? = null,
)

data class SyncScriptUpsert(
    val id: String,
    val name: String,
    val description: String = "",
    val code: String,
    val enabled: Boolean = true,
)

data class SyncScriptKey(
    val id: String,
)

data class SyncDownloadUpsert(
    val platform: String,
    val id: String,
    val deviceId: String,
    val deviceName: String = "",
    val title: String = "",
    val artist: String = "",
    val sizeBytes: Long = 0L,
    val downloadedAtMs: Long = 0L,
)

data class SyncDownloadKey(
    val platform: String,
    val id: String,
    val deviceId: String,
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
    val kind: String = "mine",
)

data class SyncPlaylistKey(
    val id: String,
    /** 歌单类型：墓碑必须带，否则服务端/对端无法区分自建与收藏（键由 kind + id 组成） */
    val kind: String = "mine",
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

data class AppVersionDto(
    val id: Long = 0L,
    @SerializedName("version_code", alternate = ["versionCode"])
    val versionCode: Long = 0L,
    @SerializedName("version_name", alternate = ["versionName"])
    val versionName: String = "",
    @SerializedName("apk_url", alternate = ["apkUrl"])
    val apkUrl: String = "",
    @SerializedName("apk_url_fallback", alternate = ["apkUrlFallback"])
    val apkUrlFallback: String = "",
    @SerializedName("file_size", alternate = ["fileSize"])
    val fileSize: Long = 0L,
    @SerializedName("hash_sha256", alternate = ["hashSha256"])
    val hashSha256: String = "",
    val changelog: String = "",
    @SerializedName("force_update", alternate = ["forceUpdate"])
    val forceUpdate: Boolean = false,
    @SerializedName("has_local_file", alternate = ["hasLocalFile"])
    val hasLocalFile: Boolean = true,
    @SerializedName("created_at", alternate = ["createdAt"])
    val createdAt: Long = 0L,
)

interface MusicApi {

    @GET("api/health")
    suspend fun health(): ApiResponse<HealthData>

    @GET("api/app/version/latest")
    suspend fun latestVersion(): ApiResponse<AppVersionDto?>

    @POST("api/auth/register")
    suspend fun register(@Body body: RegisterBody): ApiResponse<AuthData>

    @POST("api/auth/login")
    suspend fun login(@Body body: LoginBody): ApiResponse<AuthData>

    @POST("api/auth/send-code")
    suspend fun sendCode(@Body body: SendCodeBody): ApiResponse<Any>

    @POST("api/auth/email-login")
    suspend fun emailLogin(@Body body: EmailLoginBody): ApiResponse<AuthData>

    @POST("api/auth/logout")
    suspend fun logout(): ApiResponse<Any>

    @POST("api/auth/logout-all")
    suspend fun logoutAll(): ApiResponse<Any>

    @GET("api/me")
    suspend fun me(): ApiResponse<UserInfo>

    @PUT("api/me")
    suspend fun updateProfile(@Body body: ProfileBody): ApiResponse<UserInfo>

    @POST("api/me/email/bind")
    suspend fun bindEmail(@Body body: BindEmailBody): ApiResponse<UserInfo>

    @POST("api/me/email/unbind")
    suspend fun unbindEmail(@Body body: UnbindEmailBody): ApiResponse<UserInfo>

    @PUT("api/me/password")
    suspend fun updatePassword(@Body body: PasswordBody): ApiResponse<Any>

    @GET("api/me/sessions")
    suspend fun sessions(): ApiResponse<List<SessionDto>>

    @DELETE("api/me/sessions/{id}")
    suspend fun deleteSession(@Path("id") id: String): ApiResponse<Any>

    @GET("api/sync/pull")
    suspend fun syncPull(
        @Query("since") since: Long = 0L,
        @Query("eventSince") eventSince: Long = 0L,
    ): ApiResponse<CloudSyncPullData>

    @POST("api/sync/push")
    suspend fun syncPush(@Body body: SyncPushPayload): ApiResponse<SyncPushResult>

    @POST("api/logs/batch")
    suspend fun logsBatch(@Body body: LogBatchPayload): ApiResponse<LogBatchResult>
}

data class LogBatchItem(
    val level: String = "",
    val tag: String = "",
    val message: String = "",
    val stack: String? = null,
    val extra: Map<String, String>? = null,
    val timeMs: Long = 0L,
    val sessionId: String = "",
    val count: Long = 1L,
    /** 出错的线程名（主线程 / OkHttp / DefaultDispatcher-worker-3 …） */
    val thread: String? = null,
    /** 崩溃前的操作轨迹（仅 ERROR 批次随附） */
    val breadcrumbs: List<BreadcrumbItem>? = null,
    /** 该条日志自身的环境快照（崩溃时冻结，其余走批次级 context） */
    val context: Map<String, String>? = null,
)

data class BreadcrumbItem(
    val timeMs: Long = 0L,
    val level: String = "",
    val tag: String = "",
    val message: String = "",
)

data class LogBatchPayload(
    val logs: List<LogBatchItem> = emptyList(),
    val appVersion: String = "",
    val versionCode: Long = 0L,
    val os: String = "",
    val device: String = "",
    val appUid: Long? = null,
    val context: Map<String, String>? = null,
)

data class LogBatchResult(
    val accepted: Int = 0,
    val debugUntil: Long? = null,
)
