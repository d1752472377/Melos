package com.aesthetic.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 歌单封面渲染（同时支持网络/本地路径与**内联 data URL**）。
 *
 * ## 为什么需要它
 * 自建歌单的自定义封面在云端同步里是一个字符串。要让封面跨设备一致，
 * 图片就必须跟着歌单数据一起走——本项目没有图片上传接口（后端只有 APK 上传），
 * 所以选了一个不依赖服务端的办法：把选中的图片压到 ~288px / JPEG 75%，
 * 再以 `data:image/jpeg;base64,...` 的形式存进歌单的 `cover` 字段。
 * 这样它天然跟着「歌单」这条同步记录走，换设备登录即可看到同一张封面。
 *
 * 代价是封面字符串变长（十几 KB），所以只用于**用户自己选的封面**；
 * 网络封面仍然是一个普通 URL，走原来的 Coil 加载路径。
 *
 * ## 为什么不用 Coil 直接加载 data URL
 * Coil 2 的默认 Fetcher 只处理 http/https/file/content/android.resource，
 * `data:` 不在其列。与其为了一个字段引入自定义 Fetcher 组件，
 * 不如在这里直接把 Base64 解成 Bitmap（顺带用 LruCache 避免每帧重复解码）。
 */
private object DataUrlCoverCache {
    /** 最多缓存 24 张解码后的封面：列表里同屏最多也就十几张 */
    val bitmaps = LruCache<String, Bitmap>(24)
}

/** 是否是内联图片（`data:image/...;base64,...`） */
fun isInlineImage(model: String?): Boolean =
    model != null && model.startsWith("data:image", ignoreCase = true)

/** 把 `data:image/...;base64,xxx` 解码成 Bitmap（失败返回 null） */
fun decodeInlineImage(model: String?): Bitmap? {
    if (!isInlineImage(model)) return null
    val key = model!!
    DataUrlCoverCache.bitmaps.get(key)?.let { return it }
    return try {
        val comma = key.indexOf(',')
        if (comma < 0) return null
        val bytes = Base64.decode(key.substring(comma + 1), Base64.DEFAULT)
        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        DataUrlCoverCache.bitmaps.put(key, bmp)
        bmp
    } catch (_: Throwable) {
        null
    }
}

/**
 * 统一的歌单封面视图：data URL → 直接画 Bitmap；其余交给 [SongCover]（Coil）。
 *
 * @param model 封面字符串：网络 URL / 本地绝对路径 / `data:image/...`
 */
@Composable
fun PlaylistCoverImage(
    model: String?,
    modifier: Modifier = Modifier,
    corner: Dp = 10.dp,
) {
    if (isInlineImage(model)) {
        val bitmap = remember(model) { decodeInlineImage(model) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier
                    .clip(RoundedCornerShape(corner))
                    .fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            return
        }
    }
    SongCover(url = model, modifier = modifier, corner = corner)
}
