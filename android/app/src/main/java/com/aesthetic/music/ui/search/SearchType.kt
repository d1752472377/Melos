package com.aesthetic.music.ui.search

/** 搜索页结果类型（已去掉歌词搜索） */
enum class SearchType(val label: String) {
    SONG("单曲"), ARTIST("歌手"), PLAYLIST("歌单"), ALBUM("专辑")
}