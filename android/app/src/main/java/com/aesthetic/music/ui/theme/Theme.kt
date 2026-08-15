package com.aesthetic.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightAmber = Color(0xFFFEF3C7)
private val DarkAmber = Color(0xFF3E2F14)

private val LightColors = lightColorScheme(
    primary = Color(0xFF534332),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEFEAD8),
    onPrimaryContainer = Color(0xFF786244),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = LightAmber,
    onSecondaryContainer = Color(0xFFB45309),
    background = Color(0xFFF6F5F2),
    onBackground = Color(0xFF1C1917),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1917),
    surfaceVariant = Color(0xFFF5F5F4),
    onSurfaceVariant = Color(0xFF78716C),
    outline = Color(0xFFE7E5E4),
    error = Color(0xFFF43F5E),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8A7354),
    onPrimary = Color(0xFF1E1E22),
    primaryContainer = Color(0xFF2E2A22),
    onPrimaryContainer = Color(0xFFD8C9A8),
    secondary = Color(0xFFF59E0B),
    onSecondary = Color(0xFF1E1E22),
    secondaryContainer = DarkAmber,
    onSecondaryContainer = Color(0xFFFDE68A),
    background = Color(0xFF141416),
    onBackground = Color(0xFFF2F1EE),
    surface = Color(0xFF1E1E22),
    onSurface = Color(0xFFF2F1EE),
    surfaceVariant = Color(0xFF26262B),
    onSurfaceVariant = Color(0xFF9E9891),
    outline = Color(0xFF333238),
    error = Color(0xFFF43F5E),
)

// 全局少圆角：普通组件（卡片/弹窗/按钮）全部矩形，层次靠颜色与间距区分
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

@Composable
fun AestheticMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (Palette.isDark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
