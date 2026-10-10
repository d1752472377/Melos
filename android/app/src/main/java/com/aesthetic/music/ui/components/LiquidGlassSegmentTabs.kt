package com.aesthetic.music.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aesthetic.music.ui.theme.appIsDark

/**
 * 逐值还原自 GZ `LiquidGlassComponentsKt.java`：
 *  - [LiquidGlassIconContainer] ← `m331LiquidGlassIconContainerj5_y1gU`（:117-1568）
 *  - [LiquidGlassSegmentedTabs] ← `m332LiquidGlassSegmentedTabsegy_3UM`（:1569-2671）
 *
 * 解码对照（主控已验证）：
 *  - `hp1.s(m,color,shape)` = `Modifier.background`；`x8.g(m,w,color,shape)` = `Modifier.border`
 *  - `hq3.g(m,shape)` = `Modifier.clip`；`hp1.o0(m,d)` = `Modifier.padding(d)`；`hp1.q0(m,h,v,_)` = `Modifier.padding(h,v)`
 *  - `ij7.i(m,h)` = `Modifier.height`；`ij7.r(m,w)` = `Modifier.width`；`ij7.n(m,s)` = `Modifier.size(s)`
 *  - `ij7.g(m,f)` = `Modifier.fillMaxWidth(f)`；`zf8.b0(m,x,y,_)` = `Modifier.offset(x,y)`
 *  - `ez3(1.0f, true)` = `Modifier.weight(1f, fill = true)`（RowScope 的 LayoutWeightElement）
 *  - `hp1.h0(c1, t, c2)` = `lerp(c1, c2, t)`（fraction 被 clamp 到 0..1，`hp1.java:3287-3292`）
 *  - `g91` = `Color`：`.d`=White `.b`=Black `.g`=Unspecified `.c(a,c)`=`c.copy(alpha=a)`
 *  - `by6.a(f)` = `RoundedCornerShape(f.dp)`（`by6.java:13-16`，`s42` = CornerSize(Dp)）
 *  - `co7.j(11)` = `11.sp`（`co7.java:1101-1103` → `m(i, 4294967296L)`，4294967296 = TextUnitType.Sp）
 *  - `qk5.r0(ms, mask, easing)` = `tween(durationMillis=ms, easing=easing)`（`qk5.java:1654-1663`，mask 2 只吃 delay 默认 0）
 *  - `bs4.a` = `CubicBezierEasing(0.2f, 0f, 0f, 1f)`（`bs4.java:6`）
 *  - `bs4.e` = `CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)`（`bs4.java:20`）
 *  - `si`/`jj0.a(m, composer, changed)` 是**无内容**的布局（`jj0.java:12-48` 只设 measurePolicy，不 invoke content）
 *    → 指示器落地为 `Spacer`（画背景用的空节点），与 `Box` 视觉完全等价。
 */

// :1707 `ay6 by6.a(12.0f)` —— 分段栏外框圆角
private val SegmentBarShape = RoundedCornerShape(12.dp)

// :1708 `ay6 by6.a(9.0f)` —— 指示器（滑块）与外框内边距同族圆角
private val SegmentPillShape = RoundedCornerShape(9.dp)

// :1712 / :2567 `qk5.r0(300, 2, bs4.a)` —— 颜色与指示器位移共用 300ms 标准缓动
private val SegmentEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f)

// :2567 `qk5.r0(300, 2, bs4.e)` —— 指示器位移专用缓动（EmphasizedDecelerate）
private val SegmentOffsetEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

/**
 * GZ `LiquidGlassComponentsKt.java:117` 的签名（掩码还原后的默认值）：
 *
 * ```
 * LiquidGlassIconContainer(
 *     tone: Color,                                   // :117  long j
 *     modifier: Modifier = Modifier,                 // :222  (i2 & 2) → c05.a
 *     size: Dp = 32.dp,                              // :225  (i2 & 4) → 32.0f
 *     nonGlassSize: Dp = size,                       // :227-232 (i2 & 8) → f6 = f3（即 = size）
 *     shape: Shape = RoundedCornerShape(10.dp),      // :235  (i2 & 16) → by6.a(10.0f)
 *     nonGlassModifier: Modifier = Modifier,         // :240-248 (i2 & 32) → c05.a
 *     content: @Composable BoxScope.(Boolean) -> Unit // :313  qt2(BoxScope, Boolean, Composer, Int)
 * )
 * ```
 *
 * 尺寸取值分支（:266-271）：`usesLiquidGlassButtons` 为真时用 [size]，否则用 [nonGlassSize]
 * （调用方实例：`ProfileScreenKt.java:31833` 传 36.0f / 28.0f，掩码 34 → 第 2、第 6 参数走默认）。
 *
 * TODO(未确定): 第 4 参数在字节码里只是 float，语义由「非玻璃分支当尺寸用」反推，名字暂定 nonGlassSize；
 *               同理第 6 参数只在非玻璃分支被 append（:293），暂定 nonGlassModifier。
 */
