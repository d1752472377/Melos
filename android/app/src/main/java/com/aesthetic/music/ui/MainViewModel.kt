package com.aesthetic.music.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.UserInfo
import com.aesthetic.music.data.api.UserStatsDto
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.FavoriteStore
import com.aesthetic.music.data.settings.HistoryStore
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.MyPlaylist
import com.aesthetic.music.data.settings.PlaylistStore
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.UserStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AppTab { HOME, DISCOVER, PLAYLIST, PROFILE }

class MainViewModel : ViewModel() {

    private val repository = MusicRepository()

    // 页面/弹层状态
    val currentTab = MutableStateFlow(AppTab.HOME)
    val showPlayer = MutableStateFlow(false)
    val showThemeSheet = MutableStateFlow(false)
    val showSettings = MutableStateFlow(false)
    val showPlaylistDetail = MutableStateFlow<PlaylistItem?>(null)
    val showImportDialog = MutableStateFlow(false)
    val showFavorites = MutableStateFlow(false)
    val showCacheList = MutableStateFlow(false)
    val showLogin = MutableStateFlow(false)
    val showMyPlaylists = MutableStateFlow(false)
    val showCreatePlaylistDialog = MutableStateFlow(false)

    // 历史记录（最近播放，持久化：重启不丢失）
    private val _history = MutableStateFlow(HistoryStore.load())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    // 收藏（持久化：重启不丢失；登录后同步云端）
    private val _favoriteSongs = MutableStateFlow(FavoriteStore.load())
    val favoriteSongs: StateFlow<List<Song>> = _favoriteSongs.asStateFlow()

    // 收藏 key 集合（供列表红心状态使用，与 favoriteSongs 同步维护）
    private val _favoriteKeys = MutableStateFlow(
        favoriteSongs.value.map { it.platform + ":" + it.id }.toSet()
    )
    val favorites: StateFlow<Set<String>> = _favoriteKeys.asStateFlow()

    // 搜索
    val searchQuery = MutableStateFlow("")
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()
    val isSearching = MutableStateFlow(false)
    val searchError = MutableStateFlow<String?>(null)

    // 发现页：热门歌单 + 内容体系
    private val _hotPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val hotPlaylists: StateFlow<List<PlaylistItem>> = _hotPlaylists.asStateFlow()
    val isLoadingHot = MutableStateFlow(false)

    private val _dailyPlaylists = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val dailyPlaylists: StateFlow<List<PlaylistItem>> = _dailyPlaylists.asStateFlow()
    private val _newSongs = MutableStateFlow<List<Song>>(emptyList())
    val newSongs: StateFlow<List<Song>> = _newSongs.asStateFlow()
    private val _ranks = MutableStateFlow<List<PlaylistItem>>(emptyList())
    val ranks: StateFlow<List<PlaylistItem>> = _ranks.asStateFlow()
    val isLoadingContent = MutableStateFlow(false)
    val contentError = MutableStateFlow<String?>(null)

    // 歌单详情
    private val _playlistSongs = MutableStateFlow<List<Song>>(emptyList())
    val playlistSongs: StateFlow<List<Song>> = _playlistSongs.asStateFlow()
    val isLoadingPlaylist = MutableStateFlow(false)

    // 用户体系
    private val _user = MutableStateFlow(UserStore.user())
    val user: StateFlow<UserInfo?> = _user.asStateFlow()
    val authBusy = MutableStateFlow(false)
    val authError = MutableStateFlow<String?>(null)

    // 我的歌单（本地持久化，登录后同步云端）
    private val _myPlaylists = MutableStateFlow(PlaylistStore.load())
    val myPlaylists: StateFlow<List<MyPlaylist>> = _myPlaylists.asStateFlow()

    // 统计（本地真实累计 + 云端聚合）
    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()
    private val _cloudStats = MutableStateFlow<UserStatsDto?>(null)
    val cloudStats: StateFlow<UserStatsDto?> = _cloudStats.asStateFlow()

    // 主题模式
    val themeMode = MutableStateFlow(SettingsStore.getThemeMode())

    // 音质等级
    val quality = MutableStateFlow(SettingsStore.getQuality())

