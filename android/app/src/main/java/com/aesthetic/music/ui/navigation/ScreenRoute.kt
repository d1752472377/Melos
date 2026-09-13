package com.aesthetic.music.ui.navigation

/**
 * 应用页面路由定义：
 * 规范统一管理应用内所有导航目标，替代旧版脆弱的 if-else 覆盖。
 */
sealed class ScreenRoute(val route: String) {
    // 底部四大主要 Tab
    data object Home : ScreenRoute("home")
    data object Fm : ScreenRoute("fm")
    data object Playlist : ScreenRoute("playlist")
    data object Profile : ScreenRoute("profile")

    // 独立二级详情页
    data object Search : ScreenRoute("search")
    data object Ranks : ScreenRoute("ranks")
    data object SheetSquare : ScreenRoute("sheet_square") // 歌单广场
    data object PlaylistDetail : ScreenRoute("playlist_detail")
    data object AlbumDetail : ScreenRoute("album_detail")
    data object ArtistDetail : ScreenRoute("artist_detail")
    data object ArtistAllSongs : ScreenRoute("artist_all_songs")
    data object Favorites : ScreenRoute("favorites")
    data object CacheList : ScreenRoute("cache_list")
    data object History : ScreenRoute("history")
    data object Settings : ScreenRoute("settings")
    // 设置子页面
    data object SettingsAppearance : ScreenRoute("settings_appearance")
    data object SettingsQuality : ScreenRoute("settings_quality")
    data object SettingsPlayback : ScreenRoute("settings_playback")
    data object SettingsLyrics : ScreenRoute("settings_lyrics")
    data object SettingsDownload : ScreenRoute("settings_download")
    data object SettingsCache : ScreenRoute("settings_cache")
    data object ProfileEdit : ScreenRoute("profile_edit")
    data object Downloads : ScreenRoute("downloads")
    data object ListeningStats : ScreenRoute("listening_stats")
    data object CloudManage : ScreenRoute("cloud_manage")
    data object PluginManage : ScreenRoute("plugin_manage")

}
