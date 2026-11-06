package com.aesthetic.music.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.player.MelosEqualizer
import com.aesthetic.music.ui.components.BottomContentSpacer
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.glassBorder
import com.aesthetic.music.ui.home.HomeAmbientGlowBackground
import com.aesthetic.music.ui.home.iosGlassCard
import com.aesthetic.music.ui.theme.Palette
import com.aesthetic.music.ui.theme.PrimaryBrown
import com.aesthetic.music.ui.theme.TextMuted
import com.aesthetic.music.ui.theme.TextPrimary
import com.aesthetic.music.ui.theme.TextSoft
import kotlin.math.log10
import kotlin.math.pow
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

@Composable
fun EqualizerScreen(
    onBack: () -> Unit,
) {
    val eqState by MelosEqualizer.state.collectAsState()
    val isDark = Palette.isDark
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isDark) AppColors.slate900 else AppColors.slate50)
    ) {
        HomeAmbientGlowBackground()

        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.xxl, end = AppSpacing.page, top = statusBarTop + AppSpacing.md, bottom = AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextPrimary,
                    )
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "专业调音台",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        color = TextPrimary,
                    )
                    Text(
                        text = "10段图形均衡器 · 软件音效处理",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = TextSoft,
                    )
                }

                IconButton(
                    onClick = { MelosEqualizer.resetToFlat() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = "重置为平直",
                        tint = TextSoft,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(AppSpacing.xs))

                Switch(
                    checked = eqState.isEnabled,
                    onCheckedChange = { MelosEqualizer.setEnabled(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = if (isDark) AppColors.sky else PrimaryBrown,
                    ),
                    modifier = Modifier.size(42.dp, 24.dp)
                )
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxl)
            ) {
                item(key = "response_curve_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(24.dp), elevation = 3.dp)
                            .padding(AppSpacing.xxl)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Filled.Tune,
                                        contentDescription = null,
                                        tint = if (isDark) AppColors.sky else PrimaryBrown,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(AppSpacing.xs))
                                    Text(
                                        text = "频响响应曲线",
                                        style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = if (eqState.isEnabled) "实时模拟生效中" else "已旁通绕过",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = if (eqState.isEnabled) (if (isDark) AppColors.emeraldLight else AppColors.emeraldDeep) else TextMuted
                                )
                            }

                            Spacer(Modifier.height(AppSpacing.lg))

                            FrequencyResponseCanvas(
                                gains = eqState.bandGains,
                                preamp = eqState.preampGain,
                                isEnabled = eqState.isEnabled,
                                isDark = isDark,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                            )
                        }
                    }
                }

                item(key = "preamp_card") {
                    val hasBoost = remember(eqState.bandGains) { eqState.bandGains.any { it > 1.5f } }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(20.dp), elevation = 2.dp)
                            .padding(horizontal = AppSpacing.xxl, vertical = AppSpacing.xl)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "主预衰减 (Preamp)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "${if (eqState.preampGain >= 0) "+" else ""}${"%.1f".format(eqState.preampGain)} dB",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                                    color = if (isDark) AppColors.sky else PrimaryBrown
                                )
                            }

                            Slider(
                                value = eqState.preampGain,
                                onValueChange = { MelosEqualizer.setPreampGain(it) },
                                valueRange = -12f..12f,
                                steps = 47,
                                colors = SliderDefaults.colors(
                                    thumbColor = if (isDark) AppColors.sky else PrimaryBrown,
                                    activeTrackColor = if (isDark) AppColors.sky else PrimaryBrown,
                                    inactiveTrackColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.12f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (hasBoost && eqState.preampGain > -1f) {
                                Text(
                                    text = "💡 提示：存在增益提升段，建议适当调低预衰减以避免音频削波失真。",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = AppColors.amber
                                )
                            }
                        }
                    }
                }

                item(key = "presets_card") {
                    Column {
                        Text(
                            text = "调音大师预设",
                            style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                            color = TextPrimary,
                            modifier = Modifier.padding(start = AppSpacing.xxs, bottom = AppSpacing.sm)
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            items(MelosEqualizer.PRESETS, key = { it.id }) { preset ->
                                val selected = eqState.currentPresetId == preset.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (selected) {
                                                Brush.linearGradient(
                                                    listOf(
                                                        if (isDark) AppColors.sky else PrimaryBrown,
                                                        if (isDark) AppColors.indigo else AppColors.indigoLight
                                                    )
                                                )
                                            } else {
                                                Brush.linearGradient(
                                                    listOf(
                                                        (if (isDark) AppColors.slate800 else Color.White).copy(alpha = 0.85f),
                                                        (if (isDark) AppColors.slate800 else Color.White).copy(alpha = 0.85f)
                                                    )
                                                )
                                            }
                                        )
                                        .border(
                                            width = 1.dp,
                                            color = if (selected) DefaultCoverTone else glassBorder(),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .clickable { MelosEqualizer.applyPreset(preset.id) }
                                        .padding(horizontal = AppSpacing.xxl, vertical = 9.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontSize = 13.sp,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        ),
                                        color = if (selected) Color.White else TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bands_sliders_card") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .iosGlassCard(shape = RoundedCornerShape(24.dp), elevation = 3.dp)
                            .padding(AppSpacing.xxl)
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "10 频段推子",
                                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                                    color = TextPrimary
                                )
                                Text(
                                    text = "双击滑块快速归零 (0dB)",
                                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                    color = TextSoft
                                )
                            }

                            Spacer(Modifier.height(AppSpacing.xxl))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                for (idx in 0 until 10) {
                                    BandVerticalFader(
                                        freqLabel = MelosEqualizer.FREQUENCY_LABELS[idx],
                                        gain = eqState.bandGains[idx],
                                        isEnabled = eqState.isEnabled,
                                        isDark = isDark,
                                        onGainChange = { newGain -> MelosEqualizer.setBandGain(idx, newGain) },
                                        onReset = { MelosEqualizer.setBandGain(idx, 0f) }
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "bottom_spacer") {
                    Spacer(Modifier.height(BottomContentSpacer))
                }
            }
        }
    }
}

