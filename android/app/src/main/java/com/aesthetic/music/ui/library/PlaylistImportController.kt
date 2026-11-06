package com.aesthetic.music.ui.library

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.PlaylistLink
import com.aesthetic.music.data.api.PlaylistLinkParser
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 外部歌单（链接/文案）解析与导入状态 */
sealed interface PlaylistResolveState {

    data object Idle : PlaylistResolveState

    data object Loading : PlaylistResolveState

    /** 解析成功：歌单名 / 封面 / 曲目均已抓到，可直接落成本地歌单 */
    data class Ready(
        val platform: String,
        val sourcePlaylistId: String,
        val name: String,
        val coverUrl: String?,
        val songs: List<Song>,
    ) : PlaylistResolveState {
        val platformName: String get() = PlaylistLinkParser.platformName(platform)
    }

    data class Failed(val message: String) : PlaylistResolveState
}

/**
 * 外部歌单导入控制器。
 *
 * 两条链路的归属刻意不同，别混：
 * - [resolveForPreview]：「新建歌单」表单里的**可选**导入，只回填名字/封面/曲目给用户预览与修改，
 *   用户点保存后才由 `LibraryController.createPlaylist` 落成**自建歌单**（来源平台与原始 ID 一并记录）；
 * - [importFromText]：「导入歌单」弹窗（收藏歌单页的入口），导入结果是**收藏歌单**——
 *   它只是把平台上的歌单收进来听，不该混进"我自己建的歌单"。
 */
class PlaylistImportController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    /** 一次性提示（导入完成/失败） */
    private val onToast: (String) -> Unit,
    /** 加入收藏歌单（已存在则刷新信息）；由 LibraryController 实现 */
    private val onCollectPlaylist: (PlaylistItem) -> Unit,
) {

    /** 「新建歌单」里的可选导入状态 */
    private val _preview = MutableStateFlow<PlaylistResolveState>(PlaylistResolveState.Idle)
    val preview: StateFlow<PlaylistResolveState> = _preview.asStateFlow()

    /** 「导入歌单」弹窗状态 */
    private val _import = MutableStateFlow<PlaylistResolveState>(PlaylistResolveState.Idle)
    val importState: StateFlow<PlaylistResolveState> = _import.asStateFlow()

    fun resolveForPreview(text: String) = resolve(text) { _preview.value = it }

    fun clearPreview() {
        _preview.value = PlaylistResolveState.Idle
    }

    fun resetImport() {
        _import.value = PlaylistResolveState.Idle
    }

    /** 导入并收藏：抓到歌单名/封面/曲目后加入收藏歌单，成功后回调收藏条目（供跳转详情） */
    fun importFromText(text: String, onImported: (PlaylistItem) -> Unit) {
        _import.value = PlaylistResolveState.Loading
        scope.launch {
            val link = resolveLink(text)
            if (link == null) {
                _import.value = PlaylistResolveState.Failed(parseFailureMessage(text))
                return@launch
            }
            repository.getPlaylistDetail(link.platform, link.playlistId)
                .onSuccess { data ->
                    val songs = data.songs
                        .distinctBy { it.platform + ":" + it.id }
                        .filter { it.id.isNotBlank() }
                    if (songs.isEmpty()) {
                        _import.value = PlaylistResolveState.Failed("该歌单没有可导入的歌曲")
                        return@onSuccess
                    }
                    // 名字优先级：平台抓到的真名 > 分享文案里的名字 > 兜底名
                    val name = data.info?.name.orEmpty()
                        .ifBlank { link.titleHint.orEmpty() }
                        .ifBlank { "${link.platformName}歌单" }
                    val item = PlaylistItem(
                        id = link.playlistId,
                        name = name,
                        coverUrl = data.info?.coverUrl,
                        playCount = data.info?.playCount,
                        trackCount = data.info?.trackCount ?: songs.size.toLong(),
                        platform = link.platform,
                    )
                    onCollectPlaylist(item)
                    _import.value = PlaylistResolveState.Ready(
                        platform = link.platform,
                        sourcePlaylistId = link.playlistId,
                        name = name,
                        coverUrl = data.info?.coverUrl,
                        songs = songs,
                    )
                    onToast("已导入《$name》到收藏歌单，共 ${songs.size} 首")
                    onImported(item)
                }
                .onFailure { e ->
                    LogReporter.w("PlaylistImport", "导入收藏失败", e)
                    _import.value = PlaylistResolveState.Failed(
                        "导入失败：${e.message ?: "网络异常"}"
                    )
                }
        }
    }

    private fun resolve(text: String, emit: (PlaylistResolveState) -> Unit) {
        emit(PlaylistResolveState.Loading)
        scope.launch {
            val link = resolveLink(text)
            if (link == null) {
                LogReporter.w("PlaylistImport", "无法解析歌单链接")
                emit(PlaylistResolveState.Failed(parseFailureMessage(text)))
                return@launch
            }
            repository.getPlaylistDetail(link.platform, link.playlistId)
                .onSuccess { data ->
                    val songs = data.songs
                        .distinctBy { it.platform + ":" + it.id }
                        .filter { it.id.isNotBlank() }
                    val name = data.info?.name.orEmpty()
                        .ifBlank { link.titleHint.orEmpty() }
                        .ifBlank { "${link.platformName}歌单" }
                    emit(
                        PlaylistResolveState.Ready(
                            platform = link.platform,
                            sourcePlaylistId = link.playlistId,
                            name = name,
                            coverUrl = data.info?.coverUrl,
                            songs = songs,
                        )
                    )
                }
                .onFailure { e ->
                    LogReporter.w("PlaylistImport", "解析歌单详情失败", e)
                    emit(PlaylistResolveState.Failed("解析失败：${e.message ?: "网络异常"}"))
                }
        }
    }

    /**
     * 解析链接：先按文案直接识别；识别不出平台时再尝试展开跳转（短链，如 `163cn.tv/xxx`）后重试。
     * 短链本身就带不出平台与歌单 ID，只有跟着跳转拿到最终地址才能导入。
     */
    private suspend fun resolveLink(text: String): PlaylistLink? {
        PlaylistLinkParser.parse(text)?.let { return it }
        val url = PlaylistLinkParser.firstUrl(text) ?: return null
        val finalUrl = repository.resolveFinalUrl(url).getOrNull() ?: return null
        if (finalUrl == url) return null
        return PlaylistLinkParser.parse(finalUrl)
    }

    private fun parseFailureMessage(text: String): String = when {
        text.isBlank() -> "请输入歌单链接或分享文案"
        PlaylistLinkParser.firstUrl(text) != null ->
            "没识别出这个链接里的歌单：目前支持 网易云音乐 / QQ音乐 / 酷狗音乐 / 酷我音乐"
        else -> "没识别到歌单信息，请粘贴歌单链接，或直接粘贴音乐 App 里的分享文案"
    }
}
