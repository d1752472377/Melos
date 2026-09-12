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
import com.aesthetic.music.data.api.PlaylistDetailData
import com.aesthetic.music.data.api.PlaylistItem
import com.aesthetic.music.data.api.RanksData
import com.aesthetic.music.data.api.SongUrlData
import com.aesthetic.music.data.model.Song
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object PlatformMusicService {

    private const val TAG = "PlatformMusicService"

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(18, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val gson = AppJson.gson

    private fun JsonObject.optString(key: String): String? {
        val elem = get(key) ?: return null
        if (elem.isJsonNull) return null
        return try {
            elem.asString.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun JsonObject.optLong(key: String): Long? {
        val elem = get(key) ?: return null
        if (elem.isJsonNull) return null
        return try {
            elem.asLong
        } catch (_: Exception) {
            null
        }
    }

    /** 清洗酷我返回字符串中的 unicode 转义 (\\u0026 -> &) 及 HTML 实体 (&nbsp;) */
    private fun cleanKuwoText(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var s = text
        val unicodeRegex = Regex("""(?:\\+u)([0-9a-fA-F]{4})""")
        s = unicodeRegex.replace(s) { m ->
            try {
                m.groupValues[1].toInt(16).toChar().toString()
            } catch (_: Exception) {
                m.value
            }
        }
        s = s.replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
        return s.replace(Regex("""\s+"""), " ").trim()
    }

    private fun parseJson(url: String, body: String): JsonObject = try {
        gson.fromJson(body, JsonObject::class.java) ?: JsonObject()
    } catch (e: Exception) {
        Log.e(TAG, "Parse JSON failed for url: $url, body: ${body.take(100)}", e)
        JsonObject()
    }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JsonObject =
        withContext(Dispatchers.IO) {
            val b = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://music.163.com")
            headers.forEach { (k, v) -> b.header(k, v) }
            val resp = httpClient.newCall(b.build()).execute()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            parseJson(url, resp.body?.string().orEmpty())
        }

    private suspend fun postJson(
        url: String,
        form: FormBody,
        referer: String = "https://music.163.com",
        cookie: String = "os=pc; appver=2.9.7",
    ): JsonObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", referer)
                .header("Cookie", cookie)
                .post(form)
                .build()
            val resp = httpClient.newCall(request).execute()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            parseJson(url, resp.body?.string().orEmpty())
        }

    private suspend fun cloudSearch(keyword: String, type: Int, limit: Int): JsonObject =
        postJson(
            "https://music.163.com/api/cloudsearch/pc",
            FormBody.Builder()
                .add("s", keyword)
                .add("type", type.toString())
                .add("limit", limit.coerceIn(1, 50).toString())
                .add("offset", "0")
                .build(),
        )

    // ==================== 多平台搜索入口 ====================

    private const val P_NETEASE = "netease"
    private const val P_QQ = "qq"
    private const val P_KUGOU = "kugou"
    private const val P_KUWO = "kuwo"

    private fun platformsOf(platform: String?): List<String> =
        if (platform.isNullOrBlank()) listOf(P_NETEASE, P_QQ, P_KUGOU, P_KUWO)
        else listOf(platform)

    // ---------- 单曲 ----------

    suspend fun search(keyword: String, limit: Int = 30, platform: String? = null): List<Song> {
        val plats = platformsOf(platform)
        if (plats.size == 1) return searchSongsOn(plats[0], keyword, limit)
        return coroutineScope {
            val merged = plats.map { p ->
                async(Dispatchers.IO) {
                    runCatching { searchSongsOn(p, keyword, limit) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { "${it.platform}:${it.id}" }
            rankSongs(merged, keyword).take(limit * 2)
        }
    }

    // [新增] 相关性评分
    private fun relevanceScore(song: Song, keyword: String): Int {
        val kw = keyword.trim().lowercase()
        if (kw.isEmpty()) return 0
        val title = song.title.lowercase()
        val artist = song.artist.lowercase()
        var score = 0
        when {
            title == kw -> score += 100
            title.startsWith(kw) -> score += 60
            title.contains(kw) -> score += 30
        }
        when {
            artist == kw -> score += 80
            artist.contains(kw) -> score += 40
        }
        val noisy = listOf("cover", "翻唱", "现场", "live", "伴奏", "片段", "铃声", "dj", "remix", "纯音乐")
        if (noisy.any { title.contains(it) }) score -= 50
        if (noisy.any { song.album.lowercase().contains(it) }) score -= 20
        return score
    }

    // [新增] 重排
    private fun rankSongs(songs: List<Song>, keyword: String): List<Song> =
        songs.sortedByDescending { relevanceScore(it, keyword) }

    private suspend fun searchSongsOn(platform: String, kw: String, limit: Int): List<Song> =
        when (platform) {
            P_NETEASE -> searchSongsNetease(kw, limit)
            P_QQ -> searchSongsQq(kw, limit)
            P_KUGOU -> searchSongsKugou(kw, limit)
            P_KUWO -> searchSongsKuwo(kw, limit)
            else -> emptyList()
        }

    // ---------- 歌手 ----------

    suspend fun searchArtists(keyword: String, limit: Int = 12, platform: String? = null): List<ArtistItem> {
        val plats = platformsOf(platform)
        if (plats.size == 1) return searchArtistsOn(plats[0], keyword, limit)
        return coroutineScope {
            plats.map { p ->
                async(Dispatchers.IO) {
                    runCatching { searchArtistsOn(p, keyword, limit) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { "${it.platform}:${it.id}" }.take(limit * 2)
        }
    }

    private suspend fun searchArtistsOn(platform: String, kw: String, limit: Int): List<ArtistItem> =
        when (platform) {
            P_NETEASE -> searchArtistsNetease(kw, limit)
            P_QQ -> searchArtistsQq(kw, limit)
            P_KUGOU -> searchArtistsKugou(kw, limit)
            P_KUWO -> searchArtistsKuwo(kw, limit)
            else -> emptyList()
        }

    // ---------- 歌单 ----------

    suspend fun searchPlaylists(keyword: String, limit: Int = 20, platform: String? = null): List<PlaylistItem> {
        val plats = platformsOf(platform)
        if (plats.size == 1) return searchPlaylistsOn(plats[0], keyword, limit)
        return coroutineScope {
            plats.map { p ->
                async(Dispatchers.IO) {
                    runCatching { searchPlaylistsOn(p, keyword, limit) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { "${it.platform}:${it.id}" }.take(limit * 2)
        }
    }

    private suspend fun searchPlaylistsOn(platform: String, kw: String, limit: Int): List<PlaylistItem> =
        when (platform) {
            P_NETEASE -> searchPlaylistsNetease(kw, limit)
            P_QQ -> searchPlaylistsQq(kw, limit)
            P_KUGOU -> searchPlaylistsKugou(kw, limit)
            P_KUWO -> searchPlaylistsKuwo(kw, limit)
            else -> emptyList()
        }

    // ---------- 专辑 ----------

    suspend fun searchAlbums(keyword: String, limit: Int = 20, platform: String? = null): List<AlbumItem> {
        val plats = platformsOf(platform)
        if (plats.size == 1) return searchAlbumsOn(plats[0], keyword, limit)
        return coroutineScope {
            plats.map { p ->
                async(Dispatchers.IO) {
                    runCatching { searchAlbumsOn(p, keyword, limit) }.getOrDefault(emptyList())
                }
            }.awaitAll().flatten().distinctBy { "${it.platform}:${it.id}" }.take(limit * 2)
        }
    }

    private suspend fun searchAlbumsOn(platform: String, kw: String, limit: Int): List<AlbumItem> =
        when (platform) {
            P_NETEASE -> searchAlbumsNetease(kw, limit)
            P_QQ -> searchAlbumsQq(kw, limit)
            P_KUGOU -> searchAlbumsKugou(kw, limit)
            P_KUWO -> searchAlbumsKuwo(kw, limit)
            else -> emptyList()
        }

    // ==================== 网易云 ====================

    private suspend fun searchSongsNetease(kw: String, limit: Int): List<Song> {
        val root = cloudSearch(kw, type = 1, limit = limit)
        val arr = root.getAsJsonObject("result")?.getAsJsonArray("songs") ?: return emptyList()
        return arr.mapNotNull { parseNeteaseSong(it) }
    }

    private suspend fun searchArtistsNetease(kw: String, limit: Int): List<ArtistItem> {
        val root = cloudSearch(kw, type = 100, limit = limit)
        val result = root.getAsJsonObject("result") ?: return emptyList()

        result.getAsJsonArray("artists")
            ?.takeIf { it.size() > 0 }
            ?.let { return it.mapNotNull { e -> parseNeteaseArtist(e) } }

        result.getAsJsonObject("artist")
            ?.let { return listOfNotNull(parseNeteaseArtist(it)) }

        // 兜底：从 songs[].artists 去重提取
        val songs = result.getAsJsonArray("songs")
        if (songs != null && songs.size() > 0) {
            val seen = LinkedHashMap<String, ArtistItem>()
            songs.forEach { s ->
                s.asJsonObject?.getAsJsonArray("artists")?.forEach { a ->
                    val item = parseNeteaseArtist(a)
                    if (item != null && !seen.containsKey(item.id)) seen[item.id] = item
                }
            }
            if (seen.isNotEmpty()) return seen.values.take(limit).toList()
        }
        return emptyList()
    }

    private suspend fun searchPlaylistsNetease(kw: String, limit: Int): List<PlaylistItem> {
        val root = cloudSearch(kw, type = 1000, limit = limit)
        val arr = root.getAsJsonObject("result")?.getAsJsonArray("playlists") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.get("id")?.asString ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = o.get("name")?.asString.orEmpty(),
                coverUrl = fixPicUrl(o.get("coverImgUrl")?.asString ?: o.get("picUrl")?.asString),
                playCount = o.get("playCount")?.asLong,
                trackCount = o.get("trackCount")?.asLong,
                platform = P_NETEASE,
            )
        }
    }

    private suspend fun searchAlbumsNetease(kw: String, limit: Int): List<AlbumItem> {
        val root = cloudSearch(kw, type = 10, limit = limit)
        val arr = root.getAsJsonObject("result")?.getAsJsonArray("albums") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val artist = o.getAsJsonArray("artists")?.firstOrNull()?.asJsonObject?.get("name")?.asString
                ?: o.getAsJsonObject("artist")?.get("name")?.asString.orEmpty()
            AlbumItem(
                id = id,
                name = o.get("name")?.asString.orEmpty(),
                artist = artist,
                coverUrl = fixPicUrl(o.get("picUrl")?.asString ?: o.get("blurPicUrl")?.asString),
                size = o.get("size")?.asLong,
                platform = P_NETEASE,
            )
        }
    }

    private fun parseNeteaseArtist(e: JsonElement): ArtistItem? {
        val o = e.asJsonObject ?: return null
        val id = o.optString("id") ?: return null
        val aliasList = try {
            val arr = if (o.has("alias") && o.get("alias").isJsonArray) o.getAsJsonArray("alias")
            else if (o.has("alia") && o.get("alia").isJsonArray) o.getAsJsonArray("alia")
            else null
            arr?.mapNotNull { elem ->
                if (elem != null && !elem.isJsonNull) runCatching { elem.asString }.getOrNull() else null
            }?.filter { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
        return ArtistItem(
            id = id,
            name = o.optString("name").orEmpty(),
            avatar = fixPicUrl(o.optString("picUrl") ?: o.optString("img1v1Url")),
            alias = aliasList?.joinToString("/"),
            albumSize = o.optLong("albumSize"),
            musicSize = o.optLong("musicSize"),
            platform = P_NETEASE,
        )
    }

    // ==================== QQ（musicu.fcg，四类） ====================

    private fun stripJsonp(body: String): String {
        val s = body.indexOf('(')
        val e = body.lastIndexOf(')')
        return if (s in 0 until e) body.substring(s + 1, e) else body
    }

    private suspend fun qqPost(payload: String): JsonObject =
        withContext(Dispatchers.IO) {
            val url = "https://u.y.qq.com/cgi-bin/musicu.fcg"
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .header("Referer", "https://y.qq.com/")
                .header("Cookie", "uin=; qqmusic_key=;")
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            parseJson(url, resp.body?.string().orEmpty())
        }

    private suspend fun qqSearch(kw: String, searchType: Int, limit: Int): JsonObject {
        val safeKw = kw.replace("\\", "\\\\").replace("\"", "\\\"")
        val payload = """{"req_0":{"module":"music.search.SearchCgiService","method":"DoSearchForQQMusicDesktop","param":{"query":"$safeKw","search_type":$searchType,"num_per_page":$limit,"page_num":1}}}"""
        return qqPost(payload)
    }

    private fun qqData(root: JsonObject): JsonObject? =
        root.getAsJsonObject("req_0")?.getAsJsonObject("data")?.getAsJsonObject("body")

    private suspend fun searchSongsQq(kw: String, limit: Int): List<Song> {
        val root = qqSearch(kw, 0, limit)
        val list = qqData(root)?.getAsJsonObject("song")?.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val mid = o.get("mid")?.asString ?: o.get("songmid")?.asString ?: return@mapNotNull null
            val singer = o.getAsJsonArray("singer")
                ?.mapNotNull { it.asJsonObject?.get("name")?.asString }?.joinToString("/").orEmpty()
            val alb = o.getAsJsonObject("album")
            val albMid = alb?.get("mid")?.asString ?: alb?.get("albumMid")?.asString
            Song(
                id = mid,
                title = o.get("title")?.asString ?: o.get("songname")?.asString.orEmpty(),
                artist = singer,
                album = alb?.get("name")?.asString ?: alb?.get("albumName")?.asString.orEmpty(),
                artwork = albMid?.let { "https://y.qq.com/music/photo_new/T002R300x300M000$it.jpg" },
                platform = P_QQ,
            )
        }
    }

    private suspend fun searchArtistsQq(kw: String, limit: Int): List<ArtistItem> {
        val root = qqSearch(kw, 1, limit)
        val body = qqData(root)
        val list = body?.getAsJsonObject("singer")?.getAsJsonArray("list")
            ?: body?.getAsJsonObject("zhida")?.getAsJsonArray("list")
            ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val mid = o.optString("mid")
                ?: o.optString("singerMid")
                ?: o.optString("singerMID")
                ?: return@mapNotNull null
            val name = o.optString("name")
                ?: o.optString("singerName")
                ?: o.optString("title").orEmpty()
            val pic = o.optString("pic")
                ?: o.optString("singerPic")
                ?: o.optString("mid")?.let { "https://y.qq.com/music/photo_new/T001R300x300M000$it.jpg" }
            ArtistItem(
                id = mid,
                name = name,
                avatar = fixPicUrl(pic),
                alias = null,
                musicSize = o.optLong("songNum") ?: o.optLong("song_count"),
                albumSize = o.optLong("albumNum") ?: o.optLong("album_count"),
                platform = P_QQ,
            )
        }
    }

    private suspend fun searchAlbumsQq(kw: String, limit: Int): List<AlbumItem> {
        val root = qqSearch(kw, 2, limit)
        val body = qqData(root) ?: return emptyList()
        val list = body.getAsJsonObject("album")?.getAsJsonArray("list")
            ?: body.getAsJsonArray("album")
            ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val mid = o.optString("albumMID")
                ?: o.optString("mid")
                ?: o.optString("albumMid")
                ?: return@mapNotNull null
            val name = o.optString("albumName")
                ?: o.optString("name").orEmpty()
            val singer = o.optString("singerName")
                ?: o.getAsJsonArray("singer_list")
                    ?.mapNotNull { it.asJsonObject?.optString("name") }?.joinToString("/").orEmpty()
            val pic = o.optString("albumPic")
                ?: "https://y.gtimg.cn/music/photo_new/T002R300x300M000$mid.jpg"
            AlbumItem(
                id = mid,
                name = name,
                artist = singer,
                coverUrl = fixPicUrl(pic),
                size = o.optLong("song_count") ?: o.optLong("songnum"),
                platform = P_QQ,
            )
        }
    }

    private suspend fun searchPlaylistsQq(kw: String, limit: Int): List<PlaylistItem> {
        val root = qqSearch(kw, 3, limit)
        val list = qqData(root)?.getAsJsonObject("songlist")?.getAsJsonArray("list") ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.get("dissid")?.asString ?: o.get("id")?.asString ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = o.get("dissname")?.asString ?: o.get("title")?.asString.orEmpty(),
                coverUrl = o.get("imgurl")?.asString ?: o.get("pic")?.asString,
                playCount = o.get("listennum")?.asLong,
                trackCount = o.get("song_count")?.asLong,
                platform = P_QQ,
            )
        }
    }

    // ==================== 酷狗（HTTP） ====================

    private suspend fun kugouJson(url: String): JsonObject = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://www.kugou.com/")
            .build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        parseJson(url, resp.body?.string().orEmpty())
    }

    private suspend fun searchSongsKugou(kw: String, limit: Int): List<Song> {
        return try {
            // 优先使用官方 WebFilter song_search_v2 接口：Image 字段含真实专辑封面
            val url = "http://songsearch.kugou.com/song_search_v2?keyword=${URLEncoder.encode(kw, "UTF-8")}&page=1&pagesize=$limit&clientver=&platform=WebFilter"
            val root = kugouJson(url)
            val list = root.getAsJsonObject("data")?.getAsJsonArray("lists")
                ?: throw IOException("kugou song_search_v2 empty")
            list.mapNotNull { e ->
                val o = e.asJsonObject ?: return@mapNotNull null
                val hash = o.optString("FileHash")
                    ?: o.optString("HQFileHash")
                    ?: o.optString("SQFileHash")
                    ?: return@mapNotNull null
                val rawImg = o.optString("Image") ?: o.optString("AlbumImage")
                val cover = rawImg?.replace("{size}", "240")?.let { fixPicUrl(it) }
                Song(
                    id = hash,
                    title = o.optString("SongName").orEmpty(),
                    artist = o.optString("SingerName").orEmpty(),
                    album = o.optString("AlbumName").orEmpty(),
                    artwork = cover,
                    platform = P_KUGOU,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "kugou song_search_v2 failed, fallback mobilecdn: ${e.message}")
            val url = "http://mobilecdnbj.kugou.com/api/v3/search/song" +
                    "?format=json&keyword=${URLEncoder.encode(kw, "UTF-8")}&page=1&pagesize=$limit&showtype=1"
            val list = kugouJson(url).getAsJsonObject("data")?.getAsJsonArray("info") ?: return emptyList()
            list.mapNotNull { e ->
                val o = e.asJsonObject ?: return@mapNotNull null
                val hash = o.optString("hash") ?: return@mapNotNull null
                Song(
                    id = hash,
                    title = o.optString("songname").orEmpty(),
                    artist = o.optString("singername").orEmpty(),
                    album = o.optString("album_name").orEmpty(),
                    artwork = kugouCover(o),
                    platform = P_KUGOU,
                )
            }
        }
    }

    // [新增]
    private fun kugouCover(o: JsonObject): String? {
        // 1) 优先用 album_id 拼官方封面
        val albumId = o.get("album_id")?.asString?.takeIf { it.isNotBlank() && it != "0" }
        if (albumId != null) return "https://imge.kugou.com/stdmusic/240/$albumId.jpg"
        // 2) imgurl / img 直接给（含 {size} 占位符）
        val raw = o.get("imgurl")?.asString
            ?: o.get("img")?.asString
            ?: o.get("album_img")?.asString
        if (!raw.isNullOrBlank()) return raw.replace("{size}", "240")
        // 3) 最后用 hash 走酷狗封面兜底（部分接口支持）
        val hash = o.get("hash")?.asString
        if (!hash.isNullOrBlank()) return "https://imge.kugou.com/stdmusic/240/$hash.jpg"
        return null
    }

    // [新增]
    private fun kugouArtistAvatar(o: JsonObject): String? {
        val raw = o.get("imgurl")?.asString
            ?: o.get("img")?.asString
            ?: o.get("singer_img")?.asString
        return raw?.takeIf { it.isNotBlank() }?.replace("{size}", "240")
    }

    private suspend fun searchArtistsKugou(kw: String, limit: Int): List<ArtistItem> {
        val url = "http://mobilecdnbj.kugou.com/api/v3/search/singer" +
                "?format=json&keyword=${URLEncoder.encode(kw, "UTF-8")}&page=1&pagesize=$limit"
        val data = kugouJson(url).getAsJsonArray("data") ?: return emptyList()
        val basicSingers = data.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("singerid") ?: return@mapNotNull null
            val name = o.optString("singername").orEmpty()
            Pair(id, name)
        }
        if (basicSingers.isEmpty()) return emptyList()

        return coroutineScope {
            basicSingers.map { (singerId, defaultName) ->
                async(Dispatchers.IO) {
                    try {
                        val detailUrl = "http://mobilecdn.kugou.com/api/v3/singer/info?singerid=$singerId"
                        val detailRoot = kugouJson(detailUrl)
                        val d = detailRoot.getAsJsonObject("data")
                        val singerName = d?.optString("singername") ?: defaultName
                        val rawImg = d?.optString("imgurl")
                        val avatar = rawImg?.replace("{size}", "240")?.let { fixPicUrl(it) }
                        val songCount = d?.optLong("songcount")
                        val albumCount = d?.optLong("albumcount")
                        val alias = d?.optString("alias")
                        ArtistItem(
                            id = singerId,
                            name = singerName,
                            avatar = avatar,
                            alias = alias,
                            musicSize = songCount,
                            albumSize = albumCount,
                            platform = P_KUGOU,
                        )
                    } catch (e: Exception) {
                        Log.w(TAG, "kugou singer detail failed for $singerId: ${e.message}")
                        ArtistItem(
                            id = singerId,
                            name = defaultName,
                            avatar = null,
                            alias = null,
                            platform = P_KUGOU,
                        )
                    }
                }
            }.awaitAll()
        }
    }

    private suspend fun searchPlaylistsKugou(kw: String, limit: Int): List<PlaylistItem> {
        val url = "http://mobilecdn.kugou.com/api/v3/search/special" +
                "?format=json&keyword=${URLEncoder.encode(kw, "UTF-8")}&page=1&pagesize=$limit"
        val list = kugouJson(url).getAsJsonObject("data")?.getAsJsonArray("info") ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("specialid") ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = o.optString("specialname").orEmpty(),
                coverUrl = o.optString("imgurl")?.replace("{size}", "240")?.takeIf { it.isNotBlank() },
                playCount = o.optLong("playcount"),
                trackCount = o.optLong("songcount"),
                platform = P_KUGOU,
            )
        }
    }

    private suspend fun searchAlbumsKugou(kw: String, limit: Int): List<AlbumItem> {
        val url = "http://mobilecdn.kugou.com/api/v3/search/album" +
                "?format=json&keyword=${URLEncoder.encode(kw, "UTF-8")}&page=1&pagesize=$limit"
        val list = kugouJson(url).getAsJsonObject("data")?.getAsJsonArray("info") ?: return emptyList()
        return list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("albumid") ?: return@mapNotNull null
            AlbumItem(
                id = id,
                name = o.optString("albumname").orEmpty(),
                artist = o.optString("singername").orEmpty(),
                coverUrl = o.optString("imgurl")?.replace("{size}", "240")?.let { fixPicUrl(it) },
                size = o.optLong("songcount") ?: o.optLong("song_count"),
                platform = P_KUGOU,
            )
        }
    }

// ==================== 酷我（HTTP，单引号伪 JSON） ====================

    private fun kuwoNormalize(raw: String): String {
        // 酷我 r.s 是单引号伪 JSON。只把「单引号」替换为双引号，
        // 已有的反斜杠转义（\uXXXX、\'、\"）原样保留，交给 Gson 解码。
        val sb = StringBuilder(raw.length + 32)
        var i = 0
        while (i < raw.length) {
            val ch = raw[i]
            when {
                ch == '\\' && i + 1 < raw.length -> {
                    sb.append(ch).append(raw[i + 1])
                    i += 2
                }
                ch == '\'' -> { sb.append('"'); i++ }
                else -> { sb.append(ch); i++ }
            }
        }
        return sb.toString()
    }

    private suspend fun kuwoBody(url: String): String = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://www.kuwo.cn/")
            .build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        resp.body?.string().orEmpty()
    }

    private suspend fun kuwoRaw(url: String): JsonObject = parseJson(url, kuwoNormalize(kuwoBody(url)))

    /** 酷我封面：web_albumpic_short 是相对路径；hts_* 已是完整 URL */
    private fun kuwoCover(path: String?): String? {
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        return if (p.startsWith("http")) p else "https://img2.kuwo.cn/star/albumcover/$p"
    }

    private fun kuwoHead(path: String?): String? {
        val p = path?.takeIf { it.isNotBlank() } ?: return null
        return if (p.startsWith("http")) p else "https://img3.kuwo.cn/star/starheads/$p"
    }

    private suspend fun searchSongsKuwo(kw: String, limit: Int): List<Song> {
        val url = "http://search.kuwo.cn/r.s?all=${URLEncoder.encode(kw, "UTF-8")}" +
                "&ft=music&newsearch=1&alflac=1&itemset=web_2013&client=kt&cluster=0&vermerge=1&rformat=json&encoding=utf8&show_copyright_off=1&pcmp4=1&ver=merge&plat=pc&vipver=1&pn=0&rn=$limit"
        return try {
            val arr = kuwoRaw(url).getAsJsonArray("abslist") ?: return emptyList()
            arr.mapNotNull { e2 ->
                val o = e2.asJsonObject ?: return@mapNotNull null
                val rid = o.optString("MUSICRID")?.removePrefix("MUSIC_") ?: return@mapNotNull null
                val rawTitle = o.optString("SONGNAME") ?: o.optString("name").orEmpty()
                val rawArtist = o.optString("ARTIST") ?: o.optString("artist").orEmpty()
                val rawAlbum = o.optString("ALBUM") ?: o.optString("album").orEmpty()
                Song(
                    id = rid,
                    title = cleanKuwoText(rawTitle),
                    artist = cleanKuwoText(rawArtist),
                    album = cleanKuwoText(rawAlbum),
                    artwork = kuwoCover(o.optString("web_albumpic_short") ?: o.optString("hts_web_albumpic_short")),
                    platform = P_KUWO,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "kuwo search failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchArtistsKuwo(kw: String, limit: Int): List<ArtistItem> {
        val url = "http://search.kuwo.cn/r.s?all=${URLEncoder.encode(kw, "UTF-8")}" +
                "&ft=artist&itemset=web_2013&client=kt&pn=0&rn=$limit&rformat=json&encoding=utf8"
        val arr = kuwoRaw(url).getAsJsonArray("abslist") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("ARTISTID")
                ?: o.optString("DC_TARGETID")
                ?: return@mapNotNull null
            val rawName = o.optString("ARTIST").orEmpty()
            ArtistItem(
                id = id,
                name = cleanKuwoText(rawName),
                avatar = kuwoHead(o.optString("hts_PICPATH") ?: o.optString("PICPATH")),
                alias = null,
                musicSize = o.optLong("SONGNUM"),
                albumSize = o.optLong("ALBUMNUM"),
                platform = P_KUWO,
            )
        }
    }

    private suspend fun searchPlaylistsKuwo(kw: String, limit: Int): List<PlaylistItem> {
        val url = "http://search.kuwo.cn/r.s?all=${URLEncoder.encode(kw, "UTF-8")}" +
                "&ft=playlist&itemset=web_2013&client=kt&pn=0&rn=$limit&rformat=json&encoding=utf8"
        // 歌单列表字段是 abslist；名字字段是小写 name；封面 hts_pic 是完整 URL
        val arr = kuwoRaw(url).getAsJsonArray("abslist") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("playlistid")
                ?: o.optString("DC_TARGETID")
                ?: return@mapNotNull null
            val name = o.optString("name") ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = cleanKuwoText(name),
                coverUrl = o.optString("hts_pic") ?: o.optString("pic"),
                playCount = o.optLong("playcnt"),
                trackCount = o.optLong("songnum"),
                platform = P_KUWO,
            )
        }
    }

    private suspend fun searchAlbumsKuwo(kw: String, limit: Int): List<AlbumItem> {
        val url = "http://search.kuwo.cn/r.s?all=${URLEncoder.encode(kw, "UTF-8")}" +
                "&ft=album&itemset=web_2013&client=kt&pn=0&rn=$limit&rformat=json&encoding=utf8"
        // 专辑列表字段是 albumlist（不是 abslist）；名字字段是小写 name；封面 hts_img 是完整 URL
        val arr = kuwoRaw(url).getAsJsonArray("albumlist") ?: return emptyList()
        return arr.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val id = o.optString("albumid") ?: return@mapNotNull null
            val name = o.optString("name")
                ?: o.optString("title")
                ?: return@mapNotNull null
            val artist = o.optString("artist") ?: o.optString("aartist").orEmpty()
            AlbumItem(
                id = id,
                name = cleanKuwoText(name),
                artist = cleanKuwoText(artist),
                coverUrl = o.optString("hts_img") ?: o.optString("img"),
                size = o.optLong("musiccnt"),
                platform = P_KUWO,
            )
        }
    }
    // ==================== 以下保持原有实现：详情 / 榜单 / 歌词 / 直链 / 辅助 ====================

    suspend fun hotSearchWords(): List<String> {
        return try {
            val root = getJson("https://music.163.com/api/search/hot/detail")
            val dataArr = root.getAsJsonArray("data")
            if (dataArr != null && dataArr.size() > 0) {
                dataArr.mapNotNull { elem ->
                    elem.asJsonObject?.get("searchWord")?.asString?.takeIf { it.isNotBlank() }
                }.take(15)
            } else {
                val fallbackRoot = getJson("https://music.163.com/api/search/hot")
                val hots = fallbackRoot.getAsJsonObject("result")?.getAsJsonArray("hots")
                hots?.mapNotNull { it.asJsonObject?.get("first")?.asString }?.take(15).orEmpty()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Get hot words failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun getNeteaseSingerSongs(artistId: String, offset: Int, limit: Int): Pair<Long, List<Song>> {
        val url = "https://music.163.com/api/v1/artist/songs?id=${URLEncoder.encode(artistId, "UTF-8")}&offset=$offset&limit=$limit"
        val root = getJson(url)
        val total = root.optLong("total") ?: 0L
        val list = root.getAsJsonArray("songs") ?: return total to emptyList()
        val songs = list.mapNotNull { parseNeteaseSong(it) }
        return total to songs
    }

    suspend fun getQqSingerSongs(singerMid: String, begin: Int, num: Int): Pair<Long, List<Song>> {
        val payload = """{"comm":{"ct":24,"cv":0},"singerSongList":{"method":"GetSingerSongList","param":{"order":1,"singerMid":"$singerMid","begin":$begin,"num":$num},"module":"musichall.song_list_server"}}"""
        val root = qqPost(payload)
        val data = root.getAsJsonObject("singerSongList")?.getAsJsonObject("data") ?: return 0L to emptyList()
        val total = data.optLong("totalNum") ?: 0L
        val list = data.getAsJsonArray("songList") ?: return total to emptyList()
        val songs = list.mapNotNull { e ->
            val songInfo = e.asJsonObject?.getAsJsonObject("songInfo") ?: return@mapNotNull null
            val mid = songInfo.optString("mid") ?: return@mapNotNull null
            val title = songInfo.optString("title") ?: songInfo.optString("name").orEmpty()
            val singers = songInfo.getAsJsonArray("singer")
                ?.mapNotNull { it.asJsonObject?.optString("name") }?.joinToString("/").orEmpty()
            val albumObj = songInfo.getAsJsonObject("album")
            val albumName = albumObj?.optString("name").orEmpty()
            val albumMid = albumObj?.optString("mid")
            val artwork = albumMid?.takeIf { it.isNotBlank() }?.let { "https://y.gtimg.cn/music/photo_new/T002R300x300M000$it.jpg" }
            Song(
                id = mid,
                title = title,
                artist = singers,
                album = albumName,
                artwork = artwork,
                platform = P_QQ,
                artistId = singerMid,
                artistPlatform = P_QQ,
            )
        }
        return total to songs
    }

    suspend fun getKugouSingerSongs(singerId: String, page: Int, pageSize: Int): Pair<Long, List<Song>> {
        val url = "http://mobilecdn.kugou.com/api/v3/singer/song?singerid=${URLEncoder.encode(singerId, "UTF-8")}&page=$page&pagesize=$pageSize"
        val root = kugouJson(url)
        val data = root.getAsJsonObject("data") ?: return 0L to emptyList()
        val total = data.optLong("total") ?: 0L
        val list = data.getAsJsonArray("info") ?: return total to emptyList()
        val songs = list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val hash = o.optString("hash") ?: o.optString("audio_id") ?: return@mapNotNull null
            val rawFilename = o.optString("filename").orEmpty()
            val albumName = o.optString("album_name").orEmpty()
            val parts = rawFilename.split(" - ", limit = 2)
            val (artist, title) = if (parts.size == 2) parts[0].trim() to parts[1].trim() else "" to rawFilename
            val cover = o.getAsJsonObject("trans_param")?.optString("union_cover")
                ?.replace("{size}", "240")?.let { fixPicUrl(it) }
                ?: kugouCover(o)
            Song(
                id = hash,
                title = title.ifBlank { rawFilename },
                artist = artist,
                album = albumName,
                artwork = cover,
                platform = P_KUGOU,
                artistId = singerId,
                artistPlatform = P_KUGOU,
            )
        }
        return total to songs
    }

    suspend fun getKuwoSingerSongs(singerId: String, pn: Int, rn: Int): Pair<Long, List<Song>> {
        val url = "http://search.kuwo.cn/r.s?stype=artist2music&artistid=${URLEncoder.encode(singerId, "UTF-8")}&pn=$pn&rn=$rn"
        val root = kuwoRaw(url)
        val total = root.optLong("total") ?: 0L
        val list = root.getAsJsonArray("musiclist") ?: return total to emptyList()
        val songs = list.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val rid = o.optString("musicrid")?.removePrefix("MUSIC_") ?: return@mapNotNull null
            val rawTitle = o.optString("name").orEmpty()
            val rawArtist = o.optString("artist").orEmpty()
            val rawAlbum = o.optString("album").orEmpty()
            val coverPath = o.optString("hts_web_albumpic_short") ?: o.optString("web_albumpic_short")
            Song(
                id = rid,
                title = cleanKuwoText(rawTitle),
                artist = cleanKuwoText(rawArtist),
                album = cleanKuwoText(rawAlbum),
                artwork = kuwoCover(coverPath),
                platform = P_KUWO,
                artistId = singerId,
                artistPlatform = P_KUWO,
            )
        }
        return total to songs
    }

    suspend fun getArtist(platform: String?, id: String, limit: Int = 50): ArtistDetailData {
        val plat = platform?.lowercase() ?: P_NETEASE
        return when (plat) {
            P_QQ -> {
                val (total, songs) = getQqSingerSongs(id, begin = 0, num = limit)
                val first = songs.firstOrNull()
                val singerName = first?.artist?.split("/")?.firstOrNull().orEmpty().ifBlank { "QQ音乐歌手" }
                val avatar = "https://y.gtimg.cn/music/photo_new/T001R300x300M000$id.jpg"
                val info = ArtistItem(
                    id = id,
                    name = singerName,
                    avatar = avatar,
                    musicSize = total,
                    platform = P_QQ,
                )
                ArtistDetailData(info = info, songs = songs)
            }
            P_KUGOU -> {
                val detailUrl = "http://mobilecdn.kugou.com/api/v3/singer/info?singerid=${URLEncoder.encode(id, "UTF-8")}"
                val detailRoot = runCatching { kugouJson(detailUrl) }.getOrNull()
                val d = detailRoot?.getAsJsonObject("data")
                val singerName = d?.optString("singername").orEmpty().ifBlank { "酷狗歌手" }
                val avatar = d?.let { kugouArtistAvatar(it) }
                val songCount = d?.optLong("songcount")
                val albumCount = d?.optLong("albumcount")
                val intro = d?.optString("intro")

                val (_, songs) = getKugouSingerSongs(id, page = 1, pageSize = limit)
                val info = ArtistItem(
                    id = id,
                    name = singerName,
                    avatar = fixPicUrl(avatar),
                    alias = intro?.takeIf { it.isNotBlank() },
                    albumSize = albumCount,
                    musicSize = songCount,
                    platform = P_KUGOU,
                )
                ArtistDetailData(info = info, songs = songs)
            }
            P_KUWO -> {
                val (total, songs) = getKuwoSingerSongs(id, pn = 0, rn = limit)
                val first = songs.firstOrNull()
                val singerName = first?.artist.orEmpty().ifBlank { "酷我歌手" }
                val info = ArtistItem(
                    id = id,
                    name = cleanKuwoText(singerName),
                    avatar = null,
                    musicSize = total,
                    platform = P_KUWO,
                )
                ArtistDetailData(info = info, songs = songs)
            }
            else -> {
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
                    platform = P_NETEASE,
                )

                val hotSongsArr = root.getAsJsonArray("hotSongs") ?: JsonArray()
                val songs = hotSongsArr.take(safeLimit).mapNotNull { parseNeteaseSong(it) }
                ArtistDetailData(info = info, songs = songs)
            }
        }
    }

    suspend fun getArtistAllSongs(platform: String?, id: String, maxCount: Int = 1000): List<Song> =
        withContext(Dispatchers.IO) {
            val plat = platform?.lowercase() ?: P_NETEASE
            when (plat) {
                P_NETEASE -> {
                    val (total, firstPage) = getNeteaseSingerSongs(id, offset = 0, limit = 100)
                    val allSongs = firstPage.toMutableList()
                    val targetTotal = minOf(total.toInt(), maxCount)
                    if (targetTotal > 100) {
                        val offsets = (100 until targetTotal step 100).toList()
                        val remaining = offsets.map { off ->
                            async {
                                runCatching { getNeteaseSingerSongs(id, offset = off, limit = 100).second }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                        allSongs.addAll(remaining)
                    }
                    allSongs.distinctBy { it.id }
                }
                P_QQ -> {
                    val (total, firstPage) = getQqSingerSongs(id, begin = 0, num = 100)
                    val allSongs = firstPage.toMutableList()
                    val targetTotal = minOf(total.toInt(), maxCount)
                    if (targetTotal > 100) {
                        val begins = (100 until targetTotal step 100).toList()
                        val remaining = begins.map { bg ->
                            async {
                                runCatching { getQqSingerSongs(id, begin = bg, num = 100).second }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                        allSongs.addAll(remaining)
                    }
                    allSongs.distinctBy { it.id }
                }
                P_KUGOU -> {
                    val (total, firstPage) = getKugouSingerSongs(id, page = 1, pageSize = 100)
                    val allSongs = firstPage.toMutableList()
                    val targetTotal = minOf(total.toInt(), maxCount)
                    val totalPages = (targetTotal + 99) / 100
                    if (totalPages >= 2) {
                        val pages = (2..totalPages).toList()
                        val remaining = pages.map { p ->
                            async {
                                runCatching { getKugouSingerSongs(id, page = p, pageSize = 100).second }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                        allSongs.addAll(remaining)
                    }
                    allSongs.distinctBy { it.id }
                }
                P_KUWO -> {
                    val (total, firstPage) = getKuwoSingerSongs(id, pn = 0, rn = 100)
                    val allSongs = firstPage.toMutableList()
                    val targetTotal = minOf(total.toInt(), maxCount)
                    val totalPages = (targetTotal + 99) / 100
                    if (totalPages >= 2) {
                        val pns = (1 until totalPages).toList()
                        val remaining = pns.map { pn ->
                            async {
                                runCatching { getKuwoSingerSongs(id, pn = pn, rn = 100).second }.getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                        allSongs.addAll(remaining)
                    }
                    allSongs.distinctBy { it.id }
                }
                else -> emptyList()
            }
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

    /**
     * 网易云歌单曲目。
     *
     * @param limit 只要前 N 首（0 = 全部）。歌单详情接口的 `tracks` 只回前 10 首，
     *   全量曲目在 `trackIds` 里，需要按 ID 批量补全——因此做"只看前几首"的调用（分类预览、
     *   FM 种子）时必须能短路，否则每次都会为 3 首歌拉回上千首。
     */
    suspend fun getPlaylist(id: String, limit: Int = 0): List<Song> =
        neteasePlaylistDetail(id, limit).songs

    // ==================== 歌单详情（导入外部歌单：名字 + 封面 + 全部曲目） ====================

    /**
     * 抓取歌单详情。导入外部歌单时必须同时拿到名字与封面：
     * 只抓歌曲会让用户导进来的歌单叫"导入的歌单"、封面退化成首曲封面，失去"原始记录"的意义。
     */
    suspend fun getPlaylistDetail(platform: String?, id: String): PlaylistDetailData {
        val p = platform?.takeIf { it.isNotBlank() } ?: P_NETEASE
        return when (p) {
            P_NETEASE -> neteasePlaylistDetail(id)
            P_QQ -> qqPlaylistDetail(id)
            P_KUGOU -> kugouPlaylistDetail(id)
            P_KUWO -> kuwoPlaylistDetail(id)
            else -> throw IOException("暂不支持导入该平台的歌单")
        }
    }

    /** 单次导入的曲目上限：与 LibraryController 的单个歌单上限保持一致 */
    private const val MAX_PLAYLIST_SONGS = 1000

    /**
     * 展开跳转链接（网易云 `163cn.tv/xxx` 这类短链只有跳转后的地址才带平台与歌单 ID）。
     * 失败或本就没有跳转时返回原 URL，由调用方自行判断是否继续。
     */
    suspend fun resolveFinalUrl(url: String): String = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_UA)
                .build()
            httpClient.newCall(req).execute().use { resp ->
                resp.request.url.toString()
            }
        } catch (_: Exception) {
            url
        }
    }

    private suspend fun neteasePlaylistDetail(id: String, limit: Int = 0): PlaylistDetailData {
        val url = "https://music.163.com/api/v6/playlist/detail?id=${URLEncoder.encode(id, "UTF-8")}"
        val root = getJson(url)
        val playlist = root.getAsJsonObject("playlist") ?: throw IOException("歌单不存在或已被删除")

        val maxSongs = if (limit > 0) limit else MAX_PLAYLIST_SONGS
        val inline = playlist.getAsJsonArray("tracks")?.mapNotNull { parseNeteaseSong(it) } ?: emptyList()
        // trackIds 是全量曲目 ID；tracks 只有前 10 首，两者数量不一致说明必须补全
        val trackIds = playlist.getAsJsonArray("trackIds")
            ?.mapNotNull { it.asJsonObject?.get("id")?.asLong }
            ?: emptyList()
        val songs = if (trackIds.size > inline.size && inline.size < maxSongs) {
            fetchNeteaseSongsByIds(trackIds.take(maxSongs))
        } else {
            inline.take(maxSongs)
        }

        val info = PlaylistItem(
            id = id,
            name = playlist.optString("name").orEmpty(),
            coverUrl = fixPicUrl(playlist.optString("coverImgUrl") ?: playlist.optString("picUrl")),
            playCount = playlist.optLong("playCount"),
            trackCount = playlist.optLong("trackCount") ?: songs.size.toLong(),
            platform = P_NETEASE,
        )
        return PlaylistDetailData(info = info, songs = songs)
    }

    /** 按曲目 ID 批量补全歌曲：分块请求并保持歌单原始顺序 */
    private suspend fun fetchNeteaseSongsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val byId = HashMap<String, Song>(ids.size)
        for (chunk in ids.chunked(200)) {
            val url = "https://music.163.com/api/song/detail/?ids=[${chunk.joinToString(",")}]&id=${chunk.first()}"
            val root = try {
                getJson(url)
            } catch (e: Exception) {
                // 某一批失败就保留已取到的部分，总比整单导入失败好
                Log.w(TAG, "netease song detail batch failed: ${e.message}")
                break
            }
            root.getAsJsonArray("songs")?.forEach { element ->
                parseNeteaseSong(element)?.let { byId[it.id] = it }
            }
        }
        return ids.mapNotNull { byId[it.toString()] }
    }

    private suspend fun qqPlaylistDetail(id: String): PlaylistDetailData = withContext(Dispatchers.IO) {
        val url = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg" +
                "?type=1&json=1&utf8=1&onlysong=0&new_format=1&disstid=${URLEncoder.encode(id, "UTF-8")}" +
                "&g_tk=5381&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0" +
                "&platform=yqq.json&needNewCode=0"
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Referer", "https://y.qq.com/")
            .build()
        val resp = httpClient.newCall(req).execute()
        if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
        val body = resp.body?.string().orEmpty()
        // 该接口在 format=json 下是标准 JSON，但历史上也出现过 JSONP 包装；只在必要时剥离，
        // 否则歌名里的括号会被 stripJsonp 当成包裹符号截断 JSON。
        val cleaned = if (body.trimStart().startsWith("{")) body else stripJsonp(body)
        val root = parseJson(url, cleaned)
        val cd = root.getAsJsonArray("cdlist")?.firstOrNull()?.asJsonObject
            ?: throw IOException("歌单不存在或为私密歌单")
        val songs = cd.getAsJsonArray("songlist")?.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val mid = o.optString("songmid") ?: o.optString("mid") ?: return@mapNotNull null
            val singer = o.getAsJsonArray("singer")
                ?.mapNotNull { it.asJsonObject?.optString("name") }?.joinToString("/").orEmpty()
            val albumMid = o.optString("albummid")
            Song(
                id = mid,
                title = o.optString("songname") ?: o.optString("title").orEmpty(),
                artist = singer,
                album = o.optString("albumname").orEmpty(),
                artwork = albumMid?.let { "https://y.qq.com/music/photo_new/T002R300x300M000$it.jpg" },
                platform = P_QQ,
            )
        } ?: emptyList()
        val info = PlaylistItem(
            id = id,
            name = cd.optString("dissname").orEmpty(),
            coverUrl = fixPicUrl(cd.optString("logo")),
            playCount = cd.optLong("listennum"),
            trackCount = cd.optLong("songnum") ?: songs.size.toLong(),
            platform = P_QQ,
        )
        PlaylistDetailData(info = info, songs = songs)
    }

    private suspend fun kugouPlaylistDetail(id: String): PlaylistDetailData {
        // 名字/封面信息接口偶发不可用：此时只要有歌曲就仍然导入，歌单名回落为文案里的名字
        val meta = runCatching {
            kugouJson("http://mobilecdn.kugou.com/api/v3/special/info?specialid=$id&format=json")
                .getAsJsonObject("data")
        }.getOrNull()
        val songs = kugouPlaylistSongs(id)
        if (meta == null && songs.isEmpty()) throw IOException("歌单不存在或无法访问")
        val info = PlaylistItem(
            id = id,
            name = meta?.optString("specialname").orEmpty(),
            coverUrl = fixPicUrl(meta?.optString("imgurl")?.replace("{size}", "480")),
            playCount = meta?.optLong("playcount"),
            trackCount = meta?.optLong("songcount") ?: songs.size.toLong(),
            platform = P_KUGOU,
        )
        return PlaylistDetailData(info = info, songs = songs)
    }

    /** 酷狗歌单曲目分页拉取（每页 100，最多 5 页，避免超大歌单打满内存） */
    private suspend fun kugouPlaylistSongs(id: String): List<Song> {
        val out = mutableListOf<Song>()
        var page = 1
        while (page <= 5) {
            val info = runCatching {
                kugouJson(
                    "http://mobilecdn.kugou.com/api/v3/special/song" +
                            "?specialid=$id&page=$page&pagesize=100&format=json"
                ).getAsJsonObject("data")?.getAsJsonArray("info")
            }.getOrNull() ?: break
            if (info.size() == 0) break
            info.forEach { e ->
                val o = e.asJsonObject ?: return@forEach
                val hash = o.optString("hash") ?: return@forEach
                // filename 形如 "歌手 - 歌名"，作为缺失字段的兜底
                val fileName = o.optString("filename").orEmpty()
                val hasSeparator = fileName.contains(" - ")
                out += Song(
                    id = hash,
                    title = o.optString("songname")
                        ?: if (hasSeparator) fileName.substringAfter(" - ") else fileName,
                    artist = o.optString("singername")
                        ?: if (hasSeparator) fileName.substringBefore(" - ") else "",
                    album = o.optString("album_name").orEmpty(),
                    artwork = kugouCover(o),
                    platform = P_KUGOU,
                )
            }
            if (info.size() < 100) break
            page++
        }
        return out
    }

    private suspend fun kuwoPlaylistDetail(id: String): PlaylistDetailData {
        val url = "http://nplserver.kuwo.cn/pl.svc?op=getlistinfo&pid=$id&pn=0&rn=300&encode=utf-8" +
                "&keyset=pl2012&identity=kuwo&pcmp4=1&vipver=MUSIC_9.0.2.0_W1&newver=1"
        val body = kuwoBody(url)
        // pl.svc 返回标准 JSON；只有解析为空时才按酷我的单引号伪 JSON 规范化后重试
        val direct = parseJson(url, body)
        val root = if (direct.size() > 0) direct else parseJson(url, kuwoNormalize(body))
        val list = root.getAsJsonArray("musiclist")
        // 酷我 pl.svc 的顶层歌单名是 title（info 是简介，name 只出现在曲目里），封面是 pic
        val name = cleanKuwoText(root.optString("title") ?: root.optString("name"))
        // 既没有歌单名也没有曲目 → 判定为无效/私密歌单；只有空曲目则交由上层提示"没有可导入的歌曲"
        if (list == null && name.isBlank()) throw IOException("歌单不存在或为私密歌单")
        val songs = list?.mapNotNull { e ->
            val o = e.asJsonObject ?: return@mapNotNull null
            val rid = (o.optString("id") ?: o.optString("rid"))?.removePrefix("MUSIC_")
                ?: return@mapNotNull null
            Song(
                id = rid,
                title = cleanKuwoText(o.optString("name")),
                artist = cleanKuwoText(o.optString("artist")),
                album = cleanKuwoText(o.optString("album")),
                artwork = kuwoCover(o.optString("albumpic") ?: o.optString("web_albumpic_short")),
                platform = P_KUWO,
            )
        } ?: emptyList()
        val info = PlaylistItem(
            id = id,
            name = name,
            coverUrl = fixPicUrl(root.optString("pic") ?: root.optString("img")),
            playCount = root.optLong("playnum"),
            trackCount = root.optLong("total") ?: songs.size.toLong(),
            platform = P_KUWO,
        )
        return PlaylistDetailData(info = info, songs = songs)
    }

    suspend fun getHotPlaylists(limit: Int = 20): List<PlaylistItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val url = "https://music.163.com/api/personalized/playlist?limit=$safeLimit"
        val root = getJson(url)
        val result = root.getAsJsonArray("result") ?: return emptyList()
        return result.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = obj.get("name")?.asString.orEmpty(),
                coverUrl = fixPicUrl(obj.get("picUrl")?.asString),
                playCount = obj.get("playCount")?.asLong,
                trackCount = obj.get("trackCount")?.asLong,
                platform = "netease"
            )
        }
    }

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

    suspend fun artistTopSongs(artistId: String, limit: Int = 50): List<Song> {
        val safe = limit.coerceIn(1, 100)
        val root = getJson("https://music.163.com/api/artist/top/song?id=${URLEncoder.encode(artistId, "UTF-8")}&limit=$safe")
        return root.getAsJsonArray("songs")?.mapNotNull { parseNeteaseSong(it) } ?: emptyList()
    }

    suspend fun searchSongs(query: String, limit: Int = 20): List<Song> {
        val safe = limit.coerceIn(1, 50)
        val root = getJson("https://music.163.com/api/search/get?s=${URLEncoder.encode(query, "UTF-8")}&type=1&offset=0&limit=$safe")
        return root.getAsJsonObject("result")?.getAsJsonArray("songs")?.mapNotNull { parseNeteaseSong(it) } ?: emptyList()
    }

    suspend fun fmRecommendSongs(limit: Int = 100): List<Song> {
        val root = getJson("https://music.163.com/api/personalized/newsong?limit=$limit")
        return root.getAsJsonArray("result")?.mapNotNull { elem ->
            val songObj = elem.asJsonObject?.getAsJsonObject("song") ?: return@mapNotNull null
            parseNeteaseSong(songObj)
        } ?: emptyList()
    }

    suspend fun fmSeedSongs(tag: String, limit: Int = 120): List<Song> {
        val seen = hashSetOf<String>()
        val out = mutableListOf<Song>()

        if (tag == "心动模式" || tag == "私人心动" || tag.contains("心动") || tag == "全部") {
            try {
                val newSongs = fmRecommendSongs(40)
                newSongs.forEach { if (it.id.isNotBlank() && seen.add(it.id)) out += it }
            } catch (_: Exception) {}
            try {
                val hotPlaylists = getHotPlaylists(3)
                for (pl in hotPlaylists) {
                    if (out.size >= limit) break
                    val plSongs = try { getPlaylist(pl.id, limit = 30) } catch (_: Exception) { emptyList() }
                    plSongs.take(30).forEach { s ->
                        if (s.id.isNotBlank() && seen.add(s.id)) out += s
                    }
                }
            } catch (_: Exception) {}
            if (out.isNotEmpty()) return out.shuffled()
        }

        if (tag == "推荐新歌") {
            val list = fmRecommendSongs(limit.coerceAtMost(100))
            if (list.isNotEmpty()) return list
        }

        val actualCat = when (tag) {
            "二次元", "ACG" -> "ACG"
            "国风", "古风" -> "古风"
            "治愈", "助眠" -> "治愈"
            else -> tag
        }
        val encodedCat = URLEncoder.encode(actualCat, "UTF-8")
        val root = try {
            getJson("https://music.163.com/api/playlist/highquality/list?cat=$encodedCat&limit=4")
        } catch (_: Exception) { JsonObject() }
        val playlistIds = root.getAsJsonArray("playlists")?.mapNotNull { it.asJsonObject?.get("id")?.asString } ?: emptyList()
        playlistIds.take(4).forEach { id ->
            if (out.size >= limit) return@forEach
            val songs = try { getPlaylist(id, limit = limit) } catch (_: Exception) { emptyList() }
            songs.forEach { s ->
                if (s.id.isNotBlank() && seen.add(s.id)) out += s
            }
        }

        if (out.isEmpty()) {
            val fallback = fmRecommendSongs(60)
            return fallback.ifEmpty {
                try {
                    val hot = getHotPlaylists(2).firstOrNull()?.id
                    if (hot != null) getPlaylist(hot, limit = 60) else emptyList()
                } catch (_: Exception) { emptyList() }
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
                playCount = obj.get("playCount")?.asLong,
                trackCount = obj.get("trackCount")?.asLong,
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

    suspend fun getRankSongs(id: String): List<Song> = getPlaylist(id)

    val defaultSheetTags = listOf(
        "全部", "华语", "欧美", "日语", "韩语", "粤语", "流行",
        "电子", "民谣", "摇滚", "说唱", "轻音乐", "影视原声", "古风", "治愈"
    )

    suspend fun sheetPlaylists(cat: String, order: String, limit: Int = 40): List<PlaylistItem> {
        val safeLimit = limit.coerceIn(1, 50)
        val encodedCat = URLEncoder.encode(cat, "UTF-8")
        val url = "https://music.163.com/api/playlist/highquality/list?cat=$encodedCat&limit=$safeLimit"
        val root = getJson(url)
        val arr = root.getAsJsonArray("playlists") ?: return emptyList()
        val items = arr.mapNotNull { elem ->
            val obj = elem.asJsonObject ?: return@mapNotNull null
            val id = obj.get("id")?.asString ?: return@mapNotNull null
            PlaylistItem(
                id = id,
                name = obj.get("name")?.asString.orEmpty(),
                coverUrl = fixPicUrl(obj.get("coverImgUrl")?.asString ?: obj.get("picUrl")?.asString),
                playCount = obj.get("playCount")?.asLong,
                trackCount = obj.get("trackCount")?.asLong,
                platform = "netease"
            )
        }
        return if (order == "new") items else items.sortedByDescending { it.playCount ?: 0L }
    }

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
                            val songs = getPlaylist(pId, limit = 3)
                            previewSongs = songs.map { s ->
                                if (s.artist.isNotBlank()) "${s.title} - ${s.artist}" else s.title
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "分类预览歌单拉取失败: $pId", e)
                        }
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

    fun getDirectSongUrl(song: Song, quality: String = "lossless"): SongUrlData {
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
        val artistsArr = obj.getAsJsonArray("artists") ?: obj.getAsJsonArray("ar")
        val artist = artistsArr?.mapNotNull { it.asJsonObject?.get("name")?.asString }?.joinToString("/")
            ?: obj.get("artist")?.asString.orEmpty()
        val firstArtistId = artistsArr?.firstOrNull()?.asJsonObject?.get("id")?.asString
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
        return when {
            url.startsWith("//") -> "https:$url"
            url.startsWith("http://") -> "https://" + url.removePrefix("http://")
            else -> url
        }
    }
}