@Composable
fun LiquidGlassIconContainer(
    tone: Color,
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    nonGlassSize: Dp = size,
    shape: Shape = RoundedCornerShape(10.dp),
    nonGlassModifier: Modifier = Modifier,
    content: @Composable BoxScope.(liquidGlass: Boolean) -> Unit,
) {
    // :263 `ThemeKt.appIsDark(composer, 0)`
    val dark = appIsDark()
    // :264-265 `oy2Var.k(LiquidGlassButtonKt.b)` + `usesLiquidGlassButtons(db0Var, composer, 0)`
    // Melos 侧：backdrop 由组合局部推导（见 LiquidGlassButton.kt:117），玻璃可用性沿用同一判定
    val backdrop = rememberLiquidGlassButtonBackdrop()
    val glass = usesLiquidGlassButtons(settingsAvailable = backdrop != null)

    // :266-270 `if (zUsesLiquidGlassButtons) f7 = f3 else f7 = f6;`
    val boxSize = if (glass) size else nonGlassSize

    Box(
        // :271 `ij7.n(f05Var3, f7)` = modifier.size(boxSize)
        modifier = modifier
            .size(boxSize)
            .then(
                if (glass) {
                    // :276 `m330liquidGlassButtonSurface(c05.a, db0, shape, null, false, g91.g, false, …, 44)`
                    //      掩码 44 = 4|8|32 → style 默认（pc4.c Standard）、enabled=true、末位=true、color=Unspecified
                    // :279-284 tint alpha = appIsDark ? 0.13f : 0.09f，然后 background(color, shape) → clip(shape)
                    Modifier
                        .liquidGlassButtonSurface(
                            backdrop = backdrop, // GZ 的 `db0`（组合局部）——本项目用 rememberLiquidGlassButtonBackdrop() 显式取
                            shape = shape,
                            style = LiquidGlassButtonStyle.Standard, // pc4.c
                            enabled = true, // :276 第 5 实参 false 但掩码 8 置位 → 取默认 true
                            color = Color.Unspecified, // :276 第 6 实参 `g91.g`
                            highlightEnabled = true, // :276 第 7 实参 false 但掩码 32 置位 → 取默认 true
                        )
                        .background(tone.copy(alpha = if (dark) 0.13f else 0.09f), shape) // :279-284
                        .clip(shape) // :284 `hq3.g(…, md7Var2)`
                } else {
                    // :293 `f05VarG = f05Var6;` —— 降级分支只 append 调用方给的第 6 参数
                    nonGlassModifier
                },
            ),
        // :296 `jj0.d(il0.o, false)`，il0.o = xg0(0.0f, 0.0f) = Alignment.Center（il0.java:55）
        contentAlignment = Alignment.Center,
    ) {
        // :313 `qt2Var.invoke(oj0.a /*BoxScope*/, Boolean.valueOf(zUsesLiquidGlassButtons), …)`
        content(glass)
    }
}

