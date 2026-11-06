package com.aesthetic.music.ui.home

import android.animation.ValueAnimator
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.components.DefaultCoverTone
import com.aesthetic.music.ui.components.discoveryGradientCard
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textMedium
import com.aesthetic.music.ui.theme.appIsDark
import kotlin.math.min
import com.aesthetic.music.ui.theme.AppSpacing
import com.aesthetic.music.ui.theme.AppColors

/**
 * 首页「三大拟态入口卡」——逐值还原 GZ Music
 * `HomeScreenKt.java:7371-7835`（`HomeEntryCards-au3_HiA`）
 * 与其唯一子项 `HomeScreenKt.java:7060-7331`（`HomeDiscoveryEntryCard`）。
 *
 * 结构（反编译逐行对应）：
 * ```
 * Row(                                  // 7503-7520 —— gy6.a(...) = Row 量测策略
 *   Modifier.fillMaxWidth()             // 7503  ij7.g(c05.a, 1.0f)
 *           .padding(horizontal = AppSpacing.xxl)// 7503  hp1.q0(..., 16.0f, 0.0f, 2)
 *           .height(IntrinsicSize.Min)  // 7503  zh.A(...) → xp3 → yp3.i0 = bq3.c = IntrinsicSize.Min
 *   , horizontalArrangement = Arrangement.spacedBy(AppSpacing.md) // 7504  j10(10.0f, true, c(9))
 *   , verticalAlignment = Alignment.Top                    // 7504  il0.e0 = wg0(-1.0f)
 * ) {
 *   3 × HomeDiscoveryEntryCard(         // 7493 / 7496 / 7502  oa3 数据
 *     modifier = Modifier.weight(1f, true).fillMaxHeight(1f), // 7522 ez3(1.0f, true) + ij7.c(..., 1.0f)
 *   )
 * }
 * ```
 * 单卡内部（7060-7331）：
 * ```
 * Box(Modifier.discoveryGradientCard(RoundedCornerShape(20.dp), palette)
 *          .pressScale(0.98f, onClick))
 *   ├─ Spacer(matchParentSize().entryHalo(...))   // 7162（仅浅色）+ 7838 HomeEntryHalo
 *   ├─ Column(fillMaxSize().padding(AppSpacing.md))       // 7166-7168
 *   │    ├─ Text(label,  14sp / W800 / -0.4sp / 19sp / Ellipsis / 单行)  // 7184
 *   │    ├─ Spacer(height = 2.dp)                                        // 7185
 *   │    ├─ Text(status,  9sp / 13sp / Ellipsis / 单行)                   // 7196
 *   │    └─ Spacer(fillMaxWidth().height(30.dp | 48.dp))                  // 7198
 *   └─ Box(matchParentSize(), TopStart) { Icon(26.dp | 22.dp, 66%/75% 处) } // 7201-7321
 * ```
 *
 * ## 与 GZ 的三处接线替换（GZ 原值来源已写在对应注释里）
 * 1. GZ 的 `PlayerViewModel` 只被用来推导「是否正在播放」：
 *    `z6 = (queueSource?.type != "shuffle_listen") && nowPlaying.g`（7486-7490），
 *    本项目 UI 层不引入 ViewModel，改为显式参数 [isPlaying]（GZ 语义见 KDoc）。
 * 2. GZ 的 `String str` 只被用来推导封面主色：
 *    `rememberCoverTone(str, 0L, z)`（7491）= `(color, alpha)`（12991-13031），
 *    本项目没有封面取色器，改为显式参数 [coverTone] / [coverToneAlpha]。
 * 3. GZ 的 `boolean z`（7426/7466 默认 false）与 `float f2`（7469/7469 默认 16.0f）
 *    在全部调用点都走默认分支（8815/9077/9466/TabletHomeScreenKt:1686 传 `false` + `0.0f`
 *    且 `i3` 带 `0x40|0x02` 位），故这里直接写成默认参数。
 *
 * ## 波形（PlayingBars）说明
 * GZ 的这三张入口卡**不含** `PlayingBars`：`HomeDiscoveryEntryCard` 的整段函数体
 * （7060-7331）没有任何 `PlayingBarsKt.m337PlayingBarsFNF3uiM` 调用；
 * GZ 里 PlayingBars 只出现在 HomeScreenKt:2272 / 2495 / 10945 等别处。
 * 「正在播放」在本卡片上的真实表现是：第三张卡副标题变「正在播放」+ 光晕开始脉动
 * （`oa3.d && ValueAnimator.areAnimatorsEnabled()`，7162 / 7863-7866）。
 *
 * @param onOpenRecommend 第一张卡（猜你喜欢）点击：GZ `ys2Var2`（7493）
 * @param onOpenRanks     第二张卡（排行榜）点击：GZ `ys2Var`（7496）
 * @param onOpenFm        第三张卡（随心听）点击：GZ `ys2Var3`（7502）
 * @param isPlaying       是否正在播放：GZ `z6`（7486-7490）
 * @param modifier        宿主修饰符；GZ 无此参数，内部从 `Modifier`（c05.a）起链（7503）
 * @param coverTone       封面主色：GZ `rememberCoverTone(...).color`（7491 / 13030）
 * @param coverToneAlpha  封面主色透明度：GZ `rememberCoverTone(...).alpha`（7491 / 13030）
 * @param isCompact       是否紧凑机型：GZ `DeviceProfileKt.rememberDeviceProfile(...).a == rx1.c`
 *                        （7473），决定 0.75f/0.73f 与 30.dp/48.dp 两处取值
 * @param horizontalPadding GZ 字面量 16.dp（7503，参数默认值见 7763-7767）；
 *                        宿主列表若已带横向内边距，可传 0.dp 避免叠加
 */
