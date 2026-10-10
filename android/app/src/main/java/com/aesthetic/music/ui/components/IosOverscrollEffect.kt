@file:OptIn(ExperimentalFoundationApi::class)

package com.aesthetic.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/*
 * GZ Music「iOS 风格回弹过卷」逐值还原。
 *
 * 来源（JADX 反编译产物 + GZ_Music-2.1.8-release.apk 的 classes2.dex 反汇编交叉验证）：
 *   J1 = gz_decompiled/sources/com/gzmusic/app/ui/IOSOverscrollEffectKt.java
 *   J2 = gz_decompiled/sources/defpackage/li3.java            （= IOSOverScrollEffect 本体）
 *   J3 = gz_decompiled/sources/defpackage/ki3.java            （= 布局节点，设置滚动轴尺寸）
 *   J4 = gz_decompiled/sources/defpackage/s62.java            （case 20 = 布局放置块，唯一的视觉位移公式）
 *      s62 的 case 20 在 JADX 里可读；li3.a()（applyToFling）在 JADX 里被跳过，
 *      其 516 个 16-bit code unit 已从 classes2.dex 用 dexdump 反汇编逐条读回（下称 DEX）。
 *
 * 常数 → 来源（全部为字面量，无一处脑补）：
 *   过卷初值 0f                    J2:21   | Animatable 阈值 0.01f      J2:21
 *   滚动轴尺寸初值 1f              J2:22   | 回弹位移曲线 CubicBezierEasing(0.5f,0.5f,1.0f,0.25f)  J2:23
 *   回弹动画 spring(stiffness=200f) J2:24   （dampingRatio 用默认 1f；见 qk5.java:1401-1412 的
 *                                          `$default` 掩码 5 = 只传了 stiffness=200f）
 *   位移分母 = 尺寸 * 1.5f         J4:698（s62 case 20）
 *   剩余增量阈值 0.01f             J2:95   （DEX li3.d @0x009c 确认是字面量 0x3c23d70a）
 *   速度衰减估计 exponentialDecay 默认参数：frictionMultiplier=1f、absVelocityThreshold=0.1f
 *                                 → 有效摩擦 ExponentialDecayFriction = -4.2f
 *                                 （FloatDecayAnimationSpec.kt:88/107/112/115/116/146-158，
 *                                   即 li3.a() 中被 R8 内联的那段 log/exp 数学）
 *   来源判定字面量 1 / 2          J2:69、J2:95（Compose 1.7.4 中 = NestedScrollSource.UserInput / SideEffect）
 *
 * 降级点（唯一）：GZ 用的是 Compose 1.11.2（APK META-INF 实证），其 `Modifier.Node` 可直接作为
 * `effectModifier` 交给消费方按节点 delegate（J2:49-51 直接 `return this.g`，消费方 m27.java:132
 * 拿到后强转成 e05 再 delegate）。本项目是 Compose Foundation 1.7.4，而 1.7.4 的
 * `Modifier.Node : DelegatableNode`（ui Modifier.kt:172）**不是** `Modifier`，无法直接返回；
 * 因此这里用公开 API `ModifierNodeElement` 把同一个节点包成 `Modifier`。
 * 节点实例仍然与 GZ 一致——每个 effect 只有一个节点（GZ 的 m27 也用“已 attached 就不再 delegate”
 * 来规避重复挂载），所以 `effectModifier` 只应挂到一条 Modifier 链上。
 */

/**
 * [li3.a] 里被 R8 完全内联的 `exponentialDecay<Float>().calculateTargetValue(current, velocity)`。
 * 参数取 [exponentialDecay] 的默认值（1f / 0.1f），与 DEX 中内联后的
 * `max(1e-7f, abs(0.1f))` / `max(1e-4f, 1.0f) * -4.2f` 完全一致。
 */
private val iosOverScrollDecaySpec: DecayAnimationSpec<Float> = exponentialDecay()

/**
 * GZ Music 首页等列表使用的 iOS 风格回弹过卷（`IOSOverScrollEffect`）。
 *
 * @param scope 回弹动画的宿主协程作用域（`li3` 构造器的第一个参数，J2:16-19）。
 * @param orientation 过卷轴向（`fm5`，J2:16-19；Vertical=0 / Horizontal=1）。
 */
