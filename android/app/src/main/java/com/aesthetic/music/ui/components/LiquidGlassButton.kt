package com.aesthetic.music.ui.components

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer

/**
 * GZ Music `LiquidGlassButtonKt` 的逐值还原（唯一源：`gz_decompiled/sources/com/gzmusic/app/ui/LiquidGlassButtonKt.java`）。
 *
 * 反编译辅助门面解码（主控已核对）：
 *  - `ys2` = `Function0`（本文件里既用于 `()->Unit`，也用于 `()->Shape`、`()->Float`）
 *  - `pt2` = `@Composable ()->Unit`（内容槽）
 *  - `db0` = 「液态玻璃背景回放器」接口，实现只有 `px3`（真录制）与 `ga1`（降级）
 *  - `g91.d` = White、`g91.b` = Black、`g91.c(a, c)` = `c.copy(alpha = a)`
 *  - `hp1.s(m, color, shape)` = `Modifier.background(color, shape)`（LiquidGlassButtonKt.java:855 即此用法）
 *  - `hq3.g(m, shape)`    = `Modifier.clip(shape)`（LiquidGlassButtonKt.java:855）
 *  - `jv4.x(m, block)`    = `Modifier.graphicsLayer(block)`（jv4.java:745 = `m.then(nh0(block))`）
 *  - `pc4` 见下 [LiquidGlassButtonStyle]
 *
 * 本项目不使用 GZ 的设置/数据模型，凡涉及本项目没有的数据源都改成**显式参数**并注明 GZ 原来的取值来源。
 */
// LiquidGlassButtonKt.java:101 参数表（含默认值与掩码位）
// LiquidGlassButtonKt.java:234-258 默认值分支；LiquidGlassButtonKt.java:848-853 surface 默认值

// ============================================================
// 风格档位：pc4（LiquidGlassButtonKt.java 内唯一使用点 848-861）
// ============================================================

/**
 * `defpackage.pc4` 的 4 个取值，顺序 = ordinal 顺序（pc4.java:18-31）。
 *
 * 重要事实（避免照抄别处的"档位 → 尺寸/字号"表）：**GZ 的 pc4 不携带尺寸、内边距或字号**，
 * 它只影响 3 件事，全部集中在 `liquidGlassButtonSurface`：
 *  1) 底色 tint：Navigation 强制黑（LiquidGlassButtonKt.java:876）
 *  2) 底色透明度：Navigation 0.16 / Primary 0.24 / Container 0.14 / Standard 0.1（:877-885）
 *  3) 折射层几何：Primary|Container 用 5.0f 与 4.0f，Standard|Navigation 用 4.0f 与 3.0f（:887-893）
 * 按钮尺寸一律由调用方给出，例如 GZ 调用点 `ij7.n(c05Var, 42.0f)` = `Modifier.size(42.dp)`
 * （PlayerScreenKt.java:33453）、`ij7.n(c05Var2, 34.0f)` = `size(34.dp)`（LyricsViewKt.java:4542）。
 * 字号/图标/内边距全部来自调用方传入的 `content`，GZ 本体不含任何 `sp`/`padding` 字面量。
 */
enum class LiquidGlassButtonStyle {
    /** `pc4.c`（pc4.java:12-13，ordinal 0） */
    Standard,

    /** `pc4.d`（pc4.java:14-15，ordinal 1） */
    Container,

    /** `pc4.f`（pc4.java:16-17，ordinal 2） */
    Primary,

    /** `pc4.g`（pc4.java:18-19，ordinal 3） */
    Navigation,
}

// ============================================================
// 背景回放器：GZ 的 dB 抽象 db0
// ============================================================

/**
 * `defpackage.db0`（db0.java:5-8）：
 * ```java
 * boolean a();                                     // 是否可用
 * void b(g72 canvas, mu1 size, vx3 node, at2 draw)  // 回放录制的背景内容
 * ```
 * GZ 的唯一真实实现是 `px3`（px3.java:7-45）：它持有一个录制画布缓存 `r23` 与懒值 `jr5`，
 * `b(...)` 里把录制内容按当前布局大小重新平移绘制（px3.java:23-45），
 * 由 `ws4.B(composer)` 创建（LiquidGlassButtonKt.java:929）。
 *
 * 本文件无法移植该录制管线（依赖 Compose 内部 `r23/zs/m9` 等私有类型），因此：
 *  - 这里保留接口作为**接入点**；
 *  - `drawBackdrop` 的签名从 GZ 的 4 参简化为 1 参（只留绘制作用域），
 *    GZ 的 `mu1 size`/`vx3 node` 是私有录制管线参数，公共 API 无法表达 —— 见 TODO(未确定)。
 *  - 实现由调用方（或未来的主题层）提供；为 `null` 时 `usesLiquidGlassButtons()` 返回 false，
 *    自动走 LiquidGlassButtonKt.java:855 的降级路径。
 */