@Composable
fun HomeEntryCards(
    onOpenRecommend: () -> Unit,
    onOpenRanks: () -> Unit,
    onOpenFm: () -> Unit,
    isPlaying: Boolean = false,
    modifier: Modifier = Modifier,
    coverTone: Color = DefaultCoverTone,
    coverToneAlpha: Float = 1f,
    isCompact: Boolean = true,
    horizontalPadding: Dp = 16.dp,
) {
    // HomeScreenKt.java:7493 / 7496 / 7500-7502 —— wi0.A(oa3, oa3, oa3)
    val items = listOf(
        HomeEntryItem(
            label = "猜你喜欢", // HomeScreenKt.java:7493
            status = "每日精选", // HomeScreenKt.java:7493
            artwork = 0, // HomeScreenKt.java:7493
            live = false, // HomeScreenKt.java:7493
            onClick = onOpenRecommend,
        ),
        HomeEntryItem(
            label = "排行榜", // HomeScreenKt.java:7496
            status = "此刻热门", // HomeScreenKt.java:7496
            artwork = 1, // HomeScreenKt.java:7496
            live = false, // HomeScreenKt.java:7496
            onClick = onOpenRanks,
        ),
        HomeEntryItem(
            label = "随心听", // HomeScreenKt.java:7502
            status = if (isPlaying) "正在播放" else "随机漫游", // HomeScreenKt.java:7497-7501
            artwork = 2, // HomeScreenKt.java:7502
            live = isPlaying, // HomeScreenKt.java:7502（第 4 个实参 = z6）
            onClick = onOpenFm,
        ),
    )

    Row(
        modifier = modifier
            // HomeScreenKt.java:7503 —— ij7.g(c05.a, 1.0f) = fillMaxWidth(1f)
            .fillMaxWidth(1f)
            // HomeScreenKt.java:7503 —— hp1.q0(..., 16.0f, 0.0f, 2) = padding(horizontal = AppSpacing.xxl)
            .padding(horizontal = horizontalPadding)
            // HomeScreenKt.java:7503 —— zh.A(...)：zh.java:103 + xp3.java:9 + yp3.java:9
            // + bq3.java:13 → IntrinsicSize.Min
            .height(IntrinsicSize.Min),
        // HomeScreenKt.java:7504 —— gy6.a(new j10(10.0f, true, new c(9)), il0.e0, ...)
        // j10 = Arrangement.SpacedAligned(10.0f)（j10.java:16/41 → P0(10.0f)），
        // c(9) 的对齐 lambda = Alignment.Start（与 PlayingBarsKt 里同一套解码）
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        // HomeScreenKt.java:7504 —— il0.e0 = new wg0(-1.0f)（il0.java:60）= Alignment.Top
        verticalAlignment = Alignment.Top,
    ) {
        items.forEach { item ->
            HomeDiscoveryEntryCard(
                item = item,
                isCompact = isCompact,
                coverTone = coverTone,
                coverToneAlpha = coverToneAlpha,
                // HomeScreenKt.java:7522 —— ij7.c(new ez3(1.0f, true), 1.0f)
                // ez3 = RowScope 的 parentData(weight = 1.0f, fill = true)（ez3.java:5-12 / fz3.java）
                // ij7.c(..., 1.0f) = fillMaxHeight(1f)（wk2(uy1.c, 1.0f)，uy1.c = Vertical）
                modifier = Modifier
                    .weight(1f, fill = true)
                    .fillMaxHeight(1f),
            )
        }
    }
}