/**
 * GZ `LiquidGlassComponentsKt.java:1569` 的签名（掩码还原后的默认值）：
 *
 * ```
 * LiquidGlassSegmentedTabs(
 *     items: List<T>,                                    // :1569 List
 *     selectedIndex: Int,                                // :1569 int i
 *     indicatorProgress: Float? = null,                  // :1680 (i3 & 4) → f7 = null
 *     onSelect: (Int) -> Unit,                           // :1569 at2Var   （:2630 → le2.java:43 `at2.invoke(Integer)`)
 *     label: (T) -> String,                              // :1569 at2Var2  （:2652 `(String) at2Var3.invoke(next)`）
 *     tone: (T) -> Color,                                // :1569 at2Var3  （:1712 / :2624 取 Color）
 *     modifier: Modifier = Modifier,                     // :1686 (i5 & 64) → c05.a
 *     height: Dp = 28.dp,                                // :1691 (i5 & 128) → 28.0f
 * )
 * ```
 *
 * 三个 lambda 均为**必填**（`at2Var.getClass()` 之类非空检查见 :1607-1609），顺序如上：
 * :1753 把 m332 的 `at2Var3, at2Var, at2Var2` 依次塞进 lambda$2 的 `at2Var, at2Var2, at2Var3`，
 * 而 lambda$2 内 `at2Var` 出 Color、`at2Var2` 出 Unit(Int)、`at2Var3` 出 String。
 *
 * [indicatorProgress] 语义结论：
 *  - **非 null** ⇒ 指示器位置的「段索引浮点值」（0f..items.lastIndex 的索引域，不是 0..1 百分比）：
 *    :2567 `ka7.v(f, 0.0f, size-1) * itemWidth` —— 先 coerceIn 再乘段宽，得到横向偏移；
 *    这条分支**完全不走 animateFloatAsState**，即由外部（手势/ViewPager 进度）直接驱动、组件自己不加动画。
 *  - **null（默认）** ⇒ 用 :2567 的 `animateFloatAsState(itemWidth * selectedIndex, tween(300, es))`，
 *    即由 [selectedIndex] 驱动的 300ms 缓动。
 */