interface LiquidGlassBackdrop {
    /** 对应 `db0.a()`（px3.java:19-21 恒为 true）。 */
    fun isAvailable(): Boolean

    /** 对应 `db0.b(...)` 的回放绘制部分。GZ 原签名见类注释 TODO(未确定)。 */
    fun drawBackdrop(drawScope: DrawScope)
}

// ============================================================
// 1) rememberLiquidGlassButtonBackdrop —— LiquidGlassButtonKt.java:905-932
// ============================================================

/**
 * LiquidGlassButtonKt.java:905-932 逐行对应：
 * ```
 * 907-909  enabled(=z) 默认 true
 * 910-914  true  分支：读 staticCompositionLocal（字段 a，本项目 = LocalLiquidGlassButtonsEnabled）
 * 915-920  false 分支：直接用 false
 * 921-925  !flag || SDK_INT < 33 → 返回 null
 * 927-931  否则 return ws4.B(composer)   // 创建 px3 回放器
 * ```
 */
@Composable
fun rememberLiquidGlassButtonBackdrop(enabled: Boolean = true): LiquidGlassBackdrop? {
    // :910-919
    val flag = if (enabled) LocalLiquidGlassButtonsEnabled.current else false
    // :921-925
    if (!flag || Build.VERSION.SDK_INT < 33) return null
    // TODO(未确定): :929 `ws4.B(composer)` 返回 `px3` —— 需要把当前界面内容录制进 RenderNode/画布缓存
    //   （px3.java:12-14 持有 r23 + jr5，px3.java:23-45 做平移回放）。本项目没有该录制管线，
    //   编造一个"假背景"会破坏"逐值还原"，故此处返回 null（= 走降级外观）。
    //   接入方式：实现上面的 LiquidGlassBackdrop 并通过 LiquidGlassButton(backdrop = ...) 显式传入。
    return null
}

// ============================================================
// 2) Modifier.liquidGlassButtonSurface —— LiquidGlassButtonKt.java:843-903
// ============================================================

/**
 * LiquidGlassButtonKt.java:842-903（`liquidGlassButtonSurface-cd68TDI`）逐值还原。
 *
 * 参数顺序与默认值 = GZ 原样：
 * | 本函数参数        | GZ 参数              | 默认值来源                        |
 * |------------------|----------------------|----------------------------------|
 * | backdrop         | `db0 db0Var`（第2个） | GZ 无默认值（掩码位 1 从未出现），可空由 :935 的 `db0Var != null` 决定 |
 * | shape            | `md7`                | :847 `by6.a`                     |
 * | style            | `pc4`                | :848 `pc4.c` = Standard          |
 * | enabled          | `boolean z`          | :850 `true`                      |
 * | color            | `long j`             | :852 `g91.c(0.1f, g91.d)` = White 10% |
 * | highlightEnabled | `boolean z2`         | :853 `true`                      |
 *
 * 两条路径（LiquidGlassButtonKt.java:854 的开关就是 `usesLiquidGlassButtons`）：
 *  - **降级路径**（:855）：`background(color, shape)` → `clip(shape)`，注意用的是**原始 color 参数**，
 *    不是玻璃路径算出来的 tint，且**没有描边/阴影**。
 *  - **玻璃路径**（:876-900）：tint = Black/White、alpha 按 style 取值、再交给 `ig2.g`
 *    （ig2.java:884-895）合成折射+高光层。
 */
