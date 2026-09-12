package com.aesthetic.music.ui.library

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.SyncCollection
import com.aesthetic.music.data.api.SyncHistoryUpsert
import com.aesthetic.music.data.api.SyncPlaylistSongUpsert
import com.aesthetic.music.data.api.SyncPlaylistUpsert
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistCoverStore
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 用户音乐库控制器：收藏 + 最近播放 + 我的歌单 + 私有云同步。
 *
 * 从 `MainViewModel` 抽出。这四块之所以放在同一个控制器而不是再拆开，是因为它们强耦合：
 * `scheduleSyncPush()` 被三组集合的所有 mutator 调用，`buildSyncPushPayload()` 同时读三组集合，
 * `performCloudSync()` 在同一个 [syncMutex] 内改三组集合并与 [StatsTracker] 的待发事件交错——
 * 拆开会改变 LWW/墓碑/事件重放的顺序。`MainViewModel` 保留同名只读委托。
 */
class LibraryController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    /** 向用户展示一次性提示（对应 ViewModel 的全局 Snackbar 通道） */
    private val onToast: (String) -> Unit,
) {

    private companion object {
        private const val HISTORY_LIMIT = 100

        /** 收藏上限：SharedPreferences 存整组 JSON，无上限会随使用无限膨胀 */
        private const val MAX_FAVORITES = 1000

        /** 单个歌单曲目数上限 */
        private const val MAX_PLAYLIST_SONGS = 1000
    }

    // 历史记录（最近播放，持久化：重启不丢失）
    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    // 收藏（持久化：重启不丢失；登录后同步云端），带上限
    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    // 收藏 key 集合（供列表红心状态使用，与 favoriteSongs 同步维护）
    private val _favoriteKeys = MutableStateFlow(emptySet<String>())
    val favorites: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    // 收藏的歌单（来自推荐/网络精选歌单，持久化）
    private val _favoritePlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val favoritePlaylists: StateFlow<List<PlaylistItem>> = _favoritePlaylists.asStateFlow()
    private val _favoritePlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val favoritePlaylistIds: StateFlow<Set<String>> = _favoritePlaylistIds.asStateFlow()

    // 我的歌单（本地持久化，登录后同步云端）
    private val _myPlaylists = MutableStateFlow<List<MyPlaylist>>(emptyList())
    val myPlaylists: StateFlow<List<MyPlaylist>> = _myPlaylists.asStateFlow()

    // ==================== 云同步状态 ====================
    private val _syncServerUrl = MutableStateFlow(SyncServerStore.getServerUrl())
    val syncServerUrl = _syncServerUrl.asStateFlow()
    private val _syncToken = MutableStateFlow(SyncServerStore.getSyncToken())
    val syncToken = _syncToken.asStateFlow()
    private val _isSyncConfigured = MutableStateFlow(SyncServerStore.isConfigured())
    val isSyncConfigured = _isSyncConfigured.asStateFlow()
    private val _lastSyncedAt = MutableStateFlow(SyncServerStore.getLastSyncedAt())
    val lastSyncedAt = _lastSyncedAt.asStateFlow()
    private val _lastSyncStatus = MutableStateFlow(SyncServerStore.getLastSyncStatus())
    val lastSyncStatus = _lastSyncStatus.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _isTestingSync = MutableStateFlow(false)
    val isTestingSync = _isTestingSync.asStateFlow()

    /**
     * 云同步串行互斥锁 + 待发标记：
     * 收藏/历史是后端"整表替换"语义，多个并发 PUT 乱序到达会让云端被旧快照覆盖。
     * 所有推送排进同一把锁按序执行；待发标记保证激增的推送合并为"只发最新一次"。
     */
    private val syncMutex = Mutex()
    private var favPushPending = false

    /**
     * 冷启动异步加载持久化数据（历史/收藏/歌单），完成后与窗口期内已发生的变更合并，
     * 既消除主线程 JSON 解析，又保证用户极早期的操作不被回填覆盖。
     */
    fun hydrate() {
        scope.launch(Dispatchers.IO) {
            val h = HistoryStore.load()
            val f = FavoriteStore.load().take(MAX_FAVORITES)
            val favP = FavoritePlaylistStore.load()
            val p = PlaylistStore.load()
            withContext(Dispatchers.Main) {
                _history.value = mergeSongs(_history.value, h).take(HISTORY_LIMIT)
                val fav = mergeSongs(_favoriteSongs.value, f).take(MAX_FAVORITES)
                _favoriteSongs.value = fav
                _favoriteKeys.value = fav.map { it.platform + ":" + it.id }.toSet()
                _favoritePlaylists.value = favP
                _favoritePlaylistIds.value = favP.map { it.id }.toSet()
                val cur = _myPlaylists.value
                _myPlaylists.value = cur + p.filter { lp ->
                    cur.none { it.id == lp.id || (lp.cloudId.isNotBlank() && it.cloudId == lp.cloudId) }
                }
            }
        }
    }

    private fun mergeSongs(local: List<Song>, other: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Song>()
        for (s in (local + other)) {
            val key = s.platform + ":" + s.id
            if (seen.add(key)) out.add(s)
        }
        return out
    }

    // ==================== 最近播放 ====================

    fun addToHistory(song: Song) {
        val list = _history.value.toMutableList().apply {
            removeAll { it.id == song.id && it.platform == song.platform }
            add(0, song)
        }
        _history.value = list.take(HISTORY_LIMIT)
        HistoryStore.save(_history.value)
        scheduleSyncPush()
    }

    /** 最近播放页：移除单条记录（本地 + 增量推送到私有云） */
    fun removeFromHistory(song: Song) {
        val next = _history.value.filterNot { it.id == song.id && it.platform == song.platform }
        _history.value = next
        HistoryStore.save(next)
        scheduleSyncPush()
    }

    /** 最近播放页：清空全部记录 */
    fun clearHistory() {
        _history.value = emptyList()
        HistoryStore.save(emptyList())
        scheduleSyncPush()
    }

    // ==================== 收藏 ====================

    fun toggleFavorite(song: Song) {
        val list = _favoriteSongs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }
        if (idx >= 0) list.removeAt(idx) else list.add(0, song)
        val capped = list.take(MAX_FAVORITES)
        _favoriteSongs.value = capped
        _favoriteKeys.value = capped.map { it.platform + ":" + it.id }.toSet()
        FavoriteStore.save(capped)
        scheduleSyncPush()
    }

    fun isFavorite(song: Song): Boolean =
        (song.platform + ":" + song.id) in _favoriteKeys.value

    /**
     * 供 FM 使用的收藏判断：在 [isFavorite] 基础上额外接受裸 `song.id`，
     * 兼容历史数据（旧版本只存了 id 未存 platform:id）。必须与旧实现完全一致。
     */
    fun isFavoriteForFm(song: Song): Boolean {
        val key = song.platform + ":" + song.id
        return key in _favoriteKeys.value || song.id in _favoriteKeys.value
    }

    fun toggleFavoritePlaylist(playlist: PlaylistItem) {
        val list = _favoritePlaylists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        val isNowFav: Boolean
        if (idx >= 0) {
            list.removeAt(idx)
            isNowFav = false
        } else {
            list.add(0, playlist)
            isNowFav = true
        }
        _favoritePlaylists.value = list
        _favoritePlaylistIds.value = list.map { it.id }.toSet()
        FavoritePlaylistStore.save(list)
        onToast(if (isNowFav) "已收藏歌单" else "已取消收藏歌单")
    }

    fun isPlaylistFavorite(id: String): Boolean =
        id in _favoritePlaylistIds.value

    /**
     * 直接加入收藏歌单（导入外部歌单用）。
     *
     * 与 [toggleFavoritePlaylist] 的区别：**重复导入不会把歌单取消收藏**，
     * 已存在时只刷新名字/封面等元信息。导入是"收藏这个歌单"，语义上不是切换开关。
     */
    fun collectPlaylist(playlist: PlaylistItem) {
        val list = _favoritePlaylists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) list[idx] = playlist else list.add(0, playlist)
        _favoritePlaylists.value = list
        _favoritePlaylistIds.value = list.map { it.id }.toSet()
        FavoritePlaylistStore.save(list)
    }

    // ==================== 我的歌单（本地优先自治） ====================

    fun myPlaylist(id: String): MyPlaylist? = _myPlaylists.value.firstOrNull { it.id == id }

    /**
     * 新建自建歌单。
     *
     * @param cover 自定义封面：本机图片绝对路径（[com.aesthetic.music.data.settings.PlaylistCoverStore] 落盘）或网络封面 URL
     * @param sourcePlatform / [sourcePlaylistId] / [sourceName] 从外部平台导入时记录原始出处，作为"原始记录"便于溯源
     * @return 新歌单 id，便于调用方跳转到详情
     */
    fun createPlaylist(
        name: String,
        initialSongs: List<Song> = emptyList(),
        cover: String = "",
        sourcePlatform: String = "",
        sourcePlaylistId: String = "",
        sourceName: String = "",
    ): String? {
        val n = name.trim()
        if (n.isEmpty()) return null
        val p = MyPlaylist(
            id = PlaylistStore.newId(),
            name = n,
            songs = initialSongs.take(MAX_PLAYLIST_SONGS),
            cover = cover.trim(),
            sourcePlatform = sourcePlatform.trim(),
            sourcePlaylistId = sourcePlaylistId.trim(),
            sourceName = sourceName.trim(),
        )
        _myPlaylists.value = listOf(p) + _myPlaylists.value
        saveMyPlaylists()
        scheduleSyncPush()
        return p.id
    }

    fun renamePlaylist(id: String, name: String) = updatePlaylistInfo(id, name = name)

    /**
     * 编辑歌单信息（改名 / 换封面）。
     *
     * @param name null 表示不改名
     * @param cover null 表示不改封面，空串表示清除自定义封面（回到首曲封面）
     *
     * 换封面时旧的"本机封面文件"会被删除：它已不再被任何歌单引用，
     * 留着只会让私有目录随用户反复换图无限增长。
     */
    fun updatePlaylistInfo(id: String, name: String? = null, cover: String? = null) {
        val current = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        val newName = name?.trim()
        if (newName != null && newName.isEmpty()) return
        val newCover = cover?.trim()

        _myPlaylists.value = _myPlaylists.value.map {
            when {
                it.id != id -> it
                else -> it.copy(
                    name = newName ?: it.name,
                    cover = newCover ?: it.cover,
                )
            }
        }
        if (newCover != null && newCover != current.cover) {
            PlaylistCoverStore.delete(current.cover)
        }
        saveMyPlaylists()
        scheduleSyncPush()
    }

    fun deletePlaylist(id: String) {
        val target = _myPlaylists.value.firstOrNull { it.id == id }
        _myPlaylists.value = _myPlaylists.value.filterNot { it.id == id }
        // 歌单没了，它的本机封面文件也一并清掉，避免私有目录残留
        PlaylistCoverStore.delete(target?.cover)
        saveMyPlaylists()
        scheduleSyncPush()
    }

    fun addSongsToPlaylist(id: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        val merged = (target.songs + songs).distinctBy { it.platform + ":" + it.id }.take(MAX_PLAYLIST_SONGS)
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(songs = merged) else it
        }
        saveMyPlaylists()
        scheduleSyncPush()
    }

    private fun saveMyPlaylists() {
        PlaylistStore.save(_myPlaylists.value)
    }

    // ==================== 私有云同步 ====================

    fun saveSyncConfig(serverUrl: String, token: String) {
        val cleanUrl = serverUrl.trim().trimEnd('/')
        val cleanToken = token.trim()
        SyncServerStore.save(cleanUrl, cleanToken)
        _syncServerUrl.value = cleanUrl
        _syncToken.value = cleanToken
        _isSyncConfigured.value = SyncServerStore.isConfigured()
        onToast("私有云同步配置已保存")
    }

    fun testSyncConnection(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SyncServerStore.isConfigured()) {
            val msg = "请先填写完整的服务器地址和同步密钥"
            onToast(msg)
            onResult?.invoke(false, msg)
            return
        }
        scope.launch {
            _isTestingSync.value = true
            repository.testConnection().onSuccess {
                val msg = "连接正常！服务器同步功能就绪"
                onToast(msg)
                onResult?.invoke(true, msg)
            }.onFailure { e ->
                val msg = "连接失败：${e.message ?: "网络超时"}"
                onToast(msg)
                onResult?.invoke(false, msg)
            }
            _isTestingSync.value = false
        }
    }

    /** 登出时清空同步配置与状态（本地数据保留） */
    fun resetSyncState() {
        SyncServerStore.clear()
        _syncServerUrl.value = ""
        _syncToken.value = ""
        _isSyncConfigured.value = false
    }

    private fun scheduleSyncPush() {
        if (!SyncServerStore.isConfigured()) return
        favPushPending = true
        scope.launch {
            syncMutex.withLock {
                if (!favPushPending) return@withLock
                favPushPending = false
                try {
                    val payload = buildSyncPushPayload()
                    val res = repository.syncPush(payload)
                    res.getOrNull()?.let { result ->
                        SyncServerStore.setLastSyncVersion(result.version)
                        val now = System.currentTimeMillis()
                        SyncServerStore.updateSyncStatus("同步成功", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "同步成功"
                    }
                } catch (_: Exception) {
                    // 后台静默重试，不打扰用户
                }
            }
        }
    }

    private fun buildSyncPushPayload(includeEvents: Boolean = false): SyncPushPayload {
        val favUpsert = _favoriteSongs.value
        val hisUpsert = _history.value.map {
            SyncHistoryUpsert(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                artwork = it.artwork,
                platform = it.platform,
            )
        }
        val plUpsert = _myPlaylists.value.map {
            // 只把网络封面同步到云端：本机封面路径在其它设备上没有意义，
            // 传上去只会让另一台设备拿到一个必然加载失败的本地路径
            SyncPlaylistUpsert(
                id = it.id,
                name = it.name,
                coverUrl = it.coverUrl?.takeIf { url -> url.startsWith("http") },
            )
        }
        val plSongUpsert = _myPlaylists.value.flatMap { p ->
            p.songs.mapIndexed { idx, s ->
                SyncPlaylistSongUpsert(playlistId = p.id, position = idx, song = s)
            }
        }
        val events = if (includeEvents) StatsTracker.takePendingEvents() else emptyList()

        return SyncPushPayload(
            favorites = SyncCollection(upsert = favUpsert),
            history = SyncCollection(upsert = hisUpsert),
            playlists = SyncCollection(upsert = plUpsert),
            playlistSongs = SyncCollection(upsert = plSongUpsert),
            playEvents = events.ifEmpty { null },
        )
    }

    /**
     * 一键私有云增量同步 (多端 LWW + 墓碑模型)：
     * 1. 携带上次版本号 since 与事件游标 eventSince 从服务端增量拉取变动
     * 2. 本地应用删除墓碑与更新项（收藏、历史、歌单、歌单歌曲）
     * 3. 将本地最新数据快照与未上报听歌事件打包推送到云端
     * 4. 更新本地版本游标与同步状态
     */
    fun performCloudSync() {
        if (!SyncServerStore.isConfigured()) {
            onToast("未配置私有同步服务器，请前往「设置」配置")
            return
        }
        if (isSyncing.value) return

        scope.launch {
            _isSyncing.value = true
            syncMutex.withLock {
                var takenEvents: List<PlayEventDto> = emptyList()
                try {
                    val since = SyncServerStore.getLastSyncVersion()
                    val eventSince = SyncServerStore.getLastEventSince()
                    val pullResult = repository.syncPull(since = since, eventSince = eventSince)
                    if (pullResult.isFailure) {
                        val err = pullResult.exceptionOrNull()?.message ?: "拉取失败"
                        SyncServerStore.updateSyncStatus("同步失败: $err")
                        _lastSyncStatus.value = "同步失败: $err"
                        onToast("云同步失败：$err")
                        _isSyncing.value = false
                        return@withLock
                    }

                    val cloudData = pullResult.getOrNull()!!

                    // 1~3. 合并收藏 / 历史 / 歌单（LWW + 墓碑）
                    val cappedFav = mergeFavoriteSongs(_favoriteSongs.value, cloudData.favorites, MAX_FAVORITES)
                    _favoriteSongs.value = cappedFav
                    _favoriteKeys.value = cappedFav.map { it.platform + ":" + it.id }.toSet()
                    FavoriteStore.save(cappedFav)

                    val cappedHis = mergeHistory(_history.value, cloudData.history, HISTORY_LIMIT)
                    _history.value = cappedHis
                    HistoryStore.save(cappedHis)

                    _myPlaylists.value = mergePlaylists(
                        local = _myPlaylists.value,
                        cloudPlaylists = cloudData.playlists,
                        cloudSongs = cloudData.playlistSongs,
                        maxSongs = MAX_PLAYLIST_SONGS,
                    )
                    saveMyPlaylists()

                    // 4. 将合并后的最新数据回推服务端并附带听歌统计事件
                    takenEvents = StatsTracker.takePendingEvents()
                    val payload = buildSyncPushPayload(includeEvents = true)
                    val pushResult = repository.syncPush(payload)
                    if (pushResult.isFailure) {
                        StatsTracker.reenqueueEvents(takenEvents)
                        throw (pushResult.exceptionOrNull() ?: Exception("推送失败"))
                    }

                    val finalVer = pushResult.getOrNull()?.version ?: cloudData.version
                    SyncServerStore.setLastSyncVersion(finalVer)
                    SyncServerStore.setLastEventSince(cloudData.nextEventId)

                    val now = System.currentTimeMillis()
                    SyncServerStore.updateSyncStatus("同步成功", now)
                    _lastSyncedAt.value = now
                    _lastSyncStatus.value = "同步成功"
                    onToast("云端双向同步成功！数据已更新")
                } catch (e: Exception) {
                    if (takenEvents.isNotEmpty()) {
                        StatsTracker.reenqueueEvents(takenEvents)
                    }
                    val err = e.message ?: "未知错误"
                    SyncServerStore.updateSyncStatus("同步失败: $err")
                    _lastSyncStatus.value = "同步失败: $err"
                    onToast("同步异常：$err")
                } finally {
                    _isSyncing.value = false
                }
            }
        }
    }
}
