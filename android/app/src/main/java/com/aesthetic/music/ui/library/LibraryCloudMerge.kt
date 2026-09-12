package com.aesthetic.music.ui.library

import com.aesthetic.music.data.api.SyncFavoriteItem
import com.aesthetic.music.data.api.SyncHistoryItem
import com.aesthetic.music.data.api.SyncPlaylistItem
import com.aesthetic.music.data.api.SyncPlaylistSongItem
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.data.settings.MyPlaylist

/**
 * `performCloudSync` 里三段纯合并循环（LWW + 墓碑模型）。
 * 原样搬出，便于 [LibraryController] 保持精简且这三段可单独推理。
 */

/** 合并收藏。云端的删除墓碑优先，更新项按 platform:id 替换/前插，最后截断上限。 */
internal fun mergeFavoriteSongs(
    local: List<Song>,
    cloud: List<SyncFavoriteItem>,
    cap: Int,
): List<Song> {
    val favList = local.toMutableList()
    for (item in cloud) {
        if (item.deleted) {
            favList.removeAll { it.platform == item.platform && it.id == item.id }
        } else if (item.song != null) {
            val idx = favList.indexOfFirst { it.platform == item.platform && it.id == item.id }
            if (idx >= 0) {
                favList[idx] = item.song
            } else {
                favList.add(0, item.song)
            }
        }
    }
    return favList.take(cap)
}

/** 合并播放历史。语义同收藏。 */
internal fun mergeHistory(
    local: List<Song>,
    cloud: List<SyncHistoryItem>,
    cap: Int,
): List<Song> {
    val hisList = local.toMutableList()
    for (item in cloud) {
        if (item.deleted) {
            hisList.removeAll { it.platform == item.platform && it.id == item.id }
        } else if (item.song != null) {
            val idx = hisList.indexOfFirst { it.platform == item.platform && it.id == item.id }
            if (idx >= 0) {
                hisList[idx] = item.song
            } else {
                hisList.add(0, item.song)
            }
        }
    }
    return hisList.take(cap)
}

/** 合并歌单元数据 + 歌单内歌曲（两段原本连写，合并为一个纯函数）。 */
internal fun mergePlaylists(
    local: List<MyPlaylist>,
    cloudPlaylists: List<SyncPlaylistItem>,
    cloudSongs: List<SyncPlaylistSongItem>,
    maxSongs: Int,
): List<MyPlaylist> {
    val plList = local.toMutableList()
    for (cp in cloudPlaylists) {
        if (cp.deleted) {
            plList.removeAll { it.id == cp.id }
        } else {
            val existing = plList.firstOrNull { it.id == cp.id }
            if (existing == null) {
                plList.add(
                    MyPlaylist(id = cp.id, name = cp.name, songs = emptyList(), cover = cp.coverUrl)
                )
            } else {
                val applyName = existing.name != cp.name && cp.name.isNotBlank()
                // 云端只可能有网络封面：本机已设过自定义封面（含本机图片）时不覆盖
                val applyCover = existing.cover.isBlank() && cp.coverUrl.isNotBlank()
                if (applyName || applyCover) {
                    val idx = plList.indexOfFirst { it.id == cp.id }
                    if (idx >= 0) {
                        plList[idx] = existing.copy(
                            name = if (applyName) cp.name else existing.name,
                            cover = if (applyCover) cp.coverUrl else existing.cover,
                        )
                    }
                }
            }
        }
    }
    for (cps in cloudSongs) {
        val pIdx = plList.indexOfFirst { it.id == cps.playlistId }
        if (pIdx >= 0) {
            val p = plList[pIdx]
            val sList = p.songs.toMutableList()
            if (cps.deleted) {
                sList.removeAll { it.platform == cps.platform && it.id == cps.songId }
            } else if (cps.song != null) {
                val sIdx = sList.indexOfFirst { it.platform == cps.platform && it.id == cps.songId }
                if (sIdx >= 0) {
                    sList[sIdx] = cps.song
                } else {
                    val pos = cps.position.coerceIn(0, sList.size)
                    sList.add(pos, cps.song)
                }
            }
            plList[pIdx] = p.copy(songs = sList.take(maxSongs))
        }
    }
    return plList
}
