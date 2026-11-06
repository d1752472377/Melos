package com.aesthetic.music.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp

private val PlayingBarDelayMillis = intArrayOf(0, 120, 240)

private val PlayingBarRestFractions = floatArrayOf(0.45f, 0.7f, 0.35f)

@Composable
fun PlayingBars(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
) {
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (index in 0 until 3) {
            val fraction = if (isPlaying) {
                val transition = rememberInfiniteTransition(label = "playingBars")
                transition.animateFloat(
                    initialValue = 0.35f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(
                            durationMillis = 420,
                            delayMillis = PlayingBarDelayMillis[index],
                            easing = LinearEasing,
                        ),
                        repeatMode = RepeatMode.Reverse,
                    ),
                    label = "bar$index",
                ).value
            } else {
                PlayingBarRestFractions[index]
            }

            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight(fraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color, RectangleShape),
            )
        }
    }
}
