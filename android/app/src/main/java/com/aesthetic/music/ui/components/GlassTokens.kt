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
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.min

/**
 * GZ Music 全局玻璃/拟态样式 token —— 逐值精确还原。
 *
 * 来源：`gz_decompiled/sources/com/gzmusic/app/ui/GlassTokensKt.java`
 *  - 纯取色 token：  GlassTokensKt.java:1186-1653
 *  - 色调推导：      GlassTokensKt.java:1174-1184
 *  - 卡片修饰符：    GlassTokensKt.java:1247(glassCard) / 1296(glassToneCard) /
 *                    1364(liquidGlassCardSurface) / 1203(discoveryGradientCard) /
 *                    1607(songAlphabetSurface) / 1623(songListFloatingButtonBorder)
 *
 * 反编译辅助门面解码（均已核对过定义，不是猜测）：
 *  - `hp1.e(N)` = `Color(0xFF......)`；`ThemeKt.appIsDark()` = `appIsDark()`
 *  - `g91.c(a, c)` = `c.copy(alpha = a)`；`g91.d`=White、`g91.b`=Black、`g91.g`=Color.Unspecified
 *  - `hp1.s(m, color, shape)` = `Modifier.background(color, shape)`   (hp1.java:3976)
 *  - `hp1.y(a, b)` = `a.compositeOver(b)`
 *  - `x8.g(m, w, color, shape)` = `Modifier.border(w, color, shape)`  (x8.java:g)
 *  - `hq3.g(m, shape)` = `Modifier.clip(shape)`                       (hq3.java:g)
 *  - `o13.a(m, brush, shape, _)` = `Modifier.background(brush, shape)` (o13.java:a)
 *  - `mg2.I(m, elev, shape, ambient, spot, _)` = `Modifier.shadow(...)` (mg2.java:I)
 *  - `pr6.O(h,s,v)` = `Color.hsv(h,s,v)`；`pr6.S(stops,_,_,_)` = `Brush.linearGradient(colorStops)`
 *    （pr6.java:258 的 synthetic S 走 Q → `new ob4(colors, stops, start, end)`）
 *  - `pr6.M(list,_,_,_)` = `Brush.verticalGradient(colors)`（pr6.java:207，x 恒为 0）
 *  - `pr6.e0(list,_,_,_)` = `Brush.horizontalGradient(colors)`（pr6.java:378，y 恒为 0）
 *
 * 注意：GZ 的 `Modifier.glassCard` 里修饰符顺序是 **border → background → clip**
 * （GlassTokensKt.java:1265），本文件按原样保留该顺序，不要"顺手理顺"。
 */

// ============================================================
// 纯取色 token（GlassTokensKt.java:1186-1653）
// ============================================================

/** GlassTokensKt.java:1642-1644 —— 一级文字色 */
@Composable
fun textHigh(): Color = if (appIsDark()) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)

/** GlassTokensKt.java:1650-1652 —— 二级文字色 */
@Composable
fun textMedium(): Color = if (appIsDark()) Color(0xFFA8A8AE) else Color(0xFF8E8E93)

/** GlassTokensKt.java:1646-1648 —— 三级/辅助文字色 */
@Composable
fun textLow(): Color = if (appIsDark()) Color(0xFF8E8E93) else Color(0xFF9A9AA0)

/** GlassTokensKt.java:1186-1197 —— 封面描边细线 */
@Composable
fun coverHairline(): Color =
    if (appIsDark()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.06f)

/** GlassTokensKt.java:1199-1201 —— 封面占位底色（= textMedium 的 12% 透明版） */
@Composable
fun coverPlaceholder(): Color = textMedium().copy(alpha = 0.12f)

/** GlassTokensKt.java:1239-1241 —— 玻璃卡片底色 */
@Composable
fun glassBg(): Color =
    if (appIsDark()) Color(0xFF1C1C1E) else Color.White.copy(alpha = 0.55f)

/**
 * GlassTokensKt.java:1243-1245 —— 玻璃卡片描边色。
 * 深色下是 `g91.g`（Color.Unspecified），即**不画描边**；这是 GZ 的原意，不是漏写。
 */
@Composable
fun glassBorder(): Color =
    if (appIsDark()) Color.Unspecified else Color.White.copy(alpha = 0.8f)

/** GlassTokensKt.java:1282-1294 —— 次级玻璃表面 */
@Composable
fun glassSubtleSurface(): Color = if (appIsDark()) Color.White.copy(alpha = 0.08f) else glassBg()

// ============================================================
// 色调推导
// ============================================================

/**
 * GlassTokensKt.java:1306 / CoverToneKt.java:29 —— 默认封面主色调。
 * 反编译真值 `hp1.e(4294583356L)` = `0xFFFA243C`。
 */
val DefaultCoverTone = Color(0xFFFA243C)

/**
 * GlassTokensKt.java:1314-1322（以及 1441-1449）—— 卡片「色调」推导：
 * 取 base 的 HSV 色相，饱和度**上限 0.4**，明度固定 **0.3**。
 */
private fun gzCardTone(base: Color): Color {
    val hsv = FloatArray(3)
    AndroidColor.colorToHSV(base.toArgb(), hsv)
    return Color.hsv(hsv[0], min(hsv[1], 0.4f), 0.3f)
}

// ============================================================
// 液态玻璃开关
// ============================================================

