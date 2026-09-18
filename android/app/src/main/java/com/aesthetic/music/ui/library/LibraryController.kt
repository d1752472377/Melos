package com.aesthetic.music.ui.library

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
import com.aesthetic.music.service.StatsTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * 用户音乐库控制器：收藏 + 最近播放 + 我的歌单 + 云端同步。
 *
 * 从 `MainViewModel` 抽出。这几块之所以放在同一个控制器而不是再拆开，是因为它们强耦合：
 * `scheduleSyncPush()` 被所有 mutator 调用，推送时同时读多组集合，
 * `performCloudSync()` 在同一个 [syncMutex] 内改集合、推进同步基线、并与 [StatsTracker] 的待发事件交错——
 * 拆开会改变"先拉后推 / 基线推进 / 事件重放"的顺序。`MainViewModel` 保留同名只读委托。
 *
 * 同步的纯逻辑在 `data/sync/`（[SyncPlan] 差分与基线、[SyncMapper] 字段与键、[SyncStateStore] 账号级状态），
 * 本类只做编排：取本地数据 → 调接口 → 写回本地。
 */
class LibraryController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    /** 向用户展示一次性提示（对应 ViewModel 的全局 Snackbar 通道） */
    private val onToast: (String) -> Unit,
    /** 设备相关的同步适配器（音源脚本源码、音频缓存文件）：默认走 Android 实现 */
    private val deviceSync: DeviceSyncAdapter = AndroidDeviceSyncAdapter,
    /** 漫游统计吸收后的回调通知 */
    private val onStatsChanged: (() -> Unit)? = null,
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

    /** 可同步 = 已填服务器地址 + 已登录账号（缺一不可，UI 据此提示该做哪一步） */
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

    /**
     * 同步串行互斥锁 + 待发标记。
     *
     * 所有推送/拉取排进同一把锁按序执行：并发推送会让服务端的 LWW 版本号乱序，
     * 而"待发标记"保证连续多次本地改动合并为一次推送（例如批量加入 20 首歌）。
     */
    private val syncMutex = Mutex()
    private var pushPending = false

    /** 上次已提示过的"过大而跳过同步"的脚本集合（只在变化时提示，见 pushChanges） */
    private var warnedOversizedScripts: Set<String> = emptySet()

    /** 当前登录账号的 id；未登录时为 null（同步的全部前提） */
    private fun currentUserId(): Long? = UserStore.user()?.id?.takeIf { it > 0 }

    private fun currentSyncState(): SyncSyncStateView {
        val uid = currentUserId() ?: return SyncSyncStateView(0L, null)
        return SyncSyncStateView(SyncStateStore.lastSyncedAt(uid), SyncStateStore.lastSyncStatus(uid))
    }

    /** 账号级同步状态的只读投影（未登录时全为默认值） */
    private data class SyncSyncStateView(val lastSyncedAt: Long, val lastSyncStatus: String?)

    init {
        com.aesthetic.music.service.StatsTracker.onTriggerSync = {
            scheduleSyncPush()
        }
    }

    /** 登录/退出后刷新"可同步"派生状态与状态文案（由 MainViewModel 在账号变化时调用） */
    fun refreshSyncState() {
        _syncServerUrl.value = SyncServerStore.getServerUrl()
        _isSyncConfigured.value = SyncServerStore.isConfigured() && UserStore.isLoggedIn()
        val st = currentSyncState()
        _lastSyncedAt.value = st.lastSyncedAt
        _lastSyncStatus.value = st.lastSyncStatus
    }

    /** 保存服务器地址（设备级配置，与账号无关） */
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

    /** 最近播放页：移除单条记录（本地 + 增量推送到云端） */
    fun removeFromHistory(song: Song) {
        val next = _history.value.filterNot { it.id == song.id && it.platform == song.platform }
        _history.value = next
        HistoryStore.save(next)
        // 显式删除：记成待推墓碑（只有用户主动删除才记，"数量上限裁掉"不记）
        SyncStateStore.recordDeleteForCurrentUser(SyncCollections.HISTORY, SyncKey.song(song))
        scheduleSyncPush()
    }

    /** 最近播放页：清空全部记录 */
    fun clearHistory() {
        val keys = _history.value.filter { it.id.isNotBlank() }.map { SyncKey.song(it) }
        _history.value = emptyList()
        HistoryStore.save(emptyList())
        // 清空是"把我知道的历史都删掉"：云端的对应记录也一并删（本机没见过的其它设备历史不受影响，
        // 它们会在下一次拉取时回来——那属于"云端还有记录"，而不是本地删除没生效）
        SyncStateStore.recordDeletesForCurrentUser(SyncCollections.HISTORY, keys)
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
        // 取消收藏 = 显式删除；重新收藏 = 撤销待删标记（否则下次推送会把刚加回来的收藏删掉）
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
        SyncStateStore.recordAddForCurrentUser(SyncCollections.PLAYLISTS, SyncKey.playlist(PlaylistKind.FAV, playlist.id))
        scheduleSyncPush()
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
        // 显式删除：歌单本体 + 它的全部曲目都要发墓碑，否则云端会留下"孤儿歌曲"记录
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

    /**
     * 清空本机音乐库（收藏 / 最近播放 / 自建歌单 / 收藏歌单）与同步基线。
     *
     * 用途只有一个：「退出并清空本机数据」（设备转手前抹掉个人痕迹）。
     * 只清本机、**不**清云端：用户要删账号数据得在服务端做，本地清理不该顺手动云端。
     * 音源脚本不在清理范围内——它是应用配置而不是个人听歌数据，误删会让人以为"插件没了"，
     * 想清理可以在音源页单独删除。
     */
    fun clearAllLocalData() {
        _favoriteSongs.value = emptyList()
        _favoriteKeys.value = emptySet()
        _history.value = emptyList()
        _myPlaylists.value = emptyList()
        _favoritePlaylists.value = emptyList()
        _favoritePlaylistIds.value = emptySet()
        FavoriteStore.save(emptyList())
        HistoryStore.save(emptyList())
        PlaylistStore.save(emptyList())
        FavoritePlaylistStore.save(emptyList())
        currentUserId()?.let { SyncStateStore.clearUser(it) }
        refreshSyncState()
    }

    // ==================== 云端同步（基线差分 + LWW 墓碑） ====================
    //
    // 与旧实现的区别（旧实现见 git 历史：每次把本地全量当 upsert 推、从不发删除）：
    //  1. 只推送**与上次同步基线不同**的记录 —— 不再每次把整个收藏表重写一遍；
    //  2. 只有用户**显式删除**才发墓碑 —— 本地因数量上限被裁掉的记录绝不当删除，
    //     否则一台设备的上限会把云端数据一起删掉（旧实现最危险的隐患）；
    //  3. 拉取到的远端改动先落到本地，再按"应用之后的本地指纹"推进基线 —— 不会把自己的数据再推回去；
    //  4. 本地显式删除优先于远端更新（[SyncPlan.shouldApplyPulledUpsert]）—— 删除意图不会被别的设备复活；
    //  5. 游标与基线**按账号隔离**（[SyncStateStore]）—— 换账号不会串数据。
    //
    // playEvents（听歌明细）是 append-only 事件流：只上行、不下行。
    // 服务端留档用于将来的统计视图；客户端界面上的统计仍以本地 StatsStore 为准，避免两处计数打架。

    /** 一次同步里用到的本地数据（只收集一次，避免每个集合各读一遍文件/索引） */
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

    /** 各集合当前的指纹基线（键规则见 [SyncKey]，内容规则见 [SyncFingerprint]） */
    private fun fingerprintsOf(bundle: LocalBundle, collection: String): Map<String, String> = when (collection) {
        SyncCollections.FAVORITES -> SyncMapper.favoriteFingerprints(bundle.favorites)
        SyncCollections.HISTORY -> SyncMapper.historyFingerprints(bundle.history)
        SyncCollections.PLAYLISTS -> SyncMapper.playlistFingerprints(bundle.mine, bundle.favoritePlaylists)
        SyncCollections.PLAYLIST_SONGS -> SyncMapper.playlistSongFingerprints(bundle.mine)
        SyncCollections.SCRIPTS -> SyncMapper.scriptFingerprints(bundle.scripts)
        SyncCollections.DOWNLOADS -> SyncMapper.downloadFingerprints(bundle.downloads, bundle.deviceId)
        else -> emptyMap()
    }

    /**
     * 一键双向同步：拉取云端增量 → 应用到本地 → 只推送本地差异 → 推进游标与基线。
     *
     * 顺序很关键：**先拉后推**。先推会把本机尚未合并的旧状态写进服务端（LWW 下即"本机覆盖云端"），
     * 而先拉再推的语义是"以云端为基准，叠加本机自上次同步之后的改动"——这也是用户能预期的行为。
     */
    fun performCloudSync() {
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

        scope.launch {
            _isSyncing.value = true
            syncMutex.withLock {
                // 整个同步过程放在 IO 线程：读写偏好/脚本文件/缓存索引都不该占用主线程
                withContext(Dispatchers.IO) {
                    var takenEvents: List<PlayEventDto> = emptyList()
                    try {
                        val since = SyncStateStore.version(uid)
                        val eventSince = SyncStateStore.eventSince(uid)
                        val pullResult = repository.syncPull(since = since, eventSince = eventSince)
                        val cloud = pullResult.getOrNull()
                        if (pullResult.isFailure || cloud == null) {
                            val err = pullResult.exceptionOrNull()?.message ?: "拉取失败"
                            markSyncFailed(uid, err)
                            return@withContext
                        }

                        // 1. 云端改动落到本地（含墓碑），并按"应用后的本地状态"推进基线
                        applyCloudChanges(uid, cloud)

                        // 2. 只把本地差异推上去（含显式删除的墓碑）
                        takenEvents = pushChanges(uid, includeEvents = true)

                        // 3. 推进游标。
                        // 版本号取"服务端本次的头"与"本次推送后的版本"中的较大者：
                        // 若本地没有任何改动（只读设备/纯拉取），也必须把 since 推到头，
                        // 否则每轮同步都会重复拉取同一批云端改动，游标形同失效。
                        val head = maxOf(SyncStateStore.version(uid), cloud.version)
                        SyncStateStore.setVersion(uid, head)
                        SyncStateStore.setEventSince(uid, cloud.nextEventId)

                        val now = System.currentTimeMillis()
                        SyncStateStore.updateSyncStatus(uid, "同步成功", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "同步成功"
                        onToast("云端同步完成")
                    } catch (e: Exception) {
                        if (takenEvents.isNotEmpty()) StatsTracker.reenqueueEvents(takenEvents)
                        val err = e.message ?: "未知错误"
                        markSyncFailed(uid, err)
                    } finally {
                        _isSyncing.value = false
                    }
                }
            }
        }
    }

    private fun markSyncFailed(uid: Long, err: String) {
        val status = "同步失败: $err"
        SyncStateStore.updateSyncStatus(uid, status)
        _lastSyncStatus.value = status
        onToast("云同步失败：$err")
    }

    /**
     * 把云端增量应用到本地，并推进基线。
     *
     * 基线推进的规则：**只把真正应用到本地的键**写成新基线，且新指纹取"应用之后的本地状态"
     * （用 [fingerprintsOf] 重算）。这样：
     *  - 远端内容与本地合并结果一致 → 不会把自己的数据再推回去；
     *  - 因本地显式删除而**跳过**的远端更新 → 基线不动，待删标记仍在，下次推送按墓碑发上去。
     */
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

        // ===== 收藏 =====
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

        // ===== 最近播放 =====
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

        // ===== 歌单（自建 + 收藏的网络歌单，用 kind 区分）与 歌单曲目 =====
        run {
            val playlistColl = SyncCollections.PLAYLISTS
            val songColl = SyncCollections.PLAYLIST_SONGS
            var mine = _myPlaylists.value
            var fav = _favoritePlaylists.value
            var mineChanged = false
            var favChanged = false

            // 1. 处理歌单基本信息（自建 + 收藏）
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

            // 2. 处理歌单曲目（按歌单聚合，先删后按 position 有序插入）
            if (cloud.playlistSongs.isNotEmpty()) {
                val songsByPlaylist = cloud.playlistSongs.groupBy { it.playlistId }
                songsByPlaylist.forEach { (playlistId, items) ->
                    val target = mine.firstOrNull { it.id == playlistId } ?: return@forEach
                    val currentSongs = target.songs.toMutableList()
                    var songsModified = false

                    // 墓碑删除
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

                    // 增量插入（按 position 升序应用，保证顺序确定）
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

        // ===== 音源脚本（源码存在文件里，交给设备适配器）=====
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

        // ===== 下载记录（本机文件不会凭空出现，只落"账"）=====
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

        // ===== 听歌统计（多端漫游：吸收其它设备的播放事件）=====
        if (cloud.playEvents.isNotEmpty()) {
            StatsStore.ingestCloudEvents(cloud.playEvents)
            onStatsChanged?.invoke()
        }

        // ===== 基线推进（用"应用之后的本地指纹"重算，避免回推自己）=====
        val bundle = gatherLocal()
        SyncCollections.ALL.forEach { collection ->
            val keys = appliedKeys[collection]?.toSet().orEmpty()
            val dels = appliedDeletes[collection].orEmpty()
            if (keys.isEmpty() && dels.isEmpty()) return@forEach
            val localFps = fingerprintsOf(bundle, collection)
            // 只吸收真正落到本地的键：被"本地显式删除"挡下的键不进基线（待删标记仍在）
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

    /**
     * 计算并推送本地差异；返回本次已取走、但若推送失败需要回滚的听歌事件。
     *
     * 没有差异时**不发送** push 请求（省一次往返），但仍要把版本号推进到服务端最新
     * （否则下次还会从同一个游标重复拉取同一批云端改动）。
     */
    private suspend fun pushChanges(uid: Long, includeEvents: Boolean): List<PlayEventDto> {
        val bundle = gatherLocal()

        // 先取事件（取走即从队列移除；推送失败由调用方 reenqueue 回去，保证不丢也不重复）
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
        // 否则表现是"我这台机器上有这个脚本，另一台永远没有"，且没有任何提示。
        // 只在集合变化（数量或成员不同）时提示一次，避免每次同步都弹。
        val oversized = SyncMapper.oversizedScripts(bundle.scripts).map { it.id }.toSet()
        if (oversized.isNotEmpty() && oversized != warnedOversizedScripts) {
            warnedOversizedScripts = oversized
            onToast("有 ${oversized.size} 个音源脚本超过 ${SyncMapper.MAX_SCRIPT_BYTES / 1024}KB，已跳过同步（仍保留在本机）")
        } else if (oversized.isEmpty()) {
            warnedOversizedScripts = emptySet()
        }

        // 推送成功：只把本次真正发出去的键吸收进基线（推送期间的新改动不会被误标为已同步）
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

    /** 组一个集合的推送内容；两边都空时返回 null（服务端对缺省集合不做任何事） */
    private fun <U, D> syncCollection(
        upsertKeys: List<String>,
        deleteKeys: List<String>,
        upserts: (List<String>) -> List<U>,
        deleteMapper: (List<String>) -> List<D>,
    ): SyncCollection<U, D>? {
        if (upsertKeys.isEmpty() && deleteKeys.isEmpty()) return null
        return SyncCollection(upsert = upserts(upsertKeys), delete = deleteMapper(deleteKeys))
    }

    /**
     * 本地改动后的**去抖推送**（不做拉取）。
     *
     * 为什么可以只推不拉：推送内容只有"本机自上次同步后的改动"，
     * 不会把别的设备已删除的记录写活（那正是旧实现的全量 upsert 会干的事）；
     * 远端改动会在下一次 [performCloudSync] 拉取时按墓碑处理。
     */
    private fun scheduleSyncPush() {
        val uid = currentUserId() ?: return
        if (!SyncServerStore.isConfigured()) return
        pushPending = true
        scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            syncMutex.withLock {
                if (!pushPending) return@withLock
                pushPending = false
                withContext(Dispatchers.IO) {
                    try {
                        pushChanges(uid, includeEvents = true)
                        val now = System.currentTimeMillis()
                        SyncStateStore.updateSyncStatus(uid, "已同步本地改动", now)
                        _lastSyncedAt.value = now
                        _lastSyncStatus.value = "已同步本地改动"
                    } catch (_: Exception) {
                        // 静默失败：本地基线没有推进，下一次同步（自动或手动）会重新带上这批改动
                    }
                }
            }
        }
    }

    /** 本地改动去抖窗口：连续操作（批量加歌/连续删收藏）合并成一次推送 */
    private val SYNC_DEBOUNCE_MS = 1200L
}
