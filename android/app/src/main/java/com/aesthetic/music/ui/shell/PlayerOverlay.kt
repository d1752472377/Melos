package com.aesthetic.music.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.PlayerManager
import com.aesthetic.music.player.PlayerState
import com.aesthetic.music.ui.artist.ArtistController
import com.aesthetic.music.ui.artist.ArtistRef
import com.aesthetic.music.ui.components.AppBottomSheet
import com.aesthetic.music.ui.library.LibraryController
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.player.PlayerScreen
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.settings.SettingsController
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.Stone100
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import dev.chrisbanes.haze.HazeState

/** 合唱歌曲点歌手时，等用户在面板里选完再跳。 */
private data class PendingArtistPick(
    val names: List<String>,
    val primaryId: String?,
    val platform: String?,
)

@Composable
fun PlayerOverlay(
    visible: Boolean,
    playerState: PlayerState,
    playerManager: PlayerManager,
    library: LibraryController,
    artist: ArtistController,
    playlistDetail: PlaylistDetailController,
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

    // 合唱歌曲点歌手时先攒着，等用户在面板里选完再跳
    var artistPicker by remember { mutableStateOf<PendingArtistPick?>(null) }

    fun openArtist(name: String, artistId: String?, platform: String?) {
        onClose()
        val keyword = name.trim()
        if (keyword.isEmpty()) return
        if (!artistId.isNullOrBlank()) {
            artist.open(ArtistRef(platform = platform ?: "netease", id = artistId, name = keyword))
            nav.navigateSingleTop(ScreenRoute.ArtistDetail.route)
        } else {
            // 只有名字（合唱里除第一位外的歌手），先搜一次拿 id
            artist.openByName(
                platform = platform,
                name = keyword,
                onResolved = { nav.navigateSingleTop(ScreenRoute.ArtistDetail.route) },
                onMissing = { actions.toast("没有找到歌手「$keyword」") },
            )
        }
    }

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
                onArtistClick = { song ->
                    val names = song.artistNames
                    val platform = song.artistPlatform ?: song.platform
                    if (names.size <= 1) {
                        openArtist(song.artist, song.artistId, platform)
                    } else {
                        // 合唱：让用户选具体哪一位，而不是永远只跳第一位
                        artistPicker = PendingArtistPick(names, song.artistId, platform)
                    }
                },
                onAlbumClick = { albumName, artistName, platform ->
                    onClose()
                    playlistDetail.openAlbumByName(
                        platform = platform,
                        albumName = albumName,
                        artistName = artistName,
                        onResolved = { nav.navigateSingleTop(ScreenRoute.AlbumDetail.route) },
                        onMissing = { actions.toast("没有找到这张专辑") },
                    )
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

    // 播放层被关掉时，选择面板不该留在屏幕上
    LaunchedEffect(visible) {
        if (!visible) artistPicker = null
    }

    artistPicker?.let { pick ->
        ArtistPickerSheet(
            names = pick.names,
            onDismiss = { artistPicker = null },
            onPick = { index, name ->
                artistPicker = null
                // 只有第一位能直接用已存的 id，其余靠名字搜
                openArtist(name, pick.primaryId.takeIf { index == 0 }, pick.platform)
            },
        )
    }
}

/** 合唱歌曲点歌手时弹出的选择面板。 */
@Composable
private fun ArtistPickerSheet(
    names: List<String>,
    onDismiss: () -> Unit,
    onPick: (index: Int, name: String) -> Unit,
) {
    AppBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.section)
                .padding(bottom = AppSpacing.page),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "选择歌手",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "关闭",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                text = "这首歌有多位歌手",
                color = TextMuted,
                fontSize = 12.sp,
            )

            Spacer(Modifier.height(AppSpacing.md))

            names.forEachIndexed { index, name ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Stone100)
                        .clickable { onPick(index, name) }
                        .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.lg),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = PrimaryBrown,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(AppSpacing.md))
                    Text(
                        text = name,
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.height(AppSpacing.xs))
            }
        }
    }
}
