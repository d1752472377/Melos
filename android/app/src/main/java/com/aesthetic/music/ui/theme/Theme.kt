package com.aesthetic.music.ui.theme

import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.aesthetic.music.ui.components.ScaleIndication

/**
 * 全局主题入口。
 *
 * ## 配色
 * 换成 GZ Music 的真值 [GzLightColorScheme] / [GzDarkColorScheme]
 * （逐值还原自 `ThemeKt.java:57` / `:63`，见 GzColorScheme.kt）。
 * Melos 原先自定的蓝紫强调色 `LightColors` / `DarkColors` 已删除。
 *
 * ## 字体与圆角
 * GZ 的 `ThemeKt.java:604` 调用的是
 * `iu4.b(colorScheme, shapes, typography = new rg8(), content)`，
 * 其中 `rg8` = `androidx.compose.material3.Typography`，`new rg8()` 就是**默认构造**；
 * `shapes` 走的是默认参数（掩码位标记为未传）。也就是说 **GZ 用的就是 Material3 自带的
 * 默认 Typography 与 Shapes，自己并没有定制**。
 * 因此这里也去掉 Melos 原有的 `AppTypography` 与 `AppShapes`，改用 M3 默认值，与 GZ 一致。
 *
 * 需要回退时：在下面的 `MaterialTheme(...)` 上加回
 * `typography = AppTypography`（仍保留在 Type.kt 里）与 `shapes = AppShapes` 即可。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AestheticMusicTheme(content: @Composable () -> Unit) {
    val isDark = Palette.isDark
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? android.app.Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDark
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDark
            }
        }
    }
    CompositionLocalProvider(
        LocalIndication provides ScaleIndication,
        LocalRippleConfiguration provides null,
        // GZ 样式层（GlassTokens / AuroraBackground / LiquidGlass*）全部通过 appIsDark() 取深浅色，
        // 这里提供唯一来源，保证与 Palette.isDark 永远一致。
        LocalAppIsDark provides isDark,
    ) {
        MaterialTheme(
            colorScheme = if (isDark) GzDarkColorScheme else GzLightColorScheme,
            content = content,
        )
    }
}