@Composable
fun Modifier.liquidGlassButtonSurface(
    backdrop: LiquidGlassBackdrop? = null,
    shape: Shape = RoundedCornerShape(percent = 50), // by6.a：by6.java:9-11 = 4 角 CornerSize(50%)，jt5.java:6-14
    style: LiquidGlassButtonStyle = LiquidGlassButtonStyle.Standard, // pc4.c
    enabled: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.1f), // :250 同款默认值（:852）
    highlightEnabled: Boolean = true, // :853，最终变成 ig2.java:888-893 里高光的 alpha 1.0/0.0
): Modifier {
    // :854 `usesLiquidGlassButtons(db0Var, composer, changed)` = LocalX && db0 != null && SDK_INT >= 33
    if (!usesLiquidGlassButtons(settingsAvailable = backdrop != null)) {
        // :855 `hq3.g(hp1.s(f05Var, jC, md7Var2), md7Var2)` —— 顺序：background 然后 clip
        return this.background(color, shape).clip(shape)
    }

    // :859-861
    val isPrimary = style == LiquidGlassButtonStyle.Primary
    val isContainer = style == LiquidGlassButtonStyle.Container
    val isNavigation = style == LiquidGlassButtonStyle.Navigation

    // :876 `long j2 = (!z4 || z8) ? g91.b : g91.d;` —— 禁用或 Navigation → Black，否则 White
    val tint = if (!enabled || isNavigation) Color.Black else Color.White

    // :877-886 透明度（字面量逐一对应）
    val tintAlpha = when {
        !enabled -> 0.08f     // :878
        isNavigation -> 0.16f // :880
        isPrimary -> 0.24f    // :882
        isContainer -> 0.14f  // :884
        else -> 0.1f          // :851/:886 初值 0.1f（Standard）
    }

    // :887-888 `f4 = (z6 || z7) ? 5.0f : 4.0f` —— 折射层宽度系数（Primary|Container → 5.0f）
    // :889-893 `f3/f5`: 禁用 → 2.0f；否则 Primary|Container → 4.0f；其余(Standard|Navigation) → 3.0f
    // :900 传给 ig2.g 的常量：第 6 实参 = 6.0f
    @Suppress("UNUSED_VARIABLE")
    val refractionWidthFactor = if (isPrimary || isContainer) 5.0f else 4.0f
    @Suppress("UNUSED_VARIABLE")
    val refractionHeightFactor = when {
        !enabled -> 2.0f
        isPrimary || isContainer -> 4.0f
        else -> 3.0f
    }
    @Suppress("UNUSED_VARIABLE")
    val refractionStrength = 6.0f

    // TODO(未确定): GZ 的玻璃层由 `ig2.g` → `y65.d0` → 节点 `v62(db0, qd7, rw2, wc0(7,…), y65.b, c02)`
    //   绘制，本文件只落地**外观层**（tint 填充 + 背景回放钩子），其余数值原样登记如下，单位不是 dp，
    //   无法映射到 Compose 公共 API：
    //   - rw2.java:32-42（`new rw2(f4, f5, 6.0f, 1)`，ig2.java:888）：
    //       ig2.C(scope, size * 5.0f|4.0f, size * 2.0f|3.0f|4.0f)   // 折射几何（尺寸倍数）
    //       l91.a(scope)                                             // 未确定
    //       o40.n(scope, size * 6.0f)                                // 未确定
    //       bd2.M(scope, size * 5.0f|4.0f, size * 2.0f|3.0f|4.0f, 12) // 未确定
    //   - 高光 w83：w83.java:15-17 默认 `Highlight(width = 0.5f, blurRadius = 0.25f, alpha = 1.0f,
    //       style = a93.b)`，ig2.java:888-893 把 alpha 改成 `enabled ? 1.0f : 0.0f`。
    //   - 图层扩张 wc0.java:64-71：scale = ((density * 8.0f) / size.width) * 系数 + 1.0f，
    //       系数来自 `vj3(27)`（LiquidGlassButtonKt.java:895-899），vj3.java:89-91 恒返回 0.0f → scale ≡ 1.0f。
    //       故这里写成恒等 graphicsLayer，与 GZ 完全一致（不是省略）。
    //   - tint 填充：c02.java:34-38 = `drawRect(color.copy(alpha))`（K0 的 8 参默认形式），
    //       等价于 `Modifier.background(tint.copy(alpha = tintAlpha), shape)`；绘制顺序上 tint 在
    //       背景回放之后、折射/高光之前（v62 内 `c().e(...)` 先回放，再画 tint）。
    // `highlightEnabled`（:853）当前没有外观层消费者：它唯一的去向是 ig2.java:888-893 的高光 alpha
    // （`enabled ? 1.0f : 0.0f`）。保留在签名里，接入真折射层时必须传进去。

    return this
        // ig2.java:894 → wc0.java:64-71 + vj3.java:89-91 ⇒ scaleX = scaleY = 1.0f
        .graphicsLayer {
            scaleX = 1f
            scaleY = 1f
        }
        // 接入点：GZ `px3.b(...)`（px3.java:23-45）把录制内容平移回放到这里
        .drawBehind { backdrop?.drawBackdrop(this) }
        // c02.java:34-38（ig2.java:894 的最后一个实参）—— tint 填充
        .background(tint.copy(alpha = tintAlpha), shape)
        // GZ 用 shape lambda（LiquidGlassButtonKt.java:869-874 `l33(md7, 8)`，l33.java:case 8 原样返回 shape）
        // 作为内部绘制的裁切形状；外观层用 clip(shape) 表达同一裁切语义
        .clip(shape)
}

