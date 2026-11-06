package com.aesthetic.music.ui.shell

import androidx.compose.runtime.Stable
import com.aesthetic.music.data.model.Song

@Stable
class ShellActions(
    val playSong: (Song, List<Song>) -> Unit,
    val playAll: (List<Song>) -> Unit,
    val downloadSong: (Song) -> Unit,
    val toast: (String) -> Unit,
)
