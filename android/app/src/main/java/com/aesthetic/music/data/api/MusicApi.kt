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
    /**
     * 逐字歌词原始文本（网易云 YRC / 酷狗 KRC 这类带「字级时间戳」的格式）。
     *
     * 为什么不直接塞进 [lyric]：LRC 的**文本**更干净（有标点、不会被拆字），
     * 而 YRC 的价值只在「每个字的起止毫秒」。两者分开传，播放层才能
     * 「用 LRC 的文本 + 用 YRC 的时间」——这是歌词页能做到逐字染色又不丢标点的前提。
     */
    val wordByWord: String? = null,
)

/** 服务器探活响应 */
data class HealthData(
    val ok: Boolean = true,
    val time: Long = 0L,
    /** 服务端版本号（便于排查"客户端比后端新/旧"引起的字段缺失） */
    val version: String? = null,
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
    /**
     * 榜单分组名（QQ 的「巅峰榜/地区榜/特色榜/全球榜」、酷我的「官方/特色/场景/语言/全球」）。
     * 只有榜单会用到，其它歌单恒为 null：榜单页据它分组显示，缺失时退化为平铺，不影响其它入口。
     */
    val group: String? = null,
    /** 歌单描述/文案，用于卡片第二行简介展示 */
    val description: String? = null,
)

