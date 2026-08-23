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
    /** 主唱在平台内的歌手 ID（歌手页入口，旧数据/部分源可为空） */
    val artistId: String? = null,
    /** 歌手 ID 所属平台，缺省视为 platform */
    val artistPlatform: String? = null,
) {
    /** "歌手 · 专辑" 副标题 */
    val subtitleText: String
        get() = when {
            !artist.isNullOrBlank() && !album.isNullOrBlank() -> "$artist · $album"
            !artist.isNullOrBlank() -> artist
            else -> "未知歌手"
        }
}