// ============================================================
// 3) LiquidGlassButton —— LiquidGlassButtonKt.java:101-840
// ============================================================

/**
 * `LiquidGlassButton(onClick, backdrop, modifier, shape, style, enabled, color, pressedScale, content)`。
 *
 * 参数顺序 = GZ 原样（LiquidGlassButtonKt.java:101）。默认值与语义：
 * - `onClick`        : 必需（掩码位 1 从未出现）
 * - `backdrop`       : GZ 里也**没有默认值**，由调用方从 CompositionLocal 取
 *                      （PlayerScreenKt.java:474 `LocalPlayerButtonBackdrop`，其默认值
 *                      PlayerScreenKt.java:40835-40837 **返回 null**）。本项目无此 Local，
 *                      故降级为显式参数且默认 `null` → 直接走降级外观。
 * - `modifier`       : :235 `c05.a` = `Modifier`
 * - `shape`          : :239 `by6.a` = `RoundedCornerShape(percent = 50)`（by6.java:9-11 / jt5.java:6-14）
 * - `style`          : :242 `pc4.c` = Standard
 * - `enabled`        : :247 `true`
 * - `color`          : :250 `g91.c(0.1f, g91.d)` = `White.copy(alpha = 0.1f)`
 * - `pressedScale`   : :255 `0.96f`（GZ 内部调用点都显式传 0.88f，见报告）
 * - `content`        : 必需（掩码位 256 从未出现）
 *
 * 结构（:277-297）：
 * ```
 * Box(                      // :279 jj0.d(il0.o, false)：il0.o = BiasAlignment(0,0) = Alignment.Center
 *     modifier = modifier
 *         .liquidGlassButtonSurface(...)                       // :277 先表面
 *         .graphicsLayer { scaleX = scaleY = 按压动画值 }        // :277 MotionKt.c → jv4.x
 *         .clickable(interactionSource, indication = null, ...) // MotionKt.java:69 y65.U(..., null, ...)
 * ) { content() }
 * ```
 * 注意修饰符顺序：玻璃表面在**外**，按压缩放层在**内**（GZ 原样，:277 把 surface 的返回值传进 MotionKt.c），
 * 即按压时只有 content 被缩放，玻璃底色/高光不缩放。
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    backdrop: LiquidGlassBackdrop? = null, // GZ 无默认值；本项目显式传入，null 即降级
    modifier: Modifier = Modifier, // :235
    shape: Shape = RoundedCornerShape(percent = 50), // :239 by6.a
    style: LiquidGlassButtonStyle = LiquidGlassButtonStyle.Standard, // :242 pc4.c
    enabled: Boolean = true, // :247
    color: Color = Color.White.copy(alpha = 0.1f), // :250
    pressedScale: Float = 0.96f, // :255
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() } // MotionKt.java:59 `rt0.i`
    val isPressed by interactionSource.collectIsPressedAsState() // MotionKt.java:62 `xc2.i`
    val scale by animateFloatAsState(
        // MotionKt.java:62：`(pressed && enabled && z2) ? scale : 1.0f`，z2 被默认掩码置 true（MotionKt.java:81-83）
        targetValue = if (isPressed && enabled) pressedScale else 1.0f,
        // MotionKt.java:62 的动画 spec = `ct.a`（ct.java:8）= tween(150, easing = bs4.a)
        // bs4.java:7 `bs4.a` = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
        animationSpec = tween(
            durationMillis = 150, // ct.java:8
            easing = CubicBezierEasing(0.2f, 0f, 0f, 1f), // bs4.java:7
        ),
        label = "pressScale", // MotionKt.java:62 的字面量
    )

    Box(
        modifier = modifier
            // :277（:435/:633/:791 为同一份内联拷贝）—— 表面在前
            .liquidGlassButtonSurface(
                backdrop = backdrop,
                shape = shape,
                style = style,
                enabled = enabled,
                color = color,
                // :277 第 7 实参传 false 但掩码 32 置位 → 实际取默认 true
                highlightEnabled = true,
            )
            // MotionKt.java:69 `jv4.x(f05Var, at2)` → sv1.java:65-71：scaleX = scaleY = 动画值
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            // MotionKt.java:69 `y65.U(..., null, z, interactionSource, onClick, 8)`：
            // indication 传 null（无涟漪），enabled 用 GZ 的 z
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick,
            ),
        // :279 `jj0.d(il0.o, false)` = BoxMeasurePolicy(Alignment.Center, propagateMinConstraints = false)
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
