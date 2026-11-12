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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
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
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun NewSongRegionSection(
    groups: List<RegionNewSongs>,
    onPlayRegion: (List<Song>) -> Unit,
    modifier: Modifier = Modifier,
    onOpenMore: (() -> Unit)? = null,
) {
    if (groups.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl)
            .glassCard(shape = RoundedCornerShape(20.dp))
            .padding(AppSpacing.xl),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "新歌首发",
                color = textHigh(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.width(AppSpacing.sm))
            Text(
                text = "NEW RELEASES",
                color = textLow(),
                fontSize = 8.sp,
                letterSpacing = 1.sp,
                lineHeight = 12.sp,
            )
            if (onOpenMore != null) {
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = "全部新歌",
                    tint = textLow(),
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOpenMore),
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
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

        Spacer(Modifier.height(AppSpacing.sm))

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

@Composable
private fun RegionRecord(
    coverUrl: String?,
    label: String,
    modifier: Modifier = Modifier,
) {
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
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AppSpacing.xxs)
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

        for (i in 0 until 24) {
            drawCircle(
                color = Color.White.copy(alpha = if (i % 3 == 0) 0.15f else 0.07f),
                radius = (i * 0.027f + 0.34f) * radius,
                style = Stroke(width = radius * 0.007f),
            )
        }

        drawCircle(
            color = toneWhite38,
            radius = radius * 0.985f,
            style = Stroke(width = radius * 0.01f),
        )

        drawCircle(
            color = lerp(tone, Color.White, 0.28f),
            radius = radius * 0.29f,
        )

        drawCircle(
            color = tone,
            radius = radius * 0.23f,
            style = Stroke(width = radius * 0.014f),
        )

        val holeRadius = radius * 0.055f
        drawCircle(color = AppColors.homeHole, radius = holeRadius)
        drawCircle(
            color = Color.White.copy(alpha = 0.5f),
            radius = holeRadius,
            style = Stroke(width = radius * 0.01f),
        )
    }
}

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
