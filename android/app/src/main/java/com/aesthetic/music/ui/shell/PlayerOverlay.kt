package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.player.PlayerScreen
import com.aesthetic.music.ui.search.SearchController
import com.aesthetic.music.ui.search.SearchType
import com.aesthetic.music.ui.settings.SettingsController
import dev.chrisbanes.haze.HazeState

/**
 * 全屏播放器浮层（非 NavHost 路由，覆盖在整壳之上）。
 *
 * ## 为什么 [favoriteKeys] 要从外面传进来（而不是在内部调 `library.isFavorite`）
 * `library.isFavorite(song)` 读的是 `StateFlow.value`，**不是 Compose 状态**：
 * 收藏/取消收藏不会触发这里重组，于是心形图标永远不变——用户看到的就是
 * 「点了喜欢没反应」。把已收藏的 key 集合作为参数传进来（由 AppShell 用
 * `collectAsState()` 订阅），收藏变化才会真正引起重组。
 */
@Composable
fun PlayerOverlay(
    visible: Boolean,
    playerState: PlayerState,
    playerManager: PlayerManager,
    library: LibraryController,
    artist: ArtistController,
    search: SearchController,
    settings: SettingsController,
    nav: ShellNav,
    actions: ShellActions,
    onClose: () -> Unit,
    /** 已收藏歌曲的 `platform:id` 集合（Compose 状态，收藏变化会触发重组） */
    favoriteKeys: Set<String> = emptySet(),
    hazeState: HazeState? = null,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val quality by settings.quality.collectAsState()
    val songActions = rememberSongActionsState()

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    // 彻底阻断任何未处理的触摸事件向下透传至 underlying NavHost 页面
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Final)
                            event.changes.forEach { it.consume() }
                        }
                    }
                }
        ) {
            PlayerScreen(
                state = playerState,
                progressFlow = playerManager.progress,
                isFavorite = { song -> "${song.platform}:${song.id}" in favoriteKeys },
                onClose = onClose,
                onToggle = playerManager::toggle,
                onNext = playerManager::next,
                onPrevious = playerManager::previous,
                onSeek = playerManager::seekTo,
                onSeekToLyric = playerManager::seekToLyric,
                onTogglePlayMode = playerManager::togglePlayMode,
                onToggleFavorite = { song -> library.toggleFavorite(song) },
                onPlayQueueItem = playerManager::playQueueItem,
                onRemoveFromQueue = playerManager::removeFromQueue,
                onMoveQueueItem = playerManager::moveQueueItem,
                onClearQueue = playerManager::clearQueue,
                onEnsureLyrics = playerManager::ensureLyricsLoaded,
                onReloadLyrics = playerManager::reloadLyrics,
                currentQuality = quality,
                onQualityChange = { level ->
                    // 两件事必须一起做：落设置（设置页同步）+ 就地按新音质重播
                    settings.setQuality(level)
                    playerManager.reloadCurrentForQuality()
                },
                onSpeedChange = playerManager::setSpeed,
                onSleepTimer = playerManager::startSleepTimer,
                onCancelSleepTimer = playerManager::cancelSleepTimer,
                onArtistClick = { artistName, artistId, platform ->
                    onClose()
                    if (!artistId.isNullOrBlank()) {
                        artist.open(
                            ArtistRef(
                                platform = platform ?: "netease",
                                id = artistId,
                                name = artistName,
                            )
                        )
                        nav.navigate(ScreenRoute.ArtistDetail.route)
                    } else {
                        search.onQueryChanged(artistName)
                        search.setType(SearchType.ARTIST)
                        search.submit(artistName)
                        nav.navigate(ScreenRoute.Search.route)
                    }
                },
                onDownload = {
                    playerState.currentSong?.let { song ->
                        actions.downloadSong(song)
                    }
                },
                onAddToPlaylist = { song ->
                    songActions.showAddToPlaylist(song)
                },
                hazeState = hazeState,
            )

            SongActionsHost(
                state = songActions,
                library = library,
                playerManager = playerManager,
                myPlaylists = myPlaylists,
                actions = actions,
            )
        }
    }
}
