package com.aesthetic.music.ui.fm

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.LoadingBox
import com.aesthetic.music.ui.components.SongCover
import com.aesthetic.music.ui.theme.*
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ButtonDefaults

/**
 * FM 电台：全屏竖滑刷歌（免登录会话级推荐）。
 * 风格 → 种子池 → 上下滑播放 → 点赞/听完 → 歌手漂移 → 无限补充；
 * 点赞即收藏「我喜欢的音乐」并计入「我本次淘到的宝藏歌单」。
 */
@Composable
fun FmScreen(
    styles: List<String>,
    fmStyle: String?,
    fmQueue: List<Song>,
    fmIndex: Int,
    fmLoading: Boolean,
    fmError: String?,
    fmTreasure: List<Song>,
    favorites: Set<String>,
    currentSongId: String?,
    isPlaying: Boolean,
    onStart: (String) -> Unit,
    onSelect: (Int) -> Unit,
    onToggleLike: (Song) -> Unit,
    onPlayPause: () -> Unit,
    onExport: (List<Song>) -> Unit,
) {
    var showStylePicker by remember { mutableStateOf( fmStyle == null ) }
    var showTreasure by remember { mutableStateOf( false ) }
    var syncingPager by remember { mutableStateOf( false ) }
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState( initialPage =  0 , pageCount = { fmQueue.size.coerceAtLeast( 1  ) } )



    //换风格/重新开始时回到第 0 页
    LaunchedEffect( fmStyle ) {
        syncingPager = true
        scope.launch {
            pagerState.scrollToPage( 0 )
            syncingPager = false
        }
    }    // 引擎自动续播（听完自动下一首）→ 同步当前页；滑动同步锁，避免双重播放
    LaunchedEffect( currentSongId , fmQueue ) {
        val idx = fmQueue.indexOfFirst { it.id == currentSongId }
        if ( idx >=  0 && idx != pagerState.currentPage ) {
            syncingPager = true
            scope.launch {
                pagerState.scrollToPage( idx )
                syncingPager = false
            }
        }
    }

    // 用户滑动切歌 → 通知外部切换播放
    LaunchedEffect( pagerState.currentPage , fmQueue ) {
        if ( syncingPager ) return@LaunchedEffect
        if ( pagerState.currentPage != fmIndex ) onSelect( pagerState.currentPage )
    }

    Box( Modifier.fillMaxSize().background( Color( 0xFF141414 ) ) ) {
        when {
            showStylePicker -> FmStylePicker( styles = styles , onPick = { tag ->
                showStylePicker = false
                onStart( tag )
            } )

            showTreasure && fmTreasure.isNotEmpty() -> FmTreasureSheet(
                songs = fmTreasure,
                onExport = { onExport( fmTreasure ) },
                onDismiss = { showTreasure = false },
            )

            fmQueue.isEmpty() && fmLoading -> LoadingBox( message = "FM 正在拉取曲目…" )

            fmQueue.isEmpty() -> Column(
                modifier = Modifier.fillMaxSize().padding( 24.dp ),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text( text = "这里暂时没有曲目", color = TextSoft , textAlign = TextAlign.Center )
                Spacer( Modifier.height( 12.dp ) )
                if ( !fmError.isNullOrBlank() ) {
                    Text( text = fmError.orEmpty(), color = TextMuted , textAlign = TextAlign.Center )
                }
                Spacer( Modifier.height( 24.dp ) )
                Text(
                    text = "重新选个风格",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip( RoundedCornerShape( 999.dp ) )
                        .background( Surface )
                        .padding( horizontal =20.dp , vertical =12.dp )
                        .clickable { showStylePicker = true },
                )
            }

            else -> {
                VerticalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    beyondViewportPageCount =  1,
                ) { page ->
                    val song = fmQueue.getOrNull( page )
                    if ( song != null ) {
                        FmPage(
                            song = song,
                            isFavorite = favorites.contains( song.id ),
                            isCurrent = ( currentSongId == song.id ),
                            isPlaying = isPlaying,
                            onToggleLike = { onToggleLike( song ) },
                            onPlayPause = onPlayPause,
                            onNext = {
                                val next = page +  1
                                if ( next < fmQueue.size ) onSelect( next )
                            },
                        )
                    }
                }

                // 顶栏：当前风格 + 换风格
                Text(
                    text = fmStyle.orEmpty(),
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align( Alignment.TopStart )
                        .padding( start =20.dp , top =40.dp )
                        .clip( RoundedCornerShape( 999.dp ) )
                        .background( Color( 0x33FFFFFF ) )
                        .clickable { showStylePicker = true }
                        .padding( horizontal =14.dp , vertical =8.dp ),
                )

                // 底栏右侧：本次宝藏歌单
                Text(
                    text = "宝藏歌单 ${fmTreasure.size}",
                    color = TextPrimary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align( Alignment.BottomEnd )
                        .padding( end =20.dp , bottom =24.dp )
                        .clip( RoundedCornerShape( 999.dp ) )
                        .background( Color( 0x33FFFFFF ) )
                        .clickable { showTreasure = true }
                        .padding( horizontal =14.dp , vertical =8.dp ),
                )
            }
        }
    }
}

