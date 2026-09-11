package com.aesthetic.music.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.album.AlbumController
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.fm.FmChannel
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.profile.ProfileController
import com.aesthetic.music.ui.script.ScriptController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchType
import com.aesthetic.music.ui.sheet.SheetController
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.StatsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppTab { HOME, SHEET, PLAYLIST, PROFILE }

class MainViewModel : ViewModel() {

    private val repository = MusicRepository()

    // 用户音乐库（收藏/历史/我的歌单/云同步）：已抽到 LibraryController（见 ui/library/LibraryController.kt）
    private val library = LibraryController(repository, viewModelScope, onToast = { _appToast.value = it })

    // 首页内容体系：已抽到 ContentController（见 ui/home/ContentController.kt）
    private val content = ContentController(repository, viewModelScope)

    // 歌单广场：已抽到 SheetController（见 ui/sheet/SheetController.kt）
    private val sheet = SheetController(repository, viewModelScope)

    // 歌手页：已抽到 ArtistController（见 ui/artist/ArtistController.kt）
    private val artistPage = ArtistController(repository, viewModelScope)

    // 专辑详情：已抽到 AlbumController（见 ui/album/AlbumController.kt）
    private val albumPage = AlbumController(repository, viewModelScope)

    // 全局搜索：已抽到 SearchController（见 ui/search/SearchController.kt）
    private val search = SearchController(repository, viewModelScope)

    // 歌单/榜单详情：已抽到 PlaylistDetailController（见 ui/playlist/PlaylistDetailController.kt）
    private val playlistDetail =
        PlaylistDetailController(repository, viewModelScope, onToast = { _appToast.value = it })

    // 用户 / 个人中心：已抽到 ProfileController（见 ui/profile/ProfileController.kt）
    private val profile = ProfileController(
        repository = repository,
        scope = viewModelScope,
        onToast = { _appToast.value = it },
        onLoggedOut = { library.resetSyncState() },
    )

    // 页面/弹层状态
    private val _currentTab = MutableStateFlow(AppTab.HOME)
    val currentTab = _currentTab.asStateFlow()
    private val _showPlayer = MutableStateFlow(false)
    val showPlayer = _showPlayer.asStateFlow()
    val showPlaylistDetail get() = playlistDetail.showDetail
    private val _showImportDialog = MutableStateFlow(false)
    val showImportDialog = _showImportDialog.asStateFlow()
    /** 最近播放独立页（从"我的"页进入；不再跳转到歌单 Tab） */
    private val _showHistory = MutableStateFlow(false)
    val showHistory = _showHistory.asStateFlow()

    // 弹层/播放器的显式开关。
    // 这些状态对外只读（暴露 StateFlow 而非 MutableStateFlow），外部只能经这些方法变更，
    // 保证"谁在什么时候改了什么"可追溯——此前 UI 可直接对 MutableStateFlow 赋值，
    // 状态变更散落在视图层，排查问题时无法确定来源。
    fun openPlayer() { _showPlayer.value = true }
    fun closePlayer() { _showPlayer.value = false }
    fun openImportDialog() { _showImportDialog.value = true }
    fun closeImportDialog() { _showImportDialog.value = false }
    fun closePlaylistDetail() = playlistDetail.close()



    // 历史 / 收藏 / 收藏歌单：已抽到 LibraryController，保留同名只读委托
    val history get() = library.history
    val favoriteSongs get() = library.favoriteSongs
    val favorites get() = library.favorites
    val favoritePlaylists get() = library.favoritePlaylists

    // （全局搜索相关状态见下方「==================== 全局搜索 ====================」区块）

    // 内容体系（首页 / 分类页 / 排行榜页共用）：已抽到 ContentController，保留同名只读委托
    val hotPlaylists get() = content.hotPlaylists
    val isLoadingHot get() = content.isLoadingHot
    val hotPlaylistsError get() = content.hotPlaylistsError
    val dailyPlaylists get() = content.dailyPlaylists
    val newSongs get() = content.newSongs
    val ranks get() = content.ranks
    val hotSongs get() = content.hotSongs
    val homeCategories get() = content.homeCategories
    val isLoadingContent get() = content.isLoadingContent
    val contentError get() = content.contentError

    // 歌单广场（覆盖层，从首页分类歌单进入）：已抽到 SheetController，保留同名只读委托
    val sheetTags get() = sheet.tags
    val sheetCat get() = sheet.cat
    val sheetOrder get() = sheet.order
    val sheetPlaylists get() = sheet.playlists
    val isLoadingSheets get() = sheet.isLoading
    val sheetError get() = sheet.error

