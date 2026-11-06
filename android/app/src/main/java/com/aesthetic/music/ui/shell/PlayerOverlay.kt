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
    favoriteKeys: Set<String> = emptySet(),
    hazeState: HazeState? = null,
) {
    val myPlaylists by library.myPlaylists.collectAsState()
    val quality by settings.quality.collectAsState()
    val prefs by settings.prefs.collectAsState()
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
                currentPlayerTheme = prefs.playerTheme,
                onPlayerThemeChange = settings::setPlayerTheme,
                currentLyricFontSize = prefs.lyricFontSize,
                onLyricFontSizeChange = settings::setLyricFontSize,
                currentLyricBlurMode = prefs.lyricBlurMode,
                onLyricBlurModeChange = settings::setLyricBlurMode,
                currentBgBlurMode = prefs.backgroundBlurMode,
                onBgBlurModeChange = settings::setBackgroundBlurMode,
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
