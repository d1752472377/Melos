package com.aesthetic.music.ui

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.aesthetic.music.player.PlaybackProgress
import com.aesthetic.music.ui.components.RotatingDisc
import com.aesthetic.music.ui.components.glassBg
import com.aesthetic.music.ui.components.rememberCoverTone
import com.aesthetic.music.ui.components.textHigh
import com.aesthetic.music.ui.components.textLow
import com.aesthetic.music.ui.theme.Palette
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.max
import kotlin.math.min

// ============================================================================================
// GZ 真值常量 —— 全部取自 scratch/gz_extract/nav_spec.md（括号里是 spec 的行号）
// ============================================================================================

// ── 条体（spec 第 1 节）──
private val BarHeight = 60.dp             // spec:8   GZMusicAppKt.h = 60.0f
private val BarHorizontalPadding = 24.dp  // spec:9   GZMusicAppKt.i = 24.0f
private val BarBottomPadding = 14.dp      // spec:10  GZMusicAppKt.j = 14.0f
private val BarElevation = 20.dp          // spec:19  const/high16 0x41a0（clip = false）
private val BarInnerPadding = 6.dp        // spec:12  内层 tab 行左右 padding
// TODO(未确定): 条体描边线宽 GZ 未抠出（spec:18 只给了颜色）→ 取项目玻璃卡通用 1.dp
private val BarBorderWidth = 1.dp
// smali s:2836469 `Lby6;.c()` = `RoundedCornerShape(percent = 50)` ⇒ 60.dp 高时半径 30.dp（= 高度的一半）
private val BarShape = RoundedCornerShape(BarHeight / 2)

// ── 条体「透明玻璃」= 真实背景模糊（不是半透明纯色）──
// 依据：把 GZ 真机截图放大后可见，条体后面的专辑封面与文字被真实涂抹模糊，
// 而条体下方同样位置的文字（"KNK" / "Gunna"）是清晰的 ⇒ GZ 用的是背景模糊层。
// 这也解释了为什么它的填充在 smali 的 lambda 里找不到纯色常量。
private val BarBlurRadius = 24.dp // smali s:2837576 = 24.0f Dp（Haze 内部按 density 换算成 px）
private const val BarNoiseFactor = 0.02f // 与 Melos 既有 GlobalStatusBarGlass 的 0.02f 一致（非 GZ 数值）
/** smali s:2837586 `chromeFrost-ZtKyr3o` = GZ 用的就是 `Modifier.hazeEffect`；玻璃态 tint alpha */
private const val BarGlassAlphaDark = 0.72f  // smali：深色 0.72
private const val BarGlassAlphaLight = 0.75f // smali：浅色 0.75

// ── 单元格 PillTabCell（spec 第 2 节）──
private val TabIconSize = 23.dp             // spec:26  ij7.n(c05.a, 23.0f)
private const val TabPressedScale = 0.94f   // spec:28  MotionKt.c(mod,false,0.94f,…)
private const val TabSelectedScale = 1.12f  // spec:29  选中 1.12f
private const val TabUnselectedScale = 1.0f // spec:29  未选 1.0f

// ── 中间黑胶 / 进度环（spec 第 4 节）──
private val DiscContainerSize = 64.dp     // spec:47  s:2838679 ij7.n(mod,64.0f)
private val DiscRingSize = 48.dp          // spec:46  s:2838745 ij7.k(mod,48.0f)
private val DiscSize = 44.dp              // spec:45  s:2838798 ij7.k(c05.a,44.0f)
private val DiscElevation = 10.dp         // spec:49  Java:10440 / 10504
private val DiscCenterPlateSize = 10.dp   // spec:52  Java:10561
private val DiscSpindleSize = 3.dp        // spec:53  Java:10579
private const val DiscPressedScale = 0.92f // spec:54 s:2838169 const 0.92
// TODO(未确定): 第二色描边的线宽 GZ 未抠出（spec:51 只给了颜色公式）→ 取 1.dp
private val DiscOutlineWidth = 1.dp
// TODO(未确定): 进度环线宽 GZ 未抠出 → 沿用本组件既有值
private val DiscRingStroke = 2.5.dp
/** Java:10562 `hp1.e(4280032286L)` —— 内圈/中心盘底色 */
private val DiscCenterPlateColor = Color(0xFF1C1C1E)
/** spec:53 `g91.c(0.75f, g91.d)` —— 中心轴孔 White@75% */
private val DiscSpindleColor = Color.White.copy(alpha = 0.75f)

