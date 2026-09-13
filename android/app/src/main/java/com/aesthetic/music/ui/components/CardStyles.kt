package com.aesthetic.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aesthetic.music.ui.theme.CardBorder

/**
 * 通用卡片外观：圆角 + 描边 + 渐变背景。
 * 深色主题用深色描边，浅色主题用白色亮边。
 *
 * 全 App 复用，避免各卡片重复 border + clip + background 三连。
 */
fun Modifier.appCard(
    background: Brush,
    shape: RoundedCornerShape = RoundedCornerShape(20.dp),
    borderWidth: Dp = 1.dp,
): Modifier {
    return this
        .border(borderWidth, CardBorder, shape)
        .clip(shape)
        .background(background)
}

/**
 * 卡片统一取色：由封面主色生成横向渐变背景 + 同色系强调色。
 *
 * @param coverUrl 封面图，用于提取主色调
 * @param defaultAccent 取色失败时的兜底强调色
 */
@Composable
fun rememberAppCardTheme(
    coverUrl: String?,
    defaultAccent: Color,
): AppCardTheme {
    val colors = rememberDominantColors(coverUrl, defaultAccent)
    return remember(colors) {
        AppCardTheme(
            background = Brush.horizontalGradient(listOf(colors.bgStart, colors.bgEnd)),
            accent = colors.buttonColor,
            bgStart = colors.bgStart,
        )
    }
}

/** [rememberAppCardTheme] 的产物：横向渐变背景 + 同色系强调色。 */
class AppCardTheme(
    /** 卡片横向渐变背景（由封面主色生成） */
    val background: Brush,
    /** 同色系深色强调色：CTA 按钮、圆点、播放图标等 */
    val accent: Color,
    /** 渐变起始色，供自定义描边 / 叠加使用 */
    val bgStart: Color,
)
