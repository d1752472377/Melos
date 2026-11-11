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

    /**
     * 拆开的多位歌手。
     *
     * 各平台传回来的 `artist` 都是拼接好的字符串：网易/QQ/酷狗用 `/`，
     * 酷我用 `&`（例如「吴宗宪&温岚」）。这里按这几个分隔符拆开，好让界面
     * 在合唱歌曲上给出"选哪一位"的能力，而不是永远只跳第一位。
     *
     * `&` 只在两侧都没有空格时才当分隔符——「Simon & Garfunkel」这种本身
     * 就是完整艺名的不能被拆开。
     */
    val artistNames: List<String>
        get() {
            if (artist.isBlank()) return emptyList()
            // 先把「空格 & 空格」这种完整艺名整体保护起来，拆完再原样放回去
            val guard = "\u0001"
            val protected = artist.replace(" & ", guard)
            return protected
                .split('/', '、', '&')
                .map { it.replace(guard, " & ").trim() }
                .filter { it.isNotBlank() }
                .distinct()
        }

    /** 第一位的歌手 id（平台上只存了这一位）。 */
    val primaryArtistId: String? get() = artistId?.takeIf { it.isNotBlank() }
}
