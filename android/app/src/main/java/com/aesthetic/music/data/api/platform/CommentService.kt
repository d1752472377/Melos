package com.aesthetic.music.data.api.platform

import android.util.Log
import com.aesthetic.music.data.AppJson
import com.aesthetic.music.data.model.CommentPage
import com.aesthetic.music.data.model.MusicComment
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 歌曲评论（只读）。
 *
 * 四个平台的评论接口都是**匿名**的：不带任何账号 cookie / token / uin，
 * 所以这里也不接受任何登录态参数 —— 看评论不需要登录，也就不存在账号层面的风险。
 *
 * 接口本身是各家客户端在用的非公开接口，随时可能变化；调用方要把失败当成常态处理
 * （只显示一条失败提示，不要影响播放）。
 */
object CommentService {

    private const val TAG = "CommentService"

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private val gson = AppJson.gson
    private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /** QQ 评论接口只认数字 songId，这里缓存 mid -> songId 的换算结果，避免重复请求。 */
    private val qqSongIdCache = ConcurrentHashMap<String, Long>()

    /**
     * 拉取一页评论。
     *
     * @param platform Melos 的平台码：netease / qq / kugou / kuwo
     * @param songId   平台内 id（网易=歌曲 id，QQ=mid，酷狗=FileHash，酷我=rid）
     * @param hot      true 取热门，false 取最新
     */
    suspend fun fetch(
        platform: String,
        songId: String,
        page: Int = 1,
        limit: Int = 20,
        hot: Boolean = false,
    ): CommentPage = withContext(Dispatchers.IO) {
        if (songId.isBlank()) throw IOException("缺少歌曲 ID")
        when (platform.lowercase()) {
            "netease" -> netease(songId, page, limit, hot)
            "qq" -> qq(songId, page, limit, hot)
            "kugou" -> kugou(songId, page, limit, hot)
            "kuwo" -> kuwo(songId, page, limit, hot)
            else -> throw IOException("该平台暂不支持查看评论")
        }
    }

    // ==================== 网易云 ====================

    private suspend fun netease(songId: String, page: Int, limit: Int, hot: Boolean): CommentPage {
        // 这个端点不需要 weapi 加密，也不需要 cookie；offset=0 时额外带回热门评论。
        val url = "https://music.163.com/api/v1/resource/comments/R_SO_4_$songId" +
                "?limit=$limit&offset=${(page - 1) * limit}"
        val root = getJson(url, referer = "https://music.163.com")
        if (root.get("code")?.asInt != 200) throw IOException("网易云评论接口返回异常")

        // 热门评论只有 offset=0 这一次返回，翻页拿到的其实是"最新"，所以热门栏不分页。
        val arr = if (hot) {
            root.getAsJsonArray("hotComments") ?: root.getAsJsonArray("comments")
        } else {
            root.getAsJsonArray("comments")
        }
        val comments = arr?.mapNotNull { e -> parseNetease(e.asJsonObject) } ?: emptyList()
        val total = if (hot) comments.size else (root.get("total")?.asInt ?: comments.size)
        return CommentPage(comments = comments, total = total, page = page, limit = limit)
    }

    private fun parseNetease(o: JsonObject): MusicComment? {
        val id = o.get("commentId")?.asString ?: return null
        val user = o.getAsJsonObject("user")
        val text = decodeEmoji(o.get("content")?.asString.orEmpty())

        // 被回复时，标题行显示原评论作者，正文是回复内容，回复原文放进楼中楼。
        val replied = o.getAsJsonArray("beReplied")?.firstOrNull()?.asJsonObject
        val base = MusicComment(
            id = id,
            text = text,
            userName = user?.get("nickname")?.asString.orEmpty(),
            avatar = user?.get("avatarUrl")?.asString,
            timeText = formatTime(o.get("time")?.asLong ?: 0L),
            location = o.getAsJsonObject("ipLocation")?.get("location")?.asString,
            likedCount = o.get("likedCount")?.asInt,
        )
        if (replied == null) return base
        val target = MusicComment(
            id = "${id}_ref",
            text = decodeEmoji(replied.get("content")?.asString.orEmpty()),
            userName = replied.getAsJsonObject("user")?.get("nickname")?.asString.orEmpty(),
            avatar = replied.getAsJsonObject("user")?.get("avatarUrl")?.asString,
        )
        return base.copy(replies = listOf(target))
    }

    // ==================== QQ 音乐 ====================

    private suspend fun qq(songId: String, page: Int, limit: Int, hot: Boolean): CommentPage {
        val numericId = resolveQqSongId(songId)
        return if (hot) qqHot(numericId, page, limit) else qqNew(numericId, page, limit)
    }