// ============================================================
// 数据（GZ defpackage/oa3.java = HomeEntryCardItem）
// ============================================================

/** 对应 GZ `oa3`（oa3.java:5-18），字段名取自其 `toString()`（oa3.java:36-42）。 */
private data class HomeEntryItem(
    /** oa3.a —— label */
    val label: String,
    /** oa3.b —— status */
    val status: String,
    /** oa3.c —— artwork（0/1/2 决定渐变与图标） */
    val artwork: Int,
    /** oa3.d —— live（第三张卡为「正在播放」） */
    val live: Boolean,
    /** oa3.e —— onClick */
    val onClick: () -> Unit,
)

// ============================================================
// 渐变与光晕取色（全部为字面量，来源见注释）
// ============================================================

/**
 * HomeScreenKt.java:7105 / 7107 —— artwork == 0 的渐变三色。
 * 字节码：`wi0.A(new g91(hp1.e(4294942158L)), new g91(hp1.e(4294952412L)), new g91(hp1.e(4294961395L)))`
 */
private val EntryGradientArtwork0 = listOf(
    AppColors.entryPink1, // 4294942158 —— HomeScreenKt.java:7107
    AppColors.entryPink2, // 4294952412 —— HomeScreenKt.java:7107
    AppColors.entryPink3, // 4294961395 —— HomeScreenKt.java:7107
)

/**
 * HomeScreenKt.java:7105 —— artwork == 1 的渐变三色。
 * 字节码：`wi0.A(new g91(hp1.e(4290633466L)), new g91(hp1.e(4289587699L)), new g91(hp1.e(4288541661L)))`
 */
private val EntryGradientArtwork1 = listOf(
    AppColors.entryBlue1, // 4290633466 —— HomeScreenKt.java:7105
    AppColors.entryBlue2, // 4289587699 —— HomeScreenKt.java:7105
    AppColors.entryGreen1, // 4288541661 —— HomeScreenKt.java:7105
)

/**
 * HomeScreenKt.java:7105 —— artwork >= 2 的渐变三色。
 * 字节码：`wi0.A(new g91(hp1.e(4290310137L)), new g91(hp1.e(4290571997L)), new g91(hp1.e(4293458600L)))`
 */
private val EntryGradientArtwork2 = listOf(
    AppColors.entryCyan, // 4290310137 —— HomeScreenKt.java:7105
    AppColors.entryMint, // 4290571997 —— HomeScreenKt.java:7105
    AppColors.entryLime, // 4293458600 —— HomeScreenKt.java:7105
)

/**
 * HomeScreenKt.java:7119-7123 —— 光晕基色（= 该档渐变色的「末色」，但 artwork==1 的取值
 * 4288541678 = 0xFF9DF3EE 与渐变末色 4288541661 = 0xFF9DF3DD **不同**，按字节码分别保留）。
 */
private fun entryHaloColor(artwork: Int): Color = when (artwork) {
    0 -> AppColors.entryPink3 // 4294961395 —— HomeScreenKt.java:7122
    1 -> AppColors.entryCyan2 // 4288541678 —— HomeScreenKt.java:7120
    else -> AppColors.entryLime2 // 4293589385 —— HomeScreenKt.java:7120
}

/**
 * HomeScreenKt.java:7105-7118 —— 取该档渐变色。
 *
 * artwork >= 2 时逐色做一次「封面主色染色」（7113-7117）：
 * ```
 * colors.map { lerp(it, lerp(tone, Color.White, 0.75f), toneAlpha * 0.1f) }
 * ```
 * 字节码：`jH0 = hp1.h0(jH0, xl1Var.b * 0.1f, hp1.h0(xl1Var.a, 0.75f, g91.d))`，
 * `hp1.h0(a, fraction, b) = lerp(a, b, fraction)`（hp1.java:3275-3290 逐通道线性插值），
 * `g91.d = Color.White`。
 */