@Composable
fun <T> LiquidGlassSegmentedTabs(
    items: List<T>,
    selectedIndex: Int,
    indicatorProgress: Float? = null,
    onSelect: (Int) -> Unit,
    label: (T) -> String,
    tone: (T) -> Color,
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
) {
    // :1695-1701 `if (list.isEmpty()) { … return; }`
    if (items.isEmpty()) return

    // :1706 `ThemeKt.appIsDark(composer, 0)`
    val dark = appIsDark()
    // :1709-1710 `oy2Var3.k(LiquidGlassButtonKt.b)` + `usesLiquidGlassButtons(db0Var, oy2Var3, r7)`
    val backdrop = rememberLiquidGlassButtonBackdrop()
    val glass = usesLiquidGlassButtons(settingsAvailable = backdrop != null)

    // :1711 `ka7.x(i10, wi0.s(list))` = selectedIndex.coerceIn(0, list.lastIndex)（用于取色调/颜色动画）
    val toneIndex = selectedIndex.coerceIn(0, items.lastIndex)

    // :1712 `zi7.a(tone, qk5.r0(300, 2, bs4.a), "liquidSegmentTone", …)` = animateColorAsState(300ms, Standard)
    val animatedTone by animateColorAsState(
        targetValue = tone(items[toneIndex]),
        animationSpec = tween(durationMillis = 300, easing = SegmentEasing), // :1712
        label = "liquidSegmentTone", // :1712 字面量
    )

    BoxWithConstraints( // :1749 `jv4.d(…)`
        modifier = modifier
            .then(
                if (glass) {
                    // :1717 `m330liquidGlassButtonSurface(c05.a, db0, ay6VarA /*12.dp*/, pc4.d /*Container*/, false, g91.g, false, …, 40)`
                    //       掩码 40 = 8|32 → enabled/末位取默认 true；style = pc4.d = Container
                    Modifier
                        .liquidGlassButtonSurface(
                            backdrop = backdrop,
                            shape = SegmentBarShape,
                            style = LiquidGlassButtonStyle.Container, // pc4.d（pc4.java:17）
                            enabled = true, // :1717 掩码 8
                            color = Color.Unspecified, // :1717 第 6 实参 `g91.g`
                            highlightEnabled = true, // :1717 掩码 32
                        )
                        // :1720-1726 `g91.c(appIsDark ? 0.07f : 0.045f, 动画色调)` → background(color, 12.dp)
                        .background(
                            animatedTone.copy(alpha = if (dark) 0.07f else 0.045f),
                            SegmentBarShape,
                        )
                } else if (dark) {
                    // :1737 `hp1.s(Modifier, g91.c(0.06f, g91.d), ay6Var)` = background(White 6%)
                    Modifier.background(Color.White.copy(alpha = 0.06f), SegmentBarShape)
                } else {
                    // :1739 background(White 58%) + :1743 `x8.g(Modifier, 1.0f, g91.c(0.76f, g91.d), ay6Var)` = border(1.dp, White 76%)
                    Modifier
                        .background(Color.White.copy(alpha = 0.58f), SegmentBarShape)
                        .border(1.dp, Color.White.copy(alpha = 0.76f), SegmentBarShape)
                },
            )
            .clip(SegmentBarShape) // :1749 `hq3.g(…, ay6Var)`
            .padding(2.dp), // :1749 `hp1.o0(…, 2.0f)` = padding(2.dp) —— 指示器/标签相对外框内缩 2dp
    ) {
        // :2566 `((rj0) qj0Var).c() / list.size()` = BoxWithConstraintsScope.maxWidth / 段数
        val itemWidth = maxWidth / items.size

        // :2567 指示器横向偏移（Dp）
        //   非空 → `ka7.v(progress, 0.0f, size-1) * itemWidth`（progress 是**段索引浮点值**，不是 0..1 百分比）
        //   空   → `animateFloatAsState(itemWidth * selectedIndex, 300ms)`
        // 注意 itemWidth 是 Dp，所以这里对**段索引**做 Float 插值、再乘 itemWidth 得到 Dp 偏移。
        // 由于 itemWidth 在一次布局内是常量，`itemWidth * anim(index)` 与 GZ 直接动画像素偏移的轨迹逐帧等价。
        val animatedIndex by animateFloatAsState(
            targetValue = selectedIndex.toFloat(), // :2567 `fC * i`（注意用的是未 clamp 的 selectedIndex）
            animationSpec = tween(durationMillis = 300, easing = SegmentOffsetEasing), // :2567
            label = "liquidSegmentOffset", // :2567 字面量
        )
        val indicatorOffset: Dp = if (indicatorProgress != null) {
            itemWidth * indicatorProgress.coerceIn(0f, (items.size - 1).toFloat()) // :2567
        } else {
            itemWidth * animatedIndex
        }

        // :2569 `ij7.i(ij7.r(zf8.b0(c05.a, fV, 0.0f, 2), fC), f2)` = offset(x).width(itemWidth).height(height)
        val pillBackground = if (glass) {
            // :2576 `m330liquidGlassButtonSurface(c05.a, db0, ay6Var2 /*9.dp*/, pc4.f /*Primary*/, false, g91.g, false, …, 40)`
            //       + `g91.c(appIsDark ? 0.14f : 0.09f, 动画色调)`
            Modifier
                .liquidGlassButtonSurface(
                    backdrop = backdrop,
                    shape = SegmentPillShape,
                    style = LiquidGlassButtonStyle.Primary, // pc4.f（pc4.java:19）
                    enabled = true, // :2576 掩码 8
                    color = Color.Unspecified, // :2576 第 6 实参 `g91.g`
                    highlightEnabled = true, // :2576 掩码 32
                )
                .background(
                    animatedTone.copy(alpha = if (dark) 0.14f else 0.09f),
                    SegmentPillShape,
                )
        } else if (dark) {
            // :2586 `hp1.s(c05.a, g91.c(0.14f, g91.d), ay6Var2)` = background(White 14%)
            Modifier.background(Color.White.copy(alpha = 0.14f), SegmentPillShape)
        } else {
            // :2586 background(White 76%) + :2592 `x8.g(c05.a, 1.0f, g91.c(0.88f, g91.d), ay6Var2)` = border(1.dp, White 88%)
            Modifier
                .background(Color.White.copy(alpha = 0.76f), SegmentPillShape)
                .border(1.dp, Color.White.copy(alpha = 0.88f), SegmentPillShape)
        }

        // :2596 `jj0.a(hq3.g(f05Var.c(f05VarC), ay6Var2), …)`
        //   = 先 offset/width/height，再 then(背景)，最后 clip(9.dp)；无内容（`jj0.java:12-48` 没有 content invoke）
        Spacer(
            Modifier
                .offset(x = indicatorOffset, y = 0.dp) // :2569
                .width(itemWidth) // :2569
                .height(height) // :2569
                .then(pillBackground) // :2596
                .clip(SegmentPillShape), // :2596
        )

        // :2597 `ij7.g(c05.a, 1.0f)` = Modifier.fillMaxWidth()
        // :2598 `gy6.a(zh.b, il0.e0, …)`：zh.b = g10(3) = Arrangement.Start（g10.java:109）、il0.e0 = wg0(-1f) = Alignment.Top（il0.java:60）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.Top,
        ) {
            // :2614-2667 `ij1.s(composer, modifier, key, 369268385, list)` + while(hasNext) + index
            items.forEachIndexed { index, item ->
                // :2623 `z4 = i4 == i`（与未 clamp 的 selectedIndex 比较）
                val selected = index == selectedIndex

                // :2624 `at2Var.invoke(next)` = tone(item)
                val itemTone = tone(item)

                // :2625 `z2 ? hp1.h0(色调, 0.24f, g91.d) : 色调`（z2 = appIsDark）
                // :2660 未选中 → `yb2.c(…)` = `GlassTokensKt.textLow()`（yb2.java:27-32）
                val textColor = if (selected) {
                    if (dark) lerp(itemTone, Color.White, 0.24f) else itemTone // :2625
                } else {
                    textLow() // :2660
                }

                Box(
                    // :2626 `ij7.i(new ez3(1.0f, true), f2)` = weight(1f, fill = true).height(height)
                    // :2633 `MotionKt.c(…, 0.97f, …)` = bounceClick(pressedScale = 0.97f) { onSelect(index) }
                    modifier = Modifier
                        .weight(1f, fill = true) // :2626
                        .height(height) // :2626
                        .segmentTabPress(pressedScale = 0.97f) { onSelect(index) }, // :2633
                    // :2634 `jj0.d(il0.o, false)` = contentAlignment = Alignment.Center
                    contentAlignment = Alignment.Center,
                ) {
                    // :2652 label 文本；:2653 `co7.j(11)` = 11.sp；:2654 `sq2.X`=W800 / `sq2.j`=W500
                    // （sq2.java:31/34：j = new sq2(500)、X = new sq2(800)）；其余 TextStyle 参数全部取默认
                    Text(
                        text = label(item),
                        color = textColor,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.W800 else FontWeight.W500,
                    )
                }
            }
        }
    }
}

