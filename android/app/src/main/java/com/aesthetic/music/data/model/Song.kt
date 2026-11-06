package com.aesthetic.music.data.model

data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val platform: String = "",
    val subtitle: String? = null,
    val artistId: String? = null,
    val artistPlatform: String? = null,
) {
    val subtitleText: String
        get() = when {
            !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist · $album"
            !artist.isNullOrBlank() -> artist
            else -> "未知歌手"
        }
}