@OptIn(ExperimentalFoundationApi::class)
class IosOverscrollEffect(
    private val scope: CoroutineScope,
    val orientation: Orientation,
) : OverscrollEffect {

    /** 过卷位移量（px）。`jv4.a(0.0f, 0.01f)` → `Animatable(0f, 0.01f)`，J2:21。 */
    internal val overscroll = Animatable(0f, 0.01f)

    /**
     * 滚动轴方向上的容器尺寸（px），由测量阶段写入。初值 1f，J2:22。
     * 注意它**不是**最大过卷距离，而是位移的分母基数：`尺寸 * 1.5f`（J4:698）。[J3:28]
     */
    internal var containerSize: Float = 1f

    /** 过卷 → 位移的映射曲线，`CubicBezierEasing(0.5f, 0.5f, 1.0f, 0.25f)`，J2:23（[J4:698] 使用）。 */
    internal val releaseCurve = CubicBezierEasing(0.5f, 0.5f, 1.0f, 0.25f)

    /** 松手后的回位弹簧：dampingRatio=1.0f（默认）、stiffness=200.0f，J2:24。 */
    private val releaseSpec = spring<Float>(stiffness = 200f)

    /** 布局节点，J2:25。与 GZ 相同：每个 effect 只持有一个节点。（属性名不用 `node`，避免遮蔽 Modifier.Node 的同名成员） */
    private val layoutNode = OverscrollLayoutNode(this)

    /**
     * GZ 直接返回节点本身（J2:49-51）；1.7.4 的 `Modifier.Node` 不是 `Modifier`，
     * 故用 `ModifierNodeElement` 包装（见文件头「降级点」）。
     */
    override val effectModifier: Modifier = OverscrollNodeElement(layoutNode)

    /** `effectModifier` 的 fallback，仅用于 `remember` 之外的直接构造场景。 */
    override val isInProgress: Boolean
        get() = overscroll.value != 0f // J2:55

    /**
     * J2:59-99（DEX `li3.d` 512+ code unit 逐条核对）。
     *
     * [NestedScrollSource] 在本项目（Compose 1.7.4）里是 value class，GZ 直接比较的原始值
     * `2` = `SideEffect`（旧名 `Fling`）、`1` = `UserInput`（旧名 `Drag`）。
     */
    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val deltaAlongAxis = componentAlongAxis(delta)
        val current = overscroll.value // J2:64-65

        // J2:69 —— 只有在「已经过卷、来源是 UserInput、且这次增量与过卷方向相反」时才回落。
        val released = if (
            current == 0f ||
            source == NestedScrollSource.SideEffect ||
            deltaAlongAxis == 0f ||
            sign(deltaAlongAxis) == sign(current)
        ) {
            0f // J2:70
        } else {
            val target = clampAdd(deltaAlongAxis) // e()，J2:72
            // J2:73 —— `scope.launch { overscroll.snapTo(target) }`（ji3，无动画，瞬时写入）
            scope.launch { overscroll.snapTo(target) }
            target - current // J2:74
        }

        // J2:76-90 —— 把标量还原成只含轴向分量的 Offset。
        val releasedOffset =
            if (orientation == Orientation.Vertical) Offset(0f, released) else Offset(released, 0f)

        val leftForScroll = delta - releasedOffset // J2:91
        val consumed = performScroll(leftForScroll) // J2:92-93
        val leftForOverscroll = componentAlongAxis(leftForScroll - consumed) // J2:94

        // J2:95 —— 字面量阈值 0.01f（DEX li3.d @0x009c）。
        if (abs(leftForOverscroll) > 0.01f && source == NestedScrollSource.UserInput) {
            scope.launch { overscroll.snapTo(clampAdd(leftForOverscroll)) } // J2:96
        }

        return releasedOffset + consumed // J2:98
    }

    /**
     * J2:40-46（JADX 跳过，DEX `li3.a` 516 code unit 逐条读回）。
     *
     * 三条分支：
     *  1. 当前没过卷、或本次甩动在轴上没有速度 → 正常把 fling 交给子节点，再用剩余速度弹簧回 0；
     *  2. 已过卷 + 有轴速度，且衰减估计的落点与当前过卷**同号**（会继续往外拉）→ 整段速度被回弹吸收，
     *     用完整轴速度做初速度直接弹簧回 0，**不**调用 [performFling]（与 GZ 行为一致）；
     *  3. 异号（这次甩动会把张力放掉）→ 先异步弹簧回 0，再把 fling 交给子节点，剩余速度再弹簧回 0。
     */
    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val velocityAlongAxis = componentAlongAxis(velocity) // li3.g()，DEX @0x0076
        val current = overscroll.value // DEX @0x007c

        if (current == 0f || velocityAlongAxis == 0f) { // DEX @0x0086-0x008d
            val consumed = performFling(velocity) // DEX @0x00a5
            val left = componentAlongAxis(velocity - consumed) // DEX @0x00b2-0x00b9
            overscroll.animateTo(0f, releaseSpec, initialVelocity = left) // DEX @0x00d5
            return
        }

        // DEX @0x00dc-0x015b —— exponentialDecay 的 getTargetValue（默认参数）被内联在这里。
        val estimated = iosOverScrollDecaySpec.calculateTargetValue(current, velocityAlongAxis)

        if (sign(estimated) == sign(current)) { // DEX @0x0165
            overscroll.animateTo(0f, releaseSpec, initialVelocity = velocityAlongAxis) // DEX @0x0187
            return
        }

        // DEX @0x019e → j7 case 21：`scope.launch { overscroll.animateTo(0f, releaseSpec) }`
        // （initialVelocity 用默认值 = Animatable 当前速度，掩码 12 = 第 3、4 参数取默认）
        scope.launch { overscroll.animateTo(0f, releaseSpec) }

        val consumed = performFling(velocity) // DEX @0x01bc
        val left = componentAlongAxis(velocity - consumed) // DEX @0x01cd-0x01d4
        if (left != 0f) { // DEX @0x01d8
            overscroll.animateTo(0f, releaseSpec, initialVelocity = left) // DEX @0x01fc
        }
    }

    /**
     * 累加并夹在「不越过 0」的一侧，对应 `li3.e(float)`，J2:101-112（DEX `li3.e` 逐条核对）。
     */
    private fun clampAdd(delta: Float): Float {
        val current = overscroll.value
        val sum = delta + current
        if (current > 0f) {
            if (sum < 0f) return 0f
        } else if (current < 0f && sum > 0f) {
            return 0f
        }
        return sum
    }

    /** `li3.f(Offset)`：取 Offset 在轴向的分量（Vertical→y，Horizontal→x）。J2:114-124。 */
    private fun componentAlongAxis(offset: Offset): Float =
        if (orientation == Orientation.Vertical) offset.y else offset.x

    /** `li3.g(Velocity)`：取 Velocity 在轴向的分量（Vertical→y，Horizontal→x）。J2:126-136。 */
    private fun componentAlongAxis(velocity: Velocity): Float =
        if (orientation == Orientation.Vertical) velocity.y else velocity.x
}