    /** mid -> 数字 songId。评论接口只认后者。 */
    private suspend fun resolveQqSongId(mid: String): Long {
        qqSongIdCache[mid]?.let { return it }
        // 有些歌单/搜索结果拿到的本身就是数字 id，直接放行。
        mid.toLongOrNull()?.let { return it }
        val payload = """
            {"req_1":{"module":"music.pf_song_detail_svr","method":"get_song_detail","param":{"song_mid":"$mid"}}}
        """.trimIndent()
        val root = postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload, referer = "https://y.qq.com/")
        val id = root.getAsJsonObject("req_1")
            ?.getAsJsonObject("data")
            ?.getAsJsonObject("track_info")
            ?.get("id")?.asLong
            ?: throw IOException("无法解析该歌曲的评论 ID")
        qqSongIdCache[mid] = id
        return id
    }

    private suspend fun qqHot(songId: Long, page: Int, limit: Int): CommentPage {
        val payload = """
            {"comm":{"cv":4747474,"ct":24,"format":"json","inCharset":"utf-8","outCharset":"utf-8","notice":0,"platform":"yqq.json","needNewCode":1,"uin":0},
             "req":{"module":"music.globalComment.CommentRead","method":"GetHotCommentList",
               "param":{"BizType":1,"BizId":"$songId","LastCommentSeqNo":"","PageSize":$limit,
                        "PageNum":${page - 1},"HotType":1,"WithAirborne":0,"PicEnable":1}}}
        """.trimIndent()
        val root = postJson("https://u.y.qq.com/cgi-bin/musicu.fcg", payload, referer = "https://y.qq.com/")
        val req = root.getAsJsonObject("req")
        if (req?.get("code")?.asInt != 0) throw IOException("QQ 音乐热门评论接口返回异常")
        val list = req.getAsJsonObject("data")?.getAsJsonObject("CommentList")
        val total = list?.get("Total")?.asInt ?: 0
        val comments = list?.getAsJsonArray("Comments")?.mapNotNull { e ->
            val o = e.asJsonObject
            val id = o.get("CmId")?.asString ?: return@mapNotNull null
            MusicComment(
                id = id,
                text = cleanText(o.get("Content")?.asString),
                userName = o.get("Nick")?.asString.orEmpty(),
                avatar = o.get("Avatar")?.asString,
                timeText = formatSeconds(o.get("PubTime")?.asLong ?: 0L),
                location = o.get("Location")?.asString,
                likedCount = o.get("PraiseNum")?.asInt,
                replies = o.getAsJsonArray("SubComments")?.take(3)?.mapNotNull { s ->
                    val so = s.asJsonObject
                    MusicComment(
                        id = "${id}_${so.get("CmId")?.asString.orEmpty()}",
                        text = cleanText(so.get("Content")?.asString),
                        userName = so.get("Nick")?.asString.orEmpty(),
                        avatar = so.get("Avatar")?.asString,
                        timeText = formatSeconds(so.get("PubTime")?.asLong ?: 0L),
                        likedCount = so.get("PraiseNum")?.asInt,
                    )
                } ?: emptyList(),
            )
        } ?: emptyList()
        return CommentPage(comments = comments, total = total, page = page, limit = limit)
    }

    private suspend fun qqNew(songId: Long, page: Int, limit: Int): CommentPage {
        val form = FormBody.Builder()
            .add("uin", "0")
            .add("format", "json")
            .add("cid", "205360772")
            .add("reqtype", "2")
            .add("biztype", "1")
            .add("topid", songId.toString())
            .add("cmd", "8")
            .add("needmusiccrit", "1")
            .add("pagenum", (page - 1).toString())
            .add("pagesize", limit.toString())
            .build()
        val root = postForm(
            "http://c.y.qq.com/base/fcgi-bin/fcg_global_comment_h5.fcg",
            form,
            userAgent = "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; WOW64; Trident/5.0)",
        )
        if (root.get("code")?.asInt != 0) throw IOException("QQ 音乐评论接口返回异常")
        val comment = root.getAsJsonObject("comment") ?: throw IOException("QQ 音乐评论接口返回异常")
        val total = comment.get("commenttotal")?.asInt ?: 0
        val comments = comment.getAsJsonArray("commentlist")?.mapNotNull { e ->
            val o = e.asJsonObject
            val rawId = o.get("commentid")?.asString ?: return@mapNotNull null
            val middle = o.getAsJsonArray("middlecommentcontent")?.mapNotNull { m ->
                val mo = m.asJsonObject
                MusicComment(
                    id = "${rawId}_${mo.get("subcommentid")?.asString.orEmpty()}",
                    text = cleanText(mo.get("subcommentcontent")?.asString),
                    userName = mo.get("replynick")?.asString?.trimStart('@').orEmpty(),
                    avatar = mo.get("avatarurl")?.asString,
                )
            } ?: emptyList()
            MusicComment(
                id = rawId,
                text = cleanText(o.get("rootcommentcontent")?.asString),
                userName = (o.get("rootcommentnick")?.asString ?: o.get("nick")?.asString)
                    ?.trimStart('@').orEmpty(),
                avatar = o.get("avatarurl")?.asString,
                timeText = formatSeconds(o.get("time")?.asLong ?: 0L),
                likedCount = o.get("praisenum")?.asInt,
                replies = middle,
            )
        } ?: emptyList()
        return CommentPage(comments = comments, total = total, page = page, limit = limit)
    }

    // ==================== 酷狗 ====================

    private suspend fun kugou(hash: String, page: Int, limit: Int, hot: Boolean): CommentPage {
        val params = buildString {
            append("dfid=0")
            append("&mid=16249512204336365674023395779019")
            append("&clienttime=").append(System.currentTimeMillis())
            append("&uuid=0&extdata=").append(hash)
            append("&appid=1005&code=fc4be23b4e972707f36b8a828a93ba8a")
            append("&schash=").append(hash)
            append("&clientver=11409&p=").append(page)
            append("&clienttoken=&pagesize=").append(limit)
            append("&ver=10&kugouid=0")
        }
        val path = if (hot) "topliked" else "newest"
        val url = "http://m.comment.service.kugou.com/r/v1/rank/$path?$params&signature=${kugouSign(params)}"
        val root = getJson(url, userAgent = BROWSER_UA)
        if (root.get("err_code")?.asInt != 0) throw IOException("酷狗评论接口返回异常")
        val total = root.get("count")?.asInt ?: 0
        val comments = root.getAsJsonArray("list")?.mapNotNull { e ->
            val o = e.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            MusicComment(
                id = id,
                text = cleanText(replaceKugouAt(o)),
                userName = o.get("user_name")?.asString.orEmpty(),
                avatar = o.get("user_pic")?.asString,
                timeText = formatKugouTime(o.get("addtime")?.asString),
                location = o.get("location")?.asString,
                likedCount = o.getAsJsonObject("like")?.get("likenum")?.asInt,
            )
        } ?: emptyList()
        return CommentPage(comments = comments, total = total, page = page, limit = limit)
    }

    /** 酷狗正文里的 @ 是 `[at=用户id]` 占位，要按 atlist 换成真实昵称。 */
    private fun replaceKugouAt(o: JsonObject): String {
        var text = o.get("content")?.asString.orEmpty()
        o.getAsJsonArray("atlist")?.forEach { e ->
            val a = e.asJsonObject
            val id = a.get("id")?.asString ?: return@forEach
            val name = a.get("name")?.asString ?: return@forEach
            text = text.replace("[at=$id]", "@$name ")
        }
        return text
    }

    /** 酷狗签名：md5(盐 + 参数按 key 排序后拼接 + 正文 + 盐)。 */
    private fun kugouSign(params: String): String {
        val salt = "OIlwieks28dk2k092lksi2UIkp"
        val sorted = params.split("&").sorted().joinToString("")
        return md5("$salt$sorted$salt")
    }

    // ==================== 酷我 ====================

    private suspend fun kuwo(rid: String, page: Int, limit: Int, hot: Boolean): CommentPage {
        val type = if (hot) "get_rec_comment" else "get_comment"
        val url = "http://ncomment.kuwo.cn/com.s?f=web&type=$type&aapiver=1" +
                "&prod=kwplayer_ar_10.5.2.0&digest=15&sid=$rid" +
                "&start=${(page - 1) * limit}&msgflag=1&count=$limit&newver=3&uid=0"
        val root = getJson(url, userAgent = "Dalvik/2.1.0 (Linux; U; Android 9;)")
        if (root.get("code")?.asString != "200") throw IOException("酷我评论接口返回异常")
        val totalKey = if (hot) "hot_comments_counts" else "comments_counts"
        val listKey = if (hot) "hot_comments" else "comments"
        val total = root.get(totalKey)?.asInt ?: 0
        val comments = root.getAsJsonArray(listKey)?.mapNotNull { e ->
            val o = e.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            MusicComment(
                id = id,
                text = cleanText(o.get("msg")?.asString),
                userName = o.get("u_name")?.asString.orEmpty(),
                avatar = o.get("u_pic")?.asString,
                timeText = formatTime((o.get("time")?.asLong ?: 0L) * 1000L),
                likedCount = o.get("like_num")?.asInt,
                replies = o.getAsJsonArray("child_comments")?.take(3)?.mapNotNull { c ->
                    val co = c.asJsonObject
                    MusicComment(
                        id = "${id}_${co.get("id")?.asString.orEmpty()}",
                        text = cleanText(co.get("msg")?.asString),
                        userName = co.get("u_name")?.asString.orEmpty(),
                        avatar = co.get("u_pic")?.asString,
                        timeText = formatTime((co.get("time")?.asLong ?: 0L) * 1000L),
                        likedCount = co.get("like_num")?.asInt,
                    )
                } ?: emptyList(),
            )
        } ?: emptyList()
        return CommentPage(comments = comments, total = total, page = page, limit = limit)
    }

    // ==================== 公共 ====================

    private fun getJson(
        url: String,
        referer: String? = null,
        userAgent: String = BROWSER_UA,
    ): JsonObject {
        val builder = Request.Builder().url(url).header("User-Agent", userAgent)
        referer?.let { builder.header("Referer", it) }
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return parseJson(url, resp.body?.string().orEmpty())
        }
    }

    private fun postJson(url: String, payload: String, referer: String? = null): JsonObject {
        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
        referer?.let { builder.header("Referer", it) }
        httpClient.newCall(builder.build()).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return parseJson(url, resp.body?.string().orEmpty())
        }
    }

    private fun postForm(url: String, form: FormBody, userAgent: String): JsonObject {
        val req = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .post(form)
            .build()
        httpClient.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            return parseJson(url, resp.body?.string().orEmpty())
        }
    }

    private fun parseJson(url: String, body: String): JsonObject = try {
        gson.fromJson(body, JsonObject::class.java) ?: JsonObject()
    } catch (e: Exception) {
        Log.w(TAG, "评论 JSON 解析失败[$url]: ${e.message}")
        JsonObject()
    }

    private fun formatTime(ms: Long): String? =
        if (ms <= 0L) null else timeFormat.format(Date(ms))

    /** QQ 的 PubTime 是秒级时间戳。 */
    private fun formatSeconds(seconds: Long): String? =
        if (seconds <= 0L) null else formatTime(seconds * 1000L)

    /**
     * 酷狗的 addtime 是 `yyyy-MM-dd HH:mm:ss` 字符串（不是时间戳），
     * 直接按原样截到分钟显示即可，不做时区换算。
     */
    private fun formatKugouTime(raw: String?): String? {
        val text = raw?.trim().orEmpty()
        if (text.length < 16) return text.takeIf { it.isNotBlank() }
        return text.substring(0, 16)
    }

    /** 评论正文里的 `\n` 是转义过的，标题里的 HTML 实体也要还原。 */
    private fun cleanText(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace("\\n", "\n")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .trim()
    }

    /** 网易云正文里的 `[大笑]` 之类是自家表情码，转成 Unicode 更接近原生表现。 */
    private fun decodeEmoji(text: String): String {
        if (!text.contains('[')) return text
        var out = text
        NETEASE_EMOJI.forEach { (code, emoji) -> out = out.replace("[$code]", emoji) }
        return out
    }

    private fun md5(text: String): String =
        MessageDigest.getInstance("MD5")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private val NETEASE_EMOJI = mapOf(
        "大笑" to "😃", "可爱" to "😊", "憨笑" to "☺️",
        "色" to "😍", "亲亲" to "😙", "惊恐" to "😱",
        "流泪" to "😭", "亲" to "😚", "呆" to "😳",
        "哀伤" to "😔", "呲牙" to "😁", "吐舌" to "😝",
        "撇嘴" to "😒", "怒" to "😡", "奸笑" to "😏",
        "汗" to "😓", "痛苦" to "😖", "惶恐" to "😰",
        "生病" to "😨", "口罩" to "😷", "大哭" to "😂",
        "晕" to "😵", "发怒" to "👿", "开心" to "😄",
        "鬼脸" to "😜", "皱眉" to "😞", "流感" to "😢",
        "爱心" to "❤️", "心碎" to "💔", "钟情" to "💘",
        "星星" to "⭐️", "生气" to "💢", "便便" to "💩",
        "强" to "👍", "弱" to "👎", "拜" to "🙏",
        "牵手" to "👫", "跳舞" to "👯‍♀️",
        "禁止" to "🙅‍♀️", "这边" to "💁‍♀️",
        "爱意" to "💏", "嘴唇" to "👄", "狗" to "🐶",
        "猫" to "🐱", "猪" to "🐷", "兔子" to "🐰",
        "小鸡" to "🐤", "公鸡" to "🐔", "幽灵" to "👻",
        "圣诞" to "🎅", "外星" to "👽", "钻石" to "💎",
        "礼物" to "🎁", "男孩" to "👦", "女孩" to "👧",
        "蛋糕" to "🎂", "圈" to "⭕", "叉" to "❌",
    )
}