/** 登录用户 */
data class UserInfo(
    val id: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val background: String = "",
    val createdAt: Long = 0L,
    /**
     * 绑定邮箱。可空而不是空串：老服务端（或老版本的本地缓存 JSON）没有这个字段，
     * Gson 反射反序列化遇到缺失字段会留下 null，声明成非空 String 会在读取时炸。
     */
    val email: String? = null,
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
    val deviceId: String = "",
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

/** 请求体：注册（必传邮箱与验证码） */
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

/** 请求体：修改密码 */
data class PasswordBody(
    val oldPassword: String,
    val newPassword: String,
)

/** 请求体：登录 */
data class LoginBody(
    val username: String,
    val password: String,
    val deviceName: String? = null,
)

/** 请求体：修改资料（字段为 null 表示不改动该项，与后端的「缺省即不改」约定一致） */
data class ProfileBody(
    val nickname: String? = null,
    val avatar: String? = null,
    val background: String? = null,
    /** 传空串表示解绑邮箱；服务端未支持该字段时会被忽略（响应里 email 仍为 null） */
    val email: String? = null,
)

/** 请求体：发送验证码 */
data class SendCodeBody(
    val email: String,
    val scene: String = "login",
)

/** 请求体：邮箱验证码登录 / 自动注册 */
data class EmailLoginBody(
    val email: String,
    val code: String,
    val deviceName: String? = null,
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

/**
 * 音源脚本（用户上传的 JS 插件）——拉取项。
 *
 * `code` 是脚本源码本身：它是用户自己写的资产，丢了就得重写，
 * 因此随账号同步（而不是只同步"脚本名"）。服务端只做存储，不解析/不执行。
 */
data class SyncScriptItem(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val code: String = "",
    val enabled: Boolean = true,
    val updatedAtMs: Long = 0L,
    val deleted: Boolean = false,
)

/**
 * 下载记录——拉取项。
 *
 * `deviceId` 是"哪个设备上的文件"：下载是设备级事实，不同设备可以有各自的副本与删除。
 * 只同步"哪台设备下过哪首歌 + 体积 + 时间"：文件名与路径属于本机事实，
 * 换设备后真实能播的路径只能由本机缓存索引决定。
 */
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

/**
 * 增量推送变更包（POST /api/sync/push）
 *
 * 传 null 表示"本次不改动这个集合"；服务端对缺省集合不做任何事。
 */
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
    /** `mine`=自建歌单 / `fav`=收藏的网络歌单 */
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

/** 客户端版本发布条目（对应后端 GET /api/app/version/latest） */
data class AppVersionDto(
    val id: Long = 0L,
    @SerializedName("version_code", alternate = ["versionCode"])
    val versionCode: Long = 0L,
    @SerializedName("version_name", alternate = ["versionName"])
    val versionName: String = "",
    @SerializedName("apk_url", alternate = ["apkUrl"])
    val apkUrl: String = "",
    /**
     * 安装包的兜底下载地址（通常是本机相对路径，如 /api/app/download/28-2_1_3.apk）。
     *
     * 后端会把 [apkUrl] 指向大带宽镜像（自建 ECS 只有 3M 上行），
     * 这里保留一条「本机直发」的退路：镜像挂了/被墙了也不至于完全下不了。
     */
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

// ==================== Retrofit 接口 ====================

interface MusicApi {

    /**
     * 服务器可达性探测（**不需要登录**）。
     *
     * 为什么单独要一个接口：未登录时用 /api/me 或 /api/sync/pull 探测会拿到 401，
     * 客户端无法区分"地址填错了"和"地址对但没登录"——而这正是用户在配置页面最需要知道的事。
     */
    @GET("api/health")
    suspend fun health(): ApiResponse<HealthData>

    /**
     * 检查客户端最新发布版本（**不需要登录**）。
     * 当无可用版本时返回 data: null。
     */
    @GET("api/app/version/latest")
    suspend fun latestVersion(): ApiResponse<AppVersionDto?>

    // ==================== 用户体系 (Auth) ====================

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

    // ==================== 数据同步 (Sync) ====================

    @GET("api/sync/pull")
    suspend fun syncPull(
        @Query("since") since: Long = 0L,
        @Query("eventSince") eventSince: Long = 0L,
    ): ApiResponse<CloudSyncPullData>

    @POST("api/sync/push")
    suspend fun syncPush(@Body body: SyncPushPayload): ApiResponse<SyncPushResult>

    // ==================== 客户端日志上报 (Logs) ====================

    @POST("api/logs/batch")
    suspend fun logsBatch(@Body body: LogBatchPayload): ApiResponse<LogBatchResult>
}

/** 单条客户端日志（批量上报条目） */
data class LogBatchItem(
    val level: String = "",
    val tag: String = "",
    val message: String = "",
    val stack: String? = null,
    val extra: Map<String, String>? = null,
    val timeMs: Long = 0L,
    val sessionId: String = "",
    /** 同指纹去重后的出现次数（≥1；服务端展示"×N"） */
    val count: Long = 1L,
    /** 出错的线程名（主线程 / OkHttp / DefaultDispatcher-worker-3 …） */
    val thread: String? = null,
    /** 崩溃前的操作轨迹（仅 ERROR 批次随附） */
    val breadcrumbs: List<BreadcrumbItem>? = null,
    /** 该条日志自身的环境快照（崩溃时冻结，其余走批次级 context） */
    val context: Map<String, String>? = null,
)

/** 面包屑：一条操作轨迹 */
data class BreadcrumbItem(
    val timeMs: Long = 0L,
    val level: String = "",
    val tag: String = "",
    val message: String = "",
)

/** 批量上报请求体 */
data class LogBatchPayload(
    val logs: List<LogBatchItem> = emptyList(),
    val appVersion: String = "",
    val versionCode: Long = 0L,
    val os: String = "",
    val device: String = "",
    /** 已登录用户的 id（未登录为 null；服务端同时记录 IP 便于关联） */
    val appUid: Long? = null,
    /** 环境快照：设备/内存/存储/电池/网络/前台/播放状态等 */
    val context: Map<String, String>? = null,
)

/** 批量上报响应（服务端可下发远程排障开关） */
data class LogBatchResult(
    val accepted: Int = 0,
    /** epoch ms：在此之前本设备的 INFO/DEBUG 也上报（0/缺省 = 关闭） */
    val debugUntil: Long? = null,
)
