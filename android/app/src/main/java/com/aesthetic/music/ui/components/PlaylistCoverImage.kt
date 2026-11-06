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

private object DataUrlCoverCache {
    val bitmaps = LruCache<String, Bitmap>(24)
}

fun isInlineImage(model: String?): Boolean =
    model != null && model.startsWith("data:image", ignoreCase = true)

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