    // FM 电台：逻辑与状态已抽到 FmController（见 ui/fm/FmController.kt）。
    // 这里保留同名的只读委托，视图层无需改动；后续可让 FM 页直接持有控制器。
    private val fm = FmController(
        repository = repository,
        scope = viewModelScope,
        onToggleFavorite = { library.toggleFavorite(it) },
        // 兼容旧数据（额外接受裸 song.id）的判断由 LibraryController.isFavoriteForFm 提供，
        // 保持与原实现完全一致，否则老用户的收藏在 FM 里会被判为未收藏。
        isFavorite = { song -> library.isFavoriteForFm(song) },
    )

    val fmChannels get() = fm.channels
    val fmStyles get() = fm.styles
    val currentFmChannel get() = fm.currentChannel
    val fmStyle get() = fm.style
    val fmQueue get() = fm.queue
    val fmIndex get() = fm.index
    val fmLoading get() = fm.loading
    val fmError get() = fm.error
    val fmTreasure get() = fm.treasure

    // 歌单详情：已抽到 PlaylistDetailController，保留同名只读委托
    val playlistSongs get() = playlistDetail.songs
    val isLoadingPlaylist get() = playlistDetail.isLoading
    val playlistError get() = playlistDetail.error

    // 用户 / 个人中心状态：已抽到 ProfileController，保留同名只读委托
    val user get() = profile.user
    val profileAvatar get() = profile.profileAvatar
    val profileBackground get() = profile.profileBackground
    val isProfileBgBlur get() = profile.isProfileBgBlur
    val profileNickname get() = profile.profileNickname
    val profileBio get() = profile.profileBio
    val profileGender get() = profile.profileGender
    val profileBirthday get() = profile.profileBirthday
    val profileUserId get() = profile.profileUserId

    /** 一次性全局提示（导入歌单失败、改昵称失败、登录态过期等）：
     *  此前这些场景的错误要么写进没人消费的状态（albumError），要么复用只被登录弹窗读的
     *  authError，用户"点了没反应"。AppShell 订阅本流弹 Snackbar 后调用 consumeToast() 清空 */
    private val _appToast = MutableStateFlow<String?>(null)
    val appToast: StateFlow<String?> = _appToast.asStateFlow()

    fun consumeToast() { _appToast.value = null }

    // 我的歌单：已抽到 LibraryController，保留同名只读委托
    val myPlaylists get() = library.myPlaylists

    // 统计（本地真实累计）
    private val _localStats = MutableStateFlow(StatsStore.load())
    val localStats: StateFlow<LocalStats> = _localStats.asStateFlow()

    // 主题模式
    private val _themeMode = MutableStateFlow(SettingsStore.getThemeMode())
    val themeMode = _themeMode.asStateFlow()

    // 音质等级
    private val _quality = MutableStateFlow(SettingsStore.getQuality())
    val quality = _quality.asStateFlow()

    // 歌手页：已抽到 ArtistController，保留同名只读委托
    val showArtist get() = artistPage.showArtist
    val artistDetail get() = artistPage.artistDetail
    val isLoadingArtist get() = artistPage.isLoading
    val artistError get() = artistPage.error

    init {
        // 冷启动异步回填历史/收藏/歌单（与窗口期内已发生的变更合并），委托 LibraryController
        library.hydrate()
        // 登录态失效（401）处理委托 ProfileController
        profile.installAuthFailedHook()
        // 最近搜索 + 热门搜索词（委托 SearchController 异步回填）
        search.hydrate()
    }

    // ==================== 全局搜索（委托 SearchController） ====================

    // 搜索状态与逻辑已抽到 SearchController（见 ui/search/SearchController.kt），保留同名只读委托
    val searchQuery get() = search.query
    val searchType get() = search.type
    val searchPlatform get() = search.platform
    val songResults get() = search.songResults
    val lyricResults get() = search.lyricResults
    val artistResults get() = search.artistResults
    val playlistResults get() = search.playlistResults
    val albumResults get() = search.albumResults
    val isSearching get() = search.isSearching
    val searchError get() = search.searchError
    val recentSearches get() = search.recentSearches
    val hotSearchWords get() = search.hotSearchWords

    /** 专辑详情覆盖层（搜索页专辑 Tab 点击进入）：已抽到 AlbumController，保留同名只读委托 */
    val showAlbumDetail get() = albumPage.showAlbumDetail
    val albumDetailSongs get() = albumPage.albumDetailSongs
    val isLoadingAlbum get() = albumPage.isLoading
    val albumError get() = albumPage.error

    fun openAlbum(album: AlbumItem) = albumPage.open(album)

    fun closeAlbum() = albumPage.close()

