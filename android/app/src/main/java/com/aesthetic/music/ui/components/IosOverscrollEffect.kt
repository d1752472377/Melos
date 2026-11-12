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

private val iosOverScrollDecaySpec: DecayAnimationSpec<Float> = exponentialDecay()

@OptIn(ExperimentalFoundationApi::class)
class IosOverscrollEffect(
    private val scope: CoroutineScope,
    val orientation: Orientation,
) : OverscrollEffect {

    internal val overscroll = Animatable(0f, 0.01f)

    internal var containerSize: Float = 1f

    internal val releaseCurve = CubicBezierEasing(0.5f, 0.5f, 1.0f, 0.25f)

    private val releaseSpec = spring<Float>(stiffness = 200f)

    private val layoutNode = OverscrollLayoutNode(this)

    @Suppress("OVERRIDE_DEPRECATION")
    override val effectModifier: Modifier = OverscrollNodeElement(layoutNode)

    override val isInProgress: Boolean
        get() = overscroll.value != 0f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        val deltaAlongAxis = componentAlongAxis(delta)
        val current = overscroll.value

        val released = if (
            current == 0f ||
            source == NestedScrollSource.SideEffect ||
            deltaAlongAxis == 0f ||
            sign(deltaAlongAxis) == sign(current)
        ) {
            0f
        } else {
            val target = clampAdd(deltaAlongAxis)
            scope.launch { overscroll.snapTo(target) }
            target - current
        }

        val releasedOffset =
            if (orientation == Orientation.Vertical) Offset(0f, released) else Offset(released, 0f)

        val leftForScroll = delta - releasedOffset
        val consumed = performScroll(leftForScroll)
        val leftForOverscroll = componentAlongAxis(leftForScroll - consumed)

        if (abs(leftForOverscroll) > 0.01f && source == NestedScrollSource.UserInput) {
            scope.launch { overscroll.snapTo(clampAdd(leftForOverscroll)) }
        }

        return releasedOffset + consumed
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val velocityAlongAxis = componentAlongAxis(velocity)
        val current = overscroll.value

        if (current == 0f || velocityAlongAxis == 0f) {
            val consumed = performFling(velocity)
            val left = componentAlongAxis(velocity - consumed)
            overscroll.animateTo(0f, releaseSpec, initialVelocity = left)
            return
        }

        val estimated = iosOverScrollDecaySpec.calculateTargetValue(current, velocityAlongAxis)

        if (sign(estimated) == sign(current)) {
            overscroll.animateTo(0f, releaseSpec, initialVelocity = velocityAlongAxis)
            return
        }

        scope.launch { overscroll.animateTo(0f, releaseSpec) }

        val consumed = performFling(velocity)
        val left = componentAlongAxis(velocity - consumed)
        if (left != 0f) {
            overscroll.animateTo(0f, releaseSpec, initialVelocity = left)
        }
    }

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

    private fun componentAlongAxis(offset: Offset): Float =
        if (orientation == Orientation.Vertical) offset.y else offset.x

    private fun componentAlongAxis(velocity: Velocity): Float =
        if (orientation == Orientation.Vertical) velocity.y else velocity.x
}

private class OverscrollLayoutNode(
    private val effect: IosOverscrollEffect,
) : Modifier.Node(), LayoutModifierNode {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)
        effect.containerSize = if (effect.orientation == Orientation.Vertical) {
            placeable.height.toFloat()
        } else {
            placeable.width.toFloat()
        }

        return layout(placeable.width, placeable.height) {
            val offsetPx = effect.releaseCurve.transform(
                effect.overscroll.value / (effect.containerSize * 1.5f)
            ) * effect.containerSize
            val rounded = offsetPx.roundToInt()
            if (effect.orientation == Orientation.Vertical) {
                placeable.placeRelative(0, rounded)
            } else {
                placeable.placeRelative(rounded, 0)
            }
        }
    }
}

private class OverscrollNodeElement(
    private val node: OverscrollLayoutNode,
) : ModifierNodeElement<OverscrollLayoutNode>() {

    override fun create(): OverscrollLayoutNode = node

    override fun update(node: OverscrollLayoutNode) {}

    override fun equals(other: Any?): Boolean =
        other is OverscrollNodeElement && other.node === node

    override fun hashCode(): Int = System.identityHashCode(node)
}

@ExperimentalFoundationApi
@Composable
fun rememberIOSOverScrollEffect(
    orientation: Orientation = Orientation.Vertical,
    scope: CoroutineScope = rememberCoroutineScope(),
): OverscrollEffect = remember(orientation, scope) {
    IosOverscrollEffect(scope, orientation)
}