    // ==================== 搜索 ====================

    fun search(keyword: String) {
        val kw = keyword.trim()
        searchQuery.value = kw
        if (kw.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            isSearching.value = true
            searchError.value = null
            repository.search(kw).onSuccess { list ->
                _searchResults.value = list
            }.onFailure { e ->
                searchError.value = e.message ?: "搜索失败"
            }
            isSearching.value = false
        }
    }

    fun addToHistory(song: Song) {
        val list = _history.value.toMutableList().apply {
            removeAll { it.id == song.id && it.platform == song.platform }
            add(0, song)
        }
        _history.value = list.take(100)
        HistoryStore.save(_history.value)
        pushHistoryToCloud()
    }

    // ==================== 收藏 ====================

    fun toggleFavorite(song: Song) {
        val list = _favoriteSongs.value.toMutableList()
        val idx = list.indexOfFirst { it.id == song.id && it.platform == song.platform }
        if (idx >= 0) list.removeAt(idx) else list.add(0, song)
        _favoriteSongs.value = list
        _favoriteKeys.value = list.map { it.platform + ":" + it.id }.toSet()
        FavoriteStore.save(list)
        pushFavoritesToCloud()
    }

    fun isFavorite(song: Song): Boolean =
        (song.platform + ":" + song.id) in _favoriteKeys.value

    // ==================== 发现页 / 内容 ====================

    fun loadHotPlaylists() {
        if (_hotPlaylists.value.isNotEmpty()) return
        viewModelScope.launch {
            isLoadingHot.value = true
            repository.getHotPlaylists(20).onSuccess { list ->
                _hotPlaylists.value = list
            }
            isLoadingHot.value = false
        }
    }

    fun loadContent() {
        if (isLoadingContent.value) return
        viewModelScope.launch {
            isLoadingContent.value = true
            contentError.value = null
            val daily = repository.getDaily(8)
            val news = repository.getNewSongs(20)
            val ranks = repository.getRanks()
            daily.onSuccess { _dailyPlaylists.value = it.playlists }
            news.onSuccess { _newSongs.value = it.songs }
            ranks.onSuccess { _ranks.value = it.ranks }
            if (daily.isFailure && news.isFailure && ranks.isFailure) {
                contentError.value = "内容加载失败，请检查网络"
            }
            isLoadingContent.value = false
        }
    }

    // ==================== 歌单 ====================

