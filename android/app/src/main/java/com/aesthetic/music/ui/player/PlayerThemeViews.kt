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

/**
 * 唱片架黑胶封套抽拉视图（移植自瓜子音乐 RecordShelfPlayer 核心动效）
 * 包含外层黑胶纸质封套与内部抽出式黑胶唱片，播放时唱片抽出并持续旋转。
 */
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
        // 1. 抽出式黑胶唱片（处于底层）
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
                            Color(0xFF232327),
                            Color(0xFF141417),
                            Color(0xFF1E1E22),
                            Color(0xFF0F0F11),
                            Color(0xFF26262B),
                            Color(0xFF0B0B0C)
                        )
                    )
                )
                .border(1.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // 逼真音轨同心圆纹理
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

            // 唱片中心标签封面
            if (!artworkUrl.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, Color(0xFF111113), CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(105.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2A1C16)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Melos", color = AmberLight.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }
            }

            // 唱片中心金色扣孔
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(Color(0xFFE5C158), Color(0xFF8A6D1C), Color(0xFF261D09))
                        )
                    )
                    .border(1.dp, AmberLight.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF09090A))
                )
            }
        }

        // 2. 外层专辑封套（处于上层，遮盖住唱片的左半部分）
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
                .background(Color(0xFF1E1E24))
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

            // 封套右边缘立体压痕与纸套内层阴影
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

/**
 * 流光 CD 视图（移植自瓜子音乐 LuminousCdPlayer 核心动效）
 * 逼真光学光盘反射效果与全息镭射七彩偏光，旋转时流光流转。
 */
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
                        Color(0xFFF1F5F9),
                        Color(0xFFCBD5E1),
                        Color(0xFF94A3B8),
                        Color(0xFF64748B),
                        Color(0xFF334155),
                        Color(0xFF1E293B)
                    )
                )
            )
            .border(1.5.dp, Color.White.copy(alpha = 0.45f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // 全息流光彩虹干涉渐变
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerOffset = Offset(size.width / 2, size.height / 2)
            val maxR = size.width / 2

            // 镭射反光条
            val holoBrush = Brush.sweepGradient(
                colors = listOf(
                    Color(0x33F472B6), // 粉
                    Color(0x44A78BFA), // 紫
                    Color(0x4438BDF8), // 蓝
                    Color(0x4434D399), // 绿
                    Color(0x44FACC15), // 黄
                    Color(0x44FB923C), // 橙
                    Color(0x33F472B6)  // 粉环闭合
                ),
                center = centerOffset
            )
            drawCircle(brush = holoBrush, radius = maxR, center = centerOffset)

            // CD 音轨同心圆微细刻线
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

        // CD 印刷面：中心封面圆形区域
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
                    .background(Color(0xFF334155)),
                contentAlignment = Alignment.Center
            ) {
                Text("CD Audio", color = Color.White.copy(alpha = 0.6f), fontWeight = FontWeight.SemiBold)
            }
        }

        // CD 透明亚克力夹持内环与中心通孔
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
                    .background(Color(0xFF09090C))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
            )
        }
    }
}
