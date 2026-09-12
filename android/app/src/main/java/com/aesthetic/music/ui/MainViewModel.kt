package com.aesthetic.music.ui

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aesthetic.music.MelosApplication
import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.player.LocalLyricStore
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.service.PlaybackService
import com.aesthetic.music.ui.album.AlbumController
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.fm.FmChannel
import com.aesthetic.music.ui.fm.FmController
import com.aesthetic.music.ui.home.ContentController
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.library.PlaylistImportController
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.rank.RankController
import com.aesthetic.music.ui.profile.ProfileController
import com.aesthetic.music.ui.source.SourceController
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchType
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.sheet.SheetController
import com.aesthetic.music.ui.sheet.SheetTags
import com.aesthetic.music.data.settings.ListeningStats
import com.aesthetic.music.data.settings.LocalStats
import com.aesthetic.music.data.settings.QualityLevel
import com.aesthetic.music.data.settings.SettingsStore
import com.aesthetic.music.data.settings.ThemeMode
import com.aesthetic.music.data.settings.StatsStore
import com.aesthetic.music.data.settings.UserNotice
import com.aesthetic.music.player.DownloadPolicy
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

    // 外部歌单导入（链接/分享文案）：已抽到 PlaylistImportController（见 ui/library/PlaylistImportController.kt）
    private val playlistImport = PlaylistImportController(
        repository = repository,
        scope = viewModelScope,
        onToast = { _appToast.value = it },
        // 「导入歌单」弹窗的归属是收藏歌单：导入 = 把平台上的歌单收藏进来，
        // 与「新建歌单」表单里那条可选导入（落成自建歌单）刻意区分开
        onCollectPlaylist = { library.collectPlaylist(it) },
    )

    // 首页内容体系：已抽到 ContentController（见 ui/home/ContentController.kt）
    private val content = ContentController(repository, viewModelScope)

    /**
     * 设置项（主题与外观/播放/歌词/下载）：已抽到 SettingsController。
     *
     * 变更后立刻推给播放器，避免"改了设置要重启 App 才生效"。
     */
    val settings = SettingsController(
        onApplied = { prefs ->
            runCatching {
                PlayerManager.get(MelosApplication.instance).applyUserPrefs(prefs)
                PlaybackService.refreshCard(MelosApplication.instance)
            }
        },
    )

    // 歌单广场：已抽到 SheetController（见 ui/sheet/SheetController.kt）
    private val sheet = SheetController(repository, viewModelScope)

    // 歌手页：已抽到 ArtistController（见 ui/artist/ArtistController.kt）
    private val artistPage = ArtistController(repository, viewModelScope)

    // 专辑详情：已抽到 AlbumController（见 ui/album/AlbumController.kt）
    private val albumPage = AlbumController(repository, viewModelScope)

    // 全局搜索：已抽到 SearchController（见 ui/search/SearchController.kt）
    private val search = SearchController(repository, viewModelScope)

    // 歌单/榜单详情：已抽到 PlaylistDetailController（见 ui/playlist/PlaylistDetailController.kt）
    private val playlistDetail = PlaylistDetailController(repository, viewModelScope)

    // 排行榜页（平台 tag + 各平台榜单）：已抽到 RankController（见 ui/rank/RankController.kt）
    private val rank = RankController(repository, viewModelScope)

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

    // 排行榜页（平台 tag 切换）：已抽到 RankController，保留同名只读委托
    val rankPlatform get() = rank.platform
    val rankList get() = rank.ranks
    val isLoadingRanks get() = rank.isLoading
    val rankError get() = rank.error

    // 用户 / 个人中心状态：已抽到 ProfileController，保留同名只读委托
    val user get() = profile.user
    val profileAvatar get() = profile.profileAvatar
    val profileBackground get() = profile.profileBackground
    val isProfileBgBlur get() = profile.isProfileBgBlur
    val profileNickname get() = profile.profileNickname
    val profileBio get() = profile.profileBio
    val profileUserId get() = profile.profileUserId
    /** 登录用户名（账号名，非昵称）/ 已绑定邮箱 / 注册时间：只读，来自服务端 */
    val profileUsername get() = profile.username
    val profileEmail get() = profile.email
    val profileCreatedAt get() = profile.createdAt

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

    /**
     * 听歌统计页用的全量明细（按天/按时段/按歌曲）。
     *
     * 初值直接取内存快照（上面 [StatsStore.load] 已解析过一次，这里是白拿的），
     * 避免进页面时先闪一下"还没有听歌记录"再填上真实数据。
     */
    private val _listeningStats = MutableStateFlow(StatsStore.snapshot())
    val listeningStats: StateFlow<ListeningStats> = _listeningStats.asStateFlow()

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
    val allArtistSongs get() = artistPage.allSongs
    val isLoadingArtistAllSongs get() = artistPage.isLoadingAllSongs
    val artistAllSongsError get() = artistPage.allSongsError
    fun loadArtistAllSongs() = artistPage.loadAllSongs()

    init {
        // 冷启动异步回填历史/收藏/歌单（与窗口期内已发生的变更合并），委托 LibraryController
        library.hydrate()
        // 登录态失效（401）处理委托 ProfileController
        profile.installAuthFailedHook()
        // 最近搜索 + 热门搜索词（委托 SearchController 异步回填）
        search.hydrate()
        // 注：原先这里给"通知栏卡片的收藏按钮"注册过 LocalLyricStore 回调桥。
        // 卡片上的收藏按钮已删除（卡片控制整体不可用，只保留 media3 官方支持的播放控制），
        // 桥没有任何调用方，因此连同实现一起删了 —— 留着只会让人以为卡片收藏还活着。
        // 底层（缓存失败提示等）没有 UI 通道：统一接到全局 Snackbar
        UserNotice.register { msg -> _appToast.value = msg }
    }

    // ==================== 全局搜索（委托 SearchController） ====================

    // 搜索状态与逻辑已抽到 SearchController（见 ui/search/SearchController.kt），保留同名只读委托
    val searchQuery get() = search.query
    val searchType get() = search.type
    val searchPlatform get() = search.platform
    val songResults get() = search.songResults
    val artistResults get() = search.artistResults
    val playlistResults get() = search.playlistResults
    val albumResults get() = search.albumResults
    val isSearching get() = search.isSearching
    val searchError get() = search.searchError
    val recentSearches get() = search.recentSearches


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

    /** 异步下载/缓存歌曲并提供全局提示（用户主动下载：不受"边听边下"限制） */
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
            // 仅 WiFi 下载开启时，移动网络下不下载（明确告知原因，不做静默失败）
            val prefs = SettingsStore.readPrefs()
            if (!DownloadPolicy.allowed(context, prefs, manual = true)) {
                showToast("「${song.title}」未下载：${DownloadPolicy.blockedReason(prefs)}")
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
                manual = true,
            ) { path ->
                if (path != null) {
                    showToast("「${song.title}」下载完成")
                    saveSongSidecars(context, song)
                } else {
                    showToast("「${song.title}」下载失败")
                }
            }
        }
    }

    /** 下载成功后补封面/歌词副文件（「下载与缓存 → 封面与歌词」开启时） */
    private fun saveSongSidecars(context: android.content.Context, song: Song) {
        if (!SettingsStore.readPrefs().downloadCoverLyrics) return
        viewModelScope.launch {
            runCatching {
                repository.getLyric(song).getOrNull()?.let { d ->
                    LocalLyricStore.saveLocalLyric(context, song, d.lyric)
                }
                com.aesthetic.music.player.SongSidecarStore.saveCover(context, song)
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

    /** 进入歌单广场时调用（幂等）：没有数据才联网，避免"空列表被当成空分类" */
    fun ensureSheetPlaylistsLoaded() = sheet.ensureLoaded()

    /**
     * 首页分类卡片 → 歌单广场：先把分类归一到广场认识的标签再进页面。
     *
     * 归一化交给 [SheetTags]（有单测）：卡片上叫"电音"，广场标签是"电子"，
     * 直接拿卡片名当 cat 去请求只会得到空结果。
     */
    fun openSheetCategory(cardTitle: String?) {
        sheet.setCat(SheetTags.normalize(cardTitle, sheet.tags))
    }

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

    // ==================== 排行榜页（委托 RankController） ====================

    /** 切平台 tag；命中缓存立即出结果，未命中才联网 */
    fun selectRankPlatform(platform: String) = rank.select(platform)

    /** 进入排行榜页：无数据才拉一次，已有数据不重复请求 */
    fun ensureRanksLoaded() = rank.ensureLoaded()

    fun retryRanks() = rank.retry()

    // ==================== 外部歌单导入（委托 PlaylistImportController） ====================

    /** 「新建歌单」表单里的可选导入：解析结果仅用于回填，不落库 */
    val playlistImportPreview get() = playlistImport.preview

    /** 「导入歌单」弹窗状态：Idle / Loading / Ready / Failed */
    val playlistImportState get() = playlistImport.importState

    fun resolvePlaylistImportPreview(text: String) = playlistImport.resolveForPreview(text)

    fun clearPlaylistImportPreview() = playlistImport.clearPreview()

    fun resetPlaylistImportState() = playlistImport.resetImport()

    /**
     * 粘贴链接/文案导入：解析平台歌单 → 抓回名字、封面与曲目 → 加入**收藏歌单**。
     * 成功后回调收藏条目，供调用方跳转详情。
     */
    fun importPlaylistFromText(text: String, onImported: (PlaylistItem) -> Unit = {}) =
        playlistImport.importFromText(text, onImported)

    // ==================== 我的歌单（委托 LibraryController） ====================

    /** 新建自建歌单；[cover] 为本机封面绝对路径或网络封面 URL，返回新歌单 id */
    fun createPlaylist(
        name: String,
        initialSongs: List<Song> = emptyList(),
        cover: String = "",
        sourcePlatform: String = "",
        sourcePlaylistId: String = "",
        sourceName: String = "",
    ): String? = library.createPlaylist(
        name = name,
        initialSongs = initialSongs,
        cover = cover,
        sourcePlatform = sourcePlatform,
        sourcePlaylistId = sourcePlaylistId,
        sourceName = sourceName,
    )

    fun renamePlaylist(id: String, name: String) = library.renamePlaylist(id, name)

    /** 编辑歌单信息：name/cover 传 null 表示该项不变，cover 传空串表示清除自定义封面 */
    fun updatePlaylistInfo(id: String, name: String? = null, cover: String? = null) =
        library.updatePlaylistInfo(id, name, cover)

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

    // ==================== 音源插件与私有云同步 ====================

    // 音源插件：逻辑与状态已抽到 SourceController（见 ui/source/SourceController.kt）。
    // 提示文案经 _appToast 通道走全局 Snackbar，因此这里注入回调而非让控制器依赖 ViewModel。
    private val sources = SourceController(scope = viewModelScope, onToast = { _appToast.value = it })

    /** 已安装的插件（音源列表） */
    val sourcePlugins get() = sources.plugins

    /** 检测/实测结果，key 为检测目标 id */
    val sourceProbes get() = sources.probes

    /** 待检测/已检测的目标清单（内置四平台 + 插件声明平台） */
    val sourceTargets get() = sources.targets

    val sourceDetecting get() = sources.detecting
    val sourceDetectProgress get() = sources.detectProgress
    val sourceLastDetectAt get() = sources.lastDetectAt

    /** 当前手动指定的插件 id（策略开启时实际使用的源由 SourceManager 决定） */
    val activeSourceId get() = sources.activeId

    /** 沙箱里正在运行的插件 id 与引擎状态 */
    val loadedSourceId get() = sources.loadedPluginId
    val sourceEngineReady get() = sources.engineReady
    val sourceEngineError get() = sources.engineError
    val sourceDeclaredSources get() = sources.declaredSources

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

    // ==================== 音源插件（委托 SourceController） ====================

    /** 从文件导入：界面负责用系统文件选择器读出文本，这里只做校验与登记 */
    fun importSourceFromContent(content: String, targetPlatform: String) =
        sources.importFromContent(content, targetPlatform)

    fun importSourceFromUrl(url: String, targetPlatform: String, onFinished: (Boolean, String) -> Unit) =
        sources.importFromUrl(url, targetPlatform, onFinished)

    fun setSourceEnable(id: String, enable: Boolean) = sources.setEnable(id, enable)

    /** 指定插件适用的平台（空串 = 全平台通用） */
    fun setSourcePlatform(id: String, platformCode: String) = sources.setTargetPlatform(id, platformCode)

    fun activateSource(id: String) = sources.activate(id)

    fun reloadActiveSource() = sources.reloadActive()

    fun unloadSource() = sources.unload()

    fun removeSource(id: String) = sources.remove(id)

    fun detectSources() = sources.detectAll()

    fun detectSourceTarget(targetId: String) = sources.detectOne(targetId)

    fun refreshSources() = sources.refresh()

    // ==================== 音源策略开关（委托 SettingsController） ====================

    fun setSourceFailover(enabled: Boolean) = settings.setSourceFailover(enabled)

    fun setSourceSmartSwitch(enabled: Boolean) = settings.setSourceSmartSwitch(enabled)

    fun setSourceFastest(enabled: Boolean) = settings.setSourceFastest(enabled)

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
        avatarPath: String? = null,
    ) = profile.saveProfileInfo(nickname, bio, avatarPath)

    /** 绑定 / 解绑邮箱（空串 = 解绑），走服务端并以响应体回写本地状态 */
    fun bindEmail(email: String) = profile.bindEmail(email)

    // ==================== 统计 ====================

    fun refreshStats() {
        _localStats.value = StatsStore.load()
        refreshListeningStats()
    }

    /**
     * 刷新统计页明细。
     *
     * 解析统计 JSON（有明细后约 20~30KB）走 IO 线程，避免进页面时卡一下首帧。
     */
    fun refreshListeningStats() {
        viewModelScope.launch {
            val snapshot = withContext(Dispatchers.IO) { StatsStore.snapshot() }
            _listeningStats.value = snapshot
        }
    }

    /** 清空本机统计记录（统计页的"清空统计记录"） */
    fun clearListeningStats() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { StatsStore.clearAll() }
            refreshStats()
        }
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
