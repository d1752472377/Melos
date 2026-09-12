package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.aesthetic.music.ui.MainViewModel
import com.aesthetic.music.ui.navigation.ScreenRoute
import com.aesthetic.music.ui.sheet.PlaylistPage

/** 歌单广场（分类标签歌单） */
@Composable
fun SheetSquareDestination(viewModel: MainViewModel, nav: ShellNav) {
    val sheetCat by viewModel.sheetCat.collectAsState()
    val sheetOrder by viewModel.sheetOrder.collectAsState()
    val playlists by viewModel.sheetPlaylists.collectAsState()
    val isLoading by viewModel.isLoadingSheets.collectAsState()
    val error by viewModel.sheetError.collectAsState()

    // 进页面就加载（幂等：已有数据/正在加载时不会重复请求）。
    // 少了这一句，从首页进来看到的就是"该分类下暂无歌单"——列表其实是空的，不是真的没有。
    LaunchedEffect(Unit) { viewModel.ensureSheetPlaylistsLoaded() }

    PlaylistPage(
        sheetTags = viewModel.sheetTags,
        sheetCat = sheetCat,
        sheetOrder = sheetOrder,
        playlists = playlists,
        isLoading = isLoading,
        error = error,
        onSelectCat = viewModel::setSheetCat,
        onSelectOrder = viewModel::setSheetOrder,
        onRetry = { viewModel.loadSheetPlaylists(force = true) },
        onOpenPlaylist = { pl ->
            viewModel.openPlaylistDetail(pl)
            nav.navigate(ScreenRoute.PlaylistDetail.route)
        },
        onBack = nav.popBack,
    )
}
