package com.aesthetic.music.ui.library

import android.net.Uri
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.CloudSyncPullData
import com.aesthetic.music.data.api.PlayEventDto
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.SyncCollection
import com.aesthetic.music.data.api.SyncDownloadItem
import com.aesthetic.music.data.api.SyncPushPayload
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoritePlaylistStore
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistCoverPick
import com.aesthetic.music.data.settings.PlaylistCoverStore
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.SyncServerStore
import com.aesthetic.music.data.settings.UserStore
import com.aesthetic.music.data.sync.AndroidDeviceSyncAdapter
import com.aesthetic.music.data.sync.DeviceSyncAdapter
import com.aesthetic.music.data.sync.PlaylistKind
import com.aesthetic.music.data.sync.SyncCollections
import com.aesthetic.music.data.sync.SyncKey
import com.aesthetic.music.data.sync.SyncMapper
import com.aesthetic.music.data.sync.SyncPlan
import com.aesthetic.music.data.sync.SyncStateStore
import com.aesthetic.music.service.LogReporter
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class LibraryController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val onToast: (String) -> Unit,
    private val deviceSync: DeviceSyncAdapter = AndroidDeviceSyncAdapter,
    private val onStatsChanged: (() -> Unit)? = null,
) {

    private companion object {
        private const val HISTORY_LIMIT = 100

        private const val MAX_FAVORITES = 1000

        private const val MAX_PLAYLIST_SONGS = 1000
    }

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private val _favoriteSongs = MutableStateFlow<List<Song>>(emptyList())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    private val _favoriteKeys = MutableStateFlow(emptySet<String>())
    val favorites: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    private val _favoritePlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val favoritePlaylists: StateFlow<List<PlaylistItem>> = _favoritePlaylists.asStateFlow()
    private val _favoritePlaylistIds = MutableStateFlow<Set<String>>(emptySet())
    val favoritePlaylistIds: StateFlow<Set<String>> = _favoritePlaylistIds.asStateFlow()

    private val _myPlaylists = MutableStateFlow<List<MyPlaylist>>(emptyList())
    val myPlaylists: StateFlow<List<MyPlaylist>> = _myPlaylists.asStateFlow()

    private val _syncServerUrl = MutableStateFlow(SyncServerStore.getServerUrl())
    val syncServerUrl = _syncServerUrl.asStateFlow()

    private val _isSyncConfigured = MutableStateFlow(SyncServerStore.isConfigured() && UserStore.isLoggedIn())
    val isSyncConfigured = _isSyncConfigured.asStateFlow()
    private val _lastSyncedAt = MutableStateFlow(currentSyncState().lastSyncedAt)
    val lastSyncedAt = _lastSyncedAt.asStateFlow()
    private val _lastSyncStatus = MutableStateFlow(currentSyncState().lastSyncStatus)
    val lastSyncStatus = _lastSyncStatus.asStateFlow()
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()
    private val _isTestingSync = MutableStateFlow(false)
    val isTestingSync = _isTestingSync.asStateFlow()

    private val syncMutex = Mutex()
    private var pushPending = false

    private var warnedOversizedScripts: Set<String> = emptySet()

    private var loadedForUserId: Long? = currentUserId()

    /** 冷启动回填与账号切换重载的 Job；云同步开始前必须等它们完成，避免把空/旧库当成当前账号数据 */
    private var hydrateJob: Job? = null
    private var accountLoadJob: Job? = null

    private fun currentUserId(): Long? = UserStore.user()?.id?.takeIf { it > 0 }

    private fun currentSyncState(): SyncSyncStateView {
        val uid = currentUserId() ?: return SyncSyncStateView(0L, null)
        return SyncSyncStateView(SyncStateStore.lastSyncedAt(uid), SyncStateStore.lastSyncStatus(uid))
    }

    private data class SyncSyncStateView(val lastSyncedAt: Long, val lastSyncStatus: String?)

    init {
        com.aesthetic.music.service.StatsTracker.onTriggerSync = {
            scheduleSyncPush()
        }
    }

    fun refreshSyncState() {
        _syncServerUrl.value = SyncServerStore.getServerUrl()
        _isSyncConfigured.value = SyncServerStore.isConfigured() && UserStore.isLoggedIn()
        val st = currentSyncState()
        _lastSyncedAt.value = st.lastSyncedAt
        _lastSyncStatus.value = st.lastSyncStatus
        // 账号变化 → 本地库的命名空间跟着变了，内存态必须重载成"当前账号"的数据。
        switchAccountSpace()
    }

    private fun switchAccountSpace() {
        val uid = currentUserId()
        if (loadedForUserId == uid) return
        loadedForUserId = uid
        pushPending = false
        _favoriteSongs.value = emptyList()
        _favoriteKeys.value = emptySet()
        _history.value = emptyList()
        _myPlaylists.value = emptyList()
        _favoritePlaylists.value = emptyList()
        _favoritePlaylistIds.value = emptySet()
        accountLoadJob = scope.launch(Dispatchers.IO) {
            val h = HistoryStore.load().take(HISTORY_LIMIT)
            val f = FavoriteStore.load().take(MAX_FAVORITES)
            val favP = FavoritePlaylistStore.load()
            val p = PlaylistStore.load()
            withContext(Dispatchers.Main) {
                if (loadedForUserId != uid) return@withContext
                _history.value = h
                _favoriteSongs.value = f
                _favoriteKeys.value = f.map { it.platform + ":" + it.id }.toSet()
                _favoritePlaylists.value = favP
                _favoritePlaylistIds.value = favP.map { it.id }.toSet()
                _myPlaylists.value = p
            }
        }
        com.aesthetic.music.service.StatsTracker.dropPendingEvents()
    }

    fun saveServerUrl(serverUrl: String) {
        SyncServerStore.saveServerUrl(serverUrl)
        refreshSyncState()
    }

    fun testSyncConnection(onResult: ((Boolean, String) -> Unit)? = null) {
        if (!SyncServerStore.isConfigured()) {
            val msg = "请先填写服务器地址"
            onToast(msg)
            onResult?.invoke(false, msg)
            return
        }
        scope.launch {
            _isTestingSync.value = true
            repository.testConnection().onSuccess {
                val msg = "服务器可以访问"
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

    fun hydrate() {
        if (hydrateJob?.isActive == true) return
        hydrateJob = scope.launch(Dispatchers.IO) {
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

    fun addToHistory(song: Song) {
        val list = _history.value.toMutableList().apply {
            removeAll { it.id == song.id && it.platform == song.platform }
            add(0, song)
        }
        _history.value = list.take(HISTORY_LIMIT)
        HistoryStore.save(_history.value)
        scheduleSyncPush()
    }

    fun removeFromHistory(song: Song) {
        val next = _history.value.filterNot { it.id == song.id && it.platform == song.platform }
        _history.value = next
        HistoryStore.save(next)
        SyncStateStore.recordDeleteForCurrentUser(SyncCollections.HISTORY, SyncKey.song(song))
        scheduleSyncPush()
    }

    fun clearHistory() {
        val keys = _history.value.filter { it.id.isNotBlank() }.map { SyncKey.song(it) }
        _history.value = emptyList()
        HistoryStore.save(emptyList())
        SyncStateStore.recordDeletesForCurrentUser(SyncCollections.HISTORY, keys)
        scheduleSyncPush()
    }

    fun toggleFavorite(song: Song) {
        val list = _favoriteSongs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }
        if (idx >= 0) list.removeAt(idx) else list.add(0, song)
        val capped = list.take(MAX_FAVORITES)
        _favoriteSongs.value = capped
        _favoriteKeys.value = capped.map { it.platform + ":" + it.id }.toSet()
        FavoriteStore.save(capped)
        val key = SyncKey.song(song)
        if (idx >= 0) {
            SyncStateStore.recordDeleteForCurrentUser(SyncCollections.FAVORITES, key)
        } else {
            SyncStateStore.recordAddForCurrentUser(SyncCollections.FAVORITES, key)
        }
        scheduleSyncPush()
    }

    fun isFavorite(song: Song): Boolean =
        (song.platform + ":" + song.id) in _favoriteKeys.value

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
        val key = SyncKey.playlist(PlaylistKind.FAV, playlist.id)
        if (isNowFav) {
            SyncStateStore.recordAddForCurrentUser(SyncCollections.PLAYLISTS, key)
        } else {
            SyncStateStore.recordDeleteForCurrentUser(SyncCollections.PLAYLISTS, key)
        }
        onToast(if (isNowFav) "已收藏歌单" else "已取消收藏歌单")
        scheduleSyncPush()
    }

    fun isPlaylistFavorite(id: String): Boolean =
        id in _favoritePlaylistIds.value

    fun collectPlaylist(playlist: PlaylistItem) {
        val list = _favoritePlaylists.value.toMutableList()
        val idx = list.indexOfFirst { it.id == playlist.id }
        if (idx >= 0) list[idx] = playlist else list.add(0, playlist)
        _favoritePlaylists.value = list
        _favoritePlaylistIds.value = list.map { it.id }.toSet()
        FavoritePlaylistStore.save(list)
        SyncStateStore.recordAddForCurrentUser(SyncCollections.PLAYLISTS, SyncKey.playlist(PlaylistKind.FAV, playlist.id))
        scheduleSyncPush()
    }

    fun refreshFavoritePlaylist(item: PlaylistItem) {
        if (item.id.isBlank()) return
        scope.launch {
            repository.getPlaylistDetail(item.platform, item.id).onSuccess { data ->
                val fresh = data.info ?: return@onSuccess
                val updated = item.copy(
                    name = fresh.name.ifBlank { item.name },
                    coverUrl = fresh.coverUrl ?: item.coverUrl,
                    playCount = fresh.playCount ?: item.playCount,
                    trackCount = fresh.trackCount ?: data.songs.size.toLong().takeIf { it > 0 } ?: item.trackCount,
                    description = fresh.description ?: item.description,
                )
                val list = _favoritePlaylists.value.toMutableList()
                val idx = list.indexOfFirst { it.id == item.id && it.platform == item.platform }
                if (idx >= 0) {
                    list[idx] = updated
                    _favoritePlaylists.value = list
                    FavoritePlaylistStore.save(list)
                    SyncStateStore.recordAddForCurrentUser(
                        SyncCollections.PLAYLISTS,
                        SyncKey.playlist(PlaylistKind.FAV, item.id),
                    )
                    scheduleSyncPush()
                    onToast("已更新《${updated.name}》")
                }
            }.onFailure { e ->
                onToast("更新失败：${e.message ?: "网络错误"}")
            }
        }
    }

    fun myPlaylist(id: String): MyPlaylist? = _myPlaylists.value.firstOrNull { it.id == id }

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

    fun pickPlaylistCover(uri: Uri): PlaylistCoverPick? = PlaylistCoverStore.pick(uri)

    fun deletePlaylistCover(path: String?) = PlaylistCoverStore.delete(path)

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
        PlaylistCoverStore.delete(target?.cover)
        saveMyPlaylists()
        SyncStateStore.recordDeleteForCurrentUser(SyncCollections.PLAYLISTS, SyncKey.playlist(PlaylistKind.MINE, id))
        val songKeys = target?.songs.orEmpty().filter { it.id.isNotBlank() }
            .map { SyncKey.playlistSong(PlaylistKind.MINE, id, it.platform, it.id) }
        SyncStateStore.recordDeletesForCurrentUser(SyncCollections.PLAYLIST_SONGS, songKeys)
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

    fun clearAllLocalData() {
        _favoriteSongs.value = emptyList()
        _favoriteKeys.value = emptySet()
        _history.value = emptyList()
        _myPlaylists.value = emptyList()
        _favoritePlaylists.value = emptyList()
        _favoritePlaylistIds.value = emptySet()
        FavoriteStore.clear()
        HistoryStore.clear()
        PlaylistStore.clear()
        FavoritePlaylistStore.clear()
        currentUserId()?.let { SyncStateStore.clearUser(it) }
        refreshSyncState()
    }

    private data class LocalBundle(
        val favorites: List<Song>,
        val history: List<Song>,
        val mine: List<MyPlaylist>,
        val favoritePlaylists: List<PlaylistItem>,
        val scripts: List<SyncMapper.LocalScript>,
        val downloads: List<SyncMapper.LocalDownload>,
        val deviceId: String,
        val deviceName: String,
    )

    private fun gatherLocal(): LocalBundle = LocalBundle(
        favorites = _favoriteSongs.value,
        history = _history.value,
        mine = _myPlaylists.value,
        favoritePlaylists = _favoritePlaylists.value,
        scripts = deviceSync.scripts(),
        downloads = deviceSync.downloads(),
        deviceId = deviceSync.deviceId(),
        deviceName = deviceSync.deviceName(),
    )

    private fun fingerprintsOf(bundle: LocalBundle, collection: String): Map<String, String> = when (collection) {
        SyncCollections.FAVORITES -> SyncMapper.favoriteFingerprints(bundle.favorites)
        SyncCollections.HISTORY -> SyncMapper.historyFingerprints(bundle.history)
        SyncCollections.PLAYLISTS -> SyncMapper.playlistFingerprints(bundle.mine, bundle.favoritePlaylists)
        SyncCollections.PLAYLIST_SONGS -> SyncMapper.playlistSongFingerprints(bundle.mine)
        SyncCollections.SCRIPTS -> SyncMapper.scriptFingerprints(bundle.scripts)
        SyncCollections.DOWNLOADS -> SyncMapper.downloadFingerprints(bundle.downloads, bundle.deviceId)
        else -> emptyMap()
    }

    fun performCloudSync(announce: Boolean = true) {
        val uid = currentUserId()
        if (uid == null) {
            onToast("请先登录账号，再同步云端数据")
            return
        }
        if (!SyncServerStore.isConfigured()) {
            onToast("请先填写服务器地址")
            return
        }
        if (isSyncing.value) return
        LogReporter.breadcrumb("Sync", "performCloudSync uid=$uid")

        scope.launch {
            _isSyncing.value = true
            syncMutex.withLock {
                // 整个同步过程放在 IO 线程：读写偏好/脚本文件/缓存索引都不该占用主线程
                withContext(Dispatchers.IO) {
                    var takenEvents: List<PlayEventDto> = emptyList()
                    try {
                        hydrateJob?.join()
                        accountLoadJob?.join()
                        // 登录态在排队期间可能已变化：换账号后必须重新校验，否则
                        if (currentUserId() != uid) {
                            _isSyncing.value = false
                            return@withContext
                        }
                        val since = SyncStateStore.version(uid)
                        val eventSince = SyncStateStore.eventSince(uid)
                        val pullResult = repository.syncPull(since = since, eventSince = eventSince)
                        val cloud = pullResult.getOrNull()
                        if (pullResult.isFailure || cloud == null) {
                            val err = pullResult.exceptionOrNull()?.message ?: "拉取失败"
                            markSyncFailed(uid, err, announce)
                            return@withContext
                        }

                        // 拉取期间用户可能退出/换号：此时不能把旧账号的云端数据写进新账号内存。
                        if (currentUserId() != uid) {
                            _isSyncing.value = false
                            return@withContext
                        }

                        applyCloudChanges(uid, cloud)

                        if (currentUserId() != uid) {
                            _isSyncing.value = false
                            return@withContext
                        }

                        takenEvents = pushChanges(uid, includeEvents = true)

                        // 若本地没有任何改动（只读设备/纯拉取），也必须把 since 推到头，
                        val head = maxOf(SyncStateStore.version(uid), cloud.version)
                        SyncStateStore.setVersion(uid, head)
                        SyncStateStore.setEventSince(uid, cloud.nextEventId)

                        val now = System.currentTimeMillis()
                        SyncStateStore.updateSyncStatus(uid, "同步成功", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "同步成功"
                        LogReporter.breadcrumb("Sync", "sync success uid=$uid")
                        if (announce) onToast("云端同步完成")
                    } catch (e: Exception) {
                        if (takenEvents.isNotEmpty()) StatsTracker.reenqueueEvents(takenEvents)
                        val err = e.message ?: "未知错误"
                        LogReporter.e("Sync", "同步异常 uid=$uid", e)
                        markSyncFailed(uid, err, announce)
                    } finally {
                        _isSyncing.value = false
                    }
                }
            }
        }
    }

    private fun markSyncFailed(uid: Long, err: String, announce: Boolean = true) {
        LogReporter.w("Sync", "同步失败 uid=$uid err=$err")
        val status = "同步失败: $err"
        SyncStateStore.updateSyncStatus(uid, status)
        _lastSyncStatus.value = status
        if (announce) onToast("云同步失败：$err")
    }

    private fun applyCloudChanges(uid: Long, cloud: CloudSyncPullData) {
        val appliedKeys = mutableMapOf<String, MutableSet<String>>()
        val appliedDeletes = mutableMapOf<String, MutableList<String>>()

        fun noteApplied(collection: String, key: String, deleted: Boolean) {
            if (deleted) {
                appliedDeletes.getOrPut(collection) { mutableListOf() } += key
            } else {
                appliedKeys.getOrPut(collection) { mutableSetOf() } += key
            }
        }

        fun canApply(collection: String, key: String, deleted: Boolean): Boolean {
            if (deleted) return true
            return SyncPlan.shouldApplyPulledUpsert(key, SyncStateStore.snapshot(uid, collection))
        }

        run {
            val collection = SyncCollections.FAVORITES
            var list = _favoriteSongs.value
            var changed = false
            cloud.favorites.forEach { item ->
                val key = SyncKey.song(item.platform, item.id)
                if (!canApply(collection, key, item.deleted)) return@forEach
                if (item.deleted) {
                    val next = list.filterNot { it.platform == item.platform && it.id == item.id }
                    if (next.size != list.size) changed = true
                    list = next
                } else {
                    val song = item.song ?: return@forEach
                    val next = listOf(song) + list.filterNot { it.platform == song.platform && it.id == song.id }
                    if (next != list) changed = true
                    list = next
                }
                noteApplied(collection, key, item.deleted)
            }
            if (changed) {
                val capped = list.take(MAX_FAVORITES)
                _favoriteSongs.value = capped
                _favoriteKeys.value = capped.map { it.platform + ":" + it.id }.toSet()
                FavoriteStore.save(capped)
            }
        }

        run {
            val collection = SyncCollections.HISTORY
            var list = _history.value
            var changed = false
            cloud.history.forEach { item ->
                val key = SyncKey.song(item.platform, item.id)
                if (!canApply(collection, key, item.deleted)) return@forEach
                if (item.deleted) {
                    val next = list.filterNot { it.platform == item.platform && it.id == item.id }
                    if (next.size != list.size) changed = true
                    list = next
                } else {
                    val song = item.song ?: return@forEach
                    val next = listOf(song) + list.filterNot { it.platform == song.platform && it.id == song.id }
                    if (next != list) changed = true
                    list = next
                }
                noteApplied(collection, key, item.deleted)
            }
            if (changed) {
                val capped = list.take(HISTORY_LIMIT)
                _history.value = capped
                HistoryStore.save(capped)
            }
        }

        run {
            val playlistColl = SyncCollections.PLAYLISTS
            val songColl = SyncCollections.PLAYLIST_SONGS
            var mine = _myPlaylists.value
            var fav = _favoritePlaylists.value
            var mineChanged = false
            var favChanged = false

            cloud.playlists.forEach { item ->
                val kind = item.kind.takeIf { it == PlaylistKind.FAV } ?: PlaylistKind.MINE
                val key = SyncKey.playlist(kind, item.id)
                if (!canApply(playlistColl, key, item.deleted)) return@forEach
                if (kind == PlaylistKind.MINE) {
                    val next = if (item.deleted) {
                        mine.filterNot { it.id == item.id }
                    } else {
                        val existing = mine.firstOrNull { it.id == item.id }
                        if (existing == null) {
                            listOf(MyPlaylist(id = item.id, name = item.name, cover = item.coverUrl)) + mine
                        } else {
                            mine.map {
                                if (it.id == item.id) {
                                    it.copy(
                                        name = item.name.ifBlank { it.name },
                                        cover = item.coverUrl.ifBlank { it.cover },
                                    )
                                } else it
                            }
                        }
                    }
                    if (next != mine) mineChanged = true
                    mine = next
                } else {
                    val next = if (item.deleted) {
                        fav.filterNot { it.id == item.id }
                    } else {
                        val existing = fav.firstOrNull { it.id == item.id }
                        val entry = existing?.copy(
                            name = item.name.ifBlank { existing.name },
                            coverUrl = item.coverUrl.ifBlank { existing.coverUrl.orEmpty() }.ifBlank { null },
                        ) ?: PlaylistItem(id = item.id, name = item.name, coverUrl = item.coverUrl.ifBlank { null })
                        if (existing == null) listOf(entry) + fav else fav.map { if (it.id == item.id) entry else it }
                    }
                    if (next != fav) favChanged = true
                    fav = next
                }
                noteApplied(playlistColl, key, item.deleted)
            }

            if (cloud.playlistSongs.isNotEmpty()) {
                val songsByPlaylist = cloud.playlistSongs.groupBy { it.playlistId }
                songsByPlaylist.forEach { (playlistId, items) ->
                    val target = mine.firstOrNull { it.id == playlistId } ?: return@forEach
                    val currentSongs = target.songs.toMutableList()
                    var songsModified = false

                    items.filter { it.deleted }.forEach { item ->
                        val key = SyncKey.playlistSong(PlaylistKind.MINE, item.playlistId, item.platform, item.songId)
                        if (!canApply(songColl, key, true)) return@forEach
                        val idx = currentSongs.indexOfFirst { it.platform == item.platform && it.id == item.songId }
                        if (idx >= 0) {
                            currentSongs.removeAt(idx)
                            songsModified = true
                        }
                        noteApplied(songColl, key, true)
                    }

                    val upserts = items.filter { !it.deleted && it.song != null }.sortedBy { it.position }
                    upserts.forEach { item ->
                        val key = SyncKey.playlistSong(PlaylistKind.MINE, item.playlistId, item.platform, item.songId)
                        if (!canApply(songColl, key, false)) return@forEach
                        val song = item.song ?: return@forEach
                        val idx = currentSongs.indexOfFirst { it.platform == item.platform && it.id == item.songId }
                        val at = item.position.coerceIn(0, currentSongs.size)
                        if (idx >= 0) currentSongs.removeAt(idx)
                        currentSongs.add(at.coerceAtMost(currentSongs.size), song)
                        songsModified = true
                        noteApplied(songColl, key, false)
                    }

                    if (songsModified) {
                        val updated = target.copy(songs = currentSongs.take(MAX_PLAYLIST_SONGS))
                        mine = mine.map { if (it.id == playlistId) updated else it }
                        mineChanged = true
                    }
                }
            }

            if (mineChanged) {
                _myPlaylists.value = mine
                saveMyPlaylists()
            }
            if (favChanged) {
                _favoritePlaylists.value = fav
                _favoritePlaylistIds.value = fav.map { it.id }.toSet()
                FavoritePlaylistStore.save(fav)
            }
        }

        run {
            val collection = SyncCollections.SCRIPTS
            cloud.scripts.forEach { item ->
                val key = SyncKey.script(item.id)
                if (!canApply(collection, key, item.deleted)) return@forEach
                if (item.deleted) {
                    deviceSync.removeScript(item.id)
                } else {
                    if (item.code.isBlank()) return@forEach
                    deviceSync.applyScript(
                        SyncMapper.LocalScript(
                            id = item.id,
                            name = item.name,
                            description = item.description,
                            code = item.code,
                            enabled = item.enabled,
                        )
                    )
                }
                noteApplied(collection, key, item.deleted)
            }
        }

        run {
            val collection = SyncCollections.DOWNLOADS
            val upserts = mutableListOf<SyncDownloadItem>()
            val deleted = mutableSetOf<String>()
            cloud.downloads.forEach { item ->
                val key = SyncKey.download(item.deviceId, item.platform, item.id)
                if (!canApply(collection, key, item.deleted)) return@forEach
                if (item.deleted) deleted += key else upserts += item
                noteApplied(collection, key, item.deleted)
            }
            if (upserts.isNotEmpty() || deleted.isNotEmpty()) {
                deviceSync.mergeCloudDownloads(upserts, deleted)
            }
        }

        if (cloud.playEvents.isNotEmpty()) {
            StatsStore.ingestCloudEvents(cloud.playEvents)
            onStatsChanged?.invoke()
        }

        val bundle = gatherLocal()
        SyncCollections.ALL.forEach { collection ->
            val keys = appliedKeys[collection]?.toSet().orEmpty()
            val dels = appliedDeletes[collection].orEmpty()
            if (keys.isEmpty() && dels.isEmpty()) return@forEach
            val localFps = fingerprintsOf(bundle, collection)
            val upsertFps = localFps.filterKeys { it in keys }
            SyncStateStore.saveSnapshot(
                uid,
                collection,
                SyncPlan.applyPulled(
                    SyncStateStore.snapshot(uid, collection),
                    upserts = upsertFps,
                    deletes = dels,
                ),
            )
        }
    }

    private suspend fun pushChanges(uid: Long, includeEvents: Boolean): List<PlayEventDto> {
        val bundle = gatherLocal()

        val events = if (includeEvents) StatsTracker.takePendingEvents() else emptyList()

        val collections = SyncCollections.ALL.associateWith { collection ->
            val fps = fingerprintsOf(bundle, collection)
            val snapshot = SyncStateStore.snapshot(uid, collection)
            val changed = SyncPlan.changedKeys(fps, snapshot)
            val deletes = SyncPlan.effectiveDeletes(snapshot.pendingDeletes, fps.keys)
            Triple(fps, changed, deletes)
        }

        val anyChange = collections.values.any { it.second.isNotEmpty() || it.third.isNotEmpty() }
        if (!anyChange && events.isEmpty()) return emptyList()

        fun fps(collection: String) = collections.getValue(collection).first
        fun changed(collection: String) = collections.getValue(collection).second
        fun deletes(collection: String) = collections.getValue(collection).third

        val payload = SyncPushPayload(
            favorites = syncCollection(
                changed(SyncCollections.FAVORITES), deletes(SyncCollections.FAVORITES),
                upserts = { SyncMapper.favoriteUpserts(bundle.favorites, it) },
                deleteMapper = { SyncMapper.favoriteDeletes(it) },
            ),
            history = syncCollection(
                changed(SyncCollections.HISTORY), deletes(SyncCollections.HISTORY),
                upserts = { SyncMapper.historyUpserts(bundle.history, it) },
                deleteMapper = { SyncMapper.historyDeletes(it) },
            ),
            playlists = syncCollection(
                changed(SyncCollections.PLAYLISTS), deletes(SyncCollections.PLAYLISTS),
                upserts = { SyncMapper.playlistUpserts(bundle.mine, bundle.favoritePlaylists, it) },
                deleteMapper = { SyncMapper.playlistDeletes(it) },
            ),
            playlistSongs = syncCollection(
                changed(SyncCollections.PLAYLIST_SONGS), deletes(SyncCollections.PLAYLIST_SONGS),
                upserts = { SyncMapper.playlistSongUpserts(bundle.mine, it) },
                deleteMapper = { SyncMapper.playlistSongDeletes(it) },
            ),
            scripts = syncCollection(
                changed(SyncCollections.SCRIPTS), deletes(SyncCollections.SCRIPTS),
                upserts = { SyncMapper.scriptUpserts(bundle.scripts, it) },
                deleteMapper = { SyncMapper.scriptDeletes(it) },
            ),
            downloads = syncCollection(
                changed(SyncCollections.DOWNLOADS), deletes(SyncCollections.DOWNLOADS),
                upserts = { SyncMapper.downloadUpserts(bundle.downloads, bundle.deviceId, bundle.deviceName, it) },
                deleteMapper = { SyncMapper.downloadDeletes(it) },
            ),
            playEvents = events.ifEmpty { null },
        )

        val result = repository.syncPush(payload).getOrElse { e ->
            if (events.isNotEmpty()) StatsTracker.reenqueueEvents(events)
            throw e
        }

        // 大脚本被挡在源头（见 SyncMapper.MAX_SCRIPT_BYTES）：必须让用户知道"它没同步"，
        val oversized = SyncMapper.oversizedScripts(bundle.scripts).map { it.id }.toSet()
        if (oversized.isNotEmpty() && oversized != warnedOversizedScripts) {
            warnedOversizedScripts = oversized
            LogReporter.w("Sync", "跳过超大音源脚本 ${oversized.size} 个")
            onToast("有 ${oversized.size} 个音源脚本超过 ${SyncMapper.MAX_SCRIPT_BYTES / 1024}KB，已跳过同步（仍保留在本机）")
        } else if (oversized.isEmpty()) {
            warnedOversizedScripts = emptySet()
        }

        SyncCollections.ALL.forEach { collection ->
            val (fpsMap, changedKeys, deleteKeys) = collections.getValue(collection)
            if (changedKeys.isEmpty() && deleteKeys.isEmpty()) return@forEach
            val pushedFps = fpsMap.filterKeys { it in changedKeys.toSet() }
            SyncStateStore.saveSnapshot(
                uid,
                collection,
                SyncPlan.afterPush(
                    SyncStateStore.snapshot(uid, collection),
                    pushedUpserts = pushedFps,
                    pushedDeletes = deleteKeys,
                ),
            )
        }
        SyncStateStore.setVersion(uid, result.version)
        return events
    }

    private fun <U, D> syncCollection(
        upsertKeys: List<String>,
        deleteKeys: List<String>,
        upserts: (List<String>) -> List<U>,
        deleteMapper: (List<String>) -> List<D>,
    ): SyncCollection<U, D>? {
        if (upsertKeys.isEmpty() && deleteKeys.isEmpty()) return null
        return SyncCollection(upsert = upserts(upsertKeys), delete = deleteMapper(deleteKeys))
    }

    private fun scheduleSyncPush() {
        val uid = currentUserId() ?: return
        if (!SyncServerStore.isConfigured()) return
        pushPending = true
        scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            syncMutex.withLock {
                if (!pushPending) return@withLock
                pushPending = false
                // 竞态防护：debounce 等待期间可能退出/切换了账号。推送的 Authorization 头
                if (currentUserId() != uid) return@withLock
                withContext(Dispatchers.IO) {
                    try {
                        pushChanges(uid, includeEvents = true)
                        val now = System.currentTimeMillis()
                        SyncStateStore.updateSyncStatus(uid, "已同步本地改动", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "已同步本地改动"
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }

    private val SYNC_DEBOUNCE_MS = 1200L
}