// ── 动画 spec（spec 第 5 节；ct.java:8-9 = qk5.r0(150,2,MotionKt.a) / qk5.r0(250,2,MotionKt.b)）──
/** `bs4.a`（bs4.java:6）= MotionKt.a = CubicBezierEasing(0.2,0,0,1)，与项目既有 GzEmphasizedEasing 同值 */
private val GzEmphasizedEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
/** `MotionKt.b`（MotionKt.java:47 → `bs4.b`，bs4.java:7）= CubicBezierEasing(0,0,0,1) */
private val GzMotionBEasing = CubicBezierEasing(0f, 0f, 0f, 1f)
// 注：spec:60 把 `qk5.r0(150, 2, …)` 的 mask「2」记作 delayMillis=2，这里按 spec 写 2
//     （`qk5.java:1654-1663` 里 mask=2 解码为 delayMillis=0，差 2ms，无视觉差异）。
private val GzMotionA: TweenSpec<Float> = tween(150, 2, GzEmphasizedEasing) // ct.a：按压 / 唱盘
private val GzMotionB: TweenSpec<Float> = tween(250, 2, GzMotionBEasing)    // ct.b：选中缩放
// spec:39 图标色过渡 = `qk5.r0(250, i13, bs4.a)`；该处 mask 在反编译里是运行时变量
// TODO(未确定): 该处 mask 无法确定 → delayMillis 取 0，只保留确定的 250ms + bs4.a
private val GzMotionTint: TweenSpec<Color> = tween(250, 0, GzEmphasizedEasing)

/**
 * 底部导航 Dock：首页 / 中央旋转唱片（带进度环） / 我的
 *
 * 按 GZ Music 真值（`scratch/gz_extract/nav_spec.md`）重写：
 * - 条体 60.dp 高、左右 24.dp + 下 14.dp 外边距、内层 6.dp、阴影 20.dp 且 **不 clip 子节点**
 *   （`background`/`border` 给形状，中间 64.dp 的唱片容器才能溢出 60.dp 的条体）；
 *   无 haze（spec:17）——「透明玻璃」= 半透明底色 + 描边。
 * - 单元格等宽 `weight(1f, fill = true)`、图标 23.dp、按压 0.94、选中 1.12（无额外指示器）。
 * - 中间黑胶：外层 64.dp / 环 48.dp / 唱片 44.dp，底色由封面主色 HSV 推导。
 *
 * 底部留白：GZ `getBottomOverlayHeight` = 60 + 14 + 窗口 inset + 28 = **102.dp + inset**（spec 第 7 节）。
 * Melos 的 `AppShell` 没有为 Dock 预留高度的硬编码（见 AppShell.kt:268 的注释），故本次未改。
 *
 * @param containerColor 保留参数（调用方 AppShell 仍在传）。GZ 条体填充不取自页面底色，
 *   而取玻璃 token，故本组件改由 `glassBg()` 决定填充；该参数当前不再参与绘制。
 * @param isDarkBackground 是否为深色/沉浸页面，决定图标与描边走深色分支
 */