/**
 * 对应 `LiquidGlassButtonKt.java` 里那个 `staticCompositionLocalOf<Boolean>`（`a` 字段）。
 * 由上层主题提供；默认开启，与 GZ 一致。
 */
val LocalLiquidGlassButtonsEnabled = staticCompositionLocalOf { true }

/**
 * LiquidGlassButtonKt.java:934-936 ——
 * `usesLiquidGlassButtons(settings, composer, _)` = `LocalX.current && settings != null && SDK_INT >= 33`。
 * 本项目里 `settings != null` 表达为「设置已加载」布尔量。
 *
 * 注意：读 `CompositionLocal.current` 需要 @Composable 上下文（GZ 原函数同样接收 Composer）。
 */
@Composable
fun usesLiquidGlassButtons(settingsAvailable: Boolean = true): Boolean =
    LocalLiquidGlassButtonsEnabled.current && settingsAvailable && Build.VERSION.SDK_INT >= 33

// ============================================================
// 卡片修饰符
// ============================================================

/**
 * GlassTokensKt.java:1247-1266 —— `Modifier.glassCard(shape, elevation = 8.dp)`
 *
 * 原逻辑：深色且 elevation > 0 时先叠一层黑色阴影（ambient 4%、spot 8%），
 * 然后 `border(1.dp, glassBorder(), shape) → background(glassBg(), shape) → clip(shape)`。
 * 注意修饰符顺序与 GZ 完全一致（border 在前、background 在后）。
 */
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

/**
 * GlassTokensKt.java:1296-1349 ——
 * `Modifier.glassToneCard(shape, tone = DefaultCoverTone, selected = false, gradient = true)`
 *
 * 用封面主色调给玻璃卡片上色：
 *  - 深色：叠 10dp 阴影（黑 30% / 黑 40%），渐变从「色调 45% 叠在玻璃底上」到玻璃底
 *  - 浅色：无阴影，渐变三档（选中时 24%/10%/3%，未选中 20%/8%/0%）
 * 渐变统一是 `Brush.linearGradient(colorStops)`（pr6.S），不是竖向渐变。
 */
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

/**
 * GlassTokensKt.java:1364-1605 ——
 * `Modifier.liquidGlassCardSurface(shape, tone = null, tones = emptyList(), selected = false,
 *                                 gradient = tone != null, elevation = 8.dp, flat = false)`
 *
 * 这是首页卡片的主表面。未启用液态玻璃（或系统 < Android 13）时走 GlassTokensKt.java:1400-1429
 * 的降级路径，三种情形分别落到 `glassSubtleSurface` / `glassCard` / `glassToneCard`，
 * 本实现与之一致。
 *
 * 启用液态玻璃时，GZ 会把颜色参数交给 `LiquidGlassButtonKt` 的核心（`ig2.g`）去合成
 * 真实的背景折射层。本文件先把**颜色/渐变/描边/阴影**这部分逐值还原到位，
 * 折射层在 `LiquidGlassComponents.kt` 的移植里接入（见该文件的 `LiquidGlassSurface`）。
 * 这里保留同名的 `liquidGlassEnabled` 开关，接上后行为与 GZ 完全一致。
 */
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
        // GlassTokensKt.java:1400-1429 —— 降级路径
        return when {
            tone == null && flat ->
                // :1406  background(glassSubtleSurface(), shape) + border + clip
                this.border(1.dp, glassBorder(), shape)
                    .background(glassSubtleSurface(), shape)
                    .clip(shape)

            tone == null ->
                // :1411  glassCard(shape, elevation)
                this.glassCard(shape, elevation)

            else ->
                // :1422  glassToneCard(shape, tone, selected, gradient)
                this.glassToneCard(shape, tone, selected, gradient)
        }
    }

    val dark = appIsDark()
    val derived = remember(tone) { tone?.let { gzCardTone(it) } }
    val bg = glassBg()

    // GlassTokensKt.java:1459-1474 —— 渐变画刷
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

    // GlassTokensKt.java:1480-1487 —— tones 列表统一按深浅色降透明
    val startColors = remember(tones, dark) {
        tones.map { it.copy(alpha = if (dark) 0.13f else 0.075f) }
    }

    // GlassTokensKt.java:1489-1497 —— 内容色
    val contentColor = if (!dark || tone == null) {
        if (dark) Color.Black else Color.White
    } else {
        derived ?: DefaultCoverTone
    }

    // GlassTokensKt.java:1499-1554 —— 无显式画刷时叠加的色调透明度。
    // 说明：JADX 在这一段产生了互相矛盾的赋值（同一变量被先赋 0.24f 又被 0.4f 覆盖，
    // 且出现 `g91Var2 == 0` 这种 Java 里不成立的比较），分支归属无法逐字照搬。
    // 下面按代码里出现的全部字面量（0.22 / 0.24 / 0.14 / 0.46 / 0.40）重新组织分支，
    // 字面量本身全部来自反编译源，没有新增数值。
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

/**
 * GlassTokensKt.java:1203-1224 ——
 * `Modifier.discoveryGradientCard(shape, colors, useSharedGradient = false)`
 *
 * 深色下就是普通玻璃卡（elevation 用默认 8dp）；浅色下额外叠一层渐变。
 * 两个分支的渐变分别是 `Brush.verticalGradient(colors)`（pr6.M，x 恒为 0）
 * 与 `Brush.linearGradient(colors)`（`ob4(colors, null, Offset.Zero, Offset.Infinite)`）。
 */
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