/**
 * GZ `MotionKt.bounceClick`（`MotionKt.java:74-99`）在分段栏项上的取值，
 * 源自 `LiquidGlassComponentsKt.java:2633`：`MotionKt.c(modifier, false, 0.97f, false, null, onClick, 13)`，
 * 掩码 13 = 1|4|8 → `enabled`、第 4 参数、第 5 参数（interactionSource）全部走默认：
 *  - `enabled = true`（`MotionKt.java:75-77`）
 *  - 按压缩放 = 0.97f（显式传入）
 *  - 第 4 参数默认 true（`MotionKt.java:81-83`），参与 `:62` 的 `pressed && enabled && z2` 判定
 *  - interactionSource 默认 null、`role` 默认 `dx6(0)` = `Role.Button`（`MotionKt.java:84-86`）
 *
 * 动画（`MotionKt.java:62`）：`animateFloatAsState(pressed ? 0.97f : 1.0f, spec = ct.a, label = "pressScale")`，
 * `ct.a`（`ct.java:8`）= `qk5.r0(150, 2, MotionKt.a)` = `tween(150, easing = bs4.a = CubicBezierEasing(0.2f, 0f, 0f, 1f))`。
 * `jv4.x(modifier, at2)` = `Modifier.graphicsLayer { scaleX = scaleY = 动画值 }`（`MotionKt.java:69`）；
 * `y65.U(…, null, …)` = `clickable(indication = null, …)`（无涟漪）。
 */
@Composable
private fun Modifier.segmentTabPress(pressedScale: Float, onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() } // MotionKt.java:59 `rt0.i`
    val pressed by interactionSource.collectIsPressedAsState() // MotionKt.java:62 `xc2.i`
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1.0f, // MotionKt.java:62
        animationSpec = tween( // ct.java:8 = qk5.r0(150, 2, MotionKt.a)
            durationMillis = 150,
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), // bs4.java:6（MotionKt.a = bs4.a）
        ),
        label = "pressScale", // MotionKt.java:62 字面量
    )
    return this
        .graphicsLayer { // MotionKt.java:69 `jv4.x`
            scaleX = scale
            scaleY = scale
        }
        .clickable( // MotionKt.java:69 `y65.U`
            interactionSource = interactionSource,
            indication = null,
            enabled = true, // MotionKt.java:76
            role = Role.Button, // MotionKt.java:85 `new dx6(0)`
            onClick = onClick,
        )
}
