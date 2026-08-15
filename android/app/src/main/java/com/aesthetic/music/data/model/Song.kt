package com.aesthetic.music.data.model

/**
 * 歌曲模型（对应后端 /api/search 返回的标准化歌曲对象）
 */
data class Song(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artwork: String? = null,
    val platform: String = "",
    val subtitle: String? = null,
) {
    /** "歌手 · 专辑" 副标题 */
    val subtitleText: String
        get() = when {
            !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist · $album"
            !artist.isNullOrBlank() -> artist
            else -> "未知歌手"
        }
}