    // 搜索操作：委托 SearchController
    fun onSearchQueryChanged(q: String) = search.onQueryChanged(q)

    fun submitSearch(word: String? = null) = search.submit(word)

    fun setSearchType(type: SearchType) = search.setType(type)

    fun setSearchPlatform(platform: String?) = search.setPlatform(platform)

    fun clearRecentSearches() = search.clearRecent()

    // ==================== 歌手页（委托 ArtistController） ====================

    fun openArtist(artist: ArtistItem) = artistPage.open(artist)

    fun openArtist(ref: ArtistRef) = artistPage.open(ref)

    fun closeArtist() = artistPage.close()

    // ==================== 最近播放 / 收藏（委托 LibraryController） ====================

    fun addToHistory(song: Song) = library.addToHistory(song)

    fun removeFromHistory(song: Song) = library.removeFromHistory(song)

    fun clearHistory() = library.clearHistory()

    fun openHistory() { _showHistory.value = true }
    fun closeHistory() { _showHistory.value = false }

    fun toggleFavorite(song: Song) = library.toggleFavorite(song)

    fun isFavorite(song: Song): Boolean = library.isFavorite(song)

    fun toggleFavoritePlaylist(playlist: PlaylistItem) = library.toggleFavoritePlaylist(playlist)

    fun isPlaylistFavorite(id: String): Boolean = library.isPlaylistFavorite(id)

    fun showToast(msg: String) {
        _appToast.value = msg
    }

    /** 异步下载/缓存歌曲并提供全局提示 */
    fun downloadSong(context: android.content.Context, song: Song) {
        viewModelScope.launch {
            val quality = SettingsStore.getQuality().value
            val cached = withContext(Dispatchers.IO) {
                com.aesthetic.music.player.AudioCacheService.getCachedPath(context, song.platform, song.id, quality)
            }
            if (cached != null) {
                showToast("「${song.title}」已在本地下载")
                return@launch
            }
            showToast("正在获取「${song.title}」下载链接…")
            val r = repository.getSongUrl(song, quality)
            val url = r.getOrNull()?.url
            if (url.isNullOrBlank()) {
                showToast("下载失败：未能获取「${song.title}」直链")
                return@launch
            }
            showToast("开始下载「${song.title}」…")
            com.aesthetic.music.player.AudioCacheService.cacheAudio(
                context = context,
                platformCode = song.platform,
                songId = song.id,
                url = url,
                name = song.title,
                artist = song.artist,
                quality = quality,
                scope = viewModelScope,
            ) { path ->
                if (path != null) {
                    showToast("「${song.title}」下载完成")
                } else {
                    showToast("「${song.title}」下载失败")
                }
            }
        }
    }

    // ==================== 内容加载（委托 ContentController） ====================

    fun loadHotPlaylists(force: Boolean = false) = content.loadHotPlaylists(force)

    fun loadContent(force: Boolean = false) = content.loadContent(force)

    /** 用户点"重试"时强制重新拉取内容 */
    fun refreshContent() = content.refreshContent()

    // ==================== 歌单广场（委托 SheetController） ====================

    fun setSheetCat(cat: String) = sheet.setCat(cat)

    fun setSheetOrder(order: String) = sheet.setOrder(order)

    fun loadSheetPlaylists(force: Boolean = false) = sheet.load(force)

    // ==================== FM 电台（委托 FmController） ====================

    fun fmStart(channel: FmChannel = fm.channels[0], onReady: ((Song, List<Song>) -> Unit)? = null) =
        fm.start(channel, onReady)

    fun fmStart(style: String) = fm.start(style)

    fun fmRefresh(onReady: ((Song, List<Song>) -> Unit)? = null) = fm.refresh(onReady)

    fun fmSelect(index: Int) = fm.select(index)

    fun fmDislike(song: Song, onPlayNext: ((Song, List<Song>) -> Unit)? = null) =
        fm.dislike(song, onPlayNext)

    fun fmToggleLike(song: Song) = fm.toggleLike(song)

    fun fmOnSignal(song: Song) = fm.onSignal(song)

    fun fmStopDrift() = fm.stopDrift()

    // ==================== 歌单（委托 PlaylistDetailController） ====================

    fun openPlaylistDetail(item: PlaylistItem) = playlistDetail.open(item)

    fun openRankDetail(item: PlaylistItem) = playlistDetail.openRank(item)

    fun importPlaylist(id: String) = playlistDetail.import(id)

    // ==================== 我的歌单（委托 LibraryController） ====================

    fun createPlaylist(name: String, initialSongs: List<Song> = emptyList()) =
        library.createPlaylist(name, initialSongs)

    fun renamePlaylist(id: String, name: String) = library.renamePlaylist(id, name)