@Composable
private fun FrequencyResponseCanvas(
    gains: List<Float>,
    preamp: Float,
    isEnabled: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val midY = height / 2f

        val gridLines = listOf(-12f, -6f, 0f, 6f, 12f)
        for (dB in gridLines) {
            val y = midY - (dB / 15f) * (midY * 0.85f)
            val isZero = dB == 0f
            drawLine(
                color = if (isZero) {
                    (if (isDark) AppColors.slate500 else AppColors.slate400).copy(alpha = 0.5f)
                } else {
                    (if (isDark) AppColors.slate700 else AppColors.slate200).copy(alpha = 0.35f)
                },
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = if (isZero) 1.5f else 1f
            )
        }

        val sampleCount = 80
        val points = mutableListOf<Offset>()
        val minLog = log10(20f)
        val maxLog = log10(20000f)

        for (i in 0..sampleCount) {
            val ratio = i / sampleCount.toFloat()
            val logF = minLog + ratio * (maxLog - minLog)
            val f = 10.0.pow(logF.toDouble()).toFloat()
            val gain = if (isEnabled) MelosEqualizer.calculateFrequencyGain(f, gains, preamp) else 0f
            val x = ratio * width
            val y = midY - (gain / 15f) * (midY * 0.85f)
            points.add(Offset(x, y.coerceIn(4f, height - 4f)))
        }

        val curvePath = Path()
        val fillPath = Path()

        if (points.isNotEmpty()) {
            curvePath.moveTo(points[0].x, points[0].y)
            fillPath.moveTo(points[0].x, points[0].y)

            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val curr = points[i]
                val midPointX = (prev.x + curr.x) / 2f
                val midPointY = (prev.y + curr.y) / 2f
                curvePath.quadraticTo(prev.x, prev.y, midPointX, midPointY)
                fillPath.quadraticTo(prev.x, prev.y, midPointX, midPointY)
            }
            curvePath.lineTo(points.last().x, points.last().y)
            fillPath.lineTo(points.last().x, points.last().y)
            fillPath.lineTo(width, height)
            fillPath.lineTo(0f, height)
            fillPath.close()

            val fillBrush = Brush.verticalGradient(
                colors = listOf(
                    (if (isDark) AppColors.eqSkyGlow else PrimaryBrown.copy(alpha = 0.25f)),
                    Color.Transparent
                ),
                startY = 0f,
                endY = height
            )
            drawPath(fillPath, fillBrush)

            val curveBrush = Brush.horizontalGradient(
                colors = if (isEnabled) {
                    listOf(
                        AppColors.sky,
                        AppColors.indigoLight,
                        AppColors.purple,
                        AppColors.pink,
                    )
                } else {
                    listOf(TextMuted, TextMuted)
                }
            )
            drawPath(
                path = curvePath,
                brush = curveBrush,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )

            for (freq in MelosEqualizer.FREQUENCIES) {
                val ratio = (log10(freq.toFloat()) - minLog) / (maxLog - minLog)
                val x = ratio * width
                val gain = if (isEnabled) MelosEqualizer.calculateFrequencyGain(freq.toFloat(), gains, preamp) else 0f
                val y = (midY - (gain / 15f) * (midY * 0.85f)).coerceIn(4f, height - 4f)

                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(x, y)
                )
                drawCircle(
                    brush = curveBrush,
                    radius = 2.dp.toPx(),
                    center = Offset(x, y)
                )
            }
        }
    }
}

@Composable
private fun BandVerticalFader(
    freqLabel: String,
    gain: Float,
    isEnabled: Boolean,
    isDark: Boolean,
    onGainChange: (Float) -> Unit,
    onReset: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(30.dp)
    ) {
        Text(
            text = "${if (gain > 0) "+" else ""}${"%.0f".format(gain)}",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (gain != 0f) FontWeight.Bold else FontWeight.Normal
            ),
            color = when {
                !isEnabled -> TextMuted
                gain > 0 -> if (isDark) AppColors.sky else PrimaryBrown
                gain < 0 -> if (isDark) AppColors.purple else AppColors.grayDeep
                else -> TextSoft
            }
        )

        Spacer(Modifier.height(AppSpacing.xxs))

        Box(
            modifier = Modifier
                .height(110.dp)
                .width(22.dp)
                .clip(RoundedCornerShape(11.dp))
                .background((if (isDark) AppColors.slate800 else AppColors.slate200).copy(alpha = 0.6f))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { onReset() },
                        onTap = { offset ->
                            val ratio = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            val newGain = (ratio * 24f - 12f).coerceIn(-12f, 12f)
                            onGainChange(newGain)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background((if (isDark) Color.White else Color.Black).copy(alpha = 0.18f))
            )

            val normalizedRatio = ((gain + 12f) / 24f).coerceIn(0f, 1f)
            val thumbOffsetY = (1f - normalizedRatio) * (110f - 20f)

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = thumbOffsetY.dp)
                        .size(22.dp, 20.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            brush = if (!isEnabled) {
                                Brush.verticalGradient(listOf(TextMuted, TextMuted))
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        if (isDark) AppColors.sky else PrimaryBrown,
                                        if (isDark) AppColors.skyDeep else PrimaryBrown.copy(alpha = 0.85f)
                                    )
                                )
                            }
                        )
                        .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xs))

        Text(
            text = freqLabel.replace("Hz", "").replace("kHz", "k"),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = TextSoft
        )
    }
}
