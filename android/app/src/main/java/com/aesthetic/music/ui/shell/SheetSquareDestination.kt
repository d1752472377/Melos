package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.playlist.PlaylistDetailController
import com.aesthetic.music.ui.sheet.PlaylistPage
import com.aesthetic.music.ui.sheet.SheetController

@Composable
fun SheetSquareDestination(
    sheet: SheetController,
    playlistDetail: PlaylistDetailController,
    nav: ShellNav,
) {
    val sheetCat by sheet.cat.collectAsState()
    val sheetOrder by sheet.order.collectAsState()
    val playlists by sheet.playlists.collectAsState()
    val isLoading by sheet.isLoading.collectAsState()
    val error by sheet.error.collectAsState()

    LaunchedEffect(Unit) { sheet.ensureLoaded() }

    PlaylistPage(
        sheetTags = sheet.tags,
        sheetCat = sheetCat,
        sheetOrder = sheetOrder,
        playlists = playlists,
        isLoading = isLoading,
        error = error,
        onSelectCat = sheet::setCat,
        onSelectOrder = sheet::setOrder,
        onRetry = { sheet.load(force = true) },
        onOpenPlaylist = { pl ->
            playlistDetail.open(pl)
            nav.navigateSingleTop(ScreenRoute.PlaylistDetail.route)
        },
        onBack = nav.popBack,
    )
}
