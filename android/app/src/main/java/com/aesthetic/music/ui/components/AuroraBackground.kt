package com.aesthetic.music.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.aesthetic.music.ui.theme.appIsDark
import com.aesthetic.music.ui.theme.AppColors

val AuroraLightBase = AppColors.auroraLightBase

val AuroraDarkBase = AppColors.auroraDarkBase

private const val AURORA_SCALE_REFERENCE = 390f

private val AuroraDarkTop = AppColors.auroraDarkTop
private val AuroraDarkBottom = AppColors.auroraDarkBottom

private class AuroraBlob(
    val color: Color,
    val x: Float,
    val y: Float,
    val radius: Float,
)

private val AuroraLightBlobs = listOf(
    AuroraBlob(AppColors.auroraPeach, -0.85f, -0.55f, 360f),
    AuroraBlob(AppColors.auroraLavender, 0.85f, -0.35f, 340f),
    AuroraBlob(AppColors.auroraMint, -0.55f, 0.75f, 320f),
    AuroraBlob(AppColors.auroraPeach, 0.60f, 0.85f, 300f),
)

private fun DrawScope.drawAuroraBlob(
    x: Float,
    y: Float,
    radius: Float,
    color: Color,
    alpha0: Float,
    alpha55: Float,
) {
    val center = Offset(x, y)
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to color.copy(alpha = alpha0),
                0.55f to color.copy(alpha = alpha55),
                1f to color.copy(alpha = 0f),
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = appIsDark()
    Box(
        modifier
            .fillMaxSize()
            .drawWithCache {
                val scale = size.width / AURORA_SCALE_REFERENCE
                val width = size.width
                val height = size.height

                onDrawBehind {
                    if (dark) {
                        drawRect(
                            Brush.horizontalGradient(
                                listOf(AuroraDarkTop, AuroraDarkBase, AuroraDarkBottom)
                            )
                        )

                        drawAuroraBlob(
                            x = (180f - 0.3f) * scale,
                            y = (220f - 0.4f) * scale,
                            radius = 320f * scale,
                            color = AppColors.auroraDarkPurple,
                            alpha0 = 0.085f,
                            alpha55 = 0.025f,
                        )
                        drawAuroraBlob(
                            x = width - 60f * scale,
                            y = height - 120f * scale,
                            radius = 280f * scale,
                            color = AppColors.auroraDarkTeal,
                            alpha0 = 0.075f,
                            alpha55 = 0.022f,
                        )
                    } else {
                        drawRect(color = AuroraLightBase)

                        for (blob in AuroraLightBlobs) {
                            val diameter = blob.radius * scale
                            val half = diameter / 2f
                            drawAuroraBlob(
                                x = (width - diameter) * ((blob.x + 1f) / 2f) + half,
                                y = (height - diameter) * ((blob.y + 1f) / 2f) + half,
                                radius = half,
                                color = blob.color,
                                alpha0 = 0.45f,
                                alpha55 = 0.15f,
                            )
                        }

                        drawAuroraBlob(
                            x = (-80f * scale) + (380f * scale / 2f),
                            y = (-120f * scale) + (380f * scale / 2f),
                            radius = 380f * scale / 2f,
                            color = AppColors.auroraOrange,
                            alpha0 = 0.094f,
                            alpha55 = 0.031f,
                        )
                        drawAuroraBlob(
                            x = (80f * scale) + (300f * scale / 2f),
                            y = (-100f * scale) + (300f * scale / 2f),
                            radius = 300f * scale / 2f,
                            color = AppColors.auroraYellow,
                            alpha0 = 0.078f,
                            alpha55 = 0.024f,
                        )
                        drawAuroraBlob(
                            x = (width - 20f * scale - 240f * scale) + (240f * scale / 2f),
                            y = (-80f * scale) + (240f * scale / 2f),
                            radius = 240f * scale / 2f,
                            color = AppColors.auroraPink,
                            alpha0 = 0.063f,
                            alpha55 = 0.016f,
                        )
                    }
                }
            }
    )
}
