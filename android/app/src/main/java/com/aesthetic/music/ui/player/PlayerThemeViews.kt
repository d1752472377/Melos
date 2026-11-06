package com.aesthetic.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aesthetic.music.ui.theme.AmberLight
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun RecordShelfCover(
    artworkUrl: String?,
    isPlaying: Boolean,
    vinylRotation: Float,
    modifier: Modifier = Modifier,
) {
    val revealAnim = remember { Animatable(35f) }

    LaunchedEffect(isPlaying) {
        val targetOffset = if (isPlaying) 70f else 32f
        revealAnim.animateTo(
            targetValue = targetOffset,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
    }

    Box(
        modifier = modifier.size(320.dp, 260.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = (revealAnim.value + 40f).dp)
                .size(220.dp)
                .graphicsLayer { rotationZ = vinylRotation }
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            AppColors.disc1,
                            AppColors.disc2,
                            AppColors.disc3,
                            AppColors.disc4,
                            AppColors.disc5,
                            AppColors.disc6
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centerOffset = Offset(size.width / 2, size.height / 2)
                val maxRadius = size.width / 2
                val grooves = listOf(0.92f, 0.85f, 0.78f, 0.70f, 0.62f)
                grooves.forEach { factor ->
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = maxRadius * factor,
                        center = centerOffset,
                        style = Stroke(width = 1.dp.toPx())
                    )
                }
            }

            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, AppColors.discBorder, CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(AppColors.vinylLabel),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Melos", color = AmberLight.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(AppColors.goldLight, AppColors.goldMid, AppColors.goldDark)
                        )
                    )
                    .border(1.dp, AmberLight.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(AppColors.deepest)
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(x = 10.dp)
                .size(220.dp)
                .shadow(16.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .background(AppColors.playerPanel)
        ) {
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Melos Album", color = Color.White.copy(alpha = 0.5f))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    )
            )
        }
    }
}

@Composable
fun LuminousCdCover(
    artworkUrl: String?,
    isPlaying: Boolean,
    cdRotation: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(270.dp)
            .shadow(16.dp, CircleShape)
            .graphicsLayer { rotationZ = cdRotation }
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        AppColors.slate100,
                        AppColors.slate300,
                        AppColors.slate400,
                        AppColors.slate500,
                        AppColors.slate700,
                        AppColors.slate800
                    )
                )
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val maxR = size.width / 2

            val holoBrush = Brush.sweepGradient(
                colors = listOf(
                    AppColors.neonPinkSoft,
                    AppColors.neonVioletSoft,
                    AppColors.neonBlueSoft,
                    AppColors.neonGreenSoft,
                    AppColors.neonYellowSoft,
                    AppColors.neonOrangeSoft,
                    AppColors.neonPinkSoft
                ),
                center = centerOffset
            )
            drawCircle(brush = holoBrush, radius = maxR, center = centerOffset)

            val rings = listOf(0.94f, 0.88f, 0.82f, 0.76f, 0.70f)
            rings.forEach { rFactor ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = maxR * rFactor,
                    center = centerOffset,
                    style = Stroke(width = 0.8f.dp.toPx())
                )
            }
        }

        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier
                    .size(118.dp)
                    .clip(CircleShape)
                    .background(AppColors.slate700),
                contentAlignment = Alignment.Center
            ) {
                Text("CD Audio", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
            }
        }

        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f))
                .border(1.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AppColors.deepestAlt)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
        }
    }
}