@Composable
fun NavigationDock(
    currentTab: AppTab,
    currentArtwork: String?,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    onOpenPlayer: () -> Unit,
    isSpinning: Boolean = false,
    progressFlow: StateFlow<PlaybackProgress>? = null,
    modifier: Modifier = Modifier,
    isDarkBackground: Boolean = false,
    /**
     * 内容层的 Haze 数据源（`AppShell` 已用 `.hazeSource(state = …)` 注册过）。
     *
     * **为什么需要它**：把 GZ 真机截图放大后可以确认，GZ 的导航条**会把背后的内容真实模糊**
     * （条体后面的专辑封面与文字被涂抹，而条体下方同样位置的文字是清晰的）。
     * 所以 GZ 的"透明玻璃"不是半透明纯色，而是**背景模糊**——条体自己的填充 lambda 里
     * 没抠出纯色常量，正是因为它是模糊层。传 null 时退化为半透明填充（无模糊）。
     */
    hazeState: HazeState? = null,
) {
    // 深色底（沉浸页或深色主题）走深色玻璃；浅色底才用白色玻璃
    val onDark = isDarkBackground || Palette.isDark

    // smali s:2837586 `chromeFrost-ZtKyr3o` → `Modifier.hazeEffect`，tint = `barTint.copy(alpha)`，
    // 玻璃态 alpha = 0.72（深）/ 0.75（浅）（s:2837576 一带）。深色 tint 基色用 GZ 的深色玻璃底 token
    // 0xFF1C1C1E；浅色用白色（GZ 的浅色玻璃底就是 White）。
    val barBackground = if (onDark) {
        Color(0xFF1C1C1E).copy(alpha = BarGlassAlphaDark) // 0xFF1C1C1E = GZ 深色玻璃底（glassBg 的深色值）
    } else {
        Color.White.copy(alpha = BarGlassAlphaLight)
    }
    // 描边：GZ 实际是 1.dp **白色双色标 linearGradient**（smali s:2837620），但两个色标的 alpha 未抠出；
    // 这里暂用 `GlassChrome.borderColor` = White@55%（浅）/ White@30%（深）（spec:18）作纯色描边。
    // TODO(未确定): 换成 GZ 的双色标白色线性渐变（色标 alpha 未能从 smali 确定）。
    val barBorder = Color.White.copy(alpha = if (onDark) 0.30f else 0.55f)
    // 阴影：spec:20 ambient = Black@16%(浅)/24%(深)，spot = Black@26%(浅)/36%(深)
    val barAmbient = Color.Black.copy(alpha = if (onDark) 0.24f else 0.16f)
    val barSpot = Color.Black.copy(alpha = if (onDark) 0.36f else 0.26f)

    // TODO(未确定): GZ 条体外还有一层无参 WindowInsets padding（spec:14，成员名未抠出）；
    // 本项目现状未加 inset，这里保持一致，不猜。
    Box(
        modifier = modifier
            .fillMaxWidth()                              // spec:11 fillMaxWidth
            .padding(bottom = BarBottomPadding)          // spec:10 j = 14.dp
            .padding(horizontal = BarHorizontalPadding)  // spec:9  i = 24.dp
            .height(BarHeight)                           // spec:8  h = 60.dp
            // spec:19 elevation 20.dp 且 clip = false：不裁子节点，中间 64.dp 项靠溢出绘制
            .shadow(
                elevation = BarElevation,
                shape = BarShape,
                clip = false,
                ambientColor = barAmbient,
                spotColor = barSpot,
            ),
    ) {
        // ── 形状层：底色/背景模糊 + 描边 ──
        // 单独一层并裁到条体形状，这样「透明度/圆角」只作用在这一层上，
        // 而内容层的 64.dp 黑胶仍然可以溢出条体（与 GZ 的 clip = false 等价）。
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(BarShape)
                .then(
                    if (hazeState != null) {
                        // GZ 真机观感：真实背景模糊 + 半透明底色（不是纯色填充）
                        Modifier.hazeEffect(
                            state = hazeState,
                            style = HazeDefaults.style(
                                backgroundColor = barBackground,
                                blurRadius = BarBlurRadius,
                                noiseFactor = BarNoiseFactor,
                            ),
                        )
                    } else {
                        // 没有 Haze 数据源时退化为半透明填充（浅色下会偏淡，属已知退化）
                        Modifier.background(barBackground, BarShape)
                    }
                )
                .border(BarBorderWidth, barBorder, BarShape), // spec:18 White@55%(浅)/White@30%(深)
        )

        // ── 内容层：不裁切 ──
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = BarInnerPadding),      // spec:12 内层 tab 行 6.dp
            verticalAlignment = Alignment.CenterVertically,
        ) {
        DockIcon(
            modifier = Modifier.weight(1f, fill = true), // spec:25 等宽、间距 0
            icon = Icons.Filled.Home,
            label = "首页",
            selected = currentTab == AppTab.HOME,
            onDark = onDark,
            onClick = onHome,
        )

        CenterDisc(
            currentArtwork = currentArtwork,
            isSpinning = isSpinning,
            progressFlow = progressFlow,
            onOpenPlayer = onOpenPlayer,
            onDark = onDark,
        )

        DockIcon(
            modifier = Modifier.weight(1f, fill = true),
            icon = Icons.Filled.Person,
            label = "我的",
            selected = currentTab == AppTab.PROFILE,
            onDark = onDark,
            onClick = onProfile,
            )
        }
    }
}

