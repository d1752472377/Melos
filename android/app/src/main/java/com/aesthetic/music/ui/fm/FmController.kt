package com.aesthetic.music.ui.fm

import com.aesthetic.music.data.MusicRepository
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.service.LogReporter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class FmChannel(
    val id: String,
    val name: String,
    val subtitle: String,
    val tag: String,
    val icon: String = "📻",
)

class FmController(
    private val repository: MusicRepository,
    private val scope: CoroutineScope,
    private val isFavorite: (Song) -> Boolean,
    private val onToggleFavorite: (Song) -> Unit,
) {

    val channels = listOf(
        FmChannel("heart", "私人心动", "根据你的音乐偏好实时漫游", "心动模式", "💖"),
        FmChannel("new", "推荐新歌", "发现全网新鲜出炉的优质单曲", "推荐新歌", "✨"),
        FmChannel("pop", "热门流行", "热度霸榜，耳熟能详的动感旋律", "流行", "🔥"),
        FmChannel("folk", "清新民谣", "一把木吉他，唱出温暖与远方", "民谣", "🌿"),
        FmChannel("rock", "燃系摇滚", "节奏轰鸣，释放热血与激情", "摇滚", "🎸"),
        FmChannel("elec", "迷幻电音", "重低音与未来声场的感官盛宴", "电子", "⚡"),
        FmChannel("heal", "深夜治愈", "伴你入眠，安静温柔的情绪避难所", "治愈", "🌙"),
        FmChannel("acg", "动漫二次元", "燃曲与物语，穿越异次元时空", "ACG", "🌸"),
        FmChannel("gufeng", "唯美国风", "江南烟雨，墨染古韵悠扬", "古风", "📜"),
        FmChannel("jazz", "慵懒爵士", "黑胶微醺，随性摇摆的午后时光", "全部", "☕"),
        FmChannel("soundtrack", "影视原声", "经典荧幕声画，重温光影记忆", "影视原声", "🎬"),
    )

    val styles: List<String> = listOf("推荐新歌") + repository.sheetTags()

    private val _currentChannel = MutableStateFlow(channels[0])
    val currentChannel: StateFlow<FmChannel> = _currentChannel.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private val _style = MutableStateFlow<String?>(channels[0].tag)
    val style: StateFlow<String?> = _style.asStateFlow()

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _index = MutableStateFlow(0)
    val index: StateFlow<Int> = _index.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _treasure = MutableStateFlow<List<Song>>(emptyList())
    val treasure: StateFlow<List<Song>> = _treasure.asStateFlow()

    private val driftArtists = ArrayDeque<Pair<String, String>>()

    private val queuedIds = hashSetOf<String>()

    private val dislikedIds = hashSetOf<String>()

    fun start(channel: FmChannel = channels[0], onReady: ((Song, List<Song>) -> Unit)? = null) {
        _currentChannel.value = channel
        _style.value = channel.tag
        driftArtists.clear()
        queuedIds.clear()
        _queue.value = emptyList()
        _index.value = 0
        _error.value = null
        _active.value = true
        _loading.value = true
        scope.launch {
            repository.fmSeedSongs(channel.tag, 120).onSuccess { rawSongs ->
                val songs = rawSongs.filterNot { dislikedIds.contains(it.id) }
                queuedIds.addAll(songs.map { it.id })
                _queue.value = songs
                _loading.value = false
                if (songs.isNotEmpty()) {
                    onReady?.invoke(songs.first(), songs)
                }
            }.onFailure { e ->
                LogReporter.w("Fm", "电台调频失败", e)
                _error.value = e.message ?: "电台调频失败，请点击重新调频"
                _loading.value = false
            }
        }
    }

    /** 兼容旧版调用：按风格名称开拉 */
    fun start(style: String) {
        val channel = channels.find { it.tag == style || it.name == style }
            ?: FmChannel(style, style, "定制电台风格", style, "🎵")
        start(channel)
    }

    fun refresh(onReady: ((Song, List<Song>) -> Unit)? = null) {
        start(_currentChannel.value, onReady)
    }

    fun select(index: Int) {
        val q = _queue.value
        if (index < 0 || index >= q.size) return
        _index.value = index
        maybeFill(index)
    }

    fun dislike(song: Song, onPlayNext: ((Song, List<Song>) -> Unit)? = null) {
        dislikedIds.add(song.id)
        val curr = _queue.value.toMutableList()
        val idx = curr.indexOfFirst { it.id == song.id }
        if (idx >= 0) {
            curr.removeAt(idx)
            _queue.value = curr
            val nextSong = curr.getOrNull(idx) ?: curr.firstOrNull()
            if (nextSong != null) {
                _index.value = idx.coerceAtMost(curr.size - 1)
                onPlayNext?.invoke(nextSong, curr)
            }
        }
        maybeFill(_index.value)
    }

    fun toggleLike(song: Song) {
        onToggleFavorite(song)
        val trea = _treasure.value.toMutableList()
        if (isFavorite(song)) {
            if (trea.none { it.id == song.id }) {
                trea.add(song)
                _treasure.value = trea
                onSignal(song)
            }
        } else {
            _treasure.value = trea.filterNot { it.id == song.id }
        }
    }

    fun onSignal(song: Song) {
        if (!_active.value) return
        val artistId = song.artistId ?: return
        val artistName = song.artist.substringBefore("/").trim()
        if (artistId.isBlank() || artistName.isBlank()) return
        if (driftArtists.none { it.first == artistId }) {
            driftArtists.addLast(artistId to artistName)
        }
        maybeFill(_index.value)
    }

    fun stopDrift() {
        _active.value = false
    }

    fun setDriftActive(active: Boolean) {
        _active.value = active
    }

    private fun maybeFill(index: Int) {
        val q = _queue.value
        if (q.size - index >= 6) return
        if (_loading.value) return
        val style = _style.value ?: return
        _loading.value = true
        scope.launch {
            val added = mutableListOf<Song>()
            val drift = driftArtists.removeFirstOrNull()
            if (drift != null) {
                repository.artistTopSongs(drift.first, 50).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && !dislikedIds.contains(it.id) && queuedIds.add(it.id) }
                }
                repository.searchSongs(drift.second, 20).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && !dislikedIds.contains(it.id) && queuedIds.add(it.id) }
                }
            }
            if (added.isEmpty()) {
                repository.fmSeedSongs(style, 80).onSuccess { songs ->
                    added += songs.filter { it.id.isNotBlank() && !dislikedIds.contains(it.id) && queuedIds.add(it.id) }
                }
            }
            if (added.isNotEmpty()) {
                _queue.value = _queue.value + added
            }
            _loading.value = false
        }
    }
}