/**
 * 测量 + 放置节点，对应 `ki3 extends e05 implements ey3`（Modifier.Node + LayoutModifierNode）。
 *
 * `ki3.k()` 只做两件事：[J3:13-30]
 *  1. 记录滚动轴尺寸（Vertical 取 `placeable.height`，Horizontal 取 `placeable.width`，J3:19-28）；
 *  2. `layout(placeable.width, placeable.height) { ... }` 后按 [J4:698]（s62 case 20）平移子节点。
 */
private class OverscrollLayoutNode(
    private val effect: IosOverscrollEffect,
) : Modifier.Node(), LayoutModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        effect.containerSize = if (effect.orientation == Orientation.Vertical) {
            placeable.height.toFloat() // J3:20
        } else {
            placeable.width.toFloat() // J3:26
        }

        return layout(placeable.width, placeable.height) { // J3:29
            // J4:698 —— 唯一的视觉位移公式：
            //   offsetPx = releaseCurve(过卷 / (尺寸 * 1.5f)) * 尺寸
            val offsetPx = effect.releaseCurve.transform(
                effect.overscroll.value / (effect.containerSize * 1.5f)
            ) * effect.containerSize
            // J4:701/707 —— ju4.E() = roundToInt（DEX 里先 int-to-long 再拆高低 32 位）
            val rounded = offsetPx.roundToInt()
            if (effect.orientation == Orientation.Vertical) {
                placeable.placeRelative(0, rounded) // J4:709 Vertical → y
            } else {
                placeable.placeRelative(rounded, 0) // J4:709 Horizontal → x
            }
        }
    }
}

/**
 * 把 [OverscrollLayoutNode] 暴露成 `Modifier` 的公开 API 包装（见文件头「降级点」）。
 * `create()` 返回同一个节点实例，与 GZ「每个 effect 一个节点」的语义一致。
 */
private class OverscrollNodeElement(
    private val node: OverscrollLayoutNode,
) : ModifierNodeElement<OverscrollLayoutNode>() {

    override fun create(): OverscrollLayoutNode = node

    override fun update(node: OverscrollLayoutNode) {}

    override fun equals(other: Any?): Boolean =
        other is OverscrollNodeElement && other.node === node

    override fun hashCode(): Int = System.identityHashCode(node)
}

/**
 * GZ 的 `rememberIOSOverScrollEffect`（IOSOverscrollEffectKt.java:16-38）。
 *
 * 默认参数与 GZ 逐项对齐：`orientation = Orientation.Vertical`（J1:17-19，`fm5.c` = Vertical）、
 * `scope = rememberCoroutineScope()`（J1:20-30，`zf8.B(composer)` = `new RememberedCoroutineScope(composer.applyCoroutineContext)`）。
 * key 为 `(orientation, scope)`（J1:32-37）。
 */
@ExperimentalFoundationApi
@Composable
fun rememberIOSOverScrollEffect(
    orientation: Orientation = Orientation.Vertical,
    scope: CoroutineScope = rememberCoroutineScope(),
): OverscrollEffect = remember(orientation, scope) {
    IosOverscrollEffect(scope, orientation)
}