@Composable
private fun FmStylePicker(
    styles: List<String>,
    onPick: (String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding( 24.dp ).verticalScroll( rememberScrollState( ) ),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text( text = "FM 刷歌", color = TextPrimary , style = MaterialTheme.typography.headlineMedium , fontWeight = FontWeight.Bold )
        Spacer( Modifier.height( 8.dp ) )
        Text( text = "选个风格，开始无限下滑淘歌", color = TextSoft , textAlign = TextAlign.Center )
        Text( text = "免登录 · 点赞/听完自动向该歌手漂移 · 越刷越准", color = TextMuted , style = MaterialTheme.typography.bodySmall , textAlign = TextAlign.Center )
        Spacer( Modifier.height( 28.dp ) )
        styles.forEach { tag ->
            Text(
                text = tag,
                color = TextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding( vertical =12.dp ).clip( RoundedCornerShape( 999.dp ) ).background( Surface ).clickable { onPick( tag ) },
            )
        }
    }
}

@Composable
private fun FmPage(
    song: Song,
    isFavorite: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onToggleLike: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    Box( Modifier.fillMaxSize().background( Color( 0xFF141414 ) ) ) {
        if ( !song.artwork.isNullOrBlank() ) {
            SongCover(
                url = song.artwork,
                modifier = Modifier.fillMaxSize().alpha( 0.22f ),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SongCover(
                url = song.artwork,
                modifier = Modifier.size( 240.dp ).clip( CircleShape ),
            )

            Spacer( Modifier.height( 28.dp ) )

            Text(
                text = song.title.ifBlank { "未知曲目" },
                color = TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines =  1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer( Modifier.height( 4.dp ) )

            Text(
                text = song.artist.ifBlank { "未知歌手" },
                color = TextSoft,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                maxLines =  1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if ( isCurrent && isPlaying ) {
            Text( text = "♪ 播放中", color = AccentMint , style = MaterialTheme.typography.labelSmall , modifier = Modifier.align( Alignment.TopCenter ).padding( top =56.dp ) )
        }

        Row(
            modifier = Modifier.align( Alignment.BottomCenter ).fillMaxWidth().padding( bottom =56.dp ),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FmRoundButton( onClick = onToggleLike ) {
                Icon(
                    imageVector = if ( isFavorite ) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = null,
                    tint = if ( isFavorite ) AccentRose else TextMuted,
                )
            }
            FmRoundButton( size =	64.dp , onClick = onPlayPause ) {
                Icon(
                    imageVector = if ( isCurrent && isPlaying ) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = TextPrimary,
                )
            }
            FmRoundButton( onClick =onNext ) { Icon( imageVector = Icons.Filled.SkipNext , contentDescription = null , tint = TextPrimary ) }
        }
    }
}

@Composable
private fun FmRoundButton(
    size: Dp =	 44.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.size( size ).clip( CircleShape ).background( Color( 0x33FFFFFF ) ).clickable( onClick =onClick ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@OptIn( ExperimentalMaterial3Api::class )
@Composable
private fun FmTreasureSheet(
    songs: List<Song>,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet( onDismissRequest = onDismiss , containerColor = Background ) {
        Column(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text( text = "我本次淘到的宝藏歌单", color = TextPrimary , style = MaterialTheme.typography.titleMedium , fontWeight = FontWeight.Bold )
            Text( text = "共 ${songs.size} 首 · 点赞即自动收藏到「我喜欢的音乐」", color = TextMuted , style = MaterialTheme.typography.bodySmall )
            LazyColumn( Modifier.fillMaxWidth().height( 300.dp ) ) {
                items( items = songs , key = { it.id } ) { song ->
                    Row( Modifier.fillMaxWidth().padding( vertical =6.dp ), verticalAlignment = Alignment.CenterVertically ) {
                        SongCover( url = song.artwork , modifier = Modifier.size( 40.dp ) )
                        Spacer( Modifier.width( 12.dp ) )
                        Column( Modifier.weight( 1f ) ) {
                            Text( text = song.title , color = TextPrimary , style = MaterialTheme.typography.bodyMedium , maxLines =1, overflow = TextOverflow.Ellipsis )
                            Text( text = song.artist , color = TextMuted , style = MaterialTheme.typography.bodySmall , maxLines =1, overflow = TextOverflow.Ellipsis )
                        }
                    }
                }
            }
            Spacer( Modifier.height( 4.dp ) )
            Button( onClick =onExport , modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape( 14.dp ) , colors =ButtonDefaults.buttonColors( containerColor =PrimaryBrown ) ) {
                Text( text = "一键导出歌名列表" )
            }
        }
    }
}