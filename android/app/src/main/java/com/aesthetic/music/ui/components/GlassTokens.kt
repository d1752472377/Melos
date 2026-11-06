package com.aesthetic.music.ui.components

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.theme.AppColors
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.min

@Composable
fun textHigh(): Color = if (appIsDark()) AppColors.glassTextHighDark else AppColors.glassTextHighLight

@Composable
fun textMedium(): Color = if (appIsDark()) AppColors.glassTextMediumDark else AppColors.glassTextMediumLight

@Composable
fun textLow(): Color = if (appIsDark()) AppColors.glassTextLowDark else AppColors.glassTextLowLight

@Composable
fun coverHairline(): Color =
    if (appIsDark()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f)

@Composable
fun coverPlaceholder(): Color = textMedium().copy(alpha = 0.12f)

@Composable
fun glassBg(): Color =
    if (appIsDark()) AppColors.glassBgDark else Color.White.copy(alpha = 0.55f)

@Composable
fun glassBorder(): Color =
    if (appIsDark()) Color.Unspecified else Color.White.copy(alpha = 0.8f)

@Composable
fun glassSubtleSurface(): Color = if (appIsDark()) Color.White.copy(alpha = 0.08f) else glassBg()

val DefaultCoverTone = AppColors.defaultCoverTone

private fun gzCardTone(base: Color): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(base.toArgb(), hsv)
    return Color.hsv(hsv[0], min(hsv[1], 0.4f), 0.3f)
}

val LocalLiquidGlassButtonsEnabled = staticCompositionLocalOf { true }

@Composable
fun usesLiquidGlassButtons(settingsAvailable: Boolean = true): Boolean =
    LocalLiquidGlassButtonsEnabled.current && settingsAvailable && Build.VERSION.SDK_INT >= 33

@Composable
fun Modifier.glassCard(shape: Shape, elevation: Dp = 8.dp): Modifier {
    val dark = appIsDark()
    val base = if (dark && elevation > 0.dp) {
        Modifier.shadow(
            elevation = elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.04f),
            spotColor = Color.Black.copy(alpha = 0.08f),
        )
    } else {
        Modifier
    }
    return this
        .then(base)
        .border(1.dp, glassBorder(), shape)
        .background(glassBg(), shape)
        .clip(shape)
}

@Composable
fun Modifier.glassToneCard(
    shape: Shape,
    tone: Color = DefaultCoverTone,
    selected: Boolean = false,
    gradient: Boolean = true,
): Modifier {
    val dark = appIsDark()
    val derived = remember(tone) { gzCardTone(tone) }
    val bg = glassBg()

    val brush: Brush? = when {
        !gradient -> null
        dark -> Brush.linearGradient(
            colorStops = arrayOf(
                0f to tone.copy(alpha = 0.45f).compositeOver(bg),
                1f to bg,
            )
        )
        else -> Brush.linearGradient(
            colorStops = arrayOf(
                0f to tone.copy(alpha = if (selected) 0.24f else 0.20f).compositeOver(bg),
                0.55f to tone.copy(alpha = if (selected) 0.10f else 0.08f).compositeOver(bg),
                1f to tone.copy(alpha = if (selected) 0.03f else 0.0f).compositeOver(bg),
            )
        )
    }

    val base = if (dark) {
        Modifier.shadow(
            elevation = 10.dp,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.3f),
            spotColor = Color.Black.copy(alpha = 0.4f),
        )
    } else {
        Modifier
    }

    return this
        .then(base)
        .border(1.dp, glassBorder(), shape)
        .then(
            if (brush != null) Modifier.background(brush, shape)
            else Modifier.background(bg, shape)
        )
        .clip(shape)
}

@Composable
fun Modifier.liquidGlassCardSurface(
    shape: Shape,
    tone: Color? = null,
    tones: List<Color> = emptyList(),
    selected: Boolean = false,
    gradient: Boolean = tone != null,
    elevation: Dp = 8.dp,
    flat: Boolean = false,
    liquidGlassEnabled: Boolean = true,
): Modifier {
    if (!usesLiquidGlassButtons(liquidGlassEnabled)) {
        return when {
            tone == null && flat ->
                this.border(1.dp, glassBorder(), shape)
                    .background(glassSubtleSurface(), shape)
                    .clip(shape)

            tone == null ->
                this.glassCard(shape, elevation)

            else ->
                this.glassToneCard(shape, tone, selected, gradient)
        }
    }

    val dark = appIsDark()
    val derived = remember(tone) { tone?.let { gzCardTone(it) } }
    val bg = glassBg()

    val brush: Brush? = when {
        !gradient || tone == null -> null
        !dark -> Brush.linearGradient(
            colorStops = arrayOf(
                0f to tone.copy(alpha = if (selected) 0.16f else 0.12f),
                0.58f to tone.copy(alpha = if (selected) 0.06f else 0.04f),
                1f to Color.Unspecified,
            )
        )
        derived != null -> Brush.linearGradient(
            colorStops = arrayOf(
                0f to derived.copy(alpha = 0.24f),
                1f to Color.Unspecified,
            )
        )
        else -> null
    }

    val startColors = remember(tones, dark) {
        tones.map { it.copy(alpha = if (dark) 0.13f else 0.075f) }
    }

    val contentColor = if (!dark || tone == null) {
        if (dark) Color.Black else Color.White
    } else {
        derived ?: DefaultCoverTone
    }

    val toneAlpha = when {
        dark && tone != null && tone != Color.Unspecified && selected -> 0.46f
        dark && tone != null && tone != Color.Unspecified -> 0.40f
        dark -> if (tones.isEmpty()) 0.22f else 0.14f
        else -> if (tones.isEmpty()) 0.24f else 0.14f
    }

    val base = if (dark) {
        Modifier.shadow(
            elevation = if (tone != null) 10.dp else elevation,
            shape = shape,
            ambientColor = Color.Black.copy(alpha = 0.3f),
            spotColor = Color.Black.copy(alpha = 0.4f),
        )
    } else {
        Modifier
    }

    val surface: Modifier = when {
        brush != null -> Modifier.background(brush, shape)
        startColors.isNotEmpty() -> Modifier.background(
            Brush.verticalGradient(startColors.map { it.compositeOver(bg) }),
            shape,
        )
        else -> Modifier.background(contentColor.copy(alpha = toneAlpha).compositeOver(bg), shape)
    }

    return this
        .then(base)
        .border(1.dp, glassBorder(), shape)
        .then(surface)
        .clip(shape)
}

@Composable
fun Modifier.discoveryGradientCard(
    shape: Shape,
    colors: List<Color>,
    useSharedGradient: Boolean = false,
): Modifier {
    if (appIsDark()) {
        return this.glassCard(shape)
    }
    val brush = if (useSharedGradient) {
        Brush.verticalGradient(colors)
    } else {
        Brush.linearGradient(colors)
    }
    return this
        .glassCard(shape)
        .background(brush, shape)
}