/**
 * 中央唱片与环形进度条组件：独立订阅 progressFlow，防止 500ms 进度跳动引起父组件整行重组。
 * 不用 `size(64.dp)` 而用 `requiredSize`：条体 60.dp 会把它压到 60.dp，`requiredSize` 才保留
 * GZ 的 64.dp 真值并让它溢出（条体 clip = false）。
 */
@Composable
private fun CenterDisc(
    currentArtwork: String?,
    isSpinning: Boolean,
    progressFlow: StateFlow<PlaybackProgress>?,
    onOpenPlayer: () -> Unit,
    onDark: Boolean = false,
) {
    val progressState = progressFlow?.collectAsState()
    val progress = progressState?.value
    val fraction = if (progress != null && progress.durationMs > 0) {
        (progress.positionMs.toFloat() / progress.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // 判断是否显示进度条：播放中或暂停时都显示
    val shouldShowProgress = progress != null && progress.durationMs > 0

    // 封面主色（项目既有 GZ token `rememberCoverTone`）= GZ call site 里喂给唱片/环的 `o1(state)`
    // （s:2838210 取 HSV、s:2838742 作为进度环颜色实参）
    val tone = rememberCoverTone(currentArtwork)
    val coverHsv = remember(tone) {
        FloatArray(3).also { AndroidColor.colorToHSV(tone.toArgb(), it) }
    }
    // spec:50 唱片底色 = HSV(封面hue, min(封面sat×1.2, 0.85), max(封面value, 0.55))，纯实色
    val discColor = remember(coverHsv) {
        Color.hsv(coverHsv[0], min(coverHsv[1] * 1.2f, 0.85f), max(coverHsv[2], 0.55f))
    }
    // spec:51 第二色 = 同 hue，alpha = 1 − (1−value)×sat×0.7
    // TODO(未确定): spec 只写「第二色（描边/内层）」，GZ 里的确切用途未抠出 → 这里按唱片外圈描边落地
    val discEdgeColor = remember(coverHsv) {
        discColor.copy(alpha = (1f - (1f - coverHsv[2]) * coverHsv[1] * 0.7f).coerceIn(0f, 1f))
    }

    // 进度环轨道：`Java:11375-11383` White@14%（深）/ 主题色@12%（浅，映射为项目 textLow()）
    val ringTrack = if (onDark) Color.White.copy(alpha = 0.14f) else textLow().copy(alpha = 0.12f)
    // 进度环进度色：GZ 把封面主色作为颜色实参传给 VinylProgressRing（s:2838742-2838770）
    val ringProgress = tone

    // spec:54 按下 0.92（常态 1.0），label "discPressScale"，spec = ct.a
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) DiscPressedScale else 1f,
        animationSpec = GzMotionA,
        label = "discPressScale",
    )

    Box(
        Modifier
            .requiredSize(DiscContainerSize) // spec:47 外层容器 64.dp（允许溢出 60.dp 条体）
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics { contentDescription = "打开播放器" }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpenPlayer,
            ),
        contentAlignment = Alignment.Center,
    ) {
        // 进度环容器 48.dp（spec:46），无底色、无 sweep/radial 渐变（spec:55）
        Canvas(modifier = Modifier.size(DiscRingSize)) {
            val strokeWidth = DiscRingStroke.toPx()
            val halfStroke = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(halfStroke, halfStroke)

            if (shouldShowProgress) {
                drawArc(
                    color = ringTrack,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth)
                )
                if (fraction > 0f) {
                    drawArc(
                        color = ringProgress,
                        startAngle = -90f,
                        sweepAngle = 360f * fraction,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
        }

        // 黑胶唱片 44.dp（spec:45）
        Box(
            modifier = Modifier
                .size(DiscSize)
                // spec:49 elevation 10.dp + CircleShape + ambient 色@30% + spot 色@35% + clip = true
                .shadow(
                    elevation = DiscElevation,
                    shape = CircleShape,
                    clip = true,
                    ambientColor = discColor.copy(alpha = 0.30f),
                    spotColor = discColor.copy(alpha = 0.35f),
                )
                .clip(CircleShape)
                // GZ 的 44.dp 唱片底是 `R.drawable.disc_bg` 图（Java:10469），本项目没有该图，
                // 这里用上面 HSV 推导的底色打底，封面层（RotatingDisc）叠在其上
                .background(discColor),
        ) {
            RotatingDisc(
                url = currentArtwork,
                modifier = Modifier.fillMaxSize(),
                isPlaying = isSpinning,
                durationMs = 5000,
            )

            // 第二色描边：必须叠在封面之上，否则被铺满 44.dp 的封面图盖住
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = DiscOutlineWidth.toPx()
                drawCircle(
                    color = discEdgeColor,
                    radius = (size.minDimension - w) / 2f,
                    style = Stroke(width = w),
                )
            }

            // spec:52 内圈/中心盘 `0xFF1C1C1E` + clip(CircleShape)，10.dp
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(DiscCenterPlateSize)
                    .clip(CircleShape)
                    .background(DiscCenterPlateColor),
            )

            // spec:53 中心轴孔 White@75%，3.dp，CircleShape
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(DiscSpindleSize)
                    .clip(CircleShape)
                    .background(DiscSpindleColor),
            )
        }
    }
}