private fun entryGradientColors(artwork: Int, tone: Color, toneAlpha: Float): List<Color> {
    val base = when (artwork) {
        0 -> EntryGradientArtwork0
        1 -> EntryGradientArtwork1
        else -> EntryGradientArtwork2
    }
    if (artwork < 2) return base // HomeScreenKt.java:7113 —— if (i6 >= i5) 才染色
    val tint = lerp(tone, Color.White, 0.75f) // HomeScreenKt.java:7114 —— hp1.h0(xl1Var.a, 0.75f, g91.d)
    return base.map { lerp(it, tint, toneAlpha * 0.1f) } // HomeScreenKt.java:7114 —— xl1Var.b * 0.1f
}

/**
 * MotionKt.java:46-48 / ct.java:8 / bs4.java:6 ——
 * `MotionKt.a = ct.a.easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)`。
 * 同时用于：按下缩放 150ms 缓动（ct.a = qk5.r0(150, 2, MotionKt.a)）与光晕 4000ms 脉动缓动（7866）。
 */
private val MotionStandardEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f) // bs4.java:6

// ============================================================
// 单张入口卡（GZ HomeDiscoveryEntryCard，7060-7331）
// ============================================================

@Composable
private fun HomeDiscoveryEntryCard(
    item: HomeEntryItem,
    isCompact: Boolean,
    coverTone: Color,
    coverToneAlpha: Float,
    modifier: Modifier = Modifier,
) {
    val dark = appIsDark() // HomeScreenKt.java:7094 —— ThemeKt.appIsDark(composer, 0)

    // HomeScreenKt.java:7095 —— `float f4 = z ? 0.75f : 0.73f`
    // z 即 HomeDiscoveryEntryCard 第 4 个参数（7522 传入的 z5 = 紧凑机型判定），
    // 该 float 同时驱动光晕中心 Y 与图标中心 Y。
    val centerYFactor = if (isCompact) 0.75f else 0.73f

    // HomeScreenKt.java:7096-7102 —— remember { ValueAnimator.areAnimatorsEnabled() }
    val animatorsEnabled = remember { ValueAnimator.areAnimatorsEnabled() }

    val colors = entryGradientColors(item.artwork, coverTone, coverToneAlpha) // HomeScreenKt.java:7105-7118
    val haloColor = entryHaloColor(item.artwork) // HomeScreenKt.java:7119-7123

    // HomeScreenKt.java:7124 —— MotionKt.c(modifier, false, 0.98f, false, null, oa3Var.e, 13)
    //   MotionKt.c(f05, z, f, z2, dx6, onClick, flags)（MotionKt.java:74-99），flags = 13 = 0b1101：
    //     bit0 → z(enabled) 取默认 true；bit2 → z2 取默认 true；bit3 → interactionSource 取默认 remember；
    //     未置 bit1 → 缩放比例用实参 0.98f（不是默认 0.96f）。
    //   压缩 lambda（MotionKt.java:52-72）：
    //     scale = if (pressed && enabled && z2) 0.98f else 1.0f，spec = yn.b(..., ct.a, "pressScale")
    //     ct.a = qk5.r0(150, 2, MotionKt.a) = tween(150ms, easing = MotionKt.a)（ct.java:8）
    //     最后 y65.U(jv4.x(modifier, scale), interaction, null, enabled, dx6, onClick, 8)
    //       jv4.x = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }（hi.java:60-61）
    val interaction = remember { MutableInteractionSource() } // rt0.i / x45
    val pressed by interaction.collectIsPressedAsState() // xc2.i / collectIsPressedAsState
    val scale: State<Float> = animateFloatAsState(
        targetValue = if (pressed) 0.98f else 1.0f, // MotionKt.java:74 —— f = 0.98f
        animationSpec = tween(
            durationMillis = 150, // ct.java:8 —— qk5.r0(150, 2, MotionKt.a)
            easing = MotionStandardEasing, // ct.java:8 —— MotionKt.a = bs4.a
        ),
        label = "pressScale", // MotionKt.java:62
    )

    Box(
        modifier = modifier
            // HomeScreenKt.java:7124 —— GlassTokensKt.discoveryGradientCard(f05Var, by6.a(20.0f), colors, false)
            // by6.a(20.0f) = RoundedCornerShape(20.dp)
            .discoveryGradientCard(
                shape = RoundedCornerShape(20.dp),
                colors = colors,
                useSharedGradient = false, // HomeScreenKt.java:7124 第 4 个实参 false
            )
            // MotionKt.java:69 —— jv4.x(f05Var, { scaleX/scaleY })（值在 lambda 内读取，不触发重组）
            .graphicsLayer {
                val s = scale.value // hi.java:60-61 —— v23Var.p(v), v23Var.j(v)
                scaleX = s
                scaleY = s
            }
            // MotionKt.java:69 —— y65.U(jv4.x(...), interactionSource, indication=null,
            //   enabled = z(true), dx6, onClick = ys2Var, 8)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = true,
                onClick = item.onClick,
            ),
        // HomeScreenKt.java:7125-7126 —— jj0.d(il0.f, false)
        // il0.f = new xg0(-1.0f, -1.0f)（il0.java:51）= Alignment.TopStart，propagateMinConstraints = false
        contentAlignment = Alignment.TopStart,
    ) {
        if (!dark) {
            // HomeScreenKt.java:7150-7165 —— 深色分支什么都不画；浅色分支才挂光晕
            // m45HomeEntryHaloKTwxG1Y(jE, oa3Var.d && zBooleanValue, oj0Var.b(), f2, ...)
            //   oj0Var.b() = new hj0(il0.o, true)（oj0.java:13-15）
            //     il0.o = new xg0(0.0f, 0.0f) = Alignment.Center（il0.java:55），
            //     boolean = matchParentSize（BoxScope 子项数据，ij0.java / jj0.java:50-55）
            EntryHalo(
                color = haloColor,
                // HomeScreenKt.java:7162 —— oa3Var.d && zBooleanValue
                pulseEnabled = item.live && animatorsEnabled,
                centerYFactor = centerYFactor,
                modifier = Modifier.matchParentSize(),
            )
        }

        Column(
            // HomeScreenKt.java:7167 —— hp1.o0(ij7.e(c05Var, 1.0f), 10.0f)
            //   ij7.e(m, 1.0f) = fillMaxSize(1f)（wk2(uy1.f, ...)，uy1.f = Both）
            //   hp1.o0(m, 10.0f) = ko5(10f, 10f, 10f, 10f) = padding(all = AppSpacing.md)（hp1.java:3935-3937）
            modifier = Modifier
                .fillMaxSize(1f)
                .padding(AppSpacing.md),
            // HomeScreenKt.java:7168 —— aa1.a(zh.d, il0.h0, ...)
            //   zh.d = new h10() → h10.c = 1 → toString "Arrangement#Top"（h10.java:6/48）
            //   il0.h0 = new vg0(-1.0f)（il0.java:63）= Alignment.Start
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            Text(
                text = item.label, // HomeScreenKt.java:7184 —— oa3Var.a
                color = textHigh(), // HomeScreenKt.java:7184 —— GlassTokensKt.textHigh(...)
                fontSize = 14.sp, // HomeScreenKt.java:7184 —— co7.j(14)（co7.java:1101，4294967296L = sp）
                fontWeight = FontWeight.W800, // HomeScreenKt.java:7184 —— sq2.X = new sq2(800)（sq2.java:28/34）
                letterSpacing = (-0.4).sp, // HomeScreenKt.java:7184 —— co7.i(-0.4d)
                lineHeight = 19.sp, // HomeScreenKt.java:7184 —— co7.j(19)
                overflow = TextOverflow.Ellipsis, // HomeScreenKt.java:7184 —— 实参 2（默认 1 = Clip）
                softWrap = false, // HomeScreenKt.java:7184 —— 实参 false
                maxLines = 1, // HomeScreenKt.java:7184 —— 实参 1
            )

            // HomeScreenKt.java:7185 —— wl7.d(oy2Var, ij7.i(c05Var, 2.0f))
            //   wl7.d = Spacer（wl7.java:12）；ij7.i(m, f) = hj7(0, f, 0, f, 5)（ij7.java:78-80）
            //   经 hj7.java:12-13 掩码后 = SizeElement(minHeight = f, maxHeight = f) = height(f.dp)
            Spacer(Modifier.height(2.dp))

            // HomeScreenKt.java:7186-7196 —— 副标题
            // 颜色：深色走 rt0.C(...) → GlassTokensKt.textMedium（rt0.java:29-33）；
            //       浅色走 g91.c(0.55f, GlassTokensKt.textHigh(...))（7193）
            val statusColor = if (dark) {
                textMedium() // HomeScreenKt.java:7190
            } else {
                textHigh().copy(alpha = 0.55f) // HomeScreenKt.java:7193
            }
            Text(
                text = item.status, // HomeScreenKt.java:7196 —— oa3Var.b
                color = statusColor,
                fontSize = 9.sp, // HomeScreenKt.java:7196 —— co7.j(9)
                lineHeight = 13.sp, // HomeScreenKt.java:7196 —— co7.j(13)
                overflow = TextOverflow.Ellipsis, // HomeScreenKt.java:7196 —— 实参 2
                softWrap = false, // HomeScreenKt.java:7196 —— 实参 false
                maxLines = 1, // HomeScreenKt.java:7196 —— 实参 1
            )

            // HomeScreenKt.java:7198 —— wl7.d(oy2Var, ij7.i(ij7.g(c05Var, 1.0f), z ? 30.0f : 48.0f))
            Spacer(
                Modifier
                    .fillMaxWidth(1f) // ij7.g(c05Var, 1.0f)
                    .height(if (isCompact) 30.dp else 48.dp),
            )
        }

        Box(
            // HomeScreenKt.java:7200-7201 —— f05VarB = oj0Var.b()（= matchParentSize）；
            //   jj0.d(xg0Var /* = il0.f = TopStart */, false)
            modifier = Modifier.matchParentSize(),
            contentAlignment = Alignment.TopStart,
        ) {
            // HomeScreenKt.java:7215-7219 ——
            //   if (r0 != 0) { i6 != 1 ? fd2.q() : y65.i0() } else { xe2.p() }
            // 三个 ImageVector 的构造常量（nk3 名称字符串）：
            //   xe2.p() = "Rounded.Favorite"（xe2.java:537-542）
            //   y65.i0() = "Rounded.BarChart"（y65.java:1641-1646）
            //   fd2.q() = "Rounded.PlayArrow"（fd2.java:1304-1309）
            // TODO(未确定): HomeScreenKt.java:7215 的 `r0` 在 JADX 输出里没有声明
            //   （同函数 12048 行另有 `?? r0;` 的推断失败记录），无法判定它是常量 true
            //   还是某个被 R8 折叠的分支条件；此处按 artwork 三分支还原（0/1/>=2 各有一个图标，
            //   与 `i6 != 1 ? q() : i0()` 的嵌套三元完全吻合）。
            val icon: ImageVector = when (item.artwork) {
                0 -> Icons.Rounded.Favorite // xe2.p()
                1 -> Icons.Rounded.BarChart // y65.i0()
                else -> Icons.Rounded.PlayArrow // fd2.q()
            }

            Icon(
                imageVector = icon,
                contentDescription = null, // HomeScreenKt.java:7321 —— si3.a(..., null, ...)
                modifier = Modifier
                    // HomeScreenKt.java:7229/7284 —— y65.q0(c05Var, pt2)
                    // y65.java:1827 + hi.java:65-72（case 2）+ jj7.java:86（uu4.e0 = layout(w,h,emptyMap,block)）：
                    //   量测：placeable = measurable.measure(constraints)
                    //   布局：layout(placeable.width, placeable.height, emptyMap) {
                    //           placeable.place(
                    //             (constraints.maxWidth  * 0.66f - placeable.width  / 2f).toInt(),
                    //             (constraints.maxHeight * 0.75f|0.73f - placeable.height / 2f).toInt()) }
                    //   qg1.h = maxWidth、qg1.g = maxHeight（qg1 与 yp3.java:18 的用法一致）
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(
                                x = (constraints.maxWidth * 0.66f - placeable.width / 2f).toInt(), // hi.java:71
                                y = (constraints.maxHeight * centerYFactor - placeable.height / 2f).toInt(), // hi.java:71
                            )
                        }
                    }
                    // HomeScreenKt.java:7230-7235 / 7285-7290 —— ij7.n(f05VarQ0, zAppIsDark ? 26.0f : 22.0f)
                    // ij7.n(m, f) = hj7(f, f, f, f, true)（ij7.java:98-100）= SizeElement(..., enforceIncoming = true)
                    //   = Modifier.requiredSize(f.dp)
                    .requiredSize(if (dark) 26.dp else 22.dp)
                    // HomeScreenKt.java:7236-7255 / 7291-7309 —— 浅色额外两层：
                    //   jv4.x(c05Var, fw2(20)) = Modifier.graphicsLayer { a0(1) }（fw2.java:140-145）
                    //     v23.a0(int) = CompositingStrategy，1 = Offscreen（v23.java:14）
                    //   er5.t(f05VarX, fw2(21)) = Modifier.drawWithCache { … }（er5.java:1836-1838）
                    //     fw2.java:146-152 在缓存块里建 Brush.linearGradient(colorStops)：
                    //       stop 0.00f → White.copy(alpha = 0.75f)
                    //       stop 0.55f → White.copy(alpha = 0.40f)
                    //       stop 1.00f → White.copy(alpha = 0.08f)
                    //       start = Offset.Zero（第 2 实参 0L），end = Offset(size.width, size.height)
                    //     绘制：z0.java:595-602（case 25）→ g72.X0(..., 0L, 0L, 0.0f, null, 5, 62)
                    //       = drawRect(brush, topLeft = Offset.Zero, size = 整个绘制区, alpha = 1f, style = Fill)
                    // 注：这里用等价的 `Modifier.drawBehind` 直接画同一个 Brush（GZ 的
                    //   drawWithCache 只是把该 Brush 缓存到 layout 阶段，几何/颜色/停点完全一致），
                    //   以避免依赖 `CacheDrawScope.onDrawBehind` 在不同 Compose 版本里
                    //   属于成员函数还是顶层扩展函数的差异。
                    .then(
                        if (dark) {
                            Modifier // HomeScreenKt.java:7237-7239 —— 深色直接 f05VarT = c05Var
                        } else {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen } // fw2.java:140-145
                                .drawBehind {
                                    val brush = Brush.linearGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.White.copy(alpha = 0.75f), // fw2.java:151
                                            0.55f to Color.White.copy(alpha = 0.40f), // fw2.java:151
                                            1.00f to Color.White.copy(alpha = 0.08f), // fw2.java:151
                                        ),
                                        start = Offset.Zero, // fw2.java:152 —— 第 2 实参 0L
                                        end = Offset(size.width, size.height), // fw2.java:152 —— Offset(w, h)
                                    )
                                    drawRect(
                                        brush = brush,
                                        topLeft = Offset.Zero, // z0.java:601 —— g72.X0(..., 0L, 0L, ...)
                                        alpha = 1f, // z0.java:601 —— 掩码 62 使 alpha 取默认 1.0f
                                    )
                                }
                        },
                    ),
                // HomeScreenKt.java:7257-7265 / 7312-7320 ——
                //   深色：jC2 = g91.c(0.85f, GlassTokensKt.textHigh(...))
                //   浅色：jC2 = g91.d = Color.White
                tint = if (dark) textHigh().copy(alpha = 0.85f) else Color.White,
            )
        }
    }
}

