package com.aesthetic.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 全局微动缩放反馈（无阴影闪烁）：
 * 点击/按下时平滑微缩（scale 0.95f），松手弹性回弹（scale 1.0f）。
 * 彻底消除 Android 默认灰白闪烁的波纹/阴影，实现类似 iOS 图标与控件的灵动触觉微动反馈。
 */
object ScaleIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): Modifier.Node {
        return ScaleIndicationNode(interactionSource)
    }

    override fun hashCode(): Int = -1
    override fun equals(other: Any?): Boolean = other === this
}

private class ScaleIndicationNode(
    private val interactionSource: InteractionSource,
) : Modifier.Node(), DrawModifierNode {

    private val scaleAnim = Animatable(1f)
    private var job: Job? = null

    override fun onAttach() {
        super.onAttach()
        job = coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        // 按下立即迅速微缩（scale 0.95f，灵敏响应）
                        scaleAnim.animateTo(
                            targetValue = 0.95f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessHigh,
                            ),
                            block = { invalidateDraw() },
                        )
                    }
                    is PressInteraction.Release -> {
                        // 若轻点过快尚未缩至目标，先确保有微动再弹性回弹
                        if (scaleAnim.value > 0.96f) {
                            scaleAnim.snapTo(0.95f)
                            invalidateDraw()
                        }
                        // 松手回弹带弹性
                        scaleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            block = { invalidateDraw() },
                        )
                    }
                    is PressInteraction.Cancel -> {
                        scaleAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium,
                            ),
                            block = { invalidateDraw() },
                        )
                    }
                }
            }
        }
    }

    override fun onDetach() {
        super.onDetach()
        job?.cancel()
        job = null
    }

    override fun ContentDrawScope.draw() {
        val currentScale = scaleAnim.value
        if (currentScale == 1f) {
            drawContent()
        } else {
            scale(scaleX = currentScale, scaleY = currentScale, pivot = center) {
                this@draw.drawContent()
            }
        }
    }
}