    fun deletePlaylist(id: String) = library.deletePlaylist(id)

    fun addSongsToPlaylist(id: String, songs: List<Song>) = library.addSongsToPlaylist(id, songs)

    fun openMyPlaylist(id: String) {
        val p = library.myPlaylist(id) ?: return
        playlistDetail.showLocal(
            item = PlaylistItem(
                id = p.id,
                name = p.name,
                coverUrl = p.coverUrl,
                trackCount = p.songs.size.toLong(),
                platform = "local",
            ),
            songs = p.songs,
        )
    }

    // ==================== 音源脚本与私有云同步 ====================

    // 音源脚本：逻辑与状态已抽到 ScriptController（见 ui/script/ScriptController.kt）。
    // 提示文案经 _appToast 通道走全局 Snackbar，因此这里注入回调而非让控制器依赖 ViewModel。
    private val script = ScriptController(scope = viewModelScope, onToast = { _appToast.value = it })

    val activeScriptInfo get() = script.activeScriptInfo
    val isEngineReady get() = script.isEngineReady
    val engineError get() = script.engineError
    val scriptCatalog get() = script.catalog

    // 私有云同步状态：已抽到 LibraryController，保留同名只读委托
    val syncServerUrl get() = library.syncServerUrl
    val syncToken get() = library.syncToken
    val isSyncConfigured get() = library.isSyncConfigured
    val lastSyncedAt get() = library.lastSyncedAt
    val lastSyncStatus get() = library.lastSyncStatus
    val isSyncing get() = library.isSyncing

    fun saveSyncConfig(serverUrl: String, token: String) = library.saveSyncConfig(serverUrl, token)

    fun testSyncConnection(onResult: ((Boolean, String) -> Unit)? = null) =
        library.testSyncConnection(onResult)

    fun performCloudSync() = library.performCloudSync()

    // ==================== 音源脚本（委托 ScriptController） ====================

    fun importScriptFromText(content: String, name: String? = null) = script.importFromText(content, name)

    fun importScriptFromUrl(url: String, onFinished: (Boolean, String) -> Unit) =
        script.importFromUrl(url, onFinished)

    fun retryLoadScript() = script.retryLoad()

    fun unloadScript() = script.unload()

    fun switchScript(id: String) = script.switchTo(id)

    // ==================== 用户体系 / 个人中心（委托 ProfileController） ====================

    fun logout() = profile.logout()

    fun updateNickname(nickname: String) = profile.updateNickname(nickname)

    fun setProfileAvatar(path: String?) = profile.setProfileAvatar(path)

    fun setProfileBackground(path: String?) = profile.setProfileBackground(path)

    fun toggleProfileBgBlur() = profile.toggleProfileBgBlur()

    fun restoreProfileDefaults(context: android.content.Context) =
        profile.restoreProfileDefaults(context)

    fun saveProfileInfo(
        nickname: String,
        bio: String,
        gender: String,
        birthday: String,
        avatarPath: String? = null,
    ) = profile.saveProfileInfo(nickname, bio, gender, birthday, avatarPath)

    // ==================== 统计 ====================

    fun refreshStats() {
        _localStats.value = StatsStore.load()
    }

    // ==================== 主题 ====================

    fun setThemeMode(mode: ThemeMode, systemDark: Boolean) {
        _themeMode.value = mode
        SettingsStore.setThemeMode(mode)
        com.aesthetic.music.ui.theme.applyThemeMode(mode, systemDark)
    }

    fun setQuality(level: QualityLevel) {
        _quality.value = level
        SettingsStore.setQuality(level)
    }

    // ==================== 导航 ====================

    fun switchTab(tab: AppTab) {
        _currentTab.value = tab
        // 切 Tab 时关闭二级页面（歌单详情/歌手页/专辑详情/最近播放等），否则会盖住新 Tab 内容
        playlistDetail.close()
        artistPage.close()
        albumPage.close()
        _showHistory.value = false
        // FM 漂移开关由 FmController 持有（切走 FM 页即暂停漂移）
        fm.setDriftActive(tab == AppTab.SHEET)
        if (tab == AppTab.PROFILE) refreshStats()
    }

    private val _currentPageBackground = MutableStateFlow<Color?>(null)
    val currentPageBackground: StateFlow<Color?> = _currentPageBackground.asStateFlow()
    private val _isDarkPage = MutableStateFlow(false)
    val isDarkPage: StateFlow<Boolean> = _isDarkPage.asStateFlow()

    fun updatePageBackground(color: Color?, isDark: Boolean) {
        _currentPageBackground.value = color
        _isDarkPage.value = isDark
    }
}