/**
 * GZ `PillTabCell`（spec 第 2/3 节）：等宽单元格 + 按压 0.94 + 选中放大 1.12 + 变色。
 *
 * 单元格 = `weight(1f, fill = true)`（调用方传）+ `fillMaxHeight(1f)`（Java:6879），
 * 按压回弹由 `MotionKt.c`（150ms / ct.a）实现。选中态**没有**额外背景或指示器。
 *
 * @param label 只作为无障碍描述：GZ 真机不渲染 tab 文字（spec 第 6 节），故不画文本。
 */
@Composable
private fun DockIcon(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onDark: Boolean,
    onClick: () -> Unit,
) {
    // spec:34-35 深色底 未选 White@55% / 选中 White@85%；spec:35 浅色底走主题
    // （GZ 字节码 Java:6758 浅色底只有一支主题色，这里按主控指定用 textLow()/textHigh() 区分选中态）
    val targetTint = if (onDark) {
        Color.White.copy(alpha = if (selected) 0.85f else 0.55f)
    } else {
        if (selected) textHigh() else textLow()
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    // spec:28 按压回弹 0.94（动画 ct.a）
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) TabPressedScale else 1f,
        animationSpec = GzMotionA,
        label = "pressScale",
    )
    // spec:29 选中 1.12 / 未选 1.0，label "pillTabScale"（动画 ct.b）
    val selectScale by animateFloatAsState(
        targetValue = if (selected) TabSelectedScale else TabUnselectedScale,
        animationSpec = GzMotionB,
        label = "pillTabScale",
    )
    // spec:39 颜色过渡 tween(250ms)，label "pillTabTint"
    val tint by animateColorAsState(
        targetValue = targetTint,
        animationSpec = GzMotionTint,
        label = "pillTabTint",
    )

    Box(
        modifier = modifier
            .fillMaxHeight() // Java:6879 `ij7.c(jy6Var.b(1.0f, c05.a, true), 1.0f)`
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label, // GZ: contentDescription = s17.b（不渲染文字）
            tint = tint,
            modifier = Modifier
                .size(TabIconSize) // spec:26 23.dp
                .graphicsLayer {
                    scaleX = selectScale
                    scaleY = selectScale
                },
        )
    }
}
