package com.aesthetic.music.ui.home

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.aesthetic.music.data.model.Song
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.PlaylistCoverImage
import com.aesthetic.music.ui.components.coverHairline
import com.aesthetic.music.ui.components.coverPlaceholder
import com.aesthetic.music.ui.components.glassCard
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.components.textMedium

/**
 * 首页「新歌首发」版块：三个**地区新歌榜**（华语 / 欧美 / 日语 / 韩语 每日轮换出三个）。
 *
 * ## 与旧版（`NewAlbumSection`）的两处区别
 * 1. **去掉了「更多」入口**。旧版的 `MoreText` 本来就不是新碟版块的东西
 *    （只是为接调用方的 `onOpenMore` 才照搬的 `HomeScreenKt.MoreText`），
 *    点进去是歌单广场，跟「新碟」毫无关系。
 * 2. **不再拿歌单冒充专辑**：旧版把 `dailyPlaylists + hotPlaylists` 映射成专辑卡，
 *    名字叫「新碟」显示的却是歌单。现在每张卡是一个语种/地区的新歌榜。
 *
 * ## 卡片版式保持「唱片 + 封套」
 * 用户明确要求保留唱片：封面是封套（左侧），黑胶从右侧探出小半张，封套压住唱片左半。
 * 几何与配色沿用 GZ `NewAlbumSectionKt.AlbumRecord`（`sleeveSize = maxWidth / 1.216`、
 * 唱片 `0.9 × sleeveSize`、`shadow(3.dp, CircleShape)`、封套 `shadow(4.dp) + 1dp 描边`），
 * 唱片本身是 Canvas 画的（`li.java:302-339` 的 7 段 sweepGradient + 24 道音轨 + 标签 + 主轴孔）。
 * 唯一的改动是：封套右下角多了一枚小播放钮，让「点卡片=听这个地区的新歌」有可见预期。
 */
