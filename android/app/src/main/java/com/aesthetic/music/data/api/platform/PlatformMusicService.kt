package com.aesthetic.music.data.api.platform

import android.util.Log
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.api.AlbumDetailData
import com.aesthetic.music.data.api.AlbumItem
import com.aesthetic.music.data.api.ArtistDetailData
import com.aesthetic.music.data.api.ArtistItem
import com.aesthetic.music.data.api.CategoriesData
import com.aesthetic.music.data.api.DailyData
import com.aesthetic.music.data.api.HomeCategoryDto
import com.aesthetic.music.data.api.LyricData
import com.aesthetic.music.data.api.NewSongsData
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 客户端直连公共平台协议服务：
 * 绕过私有服务器反向代理，直接在 Android 客户端调用公开平台协议接口（网易云、酷我等）。
 * 优势：
 * 1. 零服务器开销与冷启动延迟；
 * 2. 无需用户配置私有服务器即可开箱即用；
 * 3. 官方 CDN 毫秒级直连。
 */
object PlatformMusicService {

    private const val TAG = "PlatformMusicService"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val gson = AppJson.gson

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val reqBuilder = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.163.com")

            headers.forEach { (k, v) -> reqBuilder.header(k, v) }

            val response = httpClient.newCall(reqBuilder.build()).execute()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${response.message}")
            }
            val body = response.body?.string().orEmpty()
            try {
                gson.fromJson(body, JsonObject::class.java) ?: JsonObject()
            } catch (e: Exception) {
                Log.e(TAG, "Parse JSON failed for url: $url, body: ${body.take(100)}", e)
                JsonObject()
            }
        }

    // ==================== 搜索 ====================

    suspend fun search(keyword: String, limit: Int = 30, platform: String? = null): List<Song> {
        val safeLimit = limit.coerceIn(1, 50)
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/?s=$encoded&type=1&limit=$safeLimit&offset=0"
        val root = getJson(url)
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val songsArr = result.getAsJsonArray("songs") ?: return emptyList()

        return songsArr.mapNotNull { parseNeteaseSong(it) }
    }

    suspend fun hotSearchWords(): List<String> {
        return try {
            val root = getJson("https://music.163.com/api/search/hot/detail")
            val dataArr = root.getAsJsonArray("data")
            if (dataArr != null && dataArr.size() > 0) {
                dataArr.mapNotNull { elem ->
                    elem.asJsonObject?.get("searchWord")?.asString?.takeIf { it.isNotBlank() }
                }.take(15)
            } else {
                // 备选接口
                val fallbackRoot = getJson("https://music.163.com/api/search/hot")
                val hots = fallbackRoot.getAsJsonObject("result")?.getAsJsonArray("hots")
                hots?.mapNotNull { it.asJsonObject?.get("first")?.asString }?.take(15).orEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get hot words failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchPlaylists(keyword: String, limit: Int = 20): List<PlaylistItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/?s=$encoded&type=1000&limit=$safeLimit&offset=0"
        val root = getJson(url)
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val playlists = result.getAsJsonArray("playlists") ?: return emptyList()

        return playlists.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val cover = obj.get("coverImgUrl")?.asString ?: obj.get("picUrl")?.asString
            val playCount = obj.get("playCount")?.asLong
            val trackCount = obj.get("trackCount")?.asLong
            PlaylistItem(
                id = id,
                name = name,
                coverUrl = fixPicUrl(cover),
                playCount = playCount,
                trackCount = trackCount,
                platform = "netease"
            )
        }
    }

    suspend fun searchAlbums(keyword: String, limit: Int = 20): List<AlbumItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/?s=$encoded&type=10&limit=$safeLimit&offset=0"
        val root = getJson(url)
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val albums = result.getAsJsonArray("albums") ?: return emptyList()

        return albums.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val cover = obj.get("picUrl")?.asString
            val size = obj.get("size")?.asLong
            val artistName = obj.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.get("name")?.asString.orEmpty()
            AlbumItem(
                id = id,
                name = name,
                artist = artistName,
                coverUrl = fixPicUrl(cover),
                size = size,
                platform = "netease"
            )
        }
    }

    suspend fun searchByLyric(keyword: String, limit: Int = 20): List<Song> {
        val safeLimit = limit.coerceIn(1, 50)
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/?s=$encoded&type=1006&limit=$safeLimit&offset=0"
        val root = getJson(url)
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val songsArr = result.getAsJsonArray("songs") ?: return emptyList()

        return songsArr.mapNotNull { parseNeteaseSong(it) }
    }

    suspend fun searchArtists(keyword: String, limit: Int = 8, platform: String? = null): List<ArtistItem> {
        val safeLimit = limit.coerceIn(1, 30)
        val encoded = URLEncoder.encode(keyword, "UTF-8")
        val url = "https://music.163.com/api/search/get/?s=$encoded&type=100&limit=$safeLimit&offset=0"
        val root = getJson(url)
        val result = root.getAsJsonObject("result") ?: return emptyList()
        val artistsArr = result.getAsJsonArray("artists") ?: return emptyList()

        return artistsArr.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val avatar = obj.get("img1v1Url")?.asString ?: obj.get("picUrl")?.asString
            val alias = obj.getAsJsonArray("alias")?.mapNotNull { it.asString }?.joinToString("/")
            ArtistItem(
                id = id,
                name = name,
                avatar = fixPicUrl(avatar),
                alias = alias,
                platform = "netease"
            )
        }
    }

    // ==================== 详情 ====================

    suspend fun getArtist(platform: String?, id: String, limit: Int = 50): ArtistDetailData {
        val safeLimit = limit.coerceIn(1, 100)
        val url = "https://music.163.com/api/artist/${URLEncoder.encode(id, "UTF-8")}"
        val root = getJson(url)
        val artistObj = root.getAsJsonObject("artist") ?: throw IOException("未找到歌手信息")
        val artistId = artistObj.get("id")?.asString ?: id
        val name = artistObj.get("name")?.asString.orEmpty()
        val avatar = artistObj.get("img1v1Url")?.asString ?: artistObj.get("picUrl")?.asString
        val alias = artistObj.getAsJsonArray("alias")?.mapNotNull { it.asString }?.joinToString("/")
        val albumSize = artistObj.get("albumSize")?.asLong
        val musicSize = artistObj.get("musicSize")?.asLong

        val info = ArtistItem(
            id = artistId,
            name = name,
            avatar = fixPicUrl(avatar),
            alias = alias,
            albumSize = albumSize,
            musicSize = musicSize,
            platform = "netease"
        )

        val hotSongsArr = root.getAsJsonArray("hotSongs") ?: JsonArray()
        val songs = hotSongsArr.take(safeLimit).mapNotNull { parseNeteaseSong(it) }

        return ArtistDetailData(info = info, songs = songs)
    }

    suspend fun getAlbum(id: String): AlbumDetailData {
        val url = "https://music.163.com/api/album/${URLEncoder.encode(id, "UTF-8")}"
        val root = getJson(url)
        val albumObj = root.getAsJsonObject("album") ?: throw IOException("未找到专辑信息")
        val albumId = albumObj.get("id")?.asString ?: id
        val name = albumObj.get("name")?.asString.orEmpty()
        val cover = albumObj.get("picUrl")?.asString
        val size = albumObj.get("size")?.asLong
        val artist = albumObj.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.get("name")?.asString.orEmpty()

        val info = AlbumItem(
            id = albumId,
            name = name,
            artist = artist,
            coverUrl = fixPicUrl(cover),
            size = size,
            platform = "netease"
        )

        val songsArr = root.getAsJsonArray("songs") ?: JsonArray()
        val songs = songsArr.mapNotNull { parseNeteaseSong(it) }

        return AlbumDetailData(info = info, songs = songs)
    }

    suspend fun getPlaylist(id: String): List<Song> {
        val url = "https://music.163.com/api/v6/playlist/detail?id=${URLEncoder.encode(id, "UTF-8")}"
        val root = getJson(url)
        val playlist = root.getAsJsonObject("playlist") ?: return emptyList()
        val tracks = playlist.getAsJsonArray("tracks") ?: return emptyList()

        return tracks.mapNotNull { parseNeteaseSong(it) }
    }

    suspend fun getHotPlaylists(limit: Int = 20): List<PlaylistItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val url = "https://music.163.com/api/personalized/playlist?limit=$safeLimit"
        val root = getJson(url)
        val result = root.getAsJsonArray("result") ?: return emptyList()

        return result.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val cover = obj.get("picUrl")?.asString
            val playCount = obj.get("playCount")?.asLong
            val trackCount = obj.get("trackCount")?.asLong
            PlaylistItem(
                id = id,
                name = name,
                coverUrl = fixPicUrl(cover),
                playCount = playCount,
                trackCount = trackCount,
                platform = "netease"
            )
        }
    }

    // ==================== 内容发现 ====================

    suspend fun getDaily(limit: Int = 8): DailyData {
        val playlists = getHotPlaylists(limit)
        return DailyData(title = "每日推荐", playlists = playlists)
    }

    suspend fun getNewSongs(limit: Int = 20): NewSongsData {
        val safeLimit = limit.coerceIn(1, 50)
        val url = "https://music.163.com/api/personalized/newsong?limit=$safeLimit"
        val root = getJson(url)
        val result = root.getAsJsonArray("result") ?: return NewSongsData()

        val songs = result.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val picUrl = obj.get("picUrl")?.asString
            val songObj = obj.getAsJsonObject("song")

            val artistArr = songObj?.getAsJsonArray("artists")
            val artist = artistArr?.mapNotNull { it.asJsonObject?.get("name")?.asString }?.joinToString("/")
                ?: songObj?.get("artist")?.asString.orEmpty()

            val albumName = songObj?.getAsJsonObject("album")?.get("name")?.asString.orEmpty()

            Song(
                id = id,
                title = name,
                artist = artist,
                album = albumName,
                artwork = fixPicUrl(picUrl),
                platform = "netease"
            )
        }

        return NewSongsData(title = "新歌速递", songs = songs)
    }

    // ==================== FM 电台（免登录会话级推荐） ====================

    suspend fun artistTopSongs( artistId : String , limit : Int =  50 ) : List<Song> {
        val safe = limit.coerceIn( 1 ,  100 )
        val root = getJson( "https://music.163.com/api/artist/top/song?id=${URLEncoder.encode( artistId , "UTF-8" )}&limit=$safe" )
        return root.getAsJsonArray( "songs" )?.mapNotNull { parseNeteaseSong( it ) } ?: emptyList()
    }

    suspend fun searchSongs( query : String , limit : Int =  20 ) : List<Song> {
        val safe = limit.coerceIn( 1 ,  50 )
        val root = getJson( "https://music.163.com/api/search/get?s=${URLEncoder.encode( query , "UTF-8" )}&type=1&offset=0&limit=$safe" )
        return root.getAsJsonObject( "result" )?.getAsJsonArray( "songs" )?.mapNotNull { parseNeteaseSong( it ) } ?: emptyList()
    }

    suspend fun fmRecommendSongs( limit : Int =  100 ) : List<Song> {
        val root = getJson( "https://music.163.com/api/personalized/newsong?limit=$limit" )
        return root.getAsJsonArray( "result" )?.mapNotNull { elem ->
            val songObj = elem.asJsonObject?.getAsJsonObject( "song" ) ?: return@mapNotNull null
            parseNeteaseSong( songObj )
        } ?: emptyList()
    }

    suspend fun fmSeedSongs( tag : String , limit : Int =  120 ) : List<Song> {
        val seen = hashSetOf<String>()
        val out = mutableListOf<Song>()

        // 1. 心动模式 / 私人心动：聚合新歌推荐与热门精选歌单曲目并随机混排，呈现类似网易云私人 FM 的心动感
        if ( tag == "心动模式" || tag == "私人心动" || tag.contains("心动") || tag == "全部" ) {
            try {
                val newSongs = fmRecommendSongs( 40 )
                newSongs.forEach { if ( it.id.isNotBlank() && seen.add( it.id ) ) out += it }
            } catch ( _ : Exception ) {}

            try {
                val hotPlaylists = getHotPlaylists( 3 )
                for ( pl in hotPlaylists ) {
                    if ( out.size >= limit ) break
                    val plSongs = try { getPlaylist( pl.id ) } catch ( _ : Exception ) { emptyList() }
                    plSongs.take( 30 ).forEach { s ->
                        if ( s.id.isNotBlank() && seen.add( s.id ) ) out += s
                    }
                }
            } catch ( _ : Exception ) {}

            if ( out.isNotEmpty() ) {
                return out.shuffled()
            }
        }

        // 2. 推荐新歌专属流
        if ( tag == "推荐新歌" ) {
            val list = fmRecommendSongs( limit.coerceAtMost( 100 ) )
            if ( list.isNotEmpty() ) return list
        }

        // 3. 标签精选流（流行/民谣/摇滚/治愈/电子/ACG等）
        val actualCat = when ( tag ) {
            "二次元", "ACG" -> "ACG"
            "国风", "古风" -> "古风"
            "治愈", "助眠" -> "治愈"
            else -> tag
        }
        val encodedCat = URLEncoder.encode( actualCat , "UTF-8" )
        val root = try {
            getJson( "https://music.163.com/api/playlist/highquality/list?cat=$encodedCat&limit=4" )
        } catch ( _ : Exception ) { JsonObject() }
        val playlistIds = root.getAsJsonArray( "playlists" )?.mapNotNull { it.asJsonObject?.get( "id" )?.asString } ?: emptyList()
        playlistIds.take( 4 ).forEach { id ->
            if ( out.size >= limit ) return@forEach
            val songs = try { getPlaylist( id ) } catch ( _ : Exception ) { emptyList() }
            songs.forEach { s ->
                if ( s.id.isNotBlank() && seen.add( s.id ) ) {
                    out += s
                }
            }
        }

        // 4. 兜底保护：如果特定标签歌单未能拉到歌曲，使用推荐新歌与热门歌单兜底，杜绝白屏
        if ( out.isEmpty() ) {
            val fallback = fmRecommendSongs( 60 )
            return fallback.ifEmpty {
                try {
                    val hot = getHotPlaylists( 2 ).firstOrNull()?.id
                    if ( hot != null ) getPlaylist( hot ) else emptyList()
                } catch ( _ : Exception ) { emptyList() }
            }
        }
        return out.shuffled()
    }

    suspend fun getRanks(): RanksData {
        val url = "https://music.163.com/api/toplist/detail"
        val root = getJson(url)
        val listArr = root.getAsJsonArray("list") ?: return RanksData()

        val ranks = listArr.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val name = obj.get("name")?.asString.orEmpty()
            val cover = obj.get("coverImgUrl")?.asString ?: obj.get("picUrl")?.asString
            val playCount = obj.get("playCount")?.asLong
            val trackCount = obj.get("trackCount")?.asLong

            val kind = when {
                name.contains("热歌") -> "hot"
                name.contains("新歌") -> "new"
                name.contains("飙升") -> "rising"
                name.contains("原创") -> "original"
                else -> "other"
            }

            PlaylistItem(
                id = id,
                name = name,
                coverUrl = fixPicUrl(cover),
                playCount = playCount,
                trackCount = trackCount,
                platform = "netease",
                kind = kind
            )
        }

        val sorted = ranks.sortedBy {
            when (it.kind) {
                "hot" -> 0
                "new" -> 1
                "rising" -> 2
                "original" -> 3
                else -> 9
            }
        }

        return RanksData(title = "排行榜", ranks = sorted)
    }

    suspend fun getRankSongs(id: String): List<Song> {
        return getPlaylist(id)
    }

    /**
     * 歌单广场标签（左侧抽屉用）。硬编码一套常用分类避免登录依赖，
     * 与网易云 highquality/list 的 cat 参数直接对应。
     */
    val defaultSheetTags = listOf(
        "全部", "华语", "欧美", "日语", "韩语", "粤语", "流行",
        "电子", "民谣", "摇滚", "说唱", "轻音乐", "影视原声", "古风", "治愈"
    )

    /**
     * 歌单广场列表：按标签 + 最新/最热拉取歌单。
     * order = "new"（最新，维持接口按 updateTime 降序）或 "hot"（最热，按播放量降序，客户端排序）。
     * 数据源改为网易云公开 /api/playlist/highquality/list（无需登录、稳定可用）；
     * 旧用的 /api/top/playlist 已下线返回 404，导致歌单广场空数据。
     */
    suspend fun sheetPlaylists(cat: String, order: String, limit: Int = 40): List<PlaylistItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val encodedCat = URLEncoder.encode(cat, "UTF-8")
        val url = "https://music.163.com/api/playlist/highquality/list" +
            "?cat=$encodedCat&limit=$safeLimit"
        val root = getJson(url)
        val arr = root.getAsJsonArray("playlists") ?: return emptyList()

        val items = arr.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            val cover = obj.get("coverImgUrl")?.asString ?: obj.get("picUrl")?.asString
            PlaylistItem(
                id = id,
                name = obj.get("name")?.asString.orEmpty(),
                coverUrl = fixPicUrl(cover),
                playCount = obj.get("playCount")?.asLong,
                trackCount = obj.get("trackCount")?.asLong,
                platform = "netease"
            )
        }
        // 最热：按播放量降序；最新：维持接口原始顺序（updateTime 降序）
        return if (order == "new") items else items.sortedByDescending { it.playCount ?: 0L }
    }

    /**
     * 首页分类歌单：内置四个分类，各拉一个精品歌单 + 前 3 首预览文案。
     * 数据源用网易云公开 /api/playlist/highquality/list（无需登录）。
     */
    suspend fun getCategories(): CategoriesData {
        val catDefs = listOf(
            Triple("pop", "流行", "人生走在路上，愿你心花路放"),
            Triple("edm", "电子", "跟随强劲节拍，释放自由灵魂"),
            Triple("folk", "民谣", "一把木吉他，诉说岁月温柔"),
            Triple("rock", "摇滚", "热血不灭，唱响青春回响")
        )

        val categories = mutableListOf<HomeCategoryDto>()
        for ((catId, catName, quote) in catDefs) {
            try {
                val encodedCat = URLEncoder.encode(catName, "UTF-8")
                val url = "https://music.163.com/api/playlist/highquality/list?cat=$encodedCat&limit=1"
                val root = getJson(url)
                val playlistObj = root.getAsJsonArray("playlists")?.firstOrNull()?.asJsonObject
                if (playlistObj != null) {
                    val pId = playlistObj.get("id")?.asString.orEmpty()
                    val pName = playlistObj.get("name")?.asString.orEmpty()
                    val cover = playlistObj.get("coverImgUrl")?.asString ?: playlistObj.get("picUrl")?.asString

                    val item = PlaylistItem(
                        id = pId,
                        name = pName,
                        coverUrl = fixPicUrl(cover),
                        platform = "netease"
                    )

                    var previewSongs = emptyList<String>()
                    if (pId.isNotBlank()) {
                        try {
                            val songs = getPlaylist(pId).take(3)
                            previewSongs = songs.map { s ->
                                if (s.artist.isNotBlank()) "${s.title} - ${s.artist}" else s.title
                            }
                        } catch (_: Exception) {}
                    }

                    categories.add(
                        HomeCategoryDto(
                            id = catId,
                            title = if (catId == "edm") "电音" else catName,
                            quote = quote,
                            coverUrl = fixPicUrl(cover),
                            previewSongs = previewSongs,
                            playlist = item
                        )
                    )
                }
            } catch (e: Exception) {
                Log.w(TAG, "Get category $catName failed: ${e.message}")
            }
        }

        return CategoriesData(title = "分类歌单", categories = categories)
    }

    // ==================== 歌词与音频直链 ====================

    suspend fun getLyric(song: Song): LyricData {
        return try {
            val url = "https://music.163.com/api/song/lyric?id=${URLEncoder.encode(song.id, "UTF-8")}&lv=-1&tv=-1"
            val root = getJson(url)
            val lrc = root.getAsJsonObject("lrc")?.get("lyric")?.asString
            val tlyric = root.getAsJsonObject("tlyric")?.get("lyric")?.asString

            LyricData(
                lyric = lrc,
                translated = tlyric,
                platform = song.platform,
                sourceName = "官方直连"
            )
        } catch (e: Exception) {
            Log.w(TAG, "Get lyric failed for song ${song.id}: ${e.message}")
            LyricData()
        }
    }

    /**
     * 官方直连音频兜底直链：
     * 当本地 QuickJS 音源脚本未安装或未解析成功时，直连公共 CDN 地址
     */
    fun getDirectSongUrl(song: Song, quality: String = "lossless"): SongUrlData {
        // 网易云标准公开重定向直链（外链播放器协议）
        val directUrl = "https://music.163.com/song/media/outer/url?id=${song.id}.mp3"
        return SongUrlData(
            url = directUrl,
            format = "mp3",
            bitrate = "320k",
            platform = song.platform,
            sourceName = "官方直连"
        )
    }

    // ==================== 辅助解析 ====================

    private fun parseNeteaseSong(element: JsonElement): Song? {
        val obj = element.asJsonObject ?: return null
        val id = obj.get("id")?.asString ?: return null
        val title = obj.get("name")?.asString.orEmpty()

        // 艺术家：兼容 artists 或 ar
        val artistsArr = obj.getAsJsonArray("artists") ?: obj.getAsJsonArray("ar")
        val artist = artistsArr?.mapNotNull { it.asJsonObject?.get("name")?.asString }?.joinToString("/")
            ?: obj.get("artist")?.asString.orEmpty()

        val firstArtistId = artistsArr?.firstOrNull()?.asJsonObject?.get("id")?.asString

        // 专辑与封面：兼容 album 或 al
        val albumObj = obj.getAsJsonObject("album") ?: obj.getAsJsonObject("al")
        val albumName = albumObj?.get("name")?.asString.orEmpty()
        val artwork = albumObj?.get("picUrl")?.asString ?: obj.get("picUrl")?.asString

        return Song(
            id = id,
            title = title,
            artist = artist,
            album = albumName,
            artwork = fixPicUrl(artwork),
            platform = "netease",
            artistId = firstArtistId,
            artistPlatform = "netease"
        )
    }

    private fun fixPicUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return if (url.startsWith("//")) "https:$url" else url
    }
}
