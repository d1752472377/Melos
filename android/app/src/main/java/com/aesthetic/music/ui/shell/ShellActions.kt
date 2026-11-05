package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Stable
import com.aesthetic.music.data.model.Song

/**
 * 壳层通用动作集合：播放、下载、全局提示。
 * 各 destination 通过它拿到跨页面复用的行为，避免逐个透传 playerManager / Snackbar。
 */
@Stable
class ShellActions(
    /** 播放一首歌（在该列表内定位索引并起播） */
    val playSong: (Song, List<Song>) -> Unit,
    /** 从第一首起播放整个列表 */
    val playAll: (List<Song>) -> Unit,
    /** 用户主动下载：带全局提示，不经过播放器预缓存 */
    val downloadSong: (Song) -> Unit,
    /** 弹一次全局 Snackbar */
    val toast: (String) -> Unit,
)