@Composable
fun NewSongRegionSection(
    groups: List<RegionNewSongs>,
    onPlayRegion: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (groups.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "新歌首发",
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "NEW RELEASES",
                color = textLow(),
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                lineHeight = 12.sp,
            )
            // 这里**没有**「更多」：见类注释第 1 点
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            groups.take(3).forEach { group ->
                RegionCard(
                    group = group,
                    onClick = { onPlayRegion(group.songs) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * 单张地区卡：唱片 + 封套 + 地区名 + 「N 首新歌」。
 *
 * 封面比例与旧版一致（`maxWidth / 1.216f`），保证换成新内容后版块高度不跳。
 */
@Composable
private fun RegionCard(
    group: RegionNewSongs,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val first = group.songs.firstOrNull()

    Column(
        modifier = modifier
            .widthIn(max = 160.dp)
            .fillMaxWidth()
            .bouncePress(pressedScale = 0.98f, onClick = onClick),
        horizontalAlignment = Alignment.Start,
    ) {
        RegionRecord(
            coverUrl = first?.artwork,
            label = group.region.label,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "${group.region.label}新歌",
            color = textHigh(),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(2.dp))

        Text(
            text = group.countLabel,
            color = textLow(),
            fontSize = 10.sp,
            lineHeight = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(3.dp))

        Text(
            text = group.region.english,
            color = textMedium(),
            fontSize = 9.sp,
            letterSpacing = 0.3.sp,
            lineHeight = 12.sp,
            maxLines = 1,
        )
    }
}

/**
 * 唱片 + 封套组合 —— 结构完全沿用 GZ `NewAlbumSectionKt.AlbumRecord`（:83-148）：
 * `BoxWithConstraints` 里 `sleeveSize = maxWidth / 1.216f`，唱片 `0.9 × sleeveSize` 靠右，
 * 封套 `sleeveSize` 后绘制、盖住唱片左侧，只露出一小截黑胶。
 */
@Composable
private fun RegionRecord(
    coverUrl: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    // 封套主色 → 唱片标签与音轨的配色（GZ 走 CoverToneKt，本项目用 rememberCoverTone）
    val tone = rememberCoverTone(coverUrl, DefaultCoverTone)

    BoxWithConstraints(modifier = modifier) {
        val sleeveSize = maxWidth / 1.216f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(sleeveSize),
            contentAlignment = Alignment.TopStart,
        ) {
            RegionVinyl(
                tone = tone,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(sleeveSize * 0.9f)
                    .shadow(elevation = 3.dp, shape = CircleShape),
            )

            RegionSleeve(
                coverUrl = coverUrl,
                label = label,
                modifier = Modifier.size(sleeveSize),
            )
        }
    }
}

/** 封套：`shadow(4.dp) → background(coverPlaceholder) → clip → 封面 → 1dp 描边` */
@Composable
private fun RegionSleeve(
    coverUrl: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .shadow(elevation = 4.dp, shape = shape)
            .background(coverPlaceholder(), shape)
            .clip(shape),
    ) {
        if (!coverUrl.isNullOrBlank()) {
            // 用统一的封面视图：内联封面（data:image/…）也能画（见 PlaylistCoverImage）
            PlaylistCoverImage(
                model = coverUrl,
                modifier = Modifier.matchParentSize(),
                corner = 3.dp,
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(width = 1.dp, color = coverHairline(), shape = shape),
        )
        // 播放入口：右下角半透明圆钮（本版新增，让「点卡片=听歌」有可见预期）
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "播放${label}新歌",
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * 黑胶唱片绘制 —— 逐值还原 `li.java:302-339`（GZ `NewAlbumSectionKt.AlbumVinyl` 的绘制体）。
 *
 * 绘制顺序：sweepGradient 底 → 24 道同心音轨 → 外圈边线 → 标签底色 → 标签描边 → 主轴孔 → 主轴孔细环。
 * R = `size.minDimension / 2`。
 */
@Composable
private fun RegionVinyl(
    tone: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f

        val toneBlack22 = lerp(tone, Color.Black, 0.22f)
        val toneBlack40 = lerp(tone, Color.Black, 0.40f)
        val toneWhite38 = lerp(tone, Color.White, 0.38f)

        // 7 个色标的角向渐变（li.java:311-320）
        drawRect(
            brush = Brush.sweepGradient(
                0.00f to toneBlack22,
                0.14f to toneWhite38,
                0.25f to toneBlack22,
                0.52f to toneBlack40,
                0.65f to toneWhite38,
                0.77f to toneBlack22,
                1.00f to toneBlack22,
            ),
        )

        // 24 道音轨（li.java:322-328）：半径 (i*0.027+0.34)R，每 3 道亮一档
        for (i in 0 until 24) {
            drawCircle(
                color = Color.White.copy(alpha = if (i % 3 == 0) 0.15f else 0.07f),
                radius = (i * 0.027f + 0.34f) * radius,
                style = Stroke(width = radius * 0.007f),
            )
        }

        // 外圈边线（li.java:331-332）
        drawCircle(
            color = toneWhite38,
            radius = radius * 0.985f,
            style = Stroke(width = radius * 0.01f),
        )

        // 标签底色（li.java:334）
        drawCircle(
            color = lerp(tone, Color.White, 0.28f),
            radius = radius * 0.29f,
        )

        // 标签描边（li.java:335）
        drawCircle(
            color = tone,
            radius = radius * 0.23f,
            style = Stroke(width = radius * 0.014f),
        )

        // 主轴孔（li.java:336-338）
        val holeRadius = radius * 0.055f
        drawCircle(color = Color(0xFF18181E), radius = holeRadius)
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = holeRadius,
            style = Stroke(width = radius * 0.01f),
        )
    }
}

/** GZ `MotionKt.bounceClick` 等价实现（150ms / `CubicBezierEasing(0.2, 0, 0, 1)` / 无涟漪） */
@Composable
private fun Modifier.bouncePress(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = tween(
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
        ),
        label = "pressScale",
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