    fun openPlaylistDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            _playlistSongs.value = emptyList()
            repository.getPlaylist(item.id).onSuccess { list ->
                _playlistSongs.value = list
            }
            isLoadingPlaylist.value = false
        }
    }

    fun openRankDetail(item: PlaylistItem) {
        showPlaylistDetail.value = item
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            _playlistSongs.value = emptyList()
            repository.getRankSongs(item.id).onSuccess { list ->
                _playlistSongs.value = list
            }
            isLoadingPlaylist.value = false
        }
    }

    fun importPlaylist(id: String) {
        viewModelScope.launch {
            isLoadingPlaylist.value = true
            repository.getPlaylist(id).onSuccess { list ->
                _playlistSongs.value = list
                showPlaylistDetail.value = PlaylistItem(id = id, name = "导入的歌单")
            }
            isLoadingPlaylist.value = false
        }
    }

    // ==================== 我的歌单 ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) {
        val n = name.trim()
        if (n.isEmpty()) return
        val p = MyPlaylist(id = PlaylistStore.newId(), name = n, songs = initialSongs)
        _myPlaylists.value = listOf(p) + _myPlaylists.value
        saveMyPlaylists()
        pushPlaylistToCloud(p)
        showCreatePlaylistDialog.value = false
    }

    fun renamePlaylist(id: String, name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(name = n) else it
        }
        saveMyPlaylists()
        viewModelScope.launch {
            val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return@launch
            if (p.cloudId.isNotBlank()) repository.renameCloudPlaylist(p.cloudId, n)
        }
    }

    fun deletePlaylist(id: String) {
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        _myPlaylists.value = _myPlaylists.value.filterNot { it.id == id }
        saveMyPlaylists()
        viewModelScope.launch {
            if (target.cloudId.isNotBlank()) repository.deleteCloudPlaylist(target.cloudId)
        }
    }

    fun addSongsToPlaylist(id: String, songs: List<Song>) {
        if (songs.isEmpty()) return
        val target = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        val merged = (target.songs + songs).distinctBy { it.platform + ":" + it.id }
        _myPlaylists.value = _myPlaylists.value.map {
            if (it.id == id) it.copy(songs = merged) else it
        }
        saveMyPlaylists()
        pushPlaylistSongsToCloud(id, merged)
    }

    fun openMyPlaylist(id: String) {
        val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return
        _playlistSongs.value = p.songs
        showPlaylistDetail.value = PlaylistItem(
            id = p.id,
            name = p.name,
            coverUrl = p.coverUrl,
            platform = "local",
        )
    }

    fun addCurrentToPlaylist(song: Song) {
        showCreatePlaylistDialog.value = true
        // 弹窗创建时可附带当前歌曲；通过一个待定队列实现
        pendingSongForNewPlaylist = song
    }

    private var pendingSongForNewPlaylist: Song? = null

    fun consumePendingSong(): Song? = pendingSongForNewPlaylist.also { pendingSongForNewPlaylist = null }

    fun createPlaylistWithPendingSong(name: String) {
        val song = pendingSongForNewPlaylist
        createPlaylist(name, listOfNotNull(song))
    }

    private fun saveMyPlaylists() {
        PlaylistStore.save(_myPlaylists.value)
    }

    private fun pushPlaylistToCloud(p: MyPlaylist) {
        viewModelScope.launch {
            if (!UserStore.isLoggedIn()) return@launch
            val created = repository.createCloudPlaylist(p.name, p.coverUrl).getOrNull() ?: return@launch
            _myPlaylists.value = _myPlaylists.value.map {
                if (it.id == p.id) it.copy(cloudId = created.id) else it
            }
            saveMyPlaylists()
            if (p.songs.isNotEmpty()) {
                repository.pushPlaylistSongs(created.id, p.songs)
            }
        }
    }

    private fun pushPlaylistSongsToCloud(id: String, songs: List<Song>) {
        viewModelScope.launch {
            if (!UserStore.isLoggedIn()) return@launch
            val p = _myPlaylists.value.firstOrNull { it.id == id } ?: return@launch
            if (p.cloudId.isBlank()) {
                // 尚未建过云端歌单：补建并同步
                pushPlaylistToCloud(p)
            } else {
                repository.pushPlaylistSongs(p.cloudId, songs)
            }
        }
    }

    // ==================== 用户 / 云同步 ====================

    fun showLoginSheet() {
        authError.value = null
        showLogin.value = true
    }

    fun dismissLogin() {
        showLogin.value = false
        authError.value = null
    }

    fun login(username: String, password: String) {
        val u = username.trim()
        if (u.isEmpty() || password.isEmpty()) {
            authError.value = "请输入用户名和密码"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.login(u, password).onSuccess { auth ->
                UserStore.save(auth.token, auth.user)
                _user.value = auth.user
                showLogin.value = false
                syncCloudDown()
                refreshCloudStats()
            }.onFailure { e ->
                authError.value = e.message ?: "登录失败"
            }
            authBusy.value = false
        }
    }

    fun register(username: String, password: String, nickname: String) {
        val u = username.trim()
        if (u.isEmpty() || password.length < 6) {
            authError.value = "用户名不能为空，密码至少 6 位"
            return
        }
        viewModelScope.launch {
            authBusy.value = true
            authError.value = null
            repository.register(u, password, nickname.trim().ifEmpty { u }).onSuccess { auth ->
                UserStore.save(auth.token, auth.user)
                _user.value = auth.user
                showLogin.value = false
                pushFavoritesToCloud()
                pushHistoryToCloud()
                refreshCloudStats()
            }.onFailure { e ->
                authError.value = e.message ?: "注册失败"
            }
            authBusy.value = false
        }
    }

    fun logout() {
        UserStore.logout()
        _user.value = null
        _cloudStats.value = null
    }

    fun updateNickname(nickname: String) {
        val n = nickname.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            repository.updateProfile(n).onSuccess { info ->
                _user.value = info
                UserStore.updateUser(info)
            }.onFailure { e ->
                authError.value = e.message ?: "修改失败"
            }
        }
    }

    /** 登录后：本地数据与云端合并（本地优先），并同步回云端 */
    private fun syncCloudDown() {
        viewModelScope.launch {
            // 收藏合并
            val cloudFav = repository.cloudFavorites().getOrElse { emptyList() }
            val mergedFav = mergeSongs(_favoriteSongs.value, cloudFav)
            _favoriteSongs.value = mergedFav
            _favoriteKeys.value = mergedFav.map { it.platform + ":" + it.id }.toSet()
            FavoriteStore.save(mergedFav)
            repository.pushFavorites(mergedFav)

            // 历史合并
            val cloudHis = repository.cloudHistory().getOrElse { emptyList() }
            val mergedHis = mergeSongs(_history.value, cloudHis).take(100)
            _history.value = mergedHis
            HistoryStore.save(mergedHis)
            repository.pushHistory(mergedHis)

            // 歌单合并
            val cloudPls = repository.cloudPlaylists().getOrElse { emptyList() }
            for (cp in cloudPls) {
                val exist = _myPlaylists.value.firstOrNull { it.id == cp.id || it.cloudId == cp.id }
                if (exist == null) {
                    val songs = repository.cloudPlaylistSongs(cp.id).getOrElse { emptyList() }
                    _myPlaylists.value = _myPlaylists.value + MyPlaylist(
                        id = cp.id, name = cp.name, songs = songs, cloudId = cp.id,
                    )
                }
            }
            saveMyPlaylists()
            // 本地歌单推送到云端
            for (p in _myPlaylists.value) {
                if (p.cloudId.isBlank()) {
                    val created = repository.createCloudPlaylist(p.name, p.coverUrl).getOrNull() ?: continue
                    val updated = p.copy(cloudId = created.id)
                    _myPlaylists.value = _myPlaylists.value.map { if (it.id == p.id) updated else it }
                    saveMyPlaylists()
                    if (p.songs.isNotEmpty()) repository.pushPlaylistSongs(created.id, p.songs)
                } else {
                    repository.pushPlaylistSongs(p.cloudId, p.songs)
                }
            }
        }
    }

    private fun mergeSongs(local: List<Song>, cloud: List<Song>): List<Song> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<Song>()
        for (s in (local + cloud)) {
            val key = s.platform + ":" + s.id
            if (seen.add(key)) out.add(s)
        }
        return out
    }

    private fun pushFavoritesToCloud() {
        if (!UserStore.isLoggedIn()) return
        viewModelScope.launch { repository.pushFavorites(_favoriteSongs.value) }
    }

    private fun pushHistoryToCloud() {
        if (!UserStore.isLoggedIn()) return
        viewModelScope.launch { repository.pushHistory(_history.value) }
    }

    // ==================== 统计 ====================

    fun refreshStats() {
        _localStats.value = StatsStore.load()
        if (UserStore.isLoggedIn()) refreshCloudStats()
    }

    private fun refreshCloudStats() {
        viewModelScope.launch {
            repository.userStats().onSuccess { _cloudStats.value = it }
        }
    }

    // ==================== 主题 ====================

    fun setThemeMode(mode: ThemeMode, systemDark: Boolean) {
        themeMode.value = mode
        SettingsStore.setThemeMode(mode)
        com.aesthetic.music.ui.theme.applyThemeMode(mode, systemDark)
    }

    fun setQuality(level: QualityLevel) {
        quality.value = level
        SettingsStore.setQuality(level)
    }

    // ==================== 导航 ====================

    fun switchTab(tab: AppTab) {
        currentTab.value = tab
        // 切 Tab 时关闭二级页面（歌单详情/收藏页等覆盖层），否则会盖住新 Tab 内容
        showPlaylistDetail.value = null
        showFavorites.value = false
        showCacheList.value = false
        showMyPlaylists.value = false
        if (tab == AppTab.DISCOVER) {
            loadHotPlaylists()
            loadContent()
        }
        if (tab == AppTab.PROFILE) refreshStats()
    }
}