// ============================================================
// 入口光晕（GZ HomeEntryHalo-KTwxG1Y，7838-7897；绘制体在 xc3.java:28-47）
// ============================================================

/**
 * `m45HomeEntryHaloKTwxG1Y(long color, boolean pulse, Modifier, float centerYFactor, ...)`：
 * 用 `er5.c(modifier, block)`（er5.java:596-617 + 1832-1834 → `Modifier.drawBehind`）
 * 挂在一个 `Spacer` 上（`wl7.d`，7861 / wl7.java:12）。
 *
 * @param pulseEnabled 7863 —— `if (z)`：GZ 传的是 `oa3Var.d && ValueAnimator.areAnimatorsEnabled()`（7162）
 * @param centerYFactor 光晕中心 Y 比例：0.75f / 0.73f（7095）
 */
@Composable
private fun EntryHalo(
    color: Color,
    pulseEnabled: Boolean,
    centerYFactor: Float,
    modifier: Modifier = Modifier,
) {
    // HomeScreenKt.java:7863-7873 ——
    //   yf2.H("entryHalo", ...) = rememberInfiniteTransition(label = "entryHalo")
    //   yf2.g(..., 0.0f, 1.0f, qk5.Z(qk5.r0(4000, 2, bs4.a), it6.d, 0L, 4), "entryHaloPulse", ...)
    //     = animateFloat(initialValue = 0f, targetValue = 1f,
    //                    infiniteRepeatable(tween(4000, easing = bs4.a), RepeatMode.Reverse),
    //                    label = "entryHaloPulse")
    //   关闭动画时形体为 null（7872），绘制体退回 0.5f（xc3.java:33）
    val pulse: State<Float>? = if (pulseEnabled) {
        val transition = rememberInfiniteTransition(label = "entryHalo") // 7866
        transition.animateFloat(
            initialValue = 0.0f, // 7866
            targetValue = 1.0f, // 7866
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = 4000, // 7866 —— qk5.r0(4000, 2, bs4.a)
                    easing = MotionStandardEasing, // 7866 —— bs4.a
                ),
                repeatMode = RepeatMode.Reverse, // 7866 —— it6.d
            ),
            label = "entryHaloPulse", // 7866
        )
    } else {
        null
    }

    Spacer(
        modifier = modifier.drawBehind {
            // xc3.java:33 —— `ys7Var != null ? ys7Var.getValue().floatValue() : 0.5f`
            val p = pulse?.value ?: 0.5f

            // xc3.java:34 —— 中心 = (size.width * 0.66f, size.height * f2)
            val cx = size.width * 0.66f
            val cy = size.height * centerYFactor

            // xc3.java:35 + ij1.java:22 ——
            //   ij1.A(a, b, c, d) = (a * b + c) * d
            //   （fFloatValue * 0.02f + 0.99f）* min(size.width * 0.29f, size.height * 0.25f)
            val base = (p * 0.02f + 0.99f) * min(size.width * 0.29f, size.height * 0.25f)
            val gradientRadius = base * 1.75f // xc3.java:39

            // 1) xc3.java:40 —— g72.F0(radialGradient(colors, center, radius), radius, center)
            //    colors = [color.copy(0.85f), color.copy(0.425f), Color.Unspecified]（xc3.java:36-38）
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = 0.85f), // xc3.java:36 —— g91.c(0.85f, j)
                        color.copy(alpha = 0.425f), // xc3.java:37 —— g91.c(0.425f, j)
                        Color.Unspecified, // xc3.java:38 —— g91.g
                    ),
                    center = Offset(cx, cy),
                    radius = gradientRadius,
                ),
                radius = gradientRadius,
                center = Offset(cx, cy),
            )

            // 2) xc3.java:42 —— g72.r0(g91.c(0.3f, g91.d), 1.5f * base, center, 0.0f, Stroke(0.8.dp.toPx()), 104)
            //    g91.d = Color.White；g72Var.E0(0.8f) = density * 0.8f = 0.8.dp.toPx()（py3.java:30-32）；
            //    掩码 104 只保留 color/radius/center（style 为显式 Stroke）
            drawCircle(
                color = Color.White.copy(alpha = 0.3f), // xc3.java:42
                radius = 1.5f * base, // xc3.java:42
                center = Offset(cx, cy),
                style = Stroke(width = 0.8.dp.toPx()), // xc3.java:42 —— lw7(0.8f.toPx(), 0f, 0, 0, null, 30)
            )

            // 3) xc3.java:43 —— g72.r0(g91.c(0.16f, j), base, center, 0.0f, null, 120) → 实心圆
            drawCircle(
                color = color.copy(alpha = 0.16f), // xc3.java:43
                radius = base, // xc3.java:43
                center = Offset(cx, cy),
            )

            // 4) xc3.java:44-46 —— g72.B0(sweepGradient(...), cornerX = p * 3f + 10f, topLeft, size, Stroke(...))
            //    ex0.java:52-58 的 B0 = Canvas.drawRoundRect(left, top, right, bottom, rx, ry, paint)，
            //    ry 在字节码里是硬编码 260.0f（ex0.java:57）
            //    topLeft = center - Size(base, base)（ji5.java:33-37 = Offset 相减，xc3.java:44）
            //    colors = [Unspecified, White.copy(0.9f), Unspecified]，center = (cx, cy)（xc3.java:46）
            //    Stroke = lw7(base * 0.15f, 0f, 1 /* Round */, 0, null, 26)（xc3.java:46）
            drawRoundRect(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Unspecified, // xc3.java:46 —— g91(j2), j2 = g91.g
                        Color.White.copy(alpha = 0.9f), // xc3.java:46 —— g91.c(0.9f, g91.d)
                        Color.Unspecified, // xc3.java:46
                    ),
                    center = Offset(cx, cy),
                ),
                topLeft = Offset(cx - base, cy - base), // xc3.java:44
                size = Size(base * 2f, base * 2f), // xc3.java:45-46 —— f3 = base * 2.0f
                cornerRadius = CornerRadius(
                    x = p * 3f + 10f, // xc3.java:46 —— (fFloatValue * 3.0f) + 10.0f
                    y = 260f, // ex0.java:57 —— 硬编码 260.0f
                ),
                style = Stroke(
                    width = base * 0.15f, // xc3.java:46 —— lw7(base * 0.15f, ...)
                    cap = StrokeCap.Round, // xc3.java:46 —— lw7(..., 1, 0, null, 26) 的 1 = Round
                ),
            )
        },
    )
}